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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.MetadataOuterClass.MetadataOrBuilder;

import org.junit.Test;

import org.microbean.helm.chart.resolver.ChartResolverException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestChartRepositoryRepository {

  public TestChartRepositoryRepository() {
    super();
  }

  @Test
  public void testFromYaml() throws IOException, URISyntaxException {
    try (final Reader reader = new BufferedReader(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResource("TestChartRepositoryRepository/repositories.yaml").openStream(), StandardCharsets.UTF_8))) {
      final ChartRepositoryRepository repositories = ChartRepositoryRepository.fromYaml(reader);
      assertNotNull(repositories);
      final Set<? extends ChartRepository> chartRepositories = repositories.getChartRepositories();
      assertNotNull(chartRepositories);
      assertEquals(2, chartRepositories.size());
    }
  }

  @Test
  public void testResolution() throws ChartResolverException, IOException, URISyntaxException {
    final String targetDirectory = System.getProperty("project.build.directory");
    assertNotNull(targetDirectory);
    final Path workArea = Paths.get(targetDirectory).resolve("TestChartRepositoryRepository");
    assertNotNull(workArea);
    final Path indexCacheDirectory = workArea.resolve("indices");
    assertNotNull(indexCacheDirectory);
    Files.createDirectories(indexCacheDirectory);
    final Path archiveCacheDirectory = workArea.resolve("archives");
    assertNotNull(archiveCacheDirectory);
    Files.createDirectories(archiveCacheDirectory);
    try (final Reader reader = new BufferedReader(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResource("TestChartRepositoryRepository/repositories.yaml").openStream(), StandardCharsets.UTF_8))) {
      final ChartRepositoryRepository repo = ChartRepositoryRepository.fromYaml(reader, archiveCacheDirectory, indexCacheDirectory);
      assertNotNull(repo);
      final Chart.Builder redis = repo.resolve("stable", "redis", "0.10.1");
      assertNotNull(redis);
      final MetadataOrBuilder metadata = redis.getMetadataOrBuilder();
      assertNotNull(metadata);
      assertEquals("redis", metadata.getName());
      assertEquals("0.10.1", metadata.getVersion());
    }
  }
  
}
