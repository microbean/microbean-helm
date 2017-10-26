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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import java.nio.file.CopyOption; // for javadoc only
import java.nio.file.LinkOption; // for javadoc only
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.nio.file.attribute.FileAttribute; // for javadoc only

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.TreeMap;

import java.util.zip.GZIPInputStream;

import com.github.zafarkhaja.semver.ParseException;
import com.github.zafarkhaja.semver.Version;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.MetadataOuterClass.Metadata;
import hapi.chart.MetadataOuterClass.MetadataOrBuilder;

import org.kamranzafar.jtar.TarInputStream;

import org.microbean.development.annotation.Experimental;

import org.microbean.helm.chart.Metadatas;
import org.microbean.helm.chart.TapeArchiveChartLoader;

import org.microbean.helm.chart.resolver.AbstractChartResolver;
import org.microbean.helm.chart.resolver.ChartResolverException;

import org.yaml.snakeyaml.Yaml;

/**
 * An {@link AbstractChartResolver} that {@linkplain #resolve(String,
 * String) resolves} <a
 * href="https://docs.helm.sh/developing_charts/#charts">Helm
 * charts</a> from <a
 * href="https://docs.helm.sh/developing_charts/#create-a-chart-repository">a
 * given Helm chart repository</a>.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #resolve(String, String)
 */
@Experimental
public class ChartRepository extends AbstractChartResolver {


  /*
   * Instance fields.
   */


  /**
   * An {@linkplain Path#isAbsolute() absolute} {@link Path}
   * representing a directory where Helm chart archives may be stored.
   *
   * <p>This field will never be {@code null}.</p>
   */
  private final Path archiveCacheDirectory;

  /**
   * An {@linkplain Path#isAbsolute() absolute} or relative {@link
   * Path} representing a local copy of a chart repository's <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file.
   *
   * <p>If the value of this field is a relative {@link Path}, then it
   * will be considered to be relative to the value of the {@link
   * #indexCacheDirectory} field.</p>
   *
   * <p>This field will never be {@code null}.</p>
   *
   * @see #getCachedIndexPath()
   */
  private final Path cachedIndexPath;

  /**
   * The {@link Index} object representing the chart repository index
   * as described canonically by its <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getIndex()
   *
   * @see #downloadIndex()
   */
  private transient Index index;

  /**
   * An {@linkplain Path#isAbsolute() absolute} {@link Path}
   * representing a directory that the value of the {@link
   * #cachedIndexPath} field will be considered to be relative to.
   *
   * <p>This field may be {@code null}, in which case it is guaranteed
   * that the {@link #cachedIndexPath} field's value is {@linkplain
   * Path#isAbsolute() absolute}.</p>
   */
  private final Path indexCacheDirectory;

  /**
   * The name of this {@link ChartRepository}.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #getName()
   */
  private final String name;

