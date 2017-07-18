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
package org.microbean.helm.chart;

import java.io.IOException;

import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.util.Objects;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import com.google.protobuf.ByteString;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ConfigOuterClass.Config;

import hapi.release.ReleaseOuterClass.Release;

import hapi.services.tiller.ReleaseServiceGrpc.ReleaseServiceFutureStub;
import hapi.services.tiller.Tiller.InstallReleaseRequest;
import hapi.services.tiller.Tiller.InstallReleaseResponse;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import org.microbean.helm.Tiller;

/**
 * A fa&ccedil;ade class for common {@link Chart}-related operations.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #install(Tiller, URL, boolean, boolean, String, String,
 * boolean, long, String, boolean)
 *
 * @see Tiller
 */
public final class Charts {

  /**
   * Using a {@link Tiller} constructed just in time for communication
   * with the Tiller component of the Helm ecosystem, installs the
   * Helm chart located at the supplied {@link URL} with default
   * options.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param chartUrl a {@link URL} that designates a Helm chart; must
   * not be {@code null}; will be loaded with a {@link URLChartLoader}
   *
   * @return a {@link Future} whose {@link Future#get()} method will
   * return the {@link Release} representing the chart that was
   * installed
   *
   * @see #install(Tiller, URL, boolean, boolean, String, String,
   * boolean, long, String, boolean)
   *
   * @see Tiller
   *
   * @exception IOException if a communication error occurs
   *
   * @exception KubernetesClientException if there is a problem with
   * Kubernetes communication itself
   *
   * @exception NullPointerException if either {@code tiller} or
   * {@code chartUrl} is {@code null}
   */
  public static final Future<Release> install(final URL chartUrl)
    throws IOException {
    try (final Tiller tiller = new Tiller(new DefaultKubernetesClient())) {
      return install(tiller,
                     chartUrl,
                     false,
                     false,
                     null,
                     null,
                     false,
                     300L, // See https://github.com/kubernetes/helm/blob/v2.5.0/cmd/helm/install.go#L189
                     null,
                     false);
    }
  }

  /**
   * Using the supplied {@link Tiller} for communication with the
   * Tiller component of the Helm ecosystem, installs the Helm chart
   * located at the supplied {@link URL} with the supplied options.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param tiller a {@link Tiller} instance to use to communicate
   * with Tiller; must not be {@code null}
   *
   * @param chartUrl a {@link URL} that designates a Helm chart; must
   * not be {@code null}; will be loaded with a {@link URLChartLoader}
   *
   * @return a {@link Future} whose {@link Future#get()} method will
   * return the {@link Release} representing the chart that was
   * installed
   *
   * @see #install(Tiller, URL, boolean, boolean, String, String,
   * boolean, long, String, boolean)
   *
   * @see Tiller
   *
   * @exception IOException if a communication error occurs
   *
   * @exception KubernetesClientException if there is a problem with
   * Kubernetes communication itself
   *
   * @exception NullPointerException if either {@code tiller} or
   * {@code chartUrl} is {@code null}
   */
  public static final Future<Release> install(final Tiller tiller,
                                              final URL chartUrl)
    throws IOException {
    return install(tiller,
                   chartUrl,
                   false,
                   false,
                   null,
                   null,
                   false,
                   300L, // See https://github.com/kubernetes/helm/blob/v2.5.0/cmd/helm/install.go#L189
                   null,
                   false);
  }

  /**
   * Using a {@link Tiller} constructed just in time for communication
   * with the Tiller component of the Helm ecosystem, installs the
   * Helm chart located at the supplied {@link URL} with the supplied
   * options.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param chartUrl a {@link URL} that designates a Helm chart; must
   * not be {@code null}; will be loaded with a {@link URLChartLoader}
   *
   * @param yamlValues a YAML-formatted {@link String} representing
   * values to override in the chart; may be {@code null}
   *
   * @param wait whether to wait for Pods and Services and such to
   * become Ready before the returned {@link Future}'s {@link
   * Future#get()} method will return a {@link Release}
   *
   * @return a {@link Future} whose {@link Future#get()} method will
   * return the {@link Release} representing the chart that was
   * installed
   *
   * @see #install(Tiller, URL, boolean, boolean, String, String,
   * boolean, long, String, boolean)
   *
   * @see Tiller
   *
   * @exception IOException if a communication error occurs
   *
   * @exception KubernetesClientException if there is a problem with
   * Kubernetes communication itself
   *
   * @exception NullPointerException if either {@code tiller} or
   * {@code chartUrl} is {@code null}
   */
  public static final Future<Release> install(final URL chartUrl,
                                              final String yamlValues,
                                              final boolean wait)
    throws IOException {
    try (final Tiller tiller = new Tiller(new DefaultKubernetesClient())) {
      return install(tiller,
                     chartUrl,
                     false,
                     false,
                     null,
                     null,
                     false,
                     300L, // See https://github.com/kubernetes/helm/blob/v2.5.0/cmd/helm/install.go#L189
                     yamlValues,
                     wait);
    }
  }

