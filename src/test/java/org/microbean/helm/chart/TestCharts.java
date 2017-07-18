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

import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import hapi.chart.ChartOuterClass.Chart;

import hapi.release.ReleaseOuterClass.Release;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import org.microbean.helm.Tiller;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import static org.junit.Assume.assumeFalse;

public class TestCharts {

  private URL redisUrl;
  
  @Before
  public void setUp() throws IOException {
    final URI uri = URI.create("https://kubernetes-charts.storage.googleapis.com/redis-0.8.0.tgz");
    assertNotNull(uri);
    final URL url = uri.toURL();
    assertNotNull(url);
    final URLConnection connection = url.openConnection();
    assertNotNull(connection);
    connection.addRequestProperty("Accept-Encoding", "gzip");
    connection.connect();
    assertEquals("application/x-tar", connection.getContentType());
    this.redisUrl = url;
  }

  @After
  public void tearDown() throws IOException {

  }

  @Test
  public void testInstall() throws ExecutionException, IOException, InterruptedException {
    assumeFalse(Boolean.getBoolean("skipClusterTests"));
    try (final DefaultKubernetesClient client = new DefaultKubernetesClient();
         final Tiller tiller = new Tiller(client)) {
      final Future<Release> releaseFuture = Charts.install(tiller, this.redisUrl);
      assertNotNull(releaseFuture);
      final Release release = releaseFuture.get();
      assertNotNull(release);
      assertNotNull(release.getName());
      assertTrue(release.hasChart());
      assertTrue(release.hasConfig());
    }
  }

}

