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

import java.util.Iterator;
import java.util.NavigableSet;

import java.util.zip.GZIPInputStream;

import hapi.chart.ChartOuterClass.Chart;

import hapi.chart.MetadataOuterClass.Metadata;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import org.kamranzafar.jtar.TarInputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class TestStreamOrientedChartLoader {

  private TarInputStream stream;
  
  @Before
  public void setUp() throws IOException {

  }

  @After
  public void tearDown() throws IOException {

  }

  @Test
  public void testGetFileNameReturnsNullWhenSubchartsAreInvolved() {
    final String input = "/wordpress/charts/bazinga/frob.txt";
    final String output = StreamOrientedChartLoader.getOrdinaryFileName(input);
    assertNull(output);
  }

  @Test
  public void testGetFileNameReturnsNullWhenTemplatesAreInvolved() {
    final String input = "/wordpress/templates/bazinga/frob.txt";
    final String output = StreamOrientedChartLoader.getOrdinaryFileName(input);
    assertNull(output);
  }
  
  @Test
  public void testGetFileName() {
    final String input = "/wordpress/foobish/bazinga/frob.txt";
    final String output = StreamOrientedChartLoader.getOrdinaryFileName(input);
    assertEquals("foobish/bazinga/frob.txt", output);
  }
  
  @Test
  public void testGetTemplateFileNameNonTemplateFile() {
    final String input = "/wordpress/README.md";
    final String output = StreamOrientedChartLoader.getTemplateFileName(input);
    assertNull(output);
  }

  @Test
  public void testGetTemplateFileNameDeep() {
    final String input = "/wordpress/charts/foobar/templates/argle.yaml";
    final String output = StreamOrientedChartLoader.getTemplateFileName(input);
    assertEquals("templates/argle.yaml", output);
  }

  @Test
  public void testGetTemplateFileNameRidiculous() {
    final String input = "/wordpress/charts/foobar/templates/bork/templates/argle.yaml";
    final String output = StreamOrientedChartLoader.getTemplateFileName(input);
    assertEquals("templates/argle.yaml", output);
  }

  
  @Test
  public void testToSubchartsSimple() throws IOException {
    final String input = "wordpress/charts/mariadb/Chart.yaml";
    final NavigableSet<String> output = StreamOrientedChartLoader.toSubcharts(input);
    assertNotNull(output);
    assertEquals(1, output.size());
    assertEquals("wordpress/charts/mariadb", output.first());
  }

  @Test
  public void testToSubchartsDeep() throws IOException {
    final String input = "wordpress/charts/mariadb/charts/frobnicator/templates/foo.yaml";
    final NavigableSet<String> output = StreamOrientedChartLoader.toSubcharts(input);
    assertNotNull(output);
    assertEquals(2, output.size());
    assertEquals("wordpress/charts/mariadb", output.first());
    assertEquals("wordpress/charts/mariadb/charts/frobnicator", output.last());
        
  }

  @Test
  public void testToSubchartsBananasAndInvalid() throws IOException {
    final String input = "wordpress/charts/mariadb/charts/frobnicator/charts/joe/barney/charts/wilma/README.md";
    final NavigableSet<String> output = StreamOrientedChartLoader.toSubcharts(input);
    assertNotNull(output);
    assertEquals(4, output.size());
    final Iterator<String> iterator = output.iterator();
    assertNotNull(iterator);
    assertTrue(iterator.hasNext());
    assertEquals("wordpress/charts/mariadb", iterator.next());
    assertTrue(iterator.hasNext());
    assertEquals("wordpress/charts/mariadb/charts/frobnicator", iterator.next());
    assertTrue(iterator.hasNext());
    assertEquals("wordpress/charts/mariadb/charts/frobnicator/charts/joe", iterator.next());
    assertTrue(iterator.hasNext());
    assertEquals("wordpress/charts/mariadb/charts/frobnicator/charts/joe/barney/charts/wilma", iterator.next());
    assertFalse(iterator.hasNext());
  }
  
  @Test
  public void testToSubchartsNone() throws IOException {
    final String input = "wordpress/values.yaml";
    final NavigableSet<String> output = StreamOrientedChartLoader.toSubcharts(input);
    assertNotNull(output);
    assertTrue(output.isEmpty());
  }

  @Test
  public void testToSubchartsNoneWithTemplates() throws IOException {
    final String input = "wordpress/templates/foo.yaml";
    final NavigableSet<String> output = StreamOrientedChartLoader.toSubcharts(input);
    assertNotNull(output);
    assertTrue(output.isEmpty());
  }

  
}
