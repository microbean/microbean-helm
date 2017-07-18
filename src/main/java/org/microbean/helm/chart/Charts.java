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

import hapi.services.tiller.ReleaseServiceGrpc.ReleaseServiceBlockingStub;
import hapi.services.tiller.Tiller.InstallReleaseRequest;
import hapi.services.tiller.Tiller.InstallReleaseResponse;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;

import org.microbean.helm.Tiller;

public final class Charts {

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
    final Chart chart = new URLChartLoader().load(chartUrl);
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
    }
    requestBuilder.setReuseName(reuseReleaseName);
    requestBuilder.setTimeout(timeoutInSeconds);
    if (yamlValues != null && !yamlValues.isEmpty()) {
      final Config.Builder configBuilder = Config.newBuilder();
      assert configBuilder != null;
      final ByteString rawBytes = ByteString.copyFrom(yamlValues, StandardCharsets.UTF_8);
      assert rawBytes != null;
      configBuilder.setRawBytes(rawBytes);
      requestBuilder.setValues(configBuilder.build());
    }
    requestBuilder.setWait(wait);
    final InstallReleaseRequest request = requestBuilder.build();
    assert request != null;
    final ReleaseServiceBlockingStub stub = tiller.getReleaseServiceBlockingStub();
    assert stub != null;
    return new FutureTask<Release>(() -> stub.installRelease(request).getRelease());
  }
  
}