  /**
   * Using the supplied {@link Tiller} for communication with the
   * Tiller component of the Helm ecosystem, installs the Helm chart
   * located at the supplied {@link URL} with the supplied options.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param tiller a {@link Tiller} instance to use to communicate
   * with Tiller; must not be {@code null}
   *
   * @param chartUrl a {@link URL} that designates a Helm chart; must
   * not be {@code null}; will be loaded with a {@link URLChartLoader}
   *
   * @param yamlValues a YAML-formatted {@link String} representing
   * values to override in the chart; may be {@code null}
   *
   * @param wait whether to wait for Pods and Services and such to
   * become Ready before the returned {@link Future}'s {@link
   * Future#get()} method will return a {@link Release}
   *
   * @see #install(Tiller, URL, boolean, boolean, String, String,
   * boolean, long, String, boolean)
   *
   * @return a {@link Future} whose {@link Future#get()} method will
   * return the {@link Release} representing the chart that was
   * installed
   *
   * @see Tiller
   *
   * @exception IOException if a communication error occurs
   *
   * @exception KubernetesClientException if there is a problem with
   * Kubernetes communication itself
   *
   * @exception NullPointerException if either {@code tiller} or
   * {@code chartUrl} is {@code null}
   */
  public static final Future<Release> install(final Tiller tiller,
                                              final URL chartUrl,
                                              final String yamlValues,
                                              final boolean wait)
    throws IOException {
    return install(tiller,
                   chartUrl,
                   false,
                   false,
                   null,
                   null,
                   false,
                   300L, // See https://github.com/kubernetes/helm/blob/v2.5.0/cmd/helm/install.go#L189
                   yamlValues,
                   wait);
  }

  /**
   * Using a {@link Tiller} constructed just in time for communication
   * with the Tiller component of the Helm ecosystem, installs the
   * Helm chart located at the supplied {@link URL} with the supplied
   * options.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param chartUrl a {@link URL} that designates a Helm chart; must
   * not be {@code null}; will be loaded with a {@link URLChartLoader}
   *
   * @param disableHooks whether the chart's hooks should be disabled
   *
   * @param dryRun if {@code true} then no installation will take
   * place, and a {@link Future} whose {@link Future#get()} method may
   * return {@code null} will be returned
   *
   * @param releaseName the name of the release; if {@code null} or
   * {@linkplain String#isEmpty() empty} then a generated name will be
   * used instead
   *
   * @param releaseNamespace the namespace into which the release
   * should be installed; if {@code null} or {@linkplain
   * String#isEmpty() empty} {@code default} will be used instead
   *
   * @param reuseReleaseName whether to silently replace any extant
   * release with the same {@code releaseName} (not suitable for
   * production)
   *
   * @param timeoutInSeconds the number of seconds after which any
   * Kubernetes operations will time out; <a
   * href="https://github.com/kubernetes/helm/blob/v2.5.0/cmd/helm/install.go#L189">set
   * in the Helm project to {@code 300L} by default</a>
   *
   * @param yamlValues a YAML-formatted {@link String} representing
   * values to override in the chart; may be {@code null}
   *
   * @param wait whether to wait for Pods and Services and such to
   * become Ready before the returned {@link Future}'s {@link
   * Future#get()} method will return a {@link Release}
   *
   * @return a {@link Future} whose {@link Future#get()} method will
   * return the {@link Release} representing the chart that was
   * installed
   *
   * @see #install(Tiller, URL, boolean, boolean, String, String,
   * boolean, long, String, boolean)
   *
   * @see Tiller
   *
   * @exception IOException if a communication error occurs
   *
   * @exception KubernetesClientException if there is a problem with
   * Kubernetes communication itself
   *
   * @exception NullPointerException if either {@code tiller} or
   * {@code chartUrl} is {@code null}
   */
  public static final Future<Release> install(final URL chartUrl,
                                              final boolean disableHooks,
                                              final boolean dryRun,
                                              final String releaseName,
                                              final String releaseNamespace,
                                              final boolean reuseReleaseName,
                                              final long timeoutInSeconds,
                                              final String yamlValues,
                                              final boolean wait)
    throws IOException {
    try (final Tiller tiller = new Tiller(new DefaultKubernetesClient())) {
      return install(tiller,
                     chartUrl,
                     disableHooks,
                     dryRun,
                     releaseName,
                     releaseNamespace,
                     reuseReleaseName,
                     timeoutInSeconds,
                     yamlValues,
                     wait);
    }
  }

