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

import java.io.IOException;

import java.util.List;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ConfigOuterClass.Config;
import hapi.chart.TemplateOuterClass.Template;

import hapi.release.ReleaseOuterClass.Release;

import hapi.services.tiller.ReleaseServiceGrpc;
import hapi.services.tiller.Tiller.GetHistoryRequest;
import hapi.services.tiller.Tiller.GetHistoryResponse;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

public class TestTillerInstaller {

  public TestTillerInstaller() {
    super();
  }

  @Test
  public void testTillerInstallation() throws IOException {
    assumeFalse(Boolean.getBoolean("skipInstallationTest"));
    final TillerInstaller installer = new TillerInstaller();
    installer.init(true);
  }

  @Test
  public void testGetRelease() throws IOException {
    final String releaseName = System.getProperty("testGetRelease.releaseName");
    assumeNotNull(releaseName);
    assumeFalse(releaseName.isEmpty());
    try (final DefaultKubernetesClient client = new DefaultKubernetesClient();
         final Tiller tiller = new Tiller(client)) {
    
      final GetHistoryRequest.Builder builder = GetHistoryRequest.newBuilder();
      assertNotNull(builder);
      builder.setMax(1);
      builder.setName(releaseName);
      final GetHistoryRequest request = builder.build();
      assertNotNull(request);
      assertEquals(releaseName, request.getName());

      final ReleaseServiceGrpc.ReleaseServiceBlockingStub stub = tiller.getReleaseServiceBlockingStub();
      assertNotNull(stub);

      final GetHistoryResponse response = stub.getHistory(request);
      assertNotNull(response);
      assertEquals(1, response.getReleasesCount());
      final List<? extends Release> releasesList = response.getReleasesList();
      assertNotNull(releasesList);
      assertEquals(1, releasesList.size());
      final Release release = releasesList.get(0);
      assertNotNull(release);
      assertEquals(releaseName, release.getName());
      final Chart chart = release.getChart();
      assertNotNull(chart);
      final Config values = chart.getValues();
      assertNotNull(values);
      System.out.println("*** values (raw): " + values.getRaw());
      System.out.println("*** values (value map): " + values.getValuesMap());
      final List<Template> templates = chart.getTemplatesList();
      assertNotNull(templates);
      for (final Template template : templates) {
        assertNotNull(template);
        System.out.println("*** retrieved template: " + template);
      }
      final List<Any> files = chart.getFilesList();
      assertNotNull(files);
      for (final Any file : files) {
        assertNotNull(file);
        System.out.println("*** retrieved: " + file.getTypeUrl());
        final ByteString value = file.getValue();
        assertNotNull(value);
        assertTrue(value.isValidUtf8());
      }
    }       
  }
  
}
