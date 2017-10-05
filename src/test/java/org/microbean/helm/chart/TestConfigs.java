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

import java.util.Map;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ChartOuterClass.ChartOrBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestConfigs {

  private ChartLoader<URL> chartLoader;
  
  private Chart chart;
 
  public TestConfigs() {
    super();
  }

  @Before
  public void setUp() throws IOException {
    final URL chartLocation = Thread.currentThread().getContextClassLoader().getResource("TestConfigs/subpop");
    assertNotNull(chartLocation);
    this.chartLoader = new URLChartLoader();
    this.chart = this.chartLoader.load(chartLocation);
    assertNotNull(this.chart);
  }

  @After
  public void tearDown() throws IOException {
    if (this.chartLoader != null) {
      this.chartLoader.close();
    }
  }
  
  @Test
  public void testToDefaultValuesMap() {
    final Map<String, Object> values = Configs.toDefaultValuesMap(this.chart);
    testDefaultValuesMap(values);
  }

  @Test
  public void testToValuesMapWithNullAsSecondArgument() {
    final Map<String, Object> defaultValues = Configs.toDefaultValuesMap(this.chart);
    testDefaultValuesMap(defaultValues);
    final Map<String, Object> shouldBeDefaultValuesToo = Configs.toValuesMap(this.chart, null);
    assertEquals(defaultValues, shouldBeDefaultValuesToo);
  }

  private static final void testDefaultValuesMap(final Map<? extends String, ?> values) {
    assertNotNull(values);
    assertEquals(9, values.size());

    @SuppressWarnings("unchecked")
    final Map<? extends String, ?> importedChart1 = (Map<? extends String, ?>)values.get("imported-chart1");
    assertNotNull(importedChart1);
    assertEquals(1, importedChart1.size());
    assertEquals("helm rocks", importedChart1.get("SPextra1"));

    @SuppressWarnings("unchecked")
    final Map<? extends String, ?> overriddenChart1 = (Map<? extends String, ?>)values.get("overridden-chart1");
    assertNotNull(overriddenChart1);
    assertEquals(5, overriddenChart1.size());
    assertEquals(Boolean.FALSE, overriddenChart1.get("SC1bool"));
    assertEquals(Double.valueOf(3.141592d), overriddenChart1.get("SC1float"));
    assertEquals(Integer.valueOf(99), overriddenChart1.get("SC1int"));
    assertEquals("pollywog", overriddenChart1.get("SC1string"));
    assertEquals(Integer.valueOf(42), overriddenChart1.get("SPextra2"));

    @SuppressWarnings("unchecked")
    final Map<? extends String, ?> importedChartA = (Map<? extends String, ?>)values.get("imported-chartA");
    assertNotNull(importedChartA);
    assertEquals(1, importedChartA.size());
    assertEquals(Double.valueOf(1.337d), importedChartA.get("SPextra3"));

    @SuppressWarnings("unchecked")
    final Map<? extends String, ?> overriddenChartA = (Map<? extends String, ?>)values.get("overridden-chartA");
    assertNotNull(overriddenChartA);
    assertEquals(5, overriddenChartA.size());
    assertEquals(Boolean.TRUE, overriddenChartA.get("SCAbool"));
    assertEquals(Double.valueOf(41.3), overriddenChartA.get("SCAfloat"));
    assertEquals(Integer.valueOf(808), overriddenChartA.get("SCAint"));
    assertEquals("jaberwocky", overriddenChartA.get("SCAstring"));
    assertEquals(Boolean.TRUE, overriddenChartA.get("SPextra4"));
    
    @SuppressWarnings("unchecked")
    final Map<? extends String, ?> importedChartAB = (Map<? extends String, ?>)values.get("imported-chartA-B");
    assertNotNull(importedChartAB);
    assertEquals(1, importedChartAB.size());
    assertEquals("k8s", importedChartAB.get("SPextra5"));

    @SuppressWarnings("unchecked")
    final Map<? extends String, ?> overriddenChartAB = (Map<? extends String, ?>)values.get("overridden-chartA-B");
    assertNotNull(overriddenChartAB);
    assertEquals(9, overriddenChartAB.size());
    assertEquals(Boolean.TRUE, overriddenChartAB.get("SCAbool"));
    assertEquals(Double.valueOf(41.3), overriddenChartAB.get("SCAfloat"));
    assertEquals(Integer.valueOf(808), overriddenChartAB.get("SCAint"));
    assertEquals("jaberwocky", overriddenChartAB.get("SCAstring"));
    assertEquals(Boolean.FALSE, overriddenChartAB.get("SCBbool"));
    assertEquals(Double.valueOf(1.99), overriddenChartAB.get("SCBfloat"));
    assertEquals(Integer.valueOf(77), overriddenChartAB.get("SCBint"));
    assertEquals("jango", overriddenChartAB.get("SCBstring"));
    assertEquals(Integer.valueOf(111), overriddenChartAB.get("SPextra6"));
    
    @SuppressWarnings("unchecked")
    final Map<? extends String, ?> tags = (Map<? extends String, ?>)values.get("tags");
    assertNotNull(tags);
    assertEquals(2, tags.size());
    assertEquals(Boolean.TRUE, tags.get("front-end"));
    assertEquals(Boolean.FALSE, tags.get("back-end"));

    System.out.println(new Yaml().dump(values));
  }
  
}
