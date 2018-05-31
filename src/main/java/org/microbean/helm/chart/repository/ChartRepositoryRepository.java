/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2017-2018 microBean.
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

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

import org.yaml.snakeyaml.constructor.SafeConstructor;

import org.microbean.helm.chart.resolver.AbstractChartResolver;
import org.microbean.helm.chart.resolver.ChartResolverException;

/**
 * A repository of {@link ChartRepository} instances, normally built
 * from a Helm {@code repositories.yaml} file.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
@Experimental
public class ChartRepositoryRepository extends AbstractChartResolver {


  /*
   * Static fields.
   */


  /**
   * A {@link Pattern} that matches a single solidus ("{@code /}").
   *
   * <p>This field is never {@code null}.</p>
   */
  private static final Pattern slashPattern = Pattern.compile("/");

  /**
   * A {@link HelmHome} instance representing the directory tree where
   * Helm stores its local information.
   *
   * <p>This field is never {@code null}.</p>
   */
  private static final HelmHome helmHome = new HelmHome();


  /*
   * Instance fields.
   */


  /**
   * The ({@linkplain Collections#unmodifiableSet(Set) immutable})
   * {@link Set} of {@link ChartRepository} instances managed by this
   * {@link ChartRepositoryRepository}.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #ChartRepositoryRepository(Set)
   *
   * @see #getChartRepositories()
   */
  private final Set<ChartRepository> chartRepositories;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link ChartRepositoryRepository}.
   *
   * @param chartRepositories the {@link Set} of {@link
   * ChartRepository} instances to be managed by this {@link
   * ChartRepositoryRepository}; may be {@code null}; copied by value
   *
   * @see #getChartRepositories()
   */
  public ChartRepositoryRepository(final Set<? extends ChartRepository> chartRepositories) {
    super();
    if (chartRepositories == null || chartRepositories.isEmpty()) {
      this.chartRepositories = Collections.emptySet();
    } else {
      this.chartRepositories = Collections.unmodifiableSet(new LinkedHashSet<>(chartRepositories));
    }
  }


  /*
   * Instance methods.
   */


  /**
   * Returns the non-{@code null} {@linkplain
   * Collections#unmodifiableSet(Set) immutable} {@link Set} of {@link
   * ChartRepository} instances managed by this {@link
   * ChartRepositoryRepository}.
   *
   * @return the non-{@code null} {@linkplain
   * Collections#unmodifiableSet(Set) immutable} {@link Set} of {@link
   * ChartRepository} instances managed by this {@link
   * ChartRepositoryRepository}
   */
  public final Set<ChartRepository> getChartRepositories() {
    return this.chartRepositories;
  }

  /**
   * Returns the {@link ChartRepository} managed by this {@link
   * ChartRepositoryRepository} with the supplied {@code name}, or
   * {@code null} if there is no such {@link ChartRepository}.
   *
   * @param name the {@linkplain ChartRepository#getName() name} of
   * the {@link ChartRepository} to return; must not be {@code null}
   *
   * @return the {@link ChartRepository} managed by this {@link
   * ChartRepositoryRepository} with the supplied {@code name}, or
   * {@code null}
   *
   * @exception NullPointerException if {@code name} is {@code null}
   */
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

  /**
   * {@inheritDoc}
   *
   * <p>This implementation splits the supplied slash-delimited {@code
   * chartName} into a <em>chart repository name</em> and a <em>chart
   * name</em>, uses the chart repository name to {@linkplain
   * #getChartRepository(String) locate a suitable
   * <code>ChartRepository</code>}, and then calls {@link
   * ChartRepository#resolve(String, String)} with the chart name and
   * the supplied {@code chartVersion}, and returns the result.</p>
   *
   * @param chartName a slash-separated {@link String} whose first
   * component is a {@linkplain ChartRepository#getName() chart
   * repository name} and whose second component is a Helm chart name;
   * must not be {@code null}
   *
   * @param chartVersion the version of the chart to resolve; may be
   * {@code null} in which case "latest" semantics are implied
   *
   * @return a {@link Chart.Builder}, or {@code null}
   *
   * @see #resolve(String, String, String)
   */
  @Override
  public Chart.Builder resolve(final String chartName, final String chartVersion) throws ChartResolverException {
    Objects.requireNonNull(chartName);
    Chart.Builder returnValue = null;
    final String[] parts = slashPattern.split(chartName, 2);
    if (parts != null && parts.length == 2) {
      returnValue = this.resolve(parts[0], parts[1], chartVersion);
    }
    return returnValue;
  }

  /**
   * Uses the supplied {@code repositoryName}, {@code chartName} and
   * {@code chartVersion} parameters to find an appropriate Helm chart
   * and returns it in the form of a {@link Chart.Builder} object.
   *
   * <p>This implementation uses the supplied {@code repositoryName}
   * to {@linkplain #getChartRepository(String) locate a suitable
   * <code>ChartRepository</code>}, and then calls {@link
   * ChartRepository#resolve(String, String)} with the chart name and
   * the supplied {@code chartVersion}, and returns the result.</p>
   *
   * @param repositoryName a {@linkplain ChartRepository#getName()
   * chart repository name}; must not be {@code null}
   *
   * @param chartName a Helm chart name; must not be {@code null}
   *
   * @param chartVersion the version of the Helm chart to select; may
   * be {@code null} in which case "latest" semantics are implied
   *
   * @return a {@link Chart.Builder}, or {@code null}
   *
   * @exception ChartResolverException if there was a problem with
   * resolution
   *
   * @exception NullPointerException if {@code repositoryName} or
   * {@code chartName} is {@code null}
   *
   * @see #getChartRepository(String)
   *
   * @see ChartRepository#getName()
   *
   * @see ChartRepository#resolve(String, String)
   */
  public Chart.Builder resolve(final String repositoryName, final String chartName, final String chartVersion) throws ChartResolverException {
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


  /*
   * Static methods.
   */

  
  /**
   * Creates and returns a new {@link ChartRepositoryRepository} from
   * the contents of a {@code repositories.yaml} file typically
   * located in the {@code ~/.helm/repository} directory.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return a new {@link ChartRepositoryRepository}; never {@code
   * null}
   *
   * @exception IOException if there was a problem reading the file
   *
   * @exception URISyntaxException if there was an invalid URI in the
   * file
   *
   * @see #fromHelmRepositoriesYaml(boolean)
   */
  public static final ChartRepositoryRepository fromHelmRepositoriesYaml() throws IOException, URISyntaxException {
    return fromHelmRepositoriesYaml(false);
  }

  /**
   * Creates and returns a new {@link ChartRepositoryRepository} from
   * the contents of a {@code repositories.yaml} file typically
   * located in the {@code ~/.helm/repository} directory.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param reifyHelmHomeIfNecessary if {@code true} and, for whatever
   * reason, the local Helm home directory structure needs to be
   * partially or entirely created, then this method will attempt to
   * reify it
   *
   * @return a new {@link ChartRepositoryRepository}; never {@code
   * null}
   *
   * @exception IOException if there was a problem reading the file or
   * reifying the local Helm home directory structure
   *
   * @exception URISyntaxException if there was an invalid URI in the
   * file
   *
   * @see #fromYaml(InputStream, Path, Path, boolean, ChartRepositoryFactory)
   */
  public static final ChartRepositoryRepository fromHelmRepositoriesYaml(final boolean reifyHelmHomeIfNecessary) throws IOException, URISyntaxException {
    if (reifyHelmHomeIfNecessary) {
      helmHome.reify();
    }
    try (final InputStream stream = new BufferedInputStream(Files.newInputStream(helmHome.toPath().resolve("repository/repositories.yaml")))) {
      return fromYaml(stream, null, null, false /* already reified */, null);
    }
  }

  /**
   * Creates and returns a new {@link ChartRepositoryRepository} from
   * the contents of a {@code repositories.yaml} file represented by
   * the supplied {@link InputStream}.
   *
   * @param stream the {@link InputStream} to read from; must not be
   * {@code null}
   *
   * @return a new {@link ChartRepositoryRepository}; never {@code
   * null}
   *
   * @exception IOException if there was a problem reading the file
   *
   * @exception URISyntaxException if there was an invalid URI in the
   * file
   *
   * @see #fromYaml(InputStream, Path, Path, boolean, ChartRepositoryFactory)
   */
  public static final ChartRepositoryRepository fromYaml(final InputStream stream) throws IOException, URISyntaxException {
    return fromYaml(stream, null, null, false, null);
  }

  /**
   * Creates and returns a new {@link ChartRepositoryRepository} from
   * the contents of a {@code repositories.yaml} file represented by
   * the supplied {@link InputStream}.
   *
   * @param stream the {@link InputStream} to read from; must not be
   * {@code null}
   *
   * @param reifyHelmHomeIfNecessary if {@code true} and, for whatever
   * reason, the local Helm home directory structure needs to be
   * partially or entirely created, then this method will attempt to
   * reify it
   *
   * @return a new {@link ChartRepositoryRepository}; never {@code
   * null}
   *
   * @exception IOException if there was a problem reading the file or
   * reifying the local Helm home directory structure
   *
   * @exception URISyntaxException if there was an invalid URI in the
   * file
   *
   * @see #fromYaml(InputStream, Path, Path, boolean, ChartRepositoryFactory)
   */
  public static final ChartRepositoryRepository fromYaml(final InputStream stream, final boolean reifyHelmHomeIfNecessary) throws IOException, URISyntaxException {
    return fromYaml(stream, null, null, reifyHelmHomeIfNecessary, null);
  }

  /**
   * Creates and returns a new {@link ChartRepositoryRepository} from
   * the contents of a {@code repositories.yaml} file represented by
   * the supplied {@link InputStream}.
   *
   * @param stream the {@link InputStream} to read from; must not be
   * {@code null}
   *
   * @param archiveCacheDirectory an {@linkplain Path#isAbsolute()
   * absolute} {@link Path} representing a directory where Helm chart
   * archives may be stored; if {@code null} then a {@link Path}
   * beginning with the absolute directory represented by the value of
   * the {@code helm.home} system property, or the value of the {@code
   * HELM_HOME} environment variable, appended with {@code
   * cache/archive} will be used instead
   *
   * @param indexCacheDirectory an {@linkplain Path#isAbsolute()
   * absolute} {@link Path} representing a directory that the supplied
   * {@code cachedIndexPath} parameter value will be considered to be
   * relative to; will be ignored and hence may be {@code null} if the
   * supplied {@code cachedIndexPath} parameter value {@linkplain
   * Path#isAbsolute()}
   *
   * @return a new {@link ChartRepositoryRepository}; never {@code
   * null}
   *
   * @exception IOException if there was a problem reading the file
   *
   * @exception URISyntaxException if there was an invalid URI in the
   * file
   *
   * @see #fromYaml(InputStream, Path, Path, boolean, ChartRepositoryFactory)
   */
  public static final ChartRepositoryRepository fromYaml(final InputStream stream, Path archiveCacheDirectory, Path indexCacheDirectory) throws IOException, URISyntaxException {
    return fromYaml(stream, archiveCacheDirectory, indexCacheDirectory, false, null);
  }

  /**
   * Creates and returns a new {@link ChartRepositoryRepository} from
   * the contents of a {@code repositories.yaml} file represented by
   * the supplied {@link InputStream}.
   *
   * @param stream the {@link InputStream} to read from; must not be
   * {@code null}
   *
   * @param archiveCacheDirectory an {@linkplain Path#isAbsolute()
   * absolute} {@link Path} representing a directory where Helm chart
   * archives may be stored; if {@code null} then a {@link Path}
   * beginning with the absolute directory represented by the value of
   * the {@code helm.home} system property, or the value of the {@code
   * HELM_HOME} environment variable, appended with {@code
   * cache/archive} will be used instead
   *
   * @param indexCacheDirectory an {@linkplain Path#isAbsolute()
   * absolute} {@link Path} representing a directory that the supplied
   * {@code cachedIndexPath} parameter value will be considered to be
   * relative to; will be ignored and hence may be {@code null} if the
   * supplied {@code cachedIndexPath} parameter value {@linkplain
   * Path#isAbsolute()}
   *
   * @param reifyHelmHomeIfNecessary if {@code true} and, for whatever
   * reason, the local Helm home directory structure needs to be
   * partially or entirely created, then this method will attempt to
   * reify it
   *
   * @return a new {@link ChartRepositoryRepository}; never {@code
   * null}
   *
   * @exception IOException if there was a problem reading the file or
   * reifying the local Helm home directory structure
   *
   * @exception URISyntaxException if there was an invalid URI in the
   * file
   *
   * @see #fromYaml(InputStream, Path, Path, boolean, ChartRepositoryFactory)
   */
  public static final ChartRepositoryRepository fromYaml(final InputStream stream,
                                                         Path archiveCacheDirectory,
                                                         Path indexCacheDirectory,
                                                         final boolean reifyHelmHomeIfNecessary)
    throws IOException, URISyntaxException {
    return fromYaml(stream, archiveCacheDirectory, indexCacheDirectory, reifyHelmHomeIfNecessary, null);
  }

  /**
   * Creates and returns a new {@link ChartRepositoryRepository} from
   * the contents of a {@code repositories.yaml} file represented by
   * the supplied {@link InputStream}.
   *
   * @param stream the {@link InputStream} to read from; must not be
   * {@code null}
   *
   * @param archiveCacheDirectory an {@linkplain Path#isAbsolute()
   * absolute} {@link Path} representing a directory where Helm chart
   * archives may be stored; if {@code null} then a {@link Path}
   * beginning with the absolute directory represented by the value of
   * the {@code helm.home} system property, or the value of the {@code
   * HELM_HOME} environment variable, appended with {@code
   * cache/archive} will be used instead
   *
   * @param indexCacheDirectory an {@linkplain Path#isAbsolute()
   * absolute} {@link Path} representing a directory that the supplied
   * {@code cachedIndexPath} parameter value will be considered to be
   * relative to; will be ignored and hence may be {@code null} if the
   * supplied {@code cachedIndexPath} parameter value {@linkplain
   * Path#isAbsolute()}
   *
   * @param factory a {@link ChartRepositoryFactory} that can create
   * {@link ChartRepository} instances; may be {@code null} in which
   * case the {@link ChartRepository#ChartRepository(String, URI,
   * Path, Path, Path)} constructor will be used instead
   *
   * @return a new {@link ChartRepositoryRepository}; never {@code
   * null}
   *
   * @exception IOException if there was a problem reading the file
   *
   * @exception URISyntaxException if there was an invalid URI in the
   * file
   *
   * @see #fromYaml(InputStream, Path, Path, boolean, ChartRepositoryFactory)
   */
  public static final ChartRepositoryRepository fromYaml(final InputStream stream,
                                                         Path archiveCacheDirectory,
                                                         Path indexCacheDirectory,
                                                         ChartRepositoryFactory factory)
    throws IOException, URISyntaxException {
    return fromYaml(stream, archiveCacheDirectory, indexCacheDirectory, false, factory);
  }

  /**
   * Creates and returns a new {@link ChartRepositoryRepository} from
   * the contents of a {@code repositories.yaml} file represented by
   * the supplied {@link InputStream}.
   *
   * @param stream the {@link InputStream} to read from; must not be
   * {@code null}
   *
   * @param archiveCacheDirectory an {@linkplain Path#isAbsolute()
   * absolute} {@link Path} representing a directory where Helm chart
   * archives may be stored; if {@code null} then a {@link Path}
   * beginning with the absolute directory represented by the value of
   * the {@code helm.home} system property, or the value of the {@code
   * HELM_HOME} environment variable, appended with {@code
   * cache/archive} will be used instead
   *
   * @param indexCacheDirectory an {@linkplain Path#isAbsolute()
   * absolute} {@link Path} representing a directory that the supplied
   * {@code cachedIndexPath} parameter value will be considered to be
   * relative to; will be ignored and hence may be {@code null} if the
   * supplied {@code cachedIndexPath} parameter value {@linkplain
   * Path#isAbsolute()}
   *
   * @param reifyHelmHomeIfNecessary if {@code true} and, for whatever
   * reason, the local Helm home directory structure needs to be
   * partially or entirely created, then this method will attempt to
   * reify it
   *
   * @param factory a {@link ChartRepositoryFactory} that can create
   * {@link ChartRepository} instances; may be {@code null} in which
   * case the {@link ChartRepository#ChartRepository(String, URI,
   * Path, Path, Path)} constructor will be used instead
   *
   * @return a new {@link ChartRepositoryRepository}; never {@code
   * null}
   *
   * @exception IOException if there was a problem reading the file or
   * reifying the local Helm home directory structure
   *
   * @exception URISyntaxException if there was an invalid URI in the
   * file
   */
  public static final ChartRepositoryRepository fromYaml(final InputStream stream,
                                                         Path archiveCacheDirectory,
                                                         Path indexCacheDirectory,
                                                         final boolean reifyHelmHomeIfNecessary,
                                                         ChartRepositoryFactory factory)
    throws IOException, URISyntaxException {
    Objects.requireNonNull(stream);
    if (factory == null) {
      factory = ChartRepository::new;
    }
    boolean reified = false;
    Path helmHomePath = null;
    if (archiveCacheDirectory == null) {
      helmHomePath = helmHome.toPath();
      assert helmHomePath != null;
      archiveCacheDirectory = helmHomePath.resolve("cache/archive");
      assert archiveCacheDirectory != null;
      if (reifyHelmHomeIfNecessary) {
        helmHome.reify();
        reified = true;
      }
    }
    if (!Files.isDirectory(archiveCacheDirectory)) {
      throw new IllegalArgumentException("!Files.isDirectory(archiveCacheDirectory): " + archiveCacheDirectory);
    }
    if (indexCacheDirectory == null) {
      if (helmHomePath == null) {
        helmHomePath = helmHome.toPath();
        assert helmHomePath != null;
      }
      indexCacheDirectory = helmHomePath.resolve("repository/cache");
      assert indexCacheDirectory != null;
      if (!reified && reifyHelmHomeIfNecessary) {
        helmHome.reify();
        reified = true;
      }
    }
    if (!Files.isDirectory(indexCacheDirectory)) {
      throw new IllegalArgumentException("!Files.isDirectory(indexCacheDirectory): " + indexCacheDirectory);
    }
    final Map<?, ?> map = new Yaml(new SafeConstructor()).load(stream);
    if (map == null || map.isEmpty()) {
      throw new IllegalArgumentException("No data readable from stream: " + stream);
    }
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
          Path cachedIndexPath = Objects.requireNonNull(Paths.get((String)repositoryMap.get("cache")));
          if (!cachedIndexPath.isAbsolute()) {
            cachedIndexPath = indexCacheDirectory.resolve(cachedIndexPath);
            assert cachedIndexPath.isAbsolute();
          }
          
          final ChartRepository chartRepository = factory.createChartRepository(name, uri, archiveCacheDirectory, indexCacheDirectory, cachedIndexPath);
          if (chartRepository == null) {
            throw new IllegalStateException("factory.createChartRepository() == null");
          }
          chartRepositories.add(chartRepository);
        }      
      }
    }
    return new ChartRepositoryRepository(chartRepositories);
  }


  /*
   * Inner and nested classes.
   */
  

  /**
   * A factory for {@link ChartRepository} instances.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   *
   * @see ChartRepository
   */
  @FunctionalInterface
  public static interface ChartRepositoryFactory {

    /**
     * Creates a new {@link ChartRepository} and returns it.
     *
     * @param name the name of the chart repository; must not be
     * {@code null}
     *
     * @param uri the {@link URI} to the root of the chart repository;
     * must not be {@code null}
     *
     * @param archiveCacheDirectory an {@linkplain Path#isAbsolute()
     * absolute} {@link Path} representing a directory where Helm chart
     * archives may be stored; if {@code null} then often a {@link Path}
     * beginning with the absolute directory represented by the value of
     * the {@code helm.home} system property, or the value of the {@code
     * HELM_HOME} environment variable, appended with {@code
     * cache/archive} will be used instead
     *
     * @param indexCacheDirectory an {@linkplain Path#isAbsolute()
     * absolute} {@link Path} representing a directory that the supplied
     * {@code cachedIndexPath} parameter value will be considered to be
     * relative to; <strong>will be ignored and hence may be {@code
     * null}</strong> if the supplied {@code cachedIndexPath} parameter
     * value {@linkplain Path#isAbsolute() is absolute}
     *
     * @param cachedIndexPath a {@link Path} naming the file that will
     * store a copy of the chart repository's {@code index.yaml} file;
     * if {@code null} then a {@link Path} relative to the absolute
     * directory represented by the value of the {@code helm.home}
     * system property, or the value of the {@code HELM_HOME}
     * environment variable, and bearing a name consisting of the
     * supplied {@code name} suffixed with {@code -index.yaml} will
     * often be used instead
     *
     * @exception NullPointerException if either {@code name} or {@code
     * uri} is {@code null}
     *
     * @exception IllegalArgumentException if {@code uri} is {@linkplain
     * URI#isAbsolute() not absolute}, or if there is no existing "Helm
     * home" directory, or if {@code archiveCacheDirectory} is
     * non-{@code null} and either empty or not {@linkplain
     * Path#isAbsolute()}
     *
     * @return a new, non-{@code null} {@link ChartRepository}
     *
     * @see ChartRepository#ChartRepository(String, URI, Path, Path,
     * Path)
     */
    public ChartRepository createChartRepository(final String name, final URI uri, final Path archiveCacheDirectory, final Path indexCacheDirectory, final Path cachedIndexPath);
    
  }
 
}
