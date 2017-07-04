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

import java.nio.file.Paths;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import java.util.zip.GZIPInputStream;

import hapi.chart.ChartOuterClass.Chart;

import hapi.chart.MetadataOuterClass.Metadata;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import org.junit.runner.RunWith;

import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.kamranzafar.jtar.TarInputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class TestHelmIgnorePathMatcher {

  @Parameters(name = "{0}, {1}, {2}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"helm.txt", "helm.txt", true},
        {"helm.*", "helm.txt", true},
        {"helm.*", "rudder.txt", false},
        {"*.txt", "tiller.txt", true},
        {"*.txt", "cargo/a.txt", true},
        {"cargo/*.txt", "cargo/a.txt", true},
        {"cargo/*.*", "cargo/a.txt", true},
        {"cargo/*.txt", "mast/a.txt", false},
        {"ru[c-e]?er.txt", "rudder.txt", true},
        {"templates/.?*", "templates/.dotfile", true},
        // "." should never get ignored. https://github.com/kubernetes/helm/issues/1776
        {".*", ".", false},
        {".*", "./", false},

        
        // Directory tests
        {"cargo/", "cargo", true},
        {"cargo/", "cargo/", true},
        {"cargo/", "mast/", false},
        {"helm.txt/", "helm.txt", false},
        
        // Negation tests
        {"!helm.txt", "helm.txt", false},
        {"!helm.txt", "tiller.txt", true},
        {"!*.txt", "cargo", true},
        {"!cargo/", "mast/", true},
        
        // Absolute path tests
        {"/a.txt", "a.txt", true},
        {"/a.txt", "cargo/a.txt", false},
        {"/cargo/a.txt", "cargo/a.txt", true}
      });
  }

  private final String pattern;

  private final String testText;

  private final boolean match;

  private HelmIgnorePathMatcher pathMatcher;
  
  public TestHelmIgnorePathMatcher(final String pattern, final String testText, final boolean match) {
    super();
    this.pattern = pattern;
    this.testText = testText;
    this.match = match;
  }

  @Before
  public void setUp() {
    this.pathMatcher = new HelmIgnorePathMatcher();
  }

  @After
  public void tearDown() {

  }

  @Test
  public void testStuff() {
    this.pathMatcher.addPattern(this.pattern);
    assertEquals(this.pattern + " did not match " + this.testText, this.match, this.pathMatcher.matches(Paths.get(this.testText)));
  }
}
