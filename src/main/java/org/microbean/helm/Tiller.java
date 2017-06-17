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

import java.io.Closeable;
import java.io.IOException;

import java.net.InetAddress;
import java.net.MalformedURLException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import hapi.services.tiller.ReleaseServiceGrpc;
import hapi.services.tiller.ReleaseServiceGrpc.ReleaseServiceBlockingStub;
import hapi.services.tiller.ReleaseServiceGrpc.ReleaseServiceFutureStub;
import hapi.services.tiller.ReleaseServiceGrpc.ReleaseServiceStub;

import io.fabric8.kubernetes.client.HttpClientAware;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;

import io.grpc.stub.MetadataUtils;

import org.microbean.kubernetes.Pods;

public class Tiller implements Closeable {

  public static final String VERSION = "2.4.2";

  public static final String DEFAULT_NAMESPACE = "kube-system";
  
  public static final int DEFAULT_PORT = 44134;

  public static final Map<String, String> DEFAULT_LABELS;

  private static final Metadata metadata = new Metadata();

  static {
    final Map<String, String> labels = new HashMap<>();
    labels.put("name", "tiller");
    labels.put("app", "helm");
    DEFAULT_LABELS = Collections.unmodifiableMap(labels);
    metadata.put(Metadata.Key.of("x-helm-api-client", Metadata.ASCII_STRING_MARSHALLER), VERSION);
  }


  /*
   * Instance fields.
   */
  

  private final LocalPortForward portForward;
  
  private final ManagedChannel channel;


  /*
   * Constructors.
   */

  
  public Tiller(final ManagedChannel channel) {
    super();
    Objects.requireNonNull(channel);
    this.portForward = null;
    this.channel = channel;
  }

  public Tiller(final LocalPortForward portForward) {
    super();
    Objects.requireNonNull(portForward);
    this.portForward = null; // yes, null
    this.channel = this.buildChannel(portForward);
  }

  public <T extends HttpClientAware & KubernetesClient> Tiller(final T client) throws MalformedURLException {
    this(client, DEFAULT_NAMESPACE, DEFAULT_PORT, DEFAULT_LABELS);
  }
  
  public <T extends HttpClientAware & KubernetesClient> Tiller(final T client, final String namespaceHousingTiller) throws MalformedURLException {
    this(client, namespaceHousingTiller, DEFAULT_PORT, DEFAULT_LABELS);
  }
  
  public <T extends HttpClientAware & KubernetesClient> Tiller(final T client,
                                                               String namespaceHousingTiller,
                                                               int tillerPort,
                                                               Map<String, String> tillerLabels) throws MalformedURLException {
    super();
    Objects.requireNonNull(client);
    if (namespaceHousingTiller == null || namespaceHousingTiller.isEmpty()) {
      namespaceHousingTiller = DEFAULT_NAMESPACE;
    }
    if (tillerPort <= 0) {
      tillerPort = DEFAULT_PORT;
    }
    if (tillerLabels == null) {
      tillerLabels = DEFAULT_LABELS;
    }
    this.portForward = Pods.forwardPort(client.getHttpClient(), client.pods().inNamespace(namespaceHousingTiller).withLabels(tillerLabels), tillerPort);
    this.channel = this.buildChannel(this.portForward);
  }


  /*
   * Instance methods.
   */
  

  protected ManagedChannel buildChannel(final LocalPortForward portForward) {
    return ManagedChannelBuilder.forAddress(portForward.getLocalAddress().getHostAddress(), portForward.getLocalPort()).usePlaintext(true).build();
  }

  @Override
  public void close() throws IOException {
    if (this.channel != null) {
      this.channel.shutdownNow();
    }
    if (this.portForward != null) {
      this.portForward.close();
    }
  }

  public ReleaseServiceBlockingStub getReleaseServiceBlockingStub() {
    ReleaseServiceBlockingStub returnValue = null;
    if (this.channel != null) {
      returnValue = MetadataUtils.attachHeaders(ReleaseServiceGrpc.newBlockingStub(this.channel), metadata);
    }
    return returnValue;
  }

  public ReleaseServiceFutureStub getReleaseServiceFutureStub() {
    ReleaseServiceFutureStub returnValue = null;
    if (this.channel != null) {
      returnValue = MetadataUtils.attachHeaders(ReleaseServiceGrpc.newFutureStub(this.channel), metadata);
    }
    return returnValue;
  }

  public ReleaseServiceStub getReleaseServiceStub() {
    ReleaseServiceStub returnValue = null;
    if (this.channel != null) {
      returnValue = MetadataUtils.attachHeaders(ReleaseServiceGrpc.newStub(this.channel), metadata);
    }
    return returnValue;
  }

}
