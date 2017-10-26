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
package org.microbean.helm.chart.repository;

import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Map;
import java.util.SortedSet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestChartRepository {

  public TestChartRepository() {
    super();
  }

  @Test
  public void testLoadIndex() throws IOException, URISyntaxException {
    final Path indexPath = Paths.get(Thread.currentThread().getContextClassLoader().getResource("TestChartRepository/stable-index.yaml").getPath());
    assertNotNull(indexPath);
    final ChartRepository chartRepository = new ChartRepository("stable", new URI("https://kubernetes-charts.storage.googleapis.com/"), indexPath);
    final ChartRepository.Index index = chartRepository.loadIndex();
    assertNotNull(index);
    final Map<String, SortedSet<ChartRepository.Index.Entry>> entries = index.getEntries();
    assertNotNull(entries);
    assertEquals(100, entries.size());
    // Pick a couple at random and verify versions are in the proper order
    final SortedSet<ChartRepository.Index.Entry> wordpressEntries = entries.get("wordpress");
    assertNotNull(wordpressEntries);
    assertEquals(25, wordpressEntries.size());
    final ChartRepository.Index.Entry mostRecentWordpress = wordpressEntries.first();
    assertNotNull(mostRecentWordpress);
    assertEquals("wordpress", mostRecentWordpress.getName());
    assertEquals("0.6.12", mostRecentWordpress.getVersion());
  }
  
}
