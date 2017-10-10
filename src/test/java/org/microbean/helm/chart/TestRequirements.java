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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import static org.junit.Assert.fail;

public class TestRequirements {

  private Chart.Builder chartBuilder;

  private AbstractChartLoader<URL> chartLoader;
  
  public TestRequirements() {
    super();
  }

  @Before
  public void setUp() throws IOException {
    final URL chartLocation = Thread.currentThread().getContextClassLoader().getResource("TestRequirements/subpop");
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
      final Yaml yaml = new Yaml();
      final Requirements requirements = yaml.loadAs(stream, Requirements.class);
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
    this.verifyRequirementsEnabled("tags:\n  nothinguseful: false\n\n", "parentchart", "subchart1", "subcharta", "subchartb");
  }

  @Test
  public void testRequirementsTagsDisabledL1() {
    this.verifyRequirementsEnabled("tags:\n  front-end: false\n\n", "parentchart");
  }

  @Test
  public void testRequirementsTagsEnabledL1() {
    this.verifyRequirementsEnabled("tags:\n  front-end: false\n\n  back-end: true\n", "parentchart", "subchart2", "subchartb", "subchartc");
  }

  @Test
  public void testRequirementsTagsDisabledL2() {
    this.verifyRequirementsEnabled("tags:\n  subcharta: false\n\n  subchartb: false\n", "parentchart", "subchart1", "subcharta", "subchartb");
  }

  @Test
  public void testRequirementsTagsDisabledL1Mixed() {
    this.verifyRequirementsEnabled("tags:\n  front-end: false\n\n  subchart1: true\n\n  back-end: false\n", "parentchart", "subchart1");
  }

  @Test
  public void testRequirementsConditionsNonValue() {
    this.verifyRequirementsEnabled("subchart1:\n  nothinguseful: false\n\n", "parentchart", "subchart1", "subcharta", "subchartb");
  }

  @Test
  public void testRequirementsConditionsEnabledL1Both() {
    this.verifyRequirementsEnabled("subchart1:\n  enabled: true\nsubchart2:\n  enabled: true\n", "parentchart", "subchart1", "subchart2", "subcharta", "subchartb");
  }

  @Test
  public void testRequirementsConditionsDisabledL1Both() {
    this.verifyRequirementsEnabled("subchart1:\n  enabled: false\nsubchart2:\n  enabled: false\n", "parentchart");
  }

  @Test
  public void testRequirementsConditionsSecond() {
    this.verifyRequirementsEnabled("subchart1:\n  subcharta:\n    enabled: false\n", "parentchart", "subchart1", "subchartb");
  }

  @Test
  public void testRequirementsConditionsDisabledL2() {
    this.verifyRequirementsEnabled("subchartc:\n  enabled: false\ntags:\n  back-end: true\n", "parentchart", "subchart1", "subchart2", "subcharta", "subchartb", "subchartb");
  }

  @Test
  public void testRequirementsConditionsCombinedDisabledL1() {
    this.verifyRequirementsEnabled("subchart1:\n  enabled: false\ntags:\n  front-end: true\n", "parentchart");
  }

  private final void verifyRequirementsEnabled(final String tags, final String... expectedChartNames) {
    assertNotNull(tags);
    assertNotNull(expectedChartNames);
    final Config.Builder configBuilder = Config.newBuilder();
    assertNotNull(configBuilder);
    configBuilder.setRaw(tags);
    final SortedSet<String> expectations = new TreeSet<>();
    for (final String expectation : expectedChartNames) {
      assertNotNull(expectation);
      expectations.add(expectation);
    }
    verifyRequirementsEnabled(configBuilder, expectations);
  }

