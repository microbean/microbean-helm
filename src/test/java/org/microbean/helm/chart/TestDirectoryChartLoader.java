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
import java.io.InputStream;

import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.nio.file.attribute.BasicFileAttributes;

import java.util.List;

import java.util.zip.GZIPInputStream;

import hapi.chart.ChartOuterClass.Chart;

import hapi.chart.MetadataOuterClass.Metadata;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class TestDirectoryChartLoader {

  private Path validChartPath;
  
  @Before
  public void setUp() throws IOException {
    // Chart is arbitrary, but it does have subcharts in it, which exercise some tricky logic
    final URL url = Thread.currentThread().getContextClassLoader().getResource(this.getClass().getSimpleName() + java.io.File.separator + "wordpress");
    assertNotNull(url);
    assertEquals("file", url.getProtocol());
    this.validChartPath = Paths.get(url.getPath()).toAbsolutePath();
  }

  @After
  public void tearDown() throws IOException {

  }
  
  @Test
  public void testLoad() throws IOException {
    final Chart chart = new DirectoryChartLoader().load(this.validChartPath);
    assertNotNull(chart);
    final Metadata metadata = chart.getMetadata();
    assertNotNull(metadata);
    assertEquals("wordpress", metadata.getName());
    assertEquals("0.6.6", metadata.getVersion());
    final List<Chart> dependencies = chart.getDependenciesList();
    assertNotNull(dependencies);
    assertEquals(dependencies.toString(), 1, dependencies.size());
  }

}