  /**
   * The {@link URI} representing the root of the chart repository
   * represented by this {@link ChartRepository}.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #getUri()
   */
  private final URI uri;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link ChartRepository} whose {@linkplain
   * #getCachedIndexPath() cached index path} will be a {@link Path}
   * relative to the absolute directory represented by the value of
   * the {@code helm.home} system property, or the value of the {@code
   * HELM_HOME} environment variable, and bearing a name consisting of
   * the supplied {@code name} suffixed with {@code -index.yaml}.
   *
   * @param name the name of this {@link ChartRepository}; must not be
   * {@code null}
   *
   * @param uri the {@linkplain URI#isAbsolute() absolute} {@link URI}
   * to the root of this {@link ChartRepository}; must not be {@code
   * null}
   *
   * @exception NullPointerException if either {@code name} or {@code
   * uri} is {@code null}
   *
   * @exception IllegalArgumentException if {@code uri} is {@linkplain
   * URI#isAbsolute() not absolute}, or if there is no existing "Helm
   * home" directory
   *
   * @see #ChartRepository(String, URI, Path, Path, Path)
   *
   * @see #getName()
   *
   * @see #getUri()
   *
   * @see #getCachedIndexPath()
   */
  public ChartRepository(final String name, final URI uri) {
    this(name, uri, null, null, null);
  }

  /**
   * Creates a new {@link ChartRepository}.
   *
   * @param name the name of this {@link ChartRepository}; must not be
   * {@code null}
   *
   * @param uri the {@link URI} to the root of this {@link
   * ChartRepository}; must not be {@code null}
   *
   * @param cachedIndexPath a {@link Path} naming the file that will
   * store a copy of the chart repository's {@code index.yaml} file;
   * if {@code null} then a {@link Path} relative to the absolute
   * directory represented by the value of the {@code helm.home}
   * system property, or the value of the {@code HELM_HOME}
   * environment variable, and bearing a name consisting of the
   * supplied {@code name} suffixed with {@code -index.yaml} will be
   * used instead
   *
   * @exception NullPointerException if either {@code name} or {@code
   * uri} is {@code null}
   *
   * @exception IllegalArgumentException if {@code uri} is {@linkplain
   * URI#isAbsolute() not absolute}, or if there is no existing "Helm
   * home" directory
   *
   * @see #ChartRepository(String, URI, Path, Path, Path)
   *
   * @see #getName()
   *
   * @see #getUri()
   *
   * @see #getCachedIndexPath()
   */
  public ChartRepository(final String name, final URI uri, final Path cachedIndexPath) {
    this(name, uri, null, null, cachedIndexPath);
  }

  /**
   * Creates a new {@link ChartRepository}.
   *
   * @param name the name of this {@link ChartRepository}; must not be
   * {@code null}
   *
   * @param uri the {@link URI} to the root of this {@link
   * ChartRepository}; must not be {@code null}
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
   * @param cachedIndexPath a {@link Path} naming the file that will
   * store a copy of the chart repository's {@code index.yaml} file;
   * if {@code null} then a {@link Path} relative to the absolute
   * directory represented by the value of the {@code helm.home}
   * system property, or the value of the {@code HELM_HOME}
   * environment variable, and bearing a name consisting of the
   * supplied {@code name} suffixed with {@code -index.yaml} will be
   * used instead
   *
   * @exception NullPointerException if either {@code name} or {@code
   * uri} is {@code null}
   *
   * @exception IllegalArgumentException if {@code uri} is {@linkplain
   * URI#isAbsolute() not absolute}, or if there is no existing "Helm
   * home" directory
   *
   * @see #ChartRepository(String, URI, Path, Path, Path)
   *
   * @see #getName()
   *
   * @see #getUri()
   *
   * @see #getCachedIndexPath()
   */
  public ChartRepository(final String name, final URI uri, final Path archiveCacheDirectory, Path indexCacheDirectory, Path cachedIndexPath) {
    super();
    Objects.requireNonNull(name);
    Objects.requireNonNull(uri);    
    if (!uri.isAbsolute()) {
      throw new IllegalArgumentException("!uri.isAbsolute(): " + uri);
    }
    
    Path helmHome = null;

    if (archiveCacheDirectory == null) {
      helmHome = getHelmHome();
      assert helmHome != null;
      this.archiveCacheDirectory = helmHome.resolve("cache/archive");
      assert this.archiveCacheDirectory.isAbsolute();
    } else if (archiveCacheDirectory.toString().isEmpty()) {
      throw new IllegalArgumentException("archiveCacheDirectory.toString().isEmpty(): " + archiveCacheDirectory);
    } else if (!archiveCacheDirectory.isAbsolute()) {
      throw new IllegalArgumentException("!archiveCacheDirectory.isAbsolute(): " + archiveCacheDirectory);
    } else {
      this.archiveCacheDirectory = archiveCacheDirectory;
    }
    if (!Files.isDirectory(this.archiveCacheDirectory)) {
      throw new IllegalArgumentException("!Files.isDirectory(this.archiveCacheDirectory): " + this.archiveCacheDirectory);
    }

    if (cachedIndexPath == null || cachedIndexPath.toString().isEmpty()) {
      cachedIndexPath = Paths.get(new StringBuilder(name).append("-index.yaml").toString());
    }
    this.cachedIndexPath = cachedIndexPath;

    if (cachedIndexPath.isAbsolute()) {
      this.indexCacheDirectory = null;
    } else {
      if (indexCacheDirectory == null) {
        if (helmHome == null) {
          helmHome = getHelmHome();
          assert helmHome != null;
        }
        this.indexCacheDirectory = helmHome.resolve("repository/cache");
        assert this.indexCacheDirectory.isAbsolute();
      } else if (!indexCacheDirectory.isAbsolute()) {
        throw new IllegalArgumentException("!indexCacheDirectory.isAbsolute(): " + indexCacheDirectory);
      } else {
        this.indexCacheDirectory = indexCacheDirectory;
      }
      if (!Files.isDirectory(indexCacheDirectory)) {
        throw new IllegalArgumentException("!Files.isDirectory(indexCacheDirectory): " + indexCacheDirectory);
      }
    }
    
    this.name = name;
    this.uri = uri;
  }


  /*
   * Instance methods.
   */


  /**
   * Returns the name of this {@link ChartRepository}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return the non-{@code null} name of this {@link ChartRepository}
   */
  public final String getName() {
    return this.name;
  }

  /**
   * Returns the {@link URI} of the root of this {@link
   * ChartRepository}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return the non-{@code null} {@link URI} of the root of this
   * {@link ChartRepository}
   */
  public final URI getUri() {
    return this.uri;
  }

  /**
   * Returns a non-{@code null}, {@linkplain Path#isAbsolute()
   * absolute} {@link Path} to the file that contains or will contain
   * a copy of the chart repository's <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return a non-{@code null}, {@linkplain Path#isAbsolute()
   * absolute} {@link Path} to the file that contains or will contain
   * a copy of the chart repository's <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file
   */
  public final Path getCachedIndexPath() {
    return this.cachedIndexPath;
  }

  /**
   * Returns the {@link Index} for this {@link ChartRepository}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>If this method has not been invoked before on this {@link
   * ChartRepository}, then the {@linkplain #getCachedIndexPath()
   * cached copy} of the chart repository's <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file is parsed into an {@link Index} and that
   * {@link Index} is stored in an instance variable before it is
   * returned.</p>
   *
   * <p>If no {@linkplain #getCachedIndexPath() cached copy} of the
   * chart repository's <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file exists, then one is {@linkplain
   * #downloadIndex() downloaded} first.</p>
   *
   * return the {@link Index} representing the contents of this {@link
   * ChartRepository}; never {@code null}
   *
   * @exception IOException if there was a problem either parsing an
   * <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file or downloading it
   *
   * @exception URISyntaxException if one of the URIs in the <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file is invalid
   *
   * @see #getIndex(boolean)
   *
   * @see #downloadIndex()
   */
  public final Index getIndex() throws IOException, URISyntaxException {
    return this.getIndex(false);
  }

  /**
   * Returns the {@link Index} for this {@link ChartRepository}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>If this method has not been invoked before on this {@link
   * ChartRepository}, then the {@linkplain #getCachedIndexPath()
   * cached copy} of the chart repository's <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file is parsed into an {@link Index} and that
   * {@link Index} is stored in an instance variable before it is
   * returned.</p>
   *
   * <p>If the {@linkplain #getCachedIndexPath() cached copy} of the
   * chart repository's <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file {@linkplain #isCachedIndexExpired() has
   * expired}, then one is {@linkplain #downloadIndex() downloaded}
   * first.</p>
   * 
   * @param forceDownload if {@code true} then no caching will happen
   *
   * @return the {@link Index} representing the contents of this {@link
   * ChartRepository}; never {@code null}
   *
   * @exception IOException if there was a problem either parsing an
   * <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file or downloading it
   *
   * @exception URISyntaxException if one of the URIs in the <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file is invalid
   *
   * @see #getIndex(boolean)
   *
   * @see #downloadIndex()
   *
   * @see #isCachedIndexExpired()
   */
  public final Index getIndex(final boolean forceDownload) throws IOException, URISyntaxException {
    if (forceDownload || this.index == null) {
      final Path cachedIndexPath = this.getCachedIndexPath();
      assert cachedIndexPath != null;
      if (forceDownload || this.isCachedIndexExpired()) {
        this.downloadIndexTo(cachedIndexPath);
      }
      this.index = Index.loadFrom(cachedIndexPath);
      assert this.index != null;
    }
    return this.index;
  }

  /**
   * Returns {@code true} if the {@linkplain #getCachedIndexPath()
   * cached copy} of the <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file is to be considered stale.
   *
   * <p>The default implementation of this method returns the negation
   * of the return value of an invocation of the {@link
   * Files#isRegularFile(Path, LinkOption...)} method on the return value of the
   * {@link #getCachedIndexPath()} method.</p>
   *
   * @return {@code true} if the {@linkplain #getCachedIndexPath()
   * cached copy} of the <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file is to be considered stale; {@code false} otherwise
   *
   * @see #getIndex(boolean)
   */
  public boolean isCachedIndexExpired() {
    final Path cachedIndexPath = this.getCachedIndexPath();
    assert cachedIndexPath != null;
    return !Files.isRegularFile(cachedIndexPath);
  }

  /**
   * Clears the {@link Index} stored internally by this {@link
   * ChartRepository}, paving the way for a fresh copy to be installed
   * by the {@link #getIndex(boolean)} method, and returns the old
   * value.
   *
   * <p>This method may return {@code null} if {@code
   * #getIndex(boolean)} has not yet been called.</p>
   *
   * @return the {@link Index}, or {@code null}
   */
  public final Index clearIndex() {
    final Index returnValue = this.index;
    this.index = null;
    return returnValue;
  }

  /**
   * Invokes the {@link #downloadIndexTo(Path)} method with the return
   * value of the {@link #getCachedIndexPath()} method.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return {@link Path} the {@link Path} to which the {@code
   * index.yaml} file was downloaded; never {@code null}
   *
   * @exception IOException if there was a problem downloading
   *
   * @see #downloadIndexTo(Path)
   */
  public final Path downloadIndex() throws IOException {
    return this.downloadIndexTo(this.getCachedIndexPath());
  }

  /**
   * Downloads a copy of the chart repository's <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file to the {@link Path} specified and returns
   * the canonical representation of the {@link Path} to which the
   * file was actually downloaded.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * <p>The default implementation of this method actually downloads
   * the <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file to a {@linkplain
   * Files#createTempFile(String, String, FileAttribute...) temporary
   * file} first, and then {@linkplain StandardCopyOption#ATOMIC_MOVE
   * atomically renames it}.</p>
   *
   * @param path the {@link Path} to download the <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">{@code
   * index.yaml}</a> file to; may be {@code null} in which case the
   * return value of the {@link #getCachedIndexPath()} method will be
   * used instead
   *
   * @return the {@link Path} to the file; never {@code null}
   *
   * @exception IOException if there was a problem downloading
   */
  public Path downloadIndexTo(Path path) throws IOException {
    final URI baseUri = this.getUri();
    if (baseUri == null) {
      throw new IllegalStateException("getUri() == null");
    }
    final URI indexUri = baseUri.resolve("index.yaml");
    assert indexUri != null;
    final URL indexUrl = indexUri.toURL();
    assert indexUrl != null;
    if (path == null) {
      path = this.getCachedIndexPath();
    }
    assert path != null;
    if (!path.isAbsolute()) {
      assert this.indexCacheDirectory != null;
      assert this.indexCacheDirectory.isAbsolute();
      path = this.indexCacheDirectory.resolve(path);
      assert path != null;
      assert path.isAbsolute();
    }
    final Path temporaryPath = Files.createTempFile(new StringBuilder(this.getName()).append("-index-").toString(), ".yaml");
    assert temporaryPath != null;
    try (final BufferedInputStream stream = new BufferedInputStream(indexUrl.openStream())) {
      Files.copy(stream, temporaryPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (final IOException throwMe) {
      try {
        Files.deleteIfExists(temporaryPath);
      } catch (final IOException suppressMe) {
        throwMe.addSuppressed(suppressMe);
      }
      throw throwMe;
    }
    return Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Creates a new {@link Index} from the contents of the {@linkplain
   * #getCachedIndexPath() cached copy of the chart repository's
   * <code>index.yaml</code> file} and returns it.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @return a new {@link Index}; never {@code null}
   *
   * @exception IOException if there was a problem reading the file
   *
   * @exception URISyntaxException if a URI in the file was invalid
   *
   * @see Index#loadFrom(Path)
   */
  public Index loadIndex() throws IOException, URISyntaxException {
    Path path = this.getCachedIndexPath();
    assert path != null;
    if (!path.isAbsolute()) {
      assert this.indexCacheDirectory != null;
      assert this.indexCacheDirectory.isAbsolute();
      path = this.indexCacheDirectory.resolve(path);
      assert path != null;
      assert path.isAbsolute();
    }
    return Index.loadFrom(path);
  }

  /**
   * Given a Helm chart name and its version, returns the local {@link
   * Path}, representing a local copy of the Helm chart as downloaded
   * from the chart repository represented by this {@link
   * ChartRepository}, downloading the archive if necessary.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @param chartName the name of the chart whose local {@link Path}
   * should be returned; must not be {@code null}
   *
   * @param chartVersion the version of the chart to select; may be
   * {@code null} in which case "latest" semantics are implied
   *
   * @return the {@link Path} to the chart archive, or {@code null}
   *
   * @exception IOException if there was a problem downloading
   *
   * @exception URISyntaxException if this {@link ChartRepository}'s
   * {@linkplain #getIndex() associated <code>Index</code>} could not
   * be parsed
   *
   * @exception NullPointerException if {@code chartName} is {@code
   * null}
   */
  public final Path getCachedChartPath(final String chartName, String chartVersion) throws IOException, URISyntaxException {
    Objects.requireNonNull(chartName);
    Path returnValue = null;
    if (chartVersion == null) {
      final Index index = this.getIndex(false);
      assert index != null;
      final Index.Entry entry = index.getEntry(chartName, null /* latest */);
      if (entry != null) {
        chartVersion = entry.getVersion();
      }
    }
    if (chartVersion != null) {
      assert this.archiveCacheDirectory != null;
      final StringBuilder chartKey = new StringBuilder(chartName).append("-").append(chartVersion);
      final String chartFilename = new StringBuilder(chartKey).append(".tgz").toString();
      final Path cachedChartPath = this.archiveCacheDirectory.resolve(chartFilename);
      assert cachedChartPath != null;
      if (!Files.isRegularFile(cachedChartPath)) {
        final Index index = this.getIndex(true);
        assert index != null;
        final Index.Entry entry = index.getEntry(chartName, chartVersion);
        if (entry != null) {
          final URI chartUri = entry.getFirstUri();
          if (chartUri != null) {
            final URL chartUrl = chartUri.toURL();
            assert chartUrl != null;
            final Path temporaryPath = Files.createTempFile(chartKey.append("-").toString(), ".tgz");
            assert temporaryPath != null;
            try (final InputStream stream = new BufferedInputStream(chartUrl.openStream())) {
              Files.copy(stream, temporaryPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException throwMe) {
              try {
                Files.deleteIfExists(temporaryPath);
              } catch (final IOException suppressMe) {
                throwMe.addSuppressed(suppressMe);
              }
              throw throwMe;
            }
            Files.move(temporaryPath, cachedChartPath, StandardCopyOption.ATOMIC_MOVE);
          }
        }
      }
      returnValue = cachedChartPath;
    }
    return returnValue;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation calls the {@link
   * #getCachedChartPath(String, String)} method with the supplied
   * arguments and uses a {@link TapeArchiveChartLoader} to load the
   * resulting archive into a {@link Chart.Builder} object.</p>
   */
  @Override
  public Chart.Builder resolve(final String chartName, String chartVersion) throws ChartResolverException {
    Objects.requireNonNull(chartName);
    Chart.Builder returnValue = null;
    Path cachedChartPath = null;
    try {
      cachedChartPath = this.getCachedChartPath(chartName, chartVersion);
    } catch (final IOException | URISyntaxException exception) {
      throw new ChartResolverException(exception.getMessage(), exception);
    }
    if (cachedChartPath != null && Files.isRegularFile(cachedChartPath)) {
      try (final TapeArchiveChartLoader loader = new TapeArchiveChartLoader()) {
        returnValue = loader.load(new TarInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(cachedChartPath)))));
      } catch (final IOException exception) {
        throw new ChartResolverException(exception.getMessage(), exception);
      }
    }
    return returnValue;
  }

  /**
   * Returns a {@link Path} representing "Helm home": the root
   * directory for various Helm-related metadata as specified by
   * either the {@code helm.home} system property or the {@code
   * HELM_HOME} environment variable.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>No guarantee is made by this method regarding whether the
   * returned {@link Path} actually denotes a directory.</p>
   *
   * @return a {@link Path} representing "Helm home"; never {@code
   * null}
   *
   * @exception SecurityException if there are not sufficient
   * permissions to read system properties or environment variables
   */
  static final Path getHelmHome() {
    String helmHome = System.getProperty("helm.home", System.getenv("HELM_HOME"));
    if (helmHome == null) {
      helmHome = Paths.get(System.getProperty("user.home")).resolve(".helm").toString();
      assert helmHome != null;
    }
    return Paths.get(helmHome);
  }


  /*
   * Inner and nested classes.
   */


  /**
   * A class representing certain of the contents of a <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-repository-structure">Helm
   * chart repository's {@code index.yaml} file</a>.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   */
  @Experimental
  public static final class Index {


    /*
     * Instance fields.
     */


    /**
     * An {@linkplain Collections#unmodifiableSortedMap(SortedMap)
     * immutable} {@link SortedMap} of {@link SortedSet}s of {@link
     * Entry} objects whose values represent enough information to
     * derive a URI to a Helm chart.
     *
     * <p>This field is never {@code null}.</p>
     */
    private final SortedMap<String, SortedSet<Entry>> entries;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link Index}.
     *
     * @param entries a {@link Map} of {@link SortedSet}s of {@link
     * Entry} objects indexed by the name of the Helm chart they
     * describe; may be {@code null}; copied by value
     */
    Index(final Map<? extends String, ? extends SortedSet<Entry>> entries) {
      super();
      if (entries == null || entries.isEmpty()) {
        this.entries = Collections.emptySortedMap();
      } else {
        this.entries = Collections.unmodifiableSortedMap(new TreeMap<>(entries));
      }
    }


    /*
     * Instance methods.
     */


    /**
     * Returns a non-{@code null}, {@linkplain
     * Collections#unmodifiableMap(Map) immutable} {@link Map} of
     * {@link SortedSet}s of {@link Entry} objects, indexed by the
     * name of the Helm chart they describe.
     *
     * @return a non-{@code null}, {@linkplain
     * Collections#unmodifiableMap(Map) immutable} {@link Map} of
     * {@link SortedSet}s of {@link Entry} objects, indexed by the
     * name of the Helm chart they describe
     */
    public final Map<String, SortedSet<Entry>> getEntries() {
      return this.entries;
    }

    /**
     * Returns an {@link Entry} identified by the supplied {@code
     * name} and {@code version}, if there is one.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @param name the name of the Helm chart whose related {@link
     * Entry} is desired; must not be {@code null}
     *
     * @param versionString the version of the Helm chart whose
     * related {@link Entry} is desired; may be {@code null} in which
     * case "latest" semantics are implied
     *
     * @return an {@link Entry}, or {@code null}
     *
     * @exception NullPointerException if {@code name} is {@code null}
     */
    public final Entry getEntry(final String name, final String versionString) {
      Objects.requireNonNull(name);
      Entry returnValue = null;
      final Map<String, SortedSet<Entry>> entries = this.getEntries();
      if (entries != null && !entries.isEmpty()) {
        final SortedSet<Entry> entrySet = entries.get(name);
        if (entrySet != null && !entrySet.isEmpty()) {
          if (versionString == null) {
            returnValue = entrySet.first();
          } else {
            for (final Entry entry : entrySet) {
              // XXX TODO FIXME: probably want to make this a
              // constraint match, not just an equality comparison
              if (entry != null && versionString.equals(entry.getVersion())) {
                returnValue = entry;
                break;
              }
            }
          }
        }
      }
      return returnValue;
    }


    /*
     * Static methods.
     */


    /**
     * Creates a new {@link Index} whose contents are sourced from the
     * YAML file located at the supplied {@link Path}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param path the {@link Path} to a YAML file whose contents are
     * those of a <a
     * href="https://docs.helm.sh/developing_charts/#the-index-file">Helm
     * chart repository index</a>; must not be {@code null}
     *
     * @return a new {@link Index}; never {@code null}
     *
     * @exception IOException if there was a problem reading the file
     *
     * @exception URISyntaxException if one of the URIs in the file
     * was invalid
     *
     * @exception NullPointerException if {@code path} is {@code null}
     *
     * @see #loadFrom(InputStream)
     */
    public static final Index loadFrom(final Path path) throws IOException, URISyntaxException {
      Objects.requireNonNull(path);
      final Index returnValue;
      try (final BufferedInputStream stream = new BufferedInputStream(Files.newInputStream(path))) {
        returnValue = loadFrom(stream);
      }
      return returnValue;
    }

    /**
     * Creates a new {@link Index} whose contents are sourced from the
     * <a
     * href="https://docs.helm.sh/developing_charts/#the-index-file">Helm
     * chart repository index</a> YAML contents represented by the
     * supplied {@link InputStream}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param stream the {@link InputStream} to a YAML file whose contents are
     * those of a <a
     * href="https://docs.helm.sh/developing_charts/#the-index-file">Helm
     * chart repository index</a>; must not be {@code null}
     *
     * @return a new {@link Index}; never {@code null}
     *
     * @exception IOException if there was a problem reading the file
     *
     * @exception URISyntaxException if one of the URIs in the file
     * was invalid
     *
     * @exception NullPointerException if {@code path} is {@code null}
     */
    public static final Index loadFrom(final InputStream stream) throws IOException, URISyntaxException {
      Objects.requireNonNull(stream);
      final Index returnValue;
      final Map<?, ?> yamlMap = new Yaml().loadAs(stream, Map.class);
      if (yamlMap == null || yamlMap.isEmpty()) {
        returnValue = new Index(null);
      } else {
        final SortedMap<String, SortedSet<Index.Entry>> sortedEntryMap = new TreeMap<>();
        @SuppressWarnings("unchecked")
        final Map<? extends String, ? extends Collection<? extends Map<?, ?>>> entriesMap = (Map<? extends String, ? extends Collection<? extends Map<?, ?>>>)yamlMap.get("entries");
        if (entriesMap != null && !entriesMap.isEmpty()) {
          final Collection<? extends Map.Entry<? extends String, ? extends Collection<? extends Map<?, ?>>>> entries = entriesMap.entrySet();
          if (entries != null && !entries.isEmpty()) {
            for (final Map.Entry<? extends String, ? extends Collection<? extends Map<?, ?>>> mapEntry : entries) {
              if (mapEntry != null) {
                final String entryName = mapEntry.getKey();
                if (entryName != null) {
                  final Collection<? extends Map<?, ?>> entryContents = mapEntry.getValue();
                  if (entryContents != null && !entryContents.isEmpty()) {
                    for (final Map<?, ?> entryMap : entryContents) {
                      if (entryMap != null && !entryMap.isEmpty()) {
                        final Metadata.Builder metadataBuilder = Metadata.newBuilder();
                        assert metadataBuilder != null;
                        Metadatas.populateMetadataBuilder(metadataBuilder, entryMap);
                        @SuppressWarnings("unchecked")
                        final Collection<? extends String> uriStrings = (Collection<? extends String>)entryMap.get("urls");
                        Set<URI> uris = new LinkedHashSet<>();
                        if (uriStrings != null && !uriStrings.isEmpty()) {
                          for (final String uriString : uriStrings) {
                            if (uriString != null && !uriString.isEmpty()) {
                              uris.add(new URI(uriString));
                            }
                          }
                        }
                        SortedSet<Index.Entry> entryObjects = sortedEntryMap.get(entryName);
                        if (entryObjects == null) {
                          entryObjects = new TreeSet<>(Collections.reverseOrder());
                          sortedEntryMap.put(entryName, entryObjects);
                        }
                        entryObjects.add(new Index.Entry(metadataBuilder, uris));
                      }
                    }
                  }
                }
              }
            }
          }      
        }
        returnValue = new Index(sortedEntryMap);
      }
      return returnValue;
    }


    /*
     * Inner and nested classes.
     */


    /**
     * An entry in a <a
     * href="https://docs.helm.sh/developing_charts/#the-index-file">Helm
     * chart repository index</a>.
     *
     * @author <a href="https://about.me/lairdnelson"
     * target="_parent">Laird Nelson</a>
     */
    @Experimental
    public static final class Entry implements Comparable<Entry> {


      /*
       * Instance fields.
       */


      /**
       * A {@link MetadataOrBuilder} representing most of the contents
       * of the entry.
       *
       * <p>This field is never {@code null}.</p>
       */
      private final MetadataOrBuilder metadata;

      /**
       * An {@linkplain Collections#unmodifiableSet(Set) immutable}
       * {@link Set} of {@link URI}s describing where the particular
       * Helm chart described by this {@link Entry} may be downloaded
       * from.
       *
       * <p>This field is never {@code null}.</p>
       */
      private final Set<URI> uris;


      /*
       * Constructors.
       */


      /**
       * Creates a new {@link Entry}.
       *
       * @param metadata a {@link MetadataOrBuilder} representing most
       * of the contents of the entry; must not be {@code null}
       *
       * @param uris a {@link Collection} of {@link URI}s describing
       * where the particular Helm chart described by this {@link
       * Entry} may be downloaded from; may be {@code null}; copied by
       * value
       *
       * @exception NullPointerException if {@code metadata} is {@code
       * null}
       */
      Entry(final MetadataOrBuilder metadata, final Collection<? extends URI> uris) {
        super();
        this.metadata = Objects.requireNonNull(metadata);
        if (uris == null || uris.isEmpty()) {
          this.uris = Collections.emptySet();
        } else {
          this.uris = new LinkedHashSet<>(uris);
        }
      }


      /*
       * Instance methods.
       */


      /**
       * Compares this {@link Entry} to the supplied {@link Entry} and
       * returns a value less than {@code 0} if this {@link Entry} is
       * "less than" the supplied {@link Entry}, {@code 1} if this
       * {@link Entry} is "greater than" the supplied {@link Entry}
       * and {@code 0} if this {@link Entry} is equal to the supplied
       * {@link Entry}.
       *
       * <p>{@link Entry} objects are compared by {@linkplain
       * #getName() name} first, then {@linkplain #getVersion()
       * version}.</p>
       *
       * <p>It is intended that this {@link
       * #compareTo(ChartRepository.Index.Entry)} method is
       * {@linkplain Comparable consistent with equals}.</p>
       *
       * @param her the {@link Entry} to compare; must not be {@code null}
       *
       * @return a value less than {@code 0} if this {@link Entry} is
       * "less than" the supplied {@link Entry}, {@code 1} if this
       * {@link Entry} is "greater than" the supplied {@link Entry}
       * and {@code 0} if this {@link Entry} is equal to the supplied
       * {@link Entry}
       *
       * @exception NullPointerException if the supplied {@link Entry}
       * is {@code null}
       */
      @Override
      public final int compareTo(final Entry her) {
        Objects.requireNonNull(her); // see Comparable documentation
        
        final String myName = this.getName();
        final String herName = her.getName();
        if (myName == null) {
          if (herName != null) {
            return -1;
          }
        } else if (herName == null) {
          return 1;
        } else {
          final int nameComparison = myName.compareTo(herName);
          if (nameComparison != 0) {
            return nameComparison;
          }
        }
        
        final String myVersionString = this.getVersion();
        final String herVersionString = her.getVersion();
        if (myVersionString == null) {
          if (herVersionString != null) {
            return -1;
          }
        } else if (herVersionString == null) {
          return 1;
        } else {
          Version myVersion = null;
          try {
            myVersion = Version.valueOf(myVersionString);
          } catch (final IllegalArgumentException | ParseException badVersion) {
            myVersion = null;
          }
          Version herVersion = null;
          try {
            herVersion = Version.valueOf(herVersionString);
          } catch (final IllegalArgumentException | ParseException badVersion) {
            herVersion = null;
          }
          if (myVersion == null) {
            if (herVersion != null) {
              return -1;
            }
          } else if (herVersion == null) {
            return 1;
          } else {
            return myVersion.compareTo(herVersion);
          }
        }

        return 0;
      }

      /**
       * Returns a hashcode for this {@link Entry} based off its
       * {@linkplain #getName() name} and {@linkplain #getVersion()
       * version}.
       *
       * @return a hashcode for this {@link Entry}
       *
       * @see #compareTo(ChartRepository.Index.Entry)
       *
       * @see #equals(Object)
       *
       * @see #getName()
       *
       * @see #getVersion()
       */
      @Override
      public final int hashCode() {
        int hashCode = 17;

        final Object name = this.getName();
        int c = name == null ? 0 : name.hashCode();
        hashCode = 37 * hashCode + c;

        final Object version = this.getVersion();
        c = version == null ? 0 : version.hashCode();
        hashCode = 37 * hashCode + c;

        return hashCode;
      }

      /**
       * Returns {@code true} if the supplied {@link Object} is an
       * {@link Entry} and has a {@linkplain #getName() name} and
       * {@linkplain #getVersion() version} equal to those of this
       * {@link Entry}.
       *
       * @param other the {@link Object} to test; may be {@code null}
       * in which case {@code false} will be returned
       *
       * @return {@code true} if this {@link Entry} is equal to the
       * supplied {@link Object}; {@code false} otherwise
       *
       * @see #compareTo(ChartRepository.Index.Entry)
       *
       * @see #getName()
       *
       * @see #getVersion()
       *
       * @see #hashCode()
       */
      @Override
      public final boolean equals(final Object other) {
        if (other == this) {
          return true;
        } else if (other instanceof Entry) {
          final Entry her = (Entry)other;

          final Object myName = this.getName();
          if (myName == null) {
            if (her.getName() != null) {
              return false;
            }
          } else if (!myName.equals(her.getName())) {
            return false;
          }

          final Object myVersion = this.getVersion();
          if (myVersion == null) {
            if (her.getVersion() != null) {
              return false;
            }
          } else if (!myVersion.equals(her.getVersion())) {
            return false;
          }

          return true;
        } else {
          return false;
        }
      }

      /**
       * Returns the {@link MetadataOrBuilder} that comprises most of
       * the contents of this {@link Entry}.
       *
       * <p>This method never returns {@code null}.</p>
       *
       * @return the {@link MetadataOrBuilder} that comprises most of
       * the contents of this {@link Entry}; never {@code null}
       */
      public final MetadataOrBuilder getMetadataOrBuilder() {
        return this.metadata;
      }

      /**
       * Returns the return value of invoking the {@link
       * MetadataOrBuilder#getName()} method on the {@link
       * MetadataOrBuilder} returned by this {@link Entry}'s {@link
       * #getMetadataOrBuilder()} method.
       *
       * <p>This method may return {@code null}.</p>
       *
       * @return this {@link Entry}'s name, or {@code null}
       *
       * @see MetadataOrBuilder#getName()
       */
      public final String getName() {
        final MetadataOrBuilder metadata = this.getMetadataOrBuilder();
        assert metadata != null;
        return metadata.getName();
      }

      /**
       * Returns the return value of invoking the {@link
       * MetadataOrBuilder#getVersion()} method on the {@link
       * MetadataOrBuilder} returned by this {@link Entry}'s {@link
       * #getMetadataOrBuilder()} method.
       *
       * <p>This method may return {@code null}.</p>
       *
       * @return this {@link Entry}'s version, or {@code null}
       *
       * @see MetadataOrBuilder#getVersion()
       */
      public final String getVersion() {
        final MetadataOrBuilder metadata = this.getMetadataOrBuilder();
        assert metadata != null;
        return metadata.getVersion();
      }

      /**
       * Returns a non-{@code null}, {@linkplain
       * Collections#unmodifiableSet(Set) immutable} {@link Set} of
       * {@link URI}s representing the URIs from which the Helm chart
       * described by this {@link Entry} may be downloaded.
       *
       * <p>This method never returns {@code null}.</p>
       *
       * @return a non-{@code null}, {@linkplain
       * Collections#unmodifiableSet(Set) immutable} {@link Set} of
       * {@link URI}s representing the URIs from which the Helm chart
       * described by this {@link Entry} may be downloaded
       *
       * @see #getFirstUri()
       */
      public final Set<URI> getUris() {
        return this.uris;
      }

      /**
       * A convenience method that returns the first {@link URI} in
       * the {@link Set} of {@link URI}s returned by the {@link
       * #getUris()} method.
       *
       * <p>This method may return {@code null}.</p>
       *
       * @return the {@linkplain SortedSet#first() first} {@link URI}
       * in the {@link Set} of {@link URI}s returned by the {@link
       * #getUris()} method, or {@code null}
       *
       * @see #getUris()
       */
      public final URI getFirstUri() {
        final Set<URI> uris = this.getUris();
        final URI returnValue;
        if (uris == null || uris.isEmpty()) {
          returnValue = null;
        } else {
          final Iterator<URI> iterator = uris.iterator();
          if (iterator == null || !iterator.hasNext()) {
            returnValue = null;
          } else {
            returnValue = iterator.next();
          }
        }
        return returnValue;
      }

      /**
       * Returns a non-{@code null} {@link String} representation of
       * this {@link Entry}.
       *
       * @return a non-{@code null} {@link String} representation of
       * this {@link Entry}
       */
      @Override
      public final String toString() {
        String name = this.getName();
        if (name == null || name.isEmpty()) {
          name = "unnamed";
        }
        return new StringBuilder(name).append(" ").append(this.getVersion()).toString();
      }
      
    }
    
  }
  
}
