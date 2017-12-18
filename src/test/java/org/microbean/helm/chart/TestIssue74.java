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

import java.io.BufferedInputStream;
import java.io.IOException;

import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;

import java.util.zip.GZIPInputStream;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.MetadataOuterClass.Metadata;

import org.junit.Test;
import org.junit.Before;

import org.microbean.development.annotation.Issue;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import org.kamranzafar.jtar.TarInputStream;

@Issue(uri = "https://github.com/microbean/microbean-helm/issues/74")
public class TestIssue74 {

  private Path validChartPath;
  
  @Before
  public void setUp() throws IOException {
    final URL url = Thread.currentThread().getContextClassLoader().getResource(this.getClass().getSimpleName() + java.io.File.separator + "ingress-4.0.208-helm.tar.gz");
    assertNotNull(url);
    assertEquals("file", url.getProtocol());
    this.validChartPath = Paths.get(url.getPath()).toAbsolutePath();
  }

  @Test
  public void testLoad() throws IOException {
    Chart.Builder builder = null;
    try (final TapeArchiveChartLoader loader = new TapeArchiveChartLoader()) {
      builder = loader.load(new TarInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(this.validChartPath)))));
    }
    final Chart chart = builder.build();
    assertNotNull(chart);
    final Metadata metadata = chart.getMetadata();
    assertNotNull(metadata);
    final List<Chart> dependencies = chart.getDependenciesList();
    assertNotNull(dependencies);
  }

}
