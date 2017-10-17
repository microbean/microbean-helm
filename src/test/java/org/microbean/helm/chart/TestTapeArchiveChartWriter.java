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

import java.io.BufferedOutputStream;
import java.io.IOException;

import java.net.URI;
import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.zip.GZIPOutputStream;

import hapi.chart.ChartOuterClass.ChartOrBuilder;
import hapi.chart.ChartOuterClass.Chart;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class TestTapeArchiveChartWriter {

  private ChartOrBuilder chart;

  private Path buildDirectory;
  
  @Before
  public void setUp() throws IOException {
    // Chart is arbitrary, but it does have subcharts in it, which exercise some tricky logic
    final URI uri = URI.create("https://kubernetes-charts.storage.googleapis.com/wordpress-0.6.6.tgz");
    assertNotNull(uri);
    final URL url = uri.toURL();
    assertNotNull(url);
    try (final URLChartLoader loader = new URLChartLoader()) {
      this.chart = loader.load(url);
    }
    assertNotNull(this.chart);
    final Path buildDirectory = Paths.get(System.getProperty("project.build.directory"));
    assertNotNull(buildDirectory);
    assertTrue(Files.isDirectory(buildDirectory));
    assertTrue(Files.isWritable(buildDirectory));
    this.buildDirectory = buildDirectory;
  }

  @After
  public void tearDown() throws IOException {
    
  }

  @Test
  public void testWrite() throws IOException {
    final Path chartPath = this.buildDirectory.resolve("TestTapeArchiveChartWriter.tar.gz");
    assertNotNull(chartPath);
    try (final TapeArchiveChartWriter writer = new TapeArchiveChartWriter(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(chartPath))))) {
      writer.write(this.chart);
    }
    assertTrue(Files.isRegularFile(chartPath));
    Files.delete(chartPath);    
  }

}
