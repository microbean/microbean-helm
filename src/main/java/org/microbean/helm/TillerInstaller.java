/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2017 MicroBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.helm;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

import java.net.URI;
import java.net.URL;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretVolumeSource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;

import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;

import org.microbean.development.annotation.Experimental;

/**
 * A class that idiomatically but faithfully emulates the
 * Tiller-installing behavior of the {@code helm init} command.
 *
 * <p>In general, this class follows the logic as expressed in <a
 * href="https://github.com/kubernetes/helm/blob/master/cmd/helm/installer/install.go">the
 * {@code install.go} source code from the Helm project</a>,
 * problematic or not.  The intent is to have an installer, usable as
 * an idiomatic Java library, that behaves just like {@code helm
 * init}.</p>
 *
 * <p><strong>Note:</strong> This class is experimental and its API is
 * subject to change without notice.</p>
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #init()
 *
 * @see <a
 * href="https://github.com/kubernetes/helm/blob/master/cmd/helm/installer/install.go">The
 * {@code install.go} source code from the Helm project</a>
 */
@Experimental
public class TillerInstaller {


  /*
   * Static fields.
   */

  
  /*
   * Atomic static fields.
   */
  
  private static final Integer ONE = Integer.valueOf(1);

  private static final ImagePullPolicy DEFAULT_IMAGE_PULL_POLICY = ImagePullPolicy.IF_NOT_PRESENT;
  
  private static final String DEFAULT_NAME = "tiller";

  private static final String DEFAULT_NAMESPACE = "kube-system";

  private static final String TILLER_TLS_CERTS_PATH = "/etc/certs";

  /**
   * The version of Tiller to install.
   */
  public static final String VERSION = "2.6.2";

  /*
   * Derivative static fields.
   */
  
  private static final String DEFAULT_IMAGE_NAME = "gcr.io/kubernetes-helm/" + DEFAULT_NAME + ":v" + VERSION;

  private static final String DEFAULT_DEPLOYMENT_NAME = DEFAULT_NAME + "-deploy";

  private static final String SECRET_NAME = DEFAULT_NAME + "-secret";


  /*
   * Instance fields.
   */

  
  private final KubernetesClient kubernetesClient;

