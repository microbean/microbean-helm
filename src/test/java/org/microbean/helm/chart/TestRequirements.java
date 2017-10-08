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

import java.net.URL;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ChartOuterClass.ChartOrBuilder;
import hapi.chart.ConfigOuterClass.Config;
import hapi.chart.ConfigOuterClass.ConfigOrBuilder;
import hapi.chart.MetadataOuterClass.MetadataOrBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.microbean.helm.chart.Requirements.Dependency;

import org.yaml.snakeyaml.Yaml;

import org.yaml.snakeyaml.constructor.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TestRequirements {

  private Chart.Builder chartBuilder;

  private AbstractChartLoader<URL> chartLoader;
  
  public TestRequirements() {
    super();
  }

  @Before
  public void setUp() throws IOException {
    final URL chartLocation = Thread.currentThread().getContextClassLoader().getResource("TestConfigs/subpop");
    assertNotNull(chartLocation);
    this.chartLoader = new URLChartLoader();
    this.chartBuilder = this.chartLoader.load(chartLocation);
    assertNotNull(this.chartBuilder);
  }

  @After
  public void tearDown() throws IOException {
    if (this.chartLoader != null) {
      this.chartLoader.close();
    }
  }

  
  @Test
  public void testYamlUnmarshalling() throws IOException {
    final URL requirementsYaml = Thread.currentThread().getContextClassLoader().getResource("TestRequirements/requirements.yaml");
    assertNotNull(requirementsYaml);
    try (final InputStream stream = new BufferedInputStream(requirementsYaml.openStream())) {
      final Yaml yaml = new Yaml(new Constructor(Requirements.class));
      final Requirements requirements = (Requirements)yaml.load(stream);
      assertNotNull(requirements);
      final Collection<Dependency> dependencies = requirements.getDependencies();
      assertNotNull(dependencies);
      assertEquals(2, dependencies.size());
      final Iterator<Dependency> iterator = dependencies.iterator();
      assertNotNull(iterator);
      assertTrue(iterator.hasNext());

      final Dependency firstDependency = iterator.next();
      assertEquals("subchart1", firstDependency.getName());
      assertEquals("http://localhost:10191", firstDependency.getRepository());
      assertEquals("0.1.0", firstDependency.getVersion());
      assertEquals("subchart1.enabled, global.subchart1.enabled", firstDependency.getCondition());
      Collection<String> tags = firstDependency.getTags();
      assertNotNull(tags);
      assertEquals(2, tags.size());
      
      assertTrue(iterator.hasNext());
      
      final Dependency secondDependency = iterator.next();
      assertEquals("subchart2", secondDependency.getName());
      assertEquals("http://localhost:10191", secondDependency.getRepository());
      assertEquals("0.1.0", secondDependency.getVersion());
      assertEquals("subchart2.enabled,global.subchart2.enabled", secondDependency.getCondition());
      tags = secondDependency.getTags();
      assertNotNull(tags);
      assertEquals(2, tags.size());

      assertFalse(iterator.hasNext());
    }
  }

  @Test
  public void testRequirementsTagsNonValue() {
    final Config.Builder configBuilder = Config.newBuilder();
    assertNotNull(configBuilder);
    configBuilder.setRaw("tags:\n  nothinguseful: false\n\n");
    final SortedSet<String> expectations = new TreeSet<>();
    expectations.add("parentchart");
    expectations.add("subchart1");
    expectations.add("subcharta");
    expectations.add("subchartb");
    verifyRequirementsEnabled(this.chartBuilder, configBuilder, expectations);
  }

  @Test
  public void testRequirementsTagsDisabledL1() {
    final Config.Builder configBuilder = Config.newBuilder();
    assertNotNull(configBuilder);
    configBuilder.setRaw("tags:\n  front-end: false\n\n");
    final SortedSet<String> expectations = new TreeSet<>();
    expectations.add("parentchart");
    verifyRequirementsEnabled(this.chartBuilder, configBuilder, expectations);
  }
  
  private static final void verifyRequirementsEnabled(final Chart.Builder chartBuilder, final ConfigOrBuilder config, final SortedSet<? extends String> expectations) {
    assertNotNull(chartBuilder);
    assertNotNull(config);
    assertNotNull(expectations);

    assertSame(chartBuilder, Requirements.apply(chartBuilder, config));

    assertEquals(expectations, getChartNames(chartBuilder));
  }

  private static final SortedSet<? extends String> getChartNames(final ChartOrBuilder rootChart) {
    assertNotNull(rootChart);

    SortedSet<String> returnValue = new TreeSet<>();
    
    final List<ChartOrBuilder> charts = new LinkedList<>();
    charts.add(rootChart);
    
    while (!charts.isEmpty()) {
      ChartOrBuilder chart = charts.remove(0);
      assertNotNull(chart);
      final MetadataOrBuilder metadata = chart.getMetadataOrBuilder();
      if (metadata != null) {
        final String name = metadata.getName();
        if (name != null && !name.isEmpty()) {
          returnValue.add(name);
        }
      }
      final Collection<? extends ChartOrBuilder> subcharts = chart.getDependenciesOrBuilderList();
      if (subcharts != null && !subcharts.isEmpty()) {
        charts.addAll(subcharts);
      }
    }
    return returnValue;
  }
  
}