  /**
   * Using the supplied {@link Tiller} for communication with the
   * Tiller component of the Helm ecosystem, installs the Helm chart
   * located at the supplied {@link URL} with the supplied options.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param tiller a {@link Tiller} instance to use to communicate
   * with Tiller; must not be {@code null}
   *
   * @param chartUrl a {@link URL} that designates a Helm chart; must
   * not be {@code null}; will be loaded with a {@link URLChartLoader}
   *
   * @param disableHooks whether the chart's hooks should be disabled
   *
   * @param dryRun if {@code true} then no installation will take
   * place, and a {@link Future} whose {@link Future#get()} method may
   * return {@code null} will be returned
   *
   * @param releaseName the name of the release; if {@code null} or
   * {@linkplain String#isEmpty() empty} then a generated name will be
   * used instead
   *
   * @param releaseNamespace the namespace into which the release
   * should be installed; if {@code null} or {@linkplain
   * String#isEmpty() empty} {@code default} will be used instead
   *
   * @param reuseReleaseName whether to silently replace any extant
   * release with the same {@code releaseName} (not suitable for
   * production)
   *
   * @param timeoutInSeconds the number of seconds after which any
   * Kubernetes operations will time out; <a
   * href="https://github.com/kubernetes/helm/blob/v2.5.0/cmd/helm/install.go#L189">set
   * in the Helm project to {@code 300L} by default</a>
   *
   * @param yamlValues a YAML-formatted {@link String} representing
   * values to override in the chart; may be {@code null}
   *
   * @param wait whether to wait for Pods and Services and such to
   * become Ready before the returned {@link Future}'s {@link
   * Future#get()} method will return a {@link Release}
   *
   * @return a {@link Future} whose {@link Future#get()} method will
   * return the {@link Release} representing the chart that was
   * installed
   *
   * @see Tiller
   *
   * @exception IOException if a communication error occurs
   *
   * @exception KubernetesClientException if there is a problem with
   * Kubernetes communication itself
   *
   * @exception NullPointerException if either {@code tiller} or
   * {@code chartUrl} is {@code null}
   */
  public static final Future<Release> install(final Tiller tiller,
                                              final URL chartUrl,
                                              final boolean disableHooks,
                                              final boolean dryRun,
                                              final String releaseName,
                                              final String releaseNamespace,
                                              final boolean reuseReleaseName,
                                              final long timeoutInSeconds,
                                              final String yamlValues,
                                              final boolean wait)
    throws IOException {
    Objects.requireNonNull(tiller);
    Objects.requireNonNull(chartUrl);
    Chart chart = null;
    try (final ChartLoader<URL> loader = new URLChartLoader()) {      
      chart = loader.load(chartUrl);
    }
    final InstallReleaseRequest.Builder requestBuilder = InstallReleaseRequest.newBuilder();
    assert requestBuilder != null;
    requestBuilder.setChart(chart);
    requestBuilder.setDisableHooks(disableHooks);
    requestBuilder.setDryRun(dryRun);
    if (releaseName != null && !releaseName.isEmpty()) {
      requestBuilder.setName(releaseName);
    }
    if (releaseNamespace != null && !releaseNamespace.isEmpty()) {
      requestBuilder.setNamespace(releaseNamespace);
    } else {
      requestBuilder.setNamespace("default"); // TODO: harvest from equivalent of kube config
    }
    requestBuilder.setReuseName(reuseReleaseName);
    requestBuilder.setTimeout(timeoutInSeconds);
    final Config.Builder configBuilder = Config.newBuilder();
    assert configBuilder != null;
    if (yamlValues != null && !yamlValues.isEmpty()) {
      final ByteString rawBytes = ByteString.copyFrom(yamlValues, StandardCharsets.UTF_8);
      assert rawBytes != null;
      configBuilder.setRawBytes(rawBytes);
    }
    requestBuilder.setValues(configBuilder.build());
    requestBuilder.setWait(wait);
    final InstallReleaseRequest request = requestBuilder.build();
    assert request != null;
    final ReleaseServiceFutureStub stub = tiller.getReleaseServiceFutureStub();
    assert stub != null;
    final Future<InstallReleaseResponse> responseFuture = stub.installRelease(request);
    final FutureTask<Release> returnValue;
    if (responseFuture == null) {
      returnValue = new FutureTask<>(() -> {}, null);
    } else {
      returnValue = new FutureTask<>(() -> {
          final Release rv;
          final InstallReleaseResponse response = responseFuture.get();
          if (response == null) {
            rv = null;
          } else {
            rv = response.getRelease();
          }
          return rv;
        });
    }
    returnValue.run();
    return returnValue;
  }

}