  @Test
  public void testProcessImportValues() {
    final Map<String, String> expectations = new HashMap<>();
    
    expectations.put("imported-chart1.SC1bool", "true");
    expectations.put("imported-chart1.SC1float", "3.14");
    expectations.put("imported-chart1.SC1int", "100");
    expectations.put("imported-chart1.SC1string", "dollywood");
    expectations.put("imported-chart1.SC1extra1", "11");
    expectations.put("imported-chart1.SPextra1", "helm rocks");
    expectations.put("imported-chart1.SC1extra1", "11");
    
    expectations.put("imported-chartA.SCAbool", "false");
    expectations.put("imported-chartA.SCAfloat", "3.1");
    expectations.put("imported-chartA.SCAint", "55");
    expectations.put("imported-chartA.SCAstring", "jabba");
    expectations.put("imported-chartA.SPextra3", "1.337");
    expectations.put("imported-chartA.SC1extra2", "1.337");
    expectations.put("imported-chartA.SCAnested1.SCAnested2", "true");
    
    expectations.put("imported-chartA-B.SCAbool", "false");
    expectations.put("imported-chartA-B.SCAfloat", "3.1");
    expectations.put("imported-chartA-B.SCAint", "55");
    expectations.put("imported-chartA-B.SCAstring", "jabba");
    
    expectations.put("imported-chartA-B.SCBbool", "true");
    expectations.put("imported-chartA-B.SCBfloat", "7.77");
    expectations.put("imported-chartA-B.SCBint", "33");
    expectations.put("imported-chartA-B.SCBstring", "boba");
    expectations.put("imported-chartA-B.SPextra5", "k8s");
    expectations.put("imported-chartA-B.SC1extra5", "tiller");
    
    expectations.put("overridden-chart1.SC1bool", "false");
    expectations.put("overridden-chart1.SC1float", "3.141592");
    expectations.put("overridden-chart1.SC1int", "99");
    expectations.put("overridden-chart1.SC1string", "pollywog");
    expectations.put("overridden-chart1.SPextra2", "42");
    
    expectations.put("overridden-chartA.SCAbool", "true");
    expectations.put("overridden-chartA.SCAfloat", "41.3");
    expectations.put("overridden-chartA.SCAint", "808");
    expectations.put("overridden-chartA.SCAstring", "jaberwocky");
    expectations.put("overridden-chartA.SPextra4", "true");
    
    expectations.put("overridden-chartA-B.SCAbool", "true");
    expectations.put("overridden-chartA-B.SCAfloat", "41.3");
    expectations.put("overridden-chartA-B.SCAint", "808");
    expectations.put("overridden-chartA-B.SCAstring", "jaberwocky");
    expectations.put("overridden-chartA-B.SCBbool", "false");
    expectations.put("overridden-chartA-B.SCBfloat", "1.99");
    expectations.put("overridden-chartA-B.SCBint", "77");
    expectations.put("overridden-chartA-B.SCBstring", "jango");
    expectations.put("overridden-chartA-B.SPextra6", "111");
    expectations.put("overridden-chartA-B.SCAextra1", "23");
    expectations.put("overridden-chartA-B.SCBextra1", "13");
    expectations.put("overridden-chartA-B.SC1extra6", "77");
    
    expectations.put("SCBexported1B", "1965");
    expectations.put("SC1extra7", "true");
    expectations.put("SCBexported2A", "blaster");
    expectations.put("global.SC1exported2.all.SC1exported3", "SC1expstr");

    this.verifyRequirementsImportValues(expectations);    
  }
  
  private final void verifyRequirementsEnabled(final ConfigOrBuilder config, final SortedSet<? extends String> expectations) {
    assertNotNull(this.chartBuilder);
    assertNotNull(config);
    assertNotNull(expectations);

    assertSame(this.chartBuilder, Requirements.apply(this.chartBuilder, config, false));

    assertEquals(expectations, getChartNames());
  }

  private final SortedSet<? extends String> getChartNames() {
    final SortedSet<String> returnValue = new TreeSet<>();
    
    final List<ChartOrBuilder> charts = new LinkedList<>();
    charts.add(this.chartBuilder);
    
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

  private final void verifyRequirementsImportValues(final Map<? extends String, ? extends String> expectations) {
    this.verifyRequirementsImportValues(null, expectations);
  }
  
  private final void verifyRequirementsImportValues(final ConfigOrBuilder config, final Map<? extends String, ? extends String> expectations) {
    assertNotNull(this.chartBuilder);
    final Requirements requirements = Requirements.fromChartOrBuilder(this.chartBuilder);
    assertNotNull(requirements);
    assertSame(this.chartBuilder, Requirements.processImportValues(this.chartBuilder));
    final Map<String, Object> chartDefaultValues = Configs.toDefaultValuesMap(this.chartBuilder);
    assertNotNull(chartDefaultValues);
    final MapTree defaultValues = new MapTree(chartDefaultValues);
    final Set<? extends Entry<? extends String, ? extends String>> expectationsEntrySet = expectations.entrySet();
    assertNotNull(expectationsEntrySet);
    for (final Entry<? extends String, ? extends String> expectationsEntry : expectationsEntrySet) {
      assertNotNull(expectationsEntry);
      final String path = expectationsEntry.getKey();
      assertNotNull(path);
      final Object pathValue = defaultValues.get(path, Object.class);
      assertNotNull(pathValue);
      if (path.endsWith("bool")) {
        assertTrue(pathValue instanceof Boolean);
      } else if (path.endsWith("string")) {
        assertTrue(pathValue instanceof String);
      } else if (path.endsWith("float")) {
        assertTrue(pathValue instanceof Double); // Java/Go type mismatch
      } else if (path.endsWith("int")) {
        assertTrue(pathValue instanceof Integer);
      }
      final Object expectedValue = expectationsEntry.getValue();
      assertNotNull(expectedValue);
      assertEquals(expectedValue.toString(), pathValue.toString());
    }
  }
  
}
