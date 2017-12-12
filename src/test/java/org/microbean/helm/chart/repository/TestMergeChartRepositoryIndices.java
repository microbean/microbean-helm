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

import org.microbean.helm.chart.repository.ChartRepository.Index;
import org.microbean.helm.chart.repository.ChartRepository.Index.Entry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;

public class TestMergeChartRepositoryIndices {

  public TestMergeChartRepositoryIndices() {
    super();
  }

  @Test
  public void testLoadIndex() throws IOException, URISyntaxException {
    final Path originalIndexPath = Paths.get(Thread.currentThread().getContextClassLoader().getResource(this.getClass().getSimpleName() + "/original-index.yaml").getPath());
    assertNotNull(originalIndexPath);

    final Path newIndexPath = Paths.get(Thread.currentThread().getContextClassLoader().getResource(this.getClass().getSimpleName() + "/new-index.yaml").getPath());
    assertNotNull(newIndexPath);

    final Index originalIndex = Index.loadFrom(originalIndexPath);
    assertNotNull(originalIndex);

    final Index newIndex = Index.loadFrom(newIndexPath);
    assertNotNull(newIndex);

    final Index mergedIndex = originalIndex.merge(newIndex);
    assertNotNull(mergedIndex);
    assertNotEquals(originalIndex, mergedIndex);

    final Map<String, SortedSet<Entry>> entries = mergedIndex.getEntries();
    assertNotNull(entries);
    assertEquals(3, entries.size());
    
    final SortedSet<Entry> zetcd = entries.get("zetcd");
    assertNotNull(zetcd);
    assertEquals(3, zetcd.size());
    Entry first = zetcd.first();
    assertNotNull(first);
    assertEquals("zetcd", first.getName());
    assertEquals("0.1.3", first.getVersion());

    final SortedSet<Entry> awsClusterAutoscaler = entries.get("aws-cluster-autoscaler");
    assertNotNull(awsClusterAutoscaler);
    assertEquals(6, awsClusterAutoscaler.size());
    first = awsClusterAutoscaler.first();
    assertNotNull(first);
    assertEquals("aws-cluster-autoscaler", first.getName());
    assertEquals("0.3.1", first.getVersion());
    
  }
  
}
