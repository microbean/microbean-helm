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
import java.io.Reader;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.Instant;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.regex.Pattern;

import hapi.chart.ChartOuterClass.Chart;

import org.microbean.development.annotation.Experimental;

import org.yaml.snakeyaml.Yaml;

import org.microbean.helm.chart.resolver.ChartResolver;

@Experimental
public class ChartRepositoryRepository extends ChartResolver {

  private static final Pattern slashPattern = Pattern.compile("/");
  
  private final String apiVersion;
  
  private final Instant generationInstant;

  private final Set<ChartRepository> chartRepositories;

  public ChartRepositoryRepository(final String apiVersion, final Instant generationInstant, final Set<? extends ChartRepository> chartRepositories) {
    super();
    this.apiVersion = Objects.requireNonNull(apiVersion);
    this.generationInstant = generationInstant;
    if (chartRepositories == null || chartRepositories.isEmpty()) {
      this.chartRepositories = Collections.emptySet();
    } else {
      this.chartRepositories = Collections.unmodifiableSet(new LinkedHashSet<>(chartRepositories));
    }
  }

  public final String getApiVersion() {
    return this.apiVersion;
  }

  public final Instant getGenerationInstant() {
    return this.generationInstant;
  }
  
  public final Set<ChartRepository> getChartRepositories() {
    return this.chartRepositories;
  }

  public ChartRepository getChartRepository(final String name) {
    Objects.requireNonNull(name);
    ChartRepository returnValue = null;
    final Collection<? extends ChartRepository> repos = this.getChartRepositories();
    if (repos != null && !repos.isEmpty()) {
      for (final ChartRepository repo : repos) {
        if (repo != null && name.equals(repo.getName())) {
          returnValue = repo;
        }
      }
    }
    return returnValue;
  }

  @Override
  public Chart.Builder resolve(final String chartName, final String chartVersion) throws IOException, URISyntaxException {
    Objects.requireNonNull(chartName);
    Chart.Builder returnValue = null;
    final String[] parts = slashPattern.split(chartName, 2);
    if (parts != null && parts.length == 2) {
      returnValue = this.resolve(parts[0], parts[1], chartVersion);
    }
    return returnValue;
  }

  public Chart.Builder resolve(final String repositoryName, final String chartName, final String chartVersion) throws IOException, URISyntaxException {
    Objects.requireNonNull(repositoryName);
    Objects.requireNonNull(chartName);
    Chart.Builder returnValue = null;
    final ChartRepository repo = this.getChartRepository(repositoryName);
    if (repo != null) {
      final Chart.Builder candidate = repo.resolve(chartName, chartVersion);
      if (candidate != null) {
        returnValue = candidate;
      }
    }
    return returnValue;
  }

  public static final ChartRepositoryRepository fromHelmRepositoriesYaml() throws IOException, URISyntaxException {
    try (final Reader reader = Files.newBufferedReader(ChartRepository.getHelmHome().resolve("repository/repositories.yaml"))) {
      return fromYaml(reader);
    }
  }

  public static final ChartRepositoryRepository fromYaml(final Reader reader) throws IOException, URISyntaxException {
    return fromYaml(reader, null, null);
  }
  
  public static final ChartRepositoryRepository fromYaml(final Reader reader, Path archiveCacheDirectory, Path indexCacheDirectory) throws IOException, URISyntaxException {
    Objects.requireNonNull(reader);
    Path helmHome = null;
    if (archiveCacheDirectory == null) {
      helmHome = ChartRepository.getHelmHome();
      assert helmHome != null;
      archiveCacheDirectory = helmHome.resolve("cache/archive");
      assert archiveCacheDirectory != null;
    }
    if (!Files.isDirectory(archiveCacheDirectory)) {
      throw new IllegalArgumentException("!Files.isDirectory(archiveCacheDirectory): " + archiveCacheDirectory);
    }
    if (indexCacheDirectory == null) {
      if (helmHome == null) {
        helmHome = ChartRepository.getHelmHome();
        assert helmHome != null;
      }
      indexCacheDirectory = helmHome.resolve("repository/cache");
      assert indexCacheDirectory != null;
    }
    if (!Files.isDirectory(indexCacheDirectory)) {
      throw new IllegalArgumentException("!Files.isDirectory(indexCacheDirectory): " + indexCacheDirectory);
    }
    final Map<?, ?> map = new Yaml().loadAs(reader, Map.class);
    if (map == null || map.isEmpty()) {
      throw new IllegalArgumentException("No data readable from reader: " + reader);
    }
    final String apiVersion = (String)Objects.requireNonNull(map.get("apiVersion"));

    final Date generationDate = (Date)map.get("generated");
    final Instant generationInstant = generationDate == null ? null : generationDate.toInstant();
    final Set<ChartRepository> chartRepositories;
    @SuppressWarnings("unchecked")      
    final Collection<? extends Map<?, ?>> repositories = (Collection<? extends Map<?, ?>>)map.get("repositories");
    if (repositories == null || repositories.isEmpty()) {
      chartRepositories = Collections.emptySet();
    } else {
      chartRepositories = new LinkedHashSet<>();
      for (final Map<?, ?> repositoryMap : repositories) {
        if (repositoryMap != null && !repositoryMap.isEmpty()) {
          final String name = Objects.requireNonNull((String)repositoryMap.get("name"));
          final URI uri = new URI((String)repositoryMap.get("url"));
          Path cache = Objects.requireNonNull(Paths.get((String)repositoryMap.get("cache")));
          if (!cache.isAbsolute()) {
            cache = indexCacheDirectory.resolve(cache);
            assert cache.isAbsolute();
          }
          final String caFileString = (String)map.get("caFile");
          final Path caFile;
          if (caFileString == null || caFileString.isEmpty()) {
            caFile = null;
          } else {
            caFile = Paths.get(caFileString);
          }
          
          final String certFileString = (String)map.get("certFile");
          final Path certFile;
          if (certFileString == null || certFileString.isEmpty()) {
            certFile = null;
          } else {
            certFile = Paths.get(certFileString);
          }
          
          final String keyFileString = (String)map.get("keyFile");
          final Path keyFile;
          if (keyFileString == null || keyFileString.isEmpty()) {
            keyFile = null;
          } else {
            keyFile = Paths.get(keyFileString);
          }
          
          final ChartRepository chartRepository = new ChartRepository(name, uri, archiveCacheDirectory, indexCacheDirectory, cache, caFile, certFile, keyFile);
          chartRepositories.add(chartRepository);
        }      
      }
    }
    return new ChartRepositoryRepository(apiVersion, generationInstant, chartRepositories);
  }
 
}
