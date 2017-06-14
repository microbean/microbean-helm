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
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;

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
public class TillerInstaller {


  /*
   * Static fields.
   */

  
  /*
   * Atomic static fields.
   */
  
  private static final Integer ONE = Integer.valueOf(1);

  private static final String DEFAULT_IMAGE_PULL_POLICY = "IfNotPresent";
  
  private static final String DEFAULT_NAME = "tiller";

  private static final String DEFAULT_NAMESPACE = "kube-system";

  private static final String TILLER_TLS_CERTS_PATH = "/etc/certs";

  /**
   * The version of Tiller to install.
   */
  public static final String VERSION = "2.4.2";

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


  public TillerInstaller() {
    this(new DefaultKubernetesClient());
  }
  
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

  public void init(final boolean upgrade,
                   String namespace,
                   final String deploymentName,
                   final String serviceName,
                   Map<String, String> labels,
                   final String serviceAccountName,
                   final String imageName,
                   final String imagePullPolicy,
                   final boolean hostNetwork,
                   final boolean tls,
                   final boolean verifyTls,
                   final URI tlsKeyUri,
                   final URI tlsCertUri,
                   final URI tlsCaCertUri)
    throws IOException {
    namespace = normalizeNamespace(namespace);
    labels = normalizeLabels(labels);
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
                      final String imagePullPolicy,
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
                            deploymentName,
                            labels,
                            serviceAccountName,
                            imageName,
                            imagePullPolicy,
                            hostNetwork,
                            tls,
                            verifyTls);
        
    this.kubernetesClient.extensions().deployments().inNamespace(namespace).create(deployment);

    final Service service = this.createService(namespace, serviceName, labels);
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
                      String imageName,
                      String imagePullPolicy,
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
  
  protected Service createService(String namespace,
                                  String serviceName,
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
                                        String deploymentName,
                                        Map<String, String> labels,
                                        final String serviceAccountName,
                                        final String imageName,
                                        final String imagePullPolicy,
                                        final boolean hostNetwork,
                                        final boolean tls,
                                        final boolean verifyTls) {
    labels = normalizeLabels(labels);

    final Deployment deployment = new Deployment();

    final ObjectMeta metadata = new ObjectMeta();
    metadata.setNamespace(normalizeNamespace(namespace));
    metadata.setName(normalizeDeploymentName(deploymentName));
    metadata.setLabels(labels);
    deployment.setMetadata(metadata);

    deployment.setSpec(this.createDeploymentSpec(labels, serviceAccountName, imageName, imagePullPolicy, namespace, hostNetwork, tls, verifyTls));
    return deployment;
  }

  protected Secret createSecret(String namespace,
                                final URI tlsKeyUri,
                                final URI tlsCertUri,
                                final URI tlsCaCertUri,
                                Map<String, String> labels)
    throws IOException {
    labels = normalizeLabels(labels);
    
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
    metadata.setLabels(labels);
    secret.setMetadata(metadata);
    
    return secret;
  }
  
  protected DeploymentSpec createDeploymentSpec(Map<String, String> labels,
                                                final String serviceAccountName,
                                                final String imageName,
                                                final String imagePullPolicy,
                                                final String namespace,
                                                final boolean hostNetwork,
                                                final boolean tls,
                                                final boolean verifyTls) {    
    labels = normalizeLabels(labels);
    final DeploymentSpec deploymentSpec = new DeploymentSpec();
    final PodTemplateSpec podTemplateSpec = new PodTemplateSpec();
    final ObjectMeta metadata = new ObjectMeta();
    metadata.setLabels(labels);
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
                                      final String imagePullPolicy,
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

  protected ServiceSpec createServiceSpec(Map<String, String> labels) {
    labels = normalizeLabels(labels);
    final ServiceSpec serviceSpec = new ServiceSpec();
    serviceSpec.setType("ClusterIP");

    final ServicePort servicePort = new ServicePort();
    servicePort.setName(DEFAULT_NAME);
    servicePort.setPort(Integer.valueOf(44134));
    servicePort.setTargetPort(new IntOrString(DEFAULT_NAME));
    serviceSpec.setPorts(Arrays.asList(servicePort));

    serviceSpec.setSelector(labels);
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
      labels = new HashMap<>();
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
  
  protected static final String normalizeImagePullPolicy(final String imagePullPolicy) {
    if (!"Always".equals(imagePullPolicy) &&
        !"IfNotPresent".equals(imagePullPolicy) &&
        !"Never".equals(imagePullPolicy)) {
      return DEFAULT_IMAGE_PULL_POLICY;
    } else {
      return imagePullPolicy;
    }
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
      final InputStream uriStream = url.openStream();
      if (uriStream == null) {
        returnValue = null;
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
  
}