  private final String tillerNamespace;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link TillerInstaller}, using a new {@link
   * DefaultKubernetesClient}.
   *
   * @see #TillerInstaller(KubernetesClient)
   */
  public TillerInstaller() {
    this(new DefaultKubernetesClient());
  }

  /**
   * Creates a new {@link TillerInstaller}.
   *
   * @param kubernetesClient the {@link KubernetesClient} to use to
   * communicate with Kubernetes; must not be {@code null}
   *
   * @exception NullPointerException if {@code kubernetesClient} is
   * {@code null}
   */
  public TillerInstaller(final KubernetesClient kubernetesClient) {
    super();
    Objects.requireNonNull(kubernetesClient);
    this.kubernetesClient = kubernetesClient;
    final String tillerNamespace = System.getenv("TILLER_NAMESPACE");
    if (tillerNamespace == null || tillerNamespace.isEmpty()) {
      this.tillerNamespace = DEFAULT_NAMESPACE;
    } else {
      this.tillerNamespace = tillerNamespace;
    }
  }


  /*
   * Instance methods.
   */
  

  
  public void init() {
    try {
      this.init(false, null, null, null, null, null, null, null, false, false, false, null, null, null);
    } catch (final IOException willNotHappen) {
      throw new AssertionError(willNotHappen);
    }
  }
  
  public void init(final boolean upgrade) {
    try {
      this.init(upgrade, null, null, null, null, null, null, null, false, false, false, null, null, null);
    } catch (final IOException willNotHappen) {
      throw new AssertionError(willNotHappen);
    }
  }

  /**
   * Attempts to {@linkplain #install(String, String, String, Map,
   * String, String, ImagePullPolicy, boolean, boolean, boolean, URI,
   * URI, URI) install} Tiller into the Kubernetes cluster, silently
   * returning if Tiller is already installed and {@code upgrade} is
   * {@code false}, or {@linkplain #upgrade(String, String, String,
   * String, String, ImagePullPolicy, Map) upgrading} the Tiller
   * installation if {@code upgrade} is {@code true} and a newer
   * version of Tiller is available.
   *
   * @param upgrade whether or not to attempt an upgrade if Tiller is
   * already installed
   *
   * @param namespace the Kubernetes namespace into which Tiller will
   * be installed, if it is not already installed; may be {@code null}
   * in which case a default will be used
   *
   * @param deploymentName the name that the Kubernetes Deployment
   * representing Tiller will have; may be {@code null}; {@code
   * tiller-deploy} by default
   *
   * @param serviceName the name that the Kubernetes Service
   * representing Tiller will have; may be {@code null}; {@code
   * tiller-deploy} (yes, {@code tiller-deploy}) by default
   *
   * @param labels the Kubernetes Labels that will be applied to
   * various Kubernetes resources representing Tiller; may be {@code
   * null} in which case a {@link Map} consisting of a label of {@code
   * app} with a value of {@code helm} and a label of {@code name}
   * with a value of {@code tiller} will be used instead
   *
   * @param serviceAccountName the name of the Kubernetes Service
   * Account that Tiller should use; may be {@code null} in which case
   * the default Service Account will be used instead
   *
   * @param imageName the name of the Docker image that contains the
   * Tiller code; may be {@code null} in which case {@code
   * gcr.io/kubernetes-helm/tiller:v2.5.0} will be used instead
   *
   * @param imagePullPolicy an {@link ImagePullPolicy} specifying how
   * the Tiller image should be pulled; may be {@code null} in which
   * case {@link ImagePullPolicy#IF_NOT_PRESENT} will be used instead
   *
   * @param hostNetwork the value to be used for the {@linkplain
   * PodSpec#setHostNetwork(Boolean) <code>hostNetwork</code>
   * property} of the Tiller Pod's {@link PodSpec}
   *
   * @param tls whether Tiller's conversations with Kubernetes will be
   * encrypted using TLS
   *
   * @param verifyTls whether, if and only if {@code tls} is {@code
   * true}, additional TLS-related verification will be performed
   *
   * @param tlsKeyUri a {@link URI} to the public key used during TLS
   * communication with Kubernetes; may be {@code null} if {@code tls}
   * is {@code false}
   *
   * @param tlsCertUri a {@link URI} to the certificate used during
   * TLS communication with Kubernetes; may be {@code null} if {@code
   * tls} is {@code false}
   *
   * @param tlsCaCertUri a {@link URI} to the certificate authority
   * used during TLS communication with Kubernetes; may be {@code
   * null} if {@code tls} is {@code false}
   *
   * @exception IOException if a communication error occurs
   *
   * @see #install(String, String, String, Map, String, String,
   * ImagePullPolicy, boolean, boolean, boolean, URI, URI, URI)
   *
   * @see #upgrade(String, String, String, String, String,
   * ImagePullPolicy, Map)
   */
  public void init(final boolean upgrade,
                   String namespace,
                   String deploymentName,
                   String serviceName,
                   Map<String, String> labels,
                   String serviceAccountName,
                   String imageName,
                   final ImagePullPolicy imagePullPolicy,
                   final boolean hostNetwork,
                   final boolean tls,
                   final boolean verifyTls,
                   final URI tlsKeyUri,
                   final URI tlsCertUri,
                   final URI tlsCaCertUri)
    throws IOException {
    namespace = normalizeNamespace(namespace);
    deploymentName = normalizeDeploymentName(deploymentName);
    serviceName = normalizeServiceName(serviceName);
    labels = normalizeLabels(labels);
    serviceAccountName = normalizeServiceAccountName(serviceAccountName);
    imageName = normalizeImageName(imageName);
    
    try {
      this.install(namespace,
                   deploymentName,
                   serviceName,
                   labels,
                   serviceAccountName,
                   imageName,
                   imagePullPolicy,
                   hostNetwork,
                   tls,
                   verifyTls,
                   tlsKeyUri,
                   tlsCertUri,
                   tlsCaCertUri);
    } catch (final KubernetesClientException kubernetesClientException) {
      final Status status = kubernetesClientException.getStatus();
      if (status == null || !"AlreadyExists".equals(status.getReason())) {
        throw kubernetesClientException;
      } else if (upgrade) {
        this.upgrade(namespace,
                     deploymentName,
                     serviceName,
                     serviceAccountName,
                     imageName,
                     imagePullPolicy,
                     labels);
      }
    }
  }

  public void install() {
    try {
      this.install(null, null, null, null, null, null, null, false, false, false, null, null, null);
    } catch (final IOException willNotHappen) {
      throw new AssertionError(willNotHappen);
    }
  }

  public void install(String namespace,
                      final String deploymentName,
                      final String serviceName,
                      Map<String, String> labels,
                      final String serviceAccountName,
                      final String imageName,
                      final ImagePullPolicy imagePullPolicy,
                      final boolean hostNetwork,
                      final boolean tls,
                      final boolean verifyTls,
                      final URI tlsKeyUri,
                      final URI tlsCertUri,
                      final URI tlsCaCertUri)
    throws IOException {
    namespace = normalizeNamespace(namespace);
    labels = normalizeLabels(labels);
    final Deployment deployment =
      this.createDeployment(namespace,
                            normalizeDeploymentName(deploymentName),
                            labels,
                            normalizeServiceAccountName(serviceAccountName),
                            normalizeImageName(imageName),
                            imagePullPolicy,
                            hostNetwork,
                            tls,
                            verifyTls);
        
    this.kubernetesClient.extensions().deployments().inNamespace(namespace).create(deployment);

    final Service service = this.createService(namespace, normalizeServiceName(serviceName), labels);
    this.kubernetesClient.services().inNamespace(namespace).create(service);
    
    if (tls) {
      final Secret secret =
        this.createSecret(namespace,
                          tlsKeyUri,
                          tlsCertUri,
                          tlsCaCertUri,
                          labels);
      this.kubernetesClient.secrets().inNamespace(namespace).create(secret);
    }
    
  }

  public void upgrade() {
    this.upgrade(null, null, null, null, null, null, null);
  }
  
  public void upgrade(String namespace,
                      final String deploymentName,
                      String serviceName,
                      final String serviceAccountName,
                      final String imageName,
                      final ImagePullPolicy imagePullPolicy,
                      final Map<String, String> labels) {
    namespace = normalizeNamespace(namespace);
    serviceName = normalizeServiceName(serviceName);

    this.kubernetesClient.extensions()
      .deployments()
      .inNamespace(namespace)
      .withName(normalizeDeploymentName(deploymentName))
      .edit()
      .editSpec()
      .editTemplate()
      .editSpec()
      .editContainer(0)
      .withImage(normalizeImageName(imageName))
      .withImagePullPolicy(normalizeImagePullPolicy(imagePullPolicy))
      .and()
      .withServiceAccountName(normalizeServiceAccountName(serviceAccountName))
      .endSpec()
      .endTemplate()
      .endSpec()
      .done();

    // TODO: this way of emulating install.go's check to see if the
    // Service exists...not sure it's right
    final Service service = this.kubernetesClient.services()
      .inNamespace(namespace)
      .withName(serviceName)
      .get();
    if (service == null) {
      this.createService(namespace, serviceName, normalizeLabels(labels));
    }
    
  }
  
  protected Service createService(final String namespace,
                                  final String serviceName,
                                  Map<String, String> labels) {
    labels = normalizeLabels(labels);

    final Service service = new Service();
    
    final ObjectMeta metadata = new ObjectMeta();
    metadata.setNamespace(normalizeNamespace(namespace));
    metadata.setName(normalizeServiceName(serviceName));
    metadata.setLabels(labels);
    
    service.setMetadata(metadata);
    service.setSpec(this.createServiceSpec(labels));

    return service;
  }

  protected Deployment createDeployment(String namespace,
                                        final String deploymentName,
                                        Map<String, String> labels,
                                        final String serviceAccountName,
                                        final String imageName,
                                        final ImagePullPolicy imagePullPolicy,
                                        final boolean hostNetwork,
                                        final boolean tls,
                                        final boolean verifyTls) {
    namespace = normalizeNamespace(namespace);
    labels = normalizeLabels(labels);

    final Deployment deployment = new Deployment();

    final ObjectMeta metadata = new ObjectMeta();
    metadata.setNamespace(namespace);
    metadata.setName(normalizeDeploymentName(deploymentName));
    metadata.setLabels(labels);
    deployment.setMetadata(metadata);

    deployment.setSpec(this.createDeploymentSpec(labels, serviceAccountName, imageName, imagePullPolicy, namespace, hostNetwork, tls, verifyTls));
    return deployment;
  }

  protected Secret createSecret(final String namespace,
                                final URI tlsKeyUri,
                                final URI tlsCertUri,
                                final URI tlsCaCertUri,
                                final Map<String, String> labels)
    throws IOException {
    
    final Secret secret = new Secret();
    secret.setType("Opaque");

    final Map<String, String> secretData = new HashMap<>();
    
    try (final InputStream tlsKeyStream = read(tlsKeyUri)) {
      if (tlsKeyStream != null) {
        secretData.put("tls.key", Base64.getEncoder().encodeToString(toByteArray(tlsKeyStream)));
      }
    }

    try (final InputStream tlsCertStream = read(tlsCertUri)) {
      if (tlsCertStream != null) {
        secretData.put("tls.crt", Base64.getEncoder().encodeToString(toByteArray(tlsCertStream)));
      }
    }
    
    try (final InputStream tlsCaCertStream = read(tlsCaCertUri)) {
      if (tlsCaCertStream != null) {
        secretData.put("ca.crt", Base64.getEncoder().encodeToString(toByteArray(tlsCaCertStream)));
      }
    }

    secret.setData(secretData);

    final ObjectMeta metadata = new ObjectMeta();
    metadata.setNamespace(normalizeNamespace(namespace));
    metadata.setName(SECRET_NAME);
    metadata.setLabels(normalizeLabels(labels));
    secret.setMetadata(metadata);
    
    return secret;
  }
  
  protected DeploymentSpec createDeploymentSpec(final Map<String, String> labels,
                                                final String serviceAccountName,
                                                final String imageName,
                                                final ImagePullPolicy imagePullPolicy,
                                                final String namespace,
                                                final boolean hostNetwork,
                                                final boolean tls,
                                                final boolean verifyTls) {    
    final DeploymentSpec deploymentSpec = new DeploymentSpec();
    final PodTemplateSpec podTemplateSpec = new PodTemplateSpec();
    final ObjectMeta metadata = new ObjectMeta();
    metadata.setLabels(normalizeLabels(labels));
    podTemplateSpec.setMetadata(metadata);
    final PodSpec podSpec = new PodSpec();
    podSpec.setServiceAccountName(normalizeServiceAccountName(serviceAccountName));
    podSpec.setContainers(Arrays.asList(this.createContainer(imageName, imagePullPolicy, namespace, tls, verifyTls)));
    podSpec.setHostNetwork(Boolean.valueOf(hostNetwork));
    final Map<String, String> nodeSelector = new HashMap<>();
    nodeSelector.put("beta.kubernetes.io/os", "linux");
    podSpec.setNodeSelector(nodeSelector);    
    if (tls) {
      final Volume volume = new Volume();
      volume.setName(DEFAULT_NAME + "-certs");
      final SecretVolumeSource secretVolumeSource = new SecretVolumeSource();
      secretVolumeSource.setSecretName(SECRET_NAME);
      volume.setSecret(secretVolumeSource);
      podSpec.setVolumes(Arrays.asList(volume));
    }
    podTemplateSpec.setSpec(podSpec);
    deploymentSpec.setTemplate(podTemplateSpec);    
    return deploymentSpec;
  }

  protected Container createContainer(final String imageName,
                                      final ImagePullPolicy imagePullPolicy,
                                      final String namespace,
                                      final boolean tls,
                                      final boolean verifyTls) {
    final Container container = new Container();
    container.setName(DEFAULT_NAME);
    container.setImage(normalizeImageName(imageName));
    container.setImagePullPolicy(normalizeImagePullPolicy(imagePullPolicy));

    final ContainerPort containerPort = new ContainerPort();
    containerPort.setContainerPort(Integer.valueOf(44134));
    containerPort.setName(DEFAULT_NAME);
    container.setPorts(Arrays.asList(containerPort));

    final List<EnvVar> env = new ArrayList<>();
    
    final EnvVar tillerNamespace = new EnvVar();
    tillerNamespace.setName("TILLER_NAMESPACE");
    tillerNamespace.setValue(normalizeNamespace(namespace));
    env.add(tillerNamespace);

    if (tls) {
      final EnvVar tlsVerify = new EnvVar();
      tlsVerify.setName("TILLER_TLS_VERIFY");
      tlsVerify.setValue(verifyTls ? "1" : "");
      env.add(tlsVerify);
      
      final EnvVar tlsEnable = new EnvVar();
      tlsEnable.setName("TILLER_TLS_ENABLE");
      tlsEnable.setValue("1");
      env.add(tlsEnable);

      final EnvVar tlsCerts = new EnvVar();
      tlsCerts.setName("TILLER_TLS_CERTS");
      tlsCerts.setValue(TILLER_TLS_CERTS_PATH);
      env.add(tlsCerts);
    }
    
    container.setEnv(env);
    

    final IntOrString port44135 = new IntOrString(Integer.valueOf(44135));
    
    final HTTPGetAction livenessHttpGetAction = new HTTPGetAction();
    livenessHttpGetAction.setPath("/liveness");
    livenessHttpGetAction.setPort(port44135);
    final Probe livenessProbe = new Probe();
    livenessProbe.setHttpGet(livenessHttpGetAction);
    livenessProbe.setInitialDelaySeconds(ONE);
    livenessProbe.setTimeoutSeconds(ONE);
    container.setLivenessProbe(livenessProbe);

    final HTTPGetAction readinessHttpGetAction = new HTTPGetAction();
    readinessHttpGetAction.setPath("/readiness");
    readinessHttpGetAction.setPort(port44135);
    final Probe readinessProbe = new Probe();
    readinessProbe.setHttpGet(readinessHttpGetAction);
    readinessProbe.setInitialDelaySeconds(ONE);
    readinessProbe.setTimeoutSeconds(ONE);
    container.setReadinessProbe(readinessProbe);

    if (tls) {
      final VolumeMount volumeMount = new VolumeMount();
      volumeMount.setName(DEFAULT_NAME + "-certs");
      volumeMount.setReadOnly(true);
      volumeMount.setMountPath(TILLER_TLS_CERTS_PATH);
      container.setVolumeMounts(Arrays.asList(volumeMount));
    }

    return container;
  }

  protected ServiceSpec createServiceSpec(final Map<String, String> labels) {
    final ServiceSpec serviceSpec = new ServiceSpec();
    serviceSpec.setType("ClusterIP");

    final ServicePort servicePort = new ServicePort();
    servicePort.setName(DEFAULT_NAME);
    servicePort.setPort(Integer.valueOf(44134));
    servicePort.setTargetPort(new IntOrString(DEFAULT_NAME));
    serviceSpec.setPorts(Arrays.asList(servicePort));

    serviceSpec.setSelector(normalizeLabels(labels));
    return serviceSpec;
  }

  protected final String normalizeNamespace(String namespace) {
    if (namespace == null || namespace.isEmpty()) {
      namespace = this.tillerNamespace;
      if (namespace == null || namespace.isEmpty()) {
        namespace = DEFAULT_NAMESPACE;
      }
    }
    return namespace;
  }


  /*
   * Static methods.
   */

  
  protected static final Map<String, String> normalizeLabels(Map<String, String> labels) {
    if (labels == null) {
      labels = new HashMap<>(7);
    }
    if (!labels.containsKey("app")) {
      labels.put("app", "helm");
    }
    if (!labels.containsKey("name")) {
      labels.put("name", DEFAULT_NAME);
    }
    return labels;
  }
  
  protected static final String normalizeDeploymentName(final String deploymentName) {
    if (deploymentName == null || deploymentName.isEmpty()) {
      return DEFAULT_DEPLOYMENT_NAME;
    } else {
      return deploymentName;
    }
  }
  
  protected static final String normalizeImageName(final String imageName) {
    if (imageName == null || imageName.isEmpty()) {
      return DEFAULT_IMAGE_NAME;
    } else {
      return imageName;
    }
  }
  
  private static final String normalizeImagePullPolicy(ImagePullPolicy imagePullPolicy) {
    if (imagePullPolicy == null) {
      imagePullPolicy = DEFAULT_IMAGE_PULL_POLICY;      
    }
    assert imagePullPolicy != null;
    return imagePullPolicy.toString();
  }

  protected static final String normalizeServiceAccountName(final String serviceAccountName) {
    return serviceAccountName == null ? "" : serviceAccountName;
  }
  
  protected static final String normalizeServiceName(final String serviceName) {
    if (serviceName == null || serviceName.isEmpty()) {
      return DEFAULT_DEPLOYMENT_NAME; // yes, DEFAULT_*DEPLOYMENT*_NAME
    } else {
      return serviceName;
    }
  }

  private static final InputStream read(final URI uri) throws IOException {
    final InputStream returnValue;
    if (uri == null) {
      returnValue = null;
    } else {
      final URL url = uri.toURL();
      assert url != null;
      final InputStream uriStream = url.openStream();
      if (uriStream == null) {
        returnValue = null;
      } else if (uriStream instanceof BufferedInputStream) {
        returnValue = (BufferedInputStream)uriStream;
      } else {
        returnValue = new BufferedInputStream(uriStream);
      }
    }
    return returnValue;
  }

  private static final byte[] toByteArray(final InputStream inputStream) throws IOException {
    // Interesting historical anecdotes at https://stackoverflow.com/a/1264737/208288.
    byte[] returnValue = null;
    if (inputStream != null) {
      final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      returnValue = new byte[4096]; // arbitrary size
      int bytesRead;
      while ((bytesRead = inputStream.read(returnValue, 0, returnValue.length)) != -1) {
        buffer.write(returnValue, 0, bytesRead);
      }      
      buffer.flush();      
      returnValue = buffer.toByteArray();
    }
    return returnValue;
  }


  /*
   * Inner and nested classes.
   */


  /**
   * An {@code enum} representing valid values for a Kubernetes {@code
   * imagePullPolicy} field.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   */
  public static enum ImagePullPolicy {


    /**
     * An {@link ImagePullPolicy} indicating that a Docker image
     * should always be pulled.
     */
    ALWAYS("Always"),

    /**
     * An {@link ImagePullPolicy} indicating that a Docker image
     * should be pulled only if it is not already cached locally.
     */
    IF_NOT_PRESENT("IfNotPresent"),

    /**
     * An {@link ImagePullPolicy} indicating that a Docker image
     * should never be pulled.
     */
    NEVER("Never");

    /**
     * The actual valid Kubernetes value for this {@link
     * ImagePullPolicy}.
     *
     * <p>This field is never {@code null}.</p>
     */
    private final String value;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ImagePullPolicy}.
     *
     * @param value the valid Kubernetes value for this {@link
     * ImagePullPolicy}; must not be {@code null}
     *
     * @exception NullPointerException if {@code value} is {@code
     * null}
     */
    ImagePullPolicy(final String value) {
      Objects.requireNonNull(value);
      this.value = value;
    }


    /*
     * Instance methods.
     */
    

    /**
     * Returns the valid Kubernetes value for this {@link
     * ImagePullPolicy}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return the valid Kubernetes value for this {@link
     * ImagePullPolicy}; never {@code null}
     */
    @Override
    public final String toString() {
      return this.value;
    }
  }
  
}
