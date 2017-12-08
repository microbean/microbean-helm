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

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;

import hapi.chart.ChartOuterClass.Chart;

import hapi.chart.MetadataOuterClass.Metadata;

import org.junit.Test;
import org.junit.Before;

import org.microbean.development.annotation.Issue;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

@Issue(uri = "https://github.com/microbean/microbean-helm/issues/63")
public class TestIssue63 {

  private Path validChartPath;
  
  @Before
  public void setUp() throws IOException {
    // Chart is arbitrary, but it does have subcharts in it, which exercise some tricky logic
    final URL url = Thread.currentThread().getContextClassLoader().getResource(this.getClass().getSimpleName() + java.io.File.separator + "chart-with-subcharts");
    assertNotNull(url);
    assertEquals("file", url.getProtocol());
    this.validChartPath = Paths.get(url.getPath()).toAbsolutePath();
  }

  @Test
  public void testLoad() throws IOException {
    final Chart chart = new DirectoryChartLoader().load(this.validChartPath).build();
    assertNotNull(chart);
    final Metadata metadata = chart.getMetadata();
    assertNotNull(metadata);
    final List<Chart> dependencies = chart.getDependenciesList();
    assertNotNull(dependencies);
  }

}
