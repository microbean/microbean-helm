/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2017-2019 microBean.
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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.zafarkhaja.semver.Version;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodList;
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

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.HttpClientAware;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import io.fabric8.kubernetes.client.dsl.Listable;
import io.fabric8.kubernetes.client.dsl.Resource;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthCheckResponseOrBuilder;
import io.grpc.health.v1.HealthGrpc.HealthBlockingStub;

import org.microbean.development.annotation.Experimental;

import org.microbean.kubernetes.Pods;

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
 * <code>install.go</code> source code from the Helm project</a>
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
  public static final String VERSION = "2.12.3";

  /*
   * Derivative static fields.
   */

  private static final Pattern TILLER_VERSION_PATTERN = Pattern.compile(":v(.+)$");
  
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
   * @see #TillerInstaller(KubernetesClient, String)
   */
  public TillerInstaller() {
    this(new DefaultKubernetesClient(), null);
  }

  /**
   * Creates a new {@link TillerInstaller}.
   *
   * @param kubernetesClient the {@link KubernetesClient} to use to
   * communicate with Kubernetes; must not be {@code null}
   *
   * @param tillerNamespace the namespace into which to install
   * Tiller; may be {@code null} in which case the value of the {@link
   * TILLER_NAMESPACE} environment variable will be used&mdash;if that
   * is {@code null} then {@code kube-system} will be used instead
   *
   * @exception NullPointerException if {@code kubernetesClient} is
   * {@code null}
   *
   * @see #TillerInstaller(KubernetesClient, String)
   */
  public TillerInstaller(final KubernetesClient kubernetesClient) {
    this(kubernetesClient, null);
  }

  /**
   * Creates a new {@link TillerInstaller}.
   *
   * @param kubernetesClient the {@link KubernetesClient} to use to
   * communicate with Kubernetes; must not be {@code null}
   *
   * @param tillerNamespace the namespace into which to install
   * Tiller; may be {@code null} in which case the value of the {@link
   * TILLER_NAMESPACE} environment variable will be used&mdash;if that
   * is {@code null} then {@code kube-system} will be used instead
   *
   * @exception NullPointerException if {@code kubernetesClient} is
   * {@code null}
   */
  public TillerInstaller(final KubernetesClient kubernetesClient, String tillerNamespace) {
    super();
    Objects.requireNonNull(kubernetesClient);
    this.kubernetesClient = kubernetesClient;
    if (tillerNamespace == null || tillerNamespace.isEmpty()) {      
      tillerNamespace = System.getProperty("tiller.namespace", System.getenv("TILLER_NAMESPACE"));
    }
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
      this.init(false, null, null, 1, null, null, null, null, null, null, 0, false, false, false, null, null, null, -1L);
    } catch (final IOException willNotHappen) {
      throw new AssertionError(willNotHappen);
    }
  }
  
  public void init(final boolean upgrade) {
    try {
      this.init(upgrade, null, null, 1, null, null, null, null, null, null, 0, false, false, false, null, null, null, -1L);
    } catch (final IOException willNotHappen) {
      throw new AssertionError(willNotHappen);
    }
  }

  public void init(final boolean upgrade, final long tillerConnectionTimeout) {
    try {
      this.init(upgrade, null, null, 1, null, null, null, null, null, null, 0, false, false, false, null, null, null, tillerConnectionTimeout);
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
   * Tiller code; may be {@code null} in which case the Java {@link
   * String} <code>"gcr.io/kubernetes-helm/tiller:v" + {@value
   * #VERSION}</code> will be used instead
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
   * @see #init(boolean, String, String, String, Map, Map, String,
   * String, ImagePullPolicy, int, boolean, boolean, boolean, URI,
   * URI, URI, long)
   *
   * @see #install(String, String, String, Map, Map, String, String,
   * ImagePullPolicy, int, boolean, boolean, boolean, URI, URI, URI)
   *
   * @see #upgrade(String, String, String, String, String,
   * ImagePullPolicy, Map)
   *
   * @deprecated Please use the {@link #init(boolean, String, String,
   * String, Map, Map, String, String, ImagePullPolicy, int, boolean,
   * boolean, boolean, URI, URI, URI, long)} method instead.
   */
  @Deprecated
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
    this.init(upgrade,
              namespace,
              deploymentName,
              1,
              serviceName,
              labels,
              null,
              serviceAccountName,
              imageName,
              imagePullPolicy,
              0,
              hostNetwork,
              tls,
              verifyTls,
              tlsKeyUri,
              tlsCertUri,
              tlsCaCertUri,
              -1L);
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
   * @param nodeSelector a {@link Map} representing labels that will
   * be written as a node selector; may be {@code null}
   *
   * @param serviceAccountName the name of the Kubernetes Service
   * Account that Tiller should use; may be {@code null} in which case
   * the default Service Account will be used instead
   *
   * @param imageName the name of the Docker image that contains the
   * Tiller code; may be {@code null} in which case the Java {@link
   * String} <code>"gcr.io/kubernetes-helm/tiller:v" + {@value
   * #VERSION}</code> will be used instead
   *
   * @param imagePullPolicy an {@link ImagePullPolicy} specifying how
   * the Tiller image should be pulled; may be {@code null} in which
   * case {@link ImagePullPolicy#IF_NOT_PRESENT} will be used instead
   *
   * @param maxHistory the maximum number of release versions stored
   * per release; a value that is less than or equal to zero means
   * there is effectively no limit
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
   * @param tillerConnectionTimeout the number of milliseconds to wait
   * for a Tiller pod to become ready; if less than {@code 0} no wait
   * will occur
   *
   * @exception IOException if a communication error occurs
   *
   * @see #install(String, String, String, Map, Map, String, String,
   * ImagePullPolicy, int, boolean, boolean, boolean, URI, URI, URI)
   *
   * @see #upgrade(String, String, String, String, String,
   * ImagePullPolicy, Map)
   */
  public void init(final boolean upgrade,
                   String namespace,
                   String deploymentName,
                   String serviceName,
                   Map<String, String> labels,
                   Map<String, String> nodeSelector,
                   String serviceAccountName,
                   String imageName,
                   final ImagePullPolicy imagePullPolicy,
                   final int maxHistory,
                   final boolean hostNetwork,
                   final boolean tls,
                   final boolean verifyTls,
                   final URI tlsKeyUri,
                   final URI tlsCertUri,
                   final URI tlsCaCertUri,
                   final long tillerConnectionTimeout)
    throws IOException {
    this.init(upgrade,
              namespace,
              deploymentName,
              1, /* one replica */
              serviceName,
              labels,
              nodeSelector,
              serviceAccountName,
              imageName,
              imagePullPolicy,
              maxHistory,
              hostNetwork,
              tls,
              verifyTls,
              tlsKeyUri,
              tlsCertUri,
              tlsCaCertUri,
              tillerConnectionTimeout);
  }

  public void init(final boolean upgrade,
                   String namespace,
                   String deploymentName,
                   int replicas,
                   String serviceName,
                   Map<String, String> labels,
                   Map<String, String> nodeSelector,
                   String serviceAccountName,
                   String imageName,
                   final ImagePullPolicy imagePullPolicy,
                   final int maxHistory,
                   final boolean hostNetwork,
                   final boolean tls,
                   final boolean verifyTls,
                   final URI tlsKeyUri,
                   final URI tlsCertUri,
                   final URI tlsCaCertUri,
                   final long tillerConnectionTimeout)
    throws IOException {
    namespace = normalizeNamespace(namespace);
    deploymentName = normalizeDeploymentName(deploymentName);
    replicas = Math.max(1, replicas);
    serviceName = normalizeServiceName(serviceName);
    labels = normalizeLabels(labels);
    serviceAccountName = normalizeServiceAccountName(serviceAccountName);
    imageName = normalizeImageName(imageName);
    
    try {
      this.install(namespace,
                   deploymentName,
                   replicas,
                   serviceName,
                   labels,
                   nodeSelector,
                   serviceAccountName,
                   imageName,
                   imagePullPolicy,
                   maxHistory,
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
                     replicas,
                     serviceName,
                     serviceAccountName,
                     imageName,
                     imagePullPolicy,
                     labels,
                     false);
      }
    }
    if (tillerConnectionTimeout >= 0 && this.kubernetesClient instanceof HttpClientAware) {
      this.ping(namespace, labels, tillerConnectionTimeout);
    }
  }

  public void install() {
    try {
      this.install(null, null, 1, null, null, null, null, null, null, 0, false, false, false, null, null, null);
    } catch (final IOException willNotHappen) {
      throw new AssertionError(willNotHappen);
    }
  }

  @Deprecated
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
    this.install(namespace,
                 deploymentName,
                 1,
                 serviceName,
                 labels,
                 null,
                 serviceAccountName,
                 imageName,
                 imagePullPolicy,
                 0, // maxHistory
                 hostNetwork,
                 tls,
                 verifyTls,
                 tlsKeyUri,
                 tlsCertUri,
                 tlsCaCertUri);
  }

  @Deprecated
  public void install(String namespace,
                      final String deploymentName,
                      final String serviceName,
                      Map<String, String> labels,
                      final Map<String, String> nodeSelector,
                      final String serviceAccountName,
                      final String imageName,
                      final ImagePullPolicy imagePullPolicy,
                      final int maxHistory,
                      final boolean hostNetwork,
                      final boolean tls,
                      final boolean verifyTls,
                      final URI tlsKeyUri,
                      final URI tlsCertUri,
                      final URI tlsCaCertUri)
    throws IOException {
    this.install(namespace,
                 deploymentName,
                 1,
                 serviceName,
                 labels,
                 nodeSelector,
                 serviceAccountName,
                 imageName,
                 imagePullPolicy,
                 maxHistory,
                 hostNetwork,
                 tls,
                 verifyTls,
                 tlsKeyUri,
                 tlsCertUri,
                 tlsCaCertUri);
  }

  public void install(String namespace,
                      final String deploymentName,
                      final int replicas,
                      final String serviceName,
                      Map<String, String> labels,
                      final Map<String, String> nodeSelector,
                      final String serviceAccountName,
                      final String imageName,
                      final ImagePullPolicy imagePullPolicy,
                      final int maxHistory,
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
                            Math.max(1, replicas),
                            labels,
                            nodeSelector,
                            normalizeServiceAccountName(serviceAccountName),
                            normalizeImageName(imageName),
                            imagePullPolicy,
                            maxHistory,
                            hostNetwork,
                            tls,
                            verifyTls);
        
    this.kubernetesClient.apps().deployments().inNamespace(namespace).create(deployment);

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
    this.upgrade(null, null, null, null, null, null, null, false);
  }

  @Deprecated
  public void upgrade(String namespace,
                      final String deploymentName,
                      String serviceName,
                      final String serviceAccountName,
                      final String imageName,
                      final ImagePullPolicy imagePullPolicy,
                      final Map<String, String> labels) {
    this.upgrade(namespace,
                 deploymentName,
                 1,
                 serviceName,
                 serviceAccountName,
                 imageName,
                 imagePullPolicy,
                 labels,
                 false);
  }

  @Deprecated
  public void upgrade(String namespace,
                      final String deploymentName,
                      String serviceName,
                      final String serviceAccountName,
                      final String imageName,
                      final ImagePullPolicy imagePullPolicy,
                      final Map<String, String> labels,
                      final boolean force) {
    this.upgrade(namespace,
                 deploymentName,
                 1,
                 serviceName,
                 serviceAccountName,
                 imageName,
                 imagePullPolicy,
                 labels,
                 force);
  }

  public void upgrade(String namespace,
                      final String deploymentName,
                      final int replicas,
                      String serviceName,
                      final String serviceAccountName,
                      final String imageName,
                      final ImagePullPolicy imagePullPolicy,
                      final Map<String, String> labels,
                      final boolean force) {
    namespace = normalizeNamespace(namespace);
    serviceName = normalizeServiceName(serviceName);

    final Resource<Deployment, DoneableDeployment> resource = this.kubernetesClient.apps()
      .deployments()
      .inNamespace(namespace)
      .withName(normalizeDeploymentName(deploymentName));
    assert resource != null;

    if (!force) {
      final String serverTillerImage = resource.get().getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
      assert serverTillerImage != null;
      
      if (isServerTillerVersionGreaterThanClientTillerVersion(serverTillerImage)) {
        throw new IllegalStateException(serverTillerImage + " is newer than " + VERSION + "; use force=true to force downgrade");
      }
    }
    
    resource.edit()
      .editSpec()
      .withNewReplicas(Math.max(1, replicas))
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

  @Deprecated
  protected Deployment createDeployment(String namespace,
                                        final String deploymentName,
                                        Map<String, String> labels,
                                        final String serviceAccountName,
                                        final String imageName,
                                        final ImagePullPolicy imagePullPolicy,
                                        final boolean hostNetwork,
                                        final boolean tls,
                                        final boolean verifyTls) {
    return this.createDeployment(namespace,
                                 deploymentName,
                                 1,
                                 labels,
                                 null,
                                 serviceAccountName,
                                 imageName,
                                 imagePullPolicy,
                                 0,
                                 hostNetwork,
                                 tls,
                                 verifyTls);
  }

  @Deprecated
  protected Deployment createDeployment(String namespace,
                                        final String deploymentName,
                                        Map<String, String> labels,
                                        final Map<String, String> nodeSelector,
                                        final String serviceAccountName,
                                        final String imageName,
                                        final ImagePullPolicy imagePullPolicy,
                                        final int maxHistory,
                                        final boolean hostNetwork,
                                        final boolean tls,
                                        final boolean verifyTls) {
    return this.createDeployment(namespace,
                                 deploymentName,
                                 1,
                                 labels,
                                 nodeSelector,
                                 serviceAccountName,
                                 imageName,
                                 imagePullPolicy,
                                 maxHistory,
                                 hostNetwork,
                                 tls,
                                 verifyTls);
  }

  protected Deployment createDeployment(String namespace,
                                        final String deploymentName,
                                        final int replicas,
                                        Map<String, String> labels,
                                        final Map<String, String> nodeSelector,
                                        final String serviceAccountName,
                                        final String imageName,
                                        final ImagePullPolicy imagePullPolicy,
                                        final int maxHistory,
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

    deployment.setSpec(this.createDeploymentSpec(Math.max(1, replicas),
                                                 labels,
                                                 nodeSelector,
                                                 serviceAccountName,
                                                 imageName,
                                                 imagePullPolicy,
                                                 maxHistory,
                                                 namespace,
                                                 hostNetwork,
                                                 tls,
                                                 verifyTls));
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

  @Deprecated
  protected DeploymentSpec createDeploymentSpec(final Map<String, String> labels,
                                                final String serviceAccountName,
                                                final String imageName,
                                                final ImagePullPolicy imagePullPolicy,
                                                final String namespace,
                                                final boolean hostNetwork,
                                                final boolean tls,
                                                final boolean verifyTls) {
    return this.createDeploymentSpec(1,
                                     labels,
                                     null,
                                     serviceAccountName,
                                     imageName,
                                     imagePullPolicy,
                                     0,
                                     namespace,
                                     hostNetwork,
                                     tls,
                                     verifyTls);
  }

  @Deprecated
  protected DeploymentSpec createDeploymentSpec(final Map<String, String> labels,
                                                final Map<String, String> nodeSelector,
                                                String serviceAccountName,
                                                final String imageName,
                                                final ImagePullPolicy imagePullPolicy,
                                                final int maxHistory,
                                                final String namespace,
                                                final boolean hostNetwork,
                                                final boolean tls,
                                                final boolean verifyTls) {
    return this.createDeploymentSpec(1,
                                     labels,
                                     nodeSelector,
                                     serviceAccountName,
                                     imageName,
                                     imagePullPolicy,
                                     maxHistory,
                                     namespace,
                                     hostNetwork,
                                     tls,
                                     verifyTls);
  }

  protected DeploymentSpec createDeploymentSpec(final int replicas,
                                                final Map<String, String> labels,
                                                final Map<String, String> nodeSelector,
                                                String serviceAccountName,
                                                final String imageName,
                                                final ImagePullPolicy imagePullPolicy,
                                                final int maxHistory,
                                                final String namespace,
                                                final boolean hostNetwork,
                                                final boolean tls,
                                                final boolean verifyTls) {    
    final DeploymentSpec deploymentSpec = new DeploymentSpec();
    deploymentSpec.setReplicas(Math.max(1, replicas));
    final PodTemplateSpec podTemplateSpec = new PodTemplateSpec();
    final ObjectMeta metadata = new ObjectMeta();
    metadata.setLabels(normalizeLabels(labels));
    podTemplateSpec.setMetadata(metadata);
    final PodSpec podSpec = new PodSpec();
    serviceAccountName = normalizeServiceAccountName(serviceAccountName);    
    podSpec.setServiceAccountName(serviceAccountName);
    podSpec.setContainers(Arrays.asList(this.createContainer(imageName, imagePullPolicy, maxHistory, namespace, tls, verifyTls)));
    podSpec.setHostNetwork(Boolean.valueOf(hostNetwork));
    if (nodeSelector != null && !nodeSelector.isEmpty()) {
      podSpec.setNodeSelector(nodeSelector);
    }
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
    final LabelSelector selector = new LabelSelector();
    selector.setMatchLabels(labels);
    deploymentSpec.setSelector(selector);
    return deploymentSpec;
  }

  protected Container createContainer(final String imageName,
                                      final ImagePullPolicy imagePullPolicy,
                                      final String namespace,
                                      final boolean tls,
                                      final boolean verifyTls) {
    return this.createContainer(imageName, imagePullPolicy, 0, namespace, tls, verifyTls);
  }
  
  protected Container createContainer(final String imageName,
                                      final ImagePullPolicy imagePullPolicy,
                                      final int maxHistory,
                                      final String namespace,
                                      final boolean tls,
                                      final boolean verifyTls) {
    final Container container = new Container();
    container.setName(DEFAULT_NAME);
    container.setImage(normalizeImageName(imageName));
    container.setImagePullPolicy(normalizeImagePullPolicy(imagePullPolicy));

    final List<ContainerPort> containerPorts = new ArrayList<>(2);

    ContainerPort containerPort = new ContainerPort();
    containerPort.setContainerPort(Integer.valueOf(44134));
    containerPort.setName(DEFAULT_NAME);
    containerPorts.add(containerPort);

    containerPort = new ContainerPort();
    containerPort.setContainerPort(Integer.valueOf(44135));
    containerPort.setName("http");
    containerPorts.add(containerPort);
    
    container.setPorts(containerPorts);

    final List<EnvVar> env = new ArrayList<>();
    
    final EnvVar tillerNamespace = new EnvVar();
    tillerNamespace.setName("TILLER_NAMESPACE");
    tillerNamespace.setValue(normalizeNamespace(namespace));
    env.add(tillerNamespace);

    final EnvVar tillerHistoryMax = new EnvVar();
    tillerHistoryMax.setName("TILLER_HISTORY_MAX");
    tillerHistoryMax.setValue(String.valueOf(maxHistory));
    env.add(tillerHistoryMax);

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

  /**
   * If the supplied {@code timeoutInMilliseconds} is zero or greater,
   * waits for there to be a {@code Ready} Tiller pod and then
   * contacts its health endpoint.
   *
   * <p>If the Tiller pod is healthy this method will return
   * normally.</p>
   *
   * @param namespace the namespace housing Tiller; may be {@code
   * null} in which case a default will be used
   *
   * @param labels the Kubernetes labels that will be used to find
   * running Tiller pods; may be {@code null} in which case a {@link
   * Map} consisting of a label of {@code app} with a value of {@code
   * helm} and a label of {@code name} with a value of {@code tiller}
   * will be used instead
   *
   * @param timeoutInMilliseconds the number of milliseconds to wait
   * for a Tiller pod to become ready; if less than {@code 0} no wait
   * will occur and this method will return immediately
   *
   * @exception KubernetesClientException if there was a problem
   * connecting to Kubernetes
   *
   * @exception MalformedURLException if there was a problem
   * forwarding a port to Tiller
   *
   * @exception TillerPollingDeadlineExceededException if Tiller could
   * not be contacted in time
   *
   * @exception TillerUnavailableException if Tiller was not healthy
   */
  protected final <T extends HttpClientAware & KubernetesClient> void ping(String namespace, Map<String, String> labels, final long timeoutInMilliseconds) throws MalformedURLException {
    if (timeoutInMilliseconds >= 0L && this.kubernetesClient instanceof HttpClientAware) {
      namespace = normalizeNamespace(namespace);
      labels = labels;
      if (!this.isTillerPodReady(namespace, labels, timeoutInMilliseconds)) {
        throw new TillerPollingDeadlineExceededException(String.valueOf(timeoutInMilliseconds));
      }
      @SuppressWarnings("unchecked")
      final Tiller tiller = new Tiller((T)this.kubernetesClient, namespace, -1 /* use default */, labels);
      final HealthBlockingStub health = tiller.getHealthBlockingStub();
      assert health != null;
      final HealthCheckRequest.Builder builder = HealthCheckRequest.newBuilder();
      assert builder != null;
      builder.setService("Tiller");
      final HealthCheckResponseOrBuilder response = health.check(builder.build());
      assert response != null;
      final ServingStatus status = response.getStatus();
      assert status != null;
      switch (status) {
      case SERVING:
        break;
      default:
        throw new TillerNotAvailableException(String.valueOf(status));
      }
    }
  }

  /**
   * Returns {@code true} if there is a running Tiller pod that is
   * {@code Ready}, waiting for a particular amount of time for this
   * result.
   *
   * @param namespace the namespace housing Tiller; may be {@code
   * null} in which case a default will be used instead
   *
   * @param labels labels identifying Tiller pods; may be {@code null}
   * in which case a default set will be used instead
   *
   * @param timeoutInMilliseconds the number of milliseconds to wait
   * for a result; if {@code 0}, this method will block and wait
   * forever; if less than {@code 0} this method will take no action
   * and will return {@code false}
   *
   * @return {@code true} if there is a running Tiller pod that is
   * {@code Ready}; {@code false} otherwise
   *
   * @exception KubernetesClientException if there was a problem
   * communicating with Kubernetes
   */
  protected final boolean isTillerPodReady(String namespace,
                                           Map<String, String> labels,
                                           final long timeoutInMilliseconds) {
    namespace = normalizeNamespace(namespace);
    labels = normalizeLabels(labels);
    final Object[] podHolder = new Object[1];
    final Listable<? extends PodList> podList = this.kubernetesClient.pods().inNamespace(namespace).withLabels(labels);
    final Thread thread = new Thread(() -> {
        while (true) {
          if ((podHolder[0] = Pods.getFirstReadyPod(podList)) == null) {
            try {
              Thread.sleep(500L);
            } catch (final InterruptedException interruptedException) {
              Thread.currentThread().interrupt();
              break;
            }
          } else {
            break;
          }
        }
      });
    thread.start();
    try {
      thread.join(timeoutInMilliseconds);
    } catch (final InterruptedException timeExpired) {
      Thread.currentThread().interrupt();
    }
    final boolean returnValue = podHolder[0] != null;
    return returnValue;
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

  private static final boolean isServerTillerVersionGreaterThanClientTillerVersion(final String serverTillerImage) {
    boolean returnValue = false;
    if (serverTillerImage != null) {
      final Matcher matcher = TILLER_VERSION_PATTERN.matcher(serverTillerImage);
      assert matcher != null;
      if (matcher.find()) {
        final String versionSpecifier = matcher.group(1);
        if (versionSpecifier != null) {
          final Version serverTillerVersion = Version.valueOf(versionSpecifier);
          assert serverTillerVersion != null;
          final Version clientTillerVersion = Version.valueOf(VERSION);
          assert clientTillerVersion != null;
          returnValue = serverTillerVersion.compareTo(clientTillerVersion) > 0;
        }
      }
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
