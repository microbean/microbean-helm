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
package org.microbean.helm.chart;

import java.io.IOException;
import java.io.InputStream;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.zip.GZIPInputStream;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ConfigOuterClass.Config;
import hapi.chart.MetadataOuterClass.Maintainer;
import hapi.chart.MetadataOuterClass.Metadata;
import hapi.chart.TemplateOuterClass.Template;

import org.kamranzafar.jtar.TarInputStream;

import org.microbean.development.annotation.Issue;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import org.yaml.snakeyaml.constructor.SafeConstructor;

import org.yaml.snakeyaml.representer.Representer;

/**
 * A partial {@link AbstractChartLoader} implementation that is capable of
 * loading a Helm-compatible chart from any source that is {@linkplain
 * #toNamedInputStreamEntries(Object) convertible into an
 * <code>Iterable</code> of <code>InputStream</code>s indexed by their
 * name}.
 *
 * @param <T> the type of source from which this {@link
 * StreamOrientedChartLoader} is capable of loading Helm charts
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #toNamedInputStreamEntries(Object)
 */
public abstract class StreamOrientedChartLoader<T> extends AbstractChartLoader<T> {


  /*
   * Static fields.
   */

  
  /**
   * A {@link Pattern} that matches the trailing component of a file
   * name in a valid Helm chart structure, provided it is not preceded
   * in its path components by either {@code /templates/} or {@code
   * /charts/}, and stores it as capturing group {@code 1}.
   *
   * <h2>Examples</h2>
   *
   * <ul>
   *
   * <li>Given {@code wordpress/README.md}, yields {@code
   * README.md}.</li>
   *
   * <li>Given {@code wordpress/charts/mariadb/README.md}, yields
   * nothing.</li>
   *
   * <li>Given {@code wordpress/templates/deployment.yaml}, yields
   * nothing.</li>
   *
   * <li>Given {@code wordpress/subdirectory/file.txt}, yields {@code
   * subdirectory/file.txt}.</li>
   *
   * </ul>
   */
  private static final Pattern fileNamePattern = Pattern.compile("^/*[^/]+(?!.*/(?:charts|templates)/)/(.+)$");

  @Issue(uri = "https://github.com/microbean/microbean-helm/issues/88")
  private static final Pattern templateFileNamePattern = Pattern.compile("^.+/(templates/.+)$");

  @Issue(uri = "https://github.com/microbean/microbean-helm/issues/63")
  private static final Pattern subchartFileNamePattern = Pattern.compile("^.+/charts/([^._][^/]+/?(.*))$");

  /**
   * <p>Please note that the lack of anchors ({@code ^} or {@code $})
   * and the leading "{@code .*?}" in this pattern's {@linkplain
   * Pattern#toString() value} are deliberate choices.</p>
   */
  private static final Pattern nonGreedySubchartsPattern = Pattern.compile(".*?/charts/[^/]+");

  private static final Pattern chartNamePattern = Pattern.compile("^.+/charts/([^/]+).*$");

  @Issue(uri = "https://github.com/microbean/microbean-helm/issues/63")
  private static final Pattern basenamePattern = Pattern.compile("^.*?([^/]+)$");


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link StreamOrientedChartLoader}.
   */
  protected StreamOrientedChartLoader() {
    super();
  }


  /*
   * Instance methods.
   */


  /**
   * Converts the supplied {@code source} into an {@link Iterable} of
   * {@link Entry} instances whose {@linkplain Entry#getKey() keys}
   * are names and whose {@linkplain Entry#getValue() values} are
   * corresponding {@link InputStream}s.
   *
   * <p>Implementations of this method must not return {@code
   * null}.</p>
   *
   * <p>The {@link Iterable} of {@link Entry} instances returned by
   * implementations of this method must {@linkplain
   * Iterable#iterator() produce an <code>Iterator</code>} that will
   * never return {@code null} from any invocation of its {@link
   * Iterator#next()} method when, on the same thread, the return
   * value of an invocation of its {@link Iterator#hasNext()} method
   * has previously returned {@code true}.</p>
   *
   * <p>{@link Entry} instances returned by {@link Iterator} instances
   * {@linkplain Iterable#iterator() produced by} the {@link Iterable}
   * returned by this method must never return {@code null} from their
   * {@link Entry#getKey()} method.  They are permitted to return
   * {@code null} from their {@link Entry#getValue()} method, and this
   * feature can be used, for example, to indicate that a particular
   * entry is a directory.</p>
   *
   * @param source the source to convert; must not be {@code null}
   *
   * @return an {@link Iterable} of suitable {@link Entry} instances;
   * never {@code null}
   *
   * @exception NullPointerException if {@code source} is {@code null}
   *
   * @exception IOException if an error occurs while converting
   */
  protected abstract Iterable<? extends Entry<? extends String, ? extends InputStream>> toNamedInputStreamEntries(final T source) throws IOException;

  /**
   * Creates a new {@link Chart} from the supplied {@code source} in
   * some manner and returns it.
   *
   * <p>This method never returns {@code null}.
   *
   * <p>This method calls the {@link
   * #load(hapi.chart.ChartOuterClass.Chart.Builder, Iterable)} method
   * with the return value of the {@link
   * #toNamedInputStreamEntries(Object)} method.</p>
   *
   * @param source the source object from which to load a new {@link
   * Chart}; must not be {@code null}
   *
   * @return a new {@link Chart}; never {@code null}
   *
   * @exception NullPointerException if {@code source} is {@code null}
   *
   * @exception IllegalStateException if the {@link
   * #load(hapi.chart.ChartOuterClass.Chart.Builder, Iterable)} method
   * returns {@code null}
   *
   * @exception IOException if a problem is encountered while creating
   * the {@link Chart} to return
   *
   * @see #toNamedInputStreamEntries(Object)
   *
   * @see #load(hapi.chart.ChartOuterClass.Chart.Builder, Iterable)
   */
  @Override
  public Chart.Builder load(final Chart.Builder parent, final T source) throws IOException {
    Objects.requireNonNull(source);
    final Chart.Builder returnValue = this.load(parent, toNamedInputStreamEntries(source));
    if (returnValue == null) {
      throw new IllegalStateException("load(toNamedInputStreamEntries(source)) == null; source: " + source);
    }
    return returnValue;
  }

  /**
   * Creates a new {@link Chart} from the supplied notional set of
   * named {@link InputStream}s and returns it.
   *
   * <p>This method never returns {@code null}.
   *
   * <p>This method is called by the {@link #load(Object)} method.</p>
   *
   * @param entrySet the {@link Iterable} of {@link Entry} instances
   * normally returned by the {@link
   * #toNamedInputStreamEntries(Object)} method; must not be {@code
   * null}
   *
   * @return a new {@link Chart}; never {@code null}
   *
   * @exception NullPointerException if {@code entrySet} is {@code
   * null}
   *
   * @exception IOException if a problem is encountered while creating
   * the {@link Chart} to return
   *
   * @see #toNamedInputStreamEntries(Object)
   *
   * @see #load(Object)
   */
  public Chart.Builder load(final Chart.Builder parent, final Iterable<? extends Entry<? extends String, ? extends InputStream>> entrySet) throws IOException {
    Objects.requireNonNull(entrySet);
    final Chart.Builder rootBuilder;
    if (parent == null) {
      rootBuilder = Chart.newBuilder();
    } else {
      rootBuilder = parent;
    }
    assert rootBuilder != null;
    final NavigableMap<String, Chart.Builder> chartBuilders = new TreeMap<>(new ChartPathComparator());
    // XXX TODO FIXME: do we really want to say the root is null?
    // Or should it always be a path named after the chart?
    chartBuilders.put(null, rootBuilder);
    for (final Entry<? extends String, ? extends InputStream> entry : entrySet) {
      if (entry != null) {
        final String key = entry.getKey();
        if (key != null) {
          final InputStream value = entry.getValue();
          if (value != null) {
            this.addFile(chartBuilders, key, value);
          }
        }
      }
    }
    return rootBuilder;
  }
  
  private final void addFile(final NavigableMap<String, Chart.Builder> chartBuilders, final String path, final InputStream stream) throws IOException {
    Objects.requireNonNull(chartBuilders);
    Objects.requireNonNull(path);
    Objects.requireNonNull(stream);
    
    final Chart.Builder builder = getChartBuilder(chartBuilders, path);
    if (builder == null) {
      throw new IllegalStateException();
    }
    
    final Object templateBuilder;
    final boolean subchartFile;
    String fileName = getTemplateFileName(path);
    if (fileName == null) {
      // Not a template file, not even in a subchart.
      templateBuilder = null;      
      fileName = getSubchartFileName(path);
      if (fileName == null) {
        // Not a subchart file or a template file so add it to the
        // root builder.
        subchartFile = false;
        fileName = getOrdinaryFileName(path);
      } else {
        subchartFile = true;
      }
    } else {
      subchartFile = false;
      templateBuilder = this.createTemplateBuilder(builder, stream, fileName);
    }
    assert fileName != null;
    if (templateBuilder == null) {
      switch (fileName) {
      case "Chart.yaml":
        this.installMetadata(builder, stream);
        break;
      case "values.yaml":
        this.installConfig(builder, stream);
        break;
      default:
        if (subchartFile) {
          if (fileName.endsWith(".prov")) {
            // The intent in the Go code, despite its implementation,
            // seems to be that a charts/foo.prov file should be
            // treated as an ordinary file whose name is, well,
            // charts/foo.prov, no matter how deep that directory
            // hierarchy is, and despite that fact that the .prov file
            // appears in a charts directory, which normally indicates
            // the presence of a subchart.
            // 
            // So ordinarily we'd be in a subchart here.  Let's say we're:
            //
            //   wordpress/charts/argle/charts/foo/charts/bar/grob/foobish/.blatz.prov.
            //
            // We don't want the Chart.Builder associated with
            // wordpress/charts/argle/charts/foo/charts/bar.  We want
            // the Chart.Builder associated with
            // wordpress/charts/argle/charts/foo.  And we want the
            // filename added to that builder to be
            // charts/bar/grob/foobish/.blatz.prov.  Let's take
            // advantage of the sorted nature of the chartBuilders Map
            // and look for our parent that way.
            final Entry<String, Chart.Builder> parentChartBuilderEntry = chartBuilders.lowerEntry(path);
            if (parentChartBuilderEntry == null) {
              throw new IllegalStateException("chartBuilders.lowerEntry(path) == null; path: " + path);
            }
            final String parentChartPath = parentChartBuilderEntry.getKey();
            final Chart.Builder parentChartBuilder = parentChartBuilderEntry.getValue();
            if (parentChartBuilder == null) {
              throw new IllegalStateException("chartBuilders.lowerEntry(path).getValue() == null; path: " + path);
            }
            final int prefixLength = ((parentChartPath == null ? "" : parentChartPath) + "/").length();
            assert path.length() > prefixLength;
            this.installAny(parentChartBuilder, stream, path.substring(prefixLength));
          } else if (!(fileName.startsWith("_") || fileName.startsWith(".")) &&
                     fileName.endsWith(".tgz") &&
                     fileName.equals(basename(fileName))) {
            assert fileName.indexOf('/') < 0;
            // A subchart *file* (i.e. not a directory) that is not a
            // .prov file, that is immediately beneath charts, that
            // doesn't start with '.' or '_', and that ends with .tgz.
            // Treat it as a tarball.
            //
            // So:  wordpress/charts/foo.tgz
            // Not: wordpress/charts/.foo.tgz
            // Not: wordpress/charts/_foo.tgz
            // Not: wordpress/charts/foo
            // Not: wordpress/charts/bar/foo.tgz
            // Not: wordpress/charts/_bar/foo.tgz
            Chart.Builder subchartBuilder = null;
            try (final TarInputStream tarInputStream = new TarInputStream(new GZIPInputStream(new NonClosingInputStream(stream)))) {
              subchartBuilder = new TapeArchiveChartLoader().load(builder, tarInputStream);
            }
            if (subchartBuilder == null) {
              throw new IllegalStateException("load(builder, tarInputStream) == null; path: " + path);
            }
            // builder.addDependencies(subchart);
          } else {
            // Not a .prov file under charts, nor a .tgz file, just a
            // regular subchart file.
            this.installAny(builder, stream, fileName);
          }
        } else {
          assert !subchartFile;
          // Not a subchart file or a template
          this.installAny(builder, stream, fileName);
        }
        break;
      }
    }
  }
  
  static final String getOrdinaryFileName(final String path) {
    String returnValue = null;
    if (path != null) {
      final Matcher fileMatcher = fileNamePattern.matcher(path);
      assert fileMatcher != null;
      if (fileMatcher.find()) {
        returnValue = fileMatcher.group(1);
      }
    }
    return returnValue;
  }
  
  static final String getSubchartFileName(final String path) {
    String returnValue = null;
    if (path != null) {
      final Matcher subchartMatcher = subchartFileNamePattern.matcher(path);
      assert subchartMatcher != null;
      if (subchartMatcher.find()) {
        // in foo/charts/bork/blatz.txt:
        //   group 1 is bork/blatz.txt
        //   group 2 is blatz.txt
        // in foo/charts/blatz.tgz:
        //   group 1 is blatz.tgz
        //   group 2 is (empty string)
        final String group2 = subchartMatcher.group(2);
        assert group2 != null;
        if (group2.isEmpty()) {
          returnValue = subchartMatcher.group(1);
          assert returnValue != null;
        } else {
          returnValue = group2;
        }
      }
    }
    return returnValue;

  }
  
  static final String getTemplateFileName(final String path) {
    String returnValue = null;
    if (path != null) {
      final Matcher templateMatcher = templateFileNamePattern.matcher(path);
      assert templateMatcher != null;
      if (templateMatcher.find()) {
        returnValue = templateMatcher.group(1);
      }
    }
    return returnValue;
  }

  /**
   * Given a semantic solidus-separated {@code chartPath} representing
   * a file or logical directory within a chart, returns the proper
   * {@link Chart.Builder} corresponding to that path.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Any intermediate {@link Chart.Builder}s will also be created
   * and properly parented.</p>
   *
   * @param chartBuilders a {@link Map} of {@link Chart.Builder}
   * instances indexed by paths; must not be {@code null}; may be
   * updated by this method
   *
   * @param chartPath a solidus-separated {@link String} representing
   * a file or directory within a chart; must not be {@code null}
   *
   * @return a {@link Chart.Builder}; never {@code null}
   *
   * @exception NullPointerException if either {@code chartBuilders}
   * or {@code chartPath} is {@code null}
   */
  private static final Chart.Builder getChartBuilder(final Map<String, Chart.Builder> chartBuilders, final String chartPath) {
    Objects.requireNonNull(chartBuilders);
    Objects.requireNonNull(chartPath);
    Chart.Builder rootBuilder = chartBuilders.get(null);
    if (rootBuilder == null) {
      rootBuilder = Chart.newBuilder();
      chartBuilders.put(null, rootBuilder);
    }
    assert rootBuilder != null;
    Chart.Builder returnValue = rootBuilder;
    final Collection<? extends String> chartPaths = toSubcharts(chartPath);
    if (chartPaths != null && !chartPaths.isEmpty()) {
      for (final String path : chartPaths) {
        // By contract, shallowest path comes first, so
        // foobar/charts/wordpress comes before, say,
        // foobar/charts/wordpress/charts/mysql
        Chart.Builder builder = chartBuilders.get(path);
        if (builder == null) {
          builder = createSubchartBuilder(returnValue, path);
          assert builder != null;
          chartBuilders.put(path, builder);
        }
        assert builder != null;
        returnValue = builder;
      }
    }
    assert returnValue != null;
    return returnValue;
  }

  /**
   * Given, e.g., {@code wordpress/charts/argle/charts/frob/foo.txt},
   * yield {@code [ wordpress/charts/argle,
   * wordpress/charts/argle/charts/frob ]}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param chartPath the "relative" solidus-separated path
   * identifying some chart resource; must not be {@code null}
   *
   * @return a {@link NavigableSet} of chart paths in ascending
   * subchart hierarchy order; never {@code null}
   */
  static final NavigableSet<String> toSubcharts(final String chartPath) {
    Objects.requireNonNull(chartPath);
    final NavigableSet<String> returnValue = new TreeSet<>(new ChartPathComparator());
    final Matcher matcher = nonGreedySubchartsPattern.matcher(chartPath);
    if (matcher != null) {
      while (matcher.find()) {
        returnValue.add(chartPath.substring(0, matcher.end()));
      }
    }
    return returnValue;
  }

  private static final Chart.Builder createSubchartBuilder(final Chart.Builder parentBuilder, final String chartPath) {
    Objects.requireNonNull(parentBuilder);
    Chart.Builder returnValue = null;
    final String chartName = getChartName(chartPath);
    if (chartName != null) {
      returnValue = parentBuilder.addDependenciesBuilder();
      assert returnValue != null;
      final Metadata.Builder builder = returnValue.getMetadataBuilder();
      assert builder != null;
      builder.setName(chartName);
    }
    return returnValue;
  }
  
  private static final String getChartName(final String chartPath) {
    String returnValue = null;
    if (chartPath != null) {
      final Matcher matcher = chartNamePattern.matcher(chartPath);
      assert matcher != null;
      if (matcher.find()) {
        returnValue = matcher.group(1);
      }
    }
    return returnValue;
  }

  private static final String basename(final String path) {
    String returnValue = null;
    if (path != null) {
      final Matcher matcher = basenamePattern.matcher(path);
      assert matcher != null;
      if (matcher.find()) {
        returnValue = matcher.group(1);
      }
    }
    return returnValue;
  }
  

  /*
   * Utility methods.
   */
  

  /**
   * Installs a {@link Config} object, represented by the supplied
   * {@link InputStream}, into the supplied {@link
   * hapi.chart.ChartOuterClass.Chart.Builder Chart.Builder}.
   *
   * @param chartBuilder the {@link
   * hapi.chart.ChartOuterClass.Chart.Builder Chart.Builder} to
   * affect; must not be {@code null}
   *
   * @param stream an {@link InputStream} representing <a
   * href="https://docs.helm.sh/developing_charts/#values-files">valid
   * values file contents</a> as defined by <a
   * href="https://docs.helm.sh/developing_charts/#values-files">the
   * chart specification</a>; must not be {@code null}
   *
   * @exception NullPointerException if {@code chartBuilder} or {@code
   * stream} is {@code null}
   *
   * @exception IOException if there was a problem reading from the
   * supplied {@link InputStream}
   *
   * @see hapi.chart.ChartOuterClass.Chart.Builder#getValuesBuilder()
   *
   * @see hapi.chart.ConfigOuterClass.Config.Builder#setRawBytes(ByteString)
   */
  protected void installConfig(final Chart.Builder chartBuilder, final InputStream stream) throws IOException {
    Objects.requireNonNull(chartBuilder);
    Objects.requireNonNull(stream);
    Config returnValue = null;
    final Config.Builder builder = chartBuilder.getValuesBuilder();
    assert builder != null;
    final ByteString rawBytes = ByteString.readFrom(stream);
    assert rawBytes != null;
    builder.setRawBytes(rawBytes);
  }

  /**
   * Installs a {@link Metadata} object, represented by the supplied
   * {@link InputStream}, into the supplied {@link
   * hapi.chart.ChartOuterClass.Chart.Builder Chart.Builder}.
   *
   * @param chartBuilder the {@link
   * hapi.chart.ChartOuterClass.Chart.Builder Chart.Builder} to
   * affect; must not be {@code null}
   *
   * @param stream an {@link InputStream} representing <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-yaml-file">valid
   * {@code Chart.yaml} contents</a> as defined by <a
   * href="https://docs.helm.sh/developing_charts/#the-chart-yaml-file">the
   * chart specification</a>; must not be {@code null}
   *
   * @exception NullPointerException if {@code chartBuilder} or {@code
   * stream} is {@code null}
   *
   * @exception IOException if there was a problem reading from the
   * supplied {@link InputStream}
   *
   * @see hapi.chart.ChartOuterClass.Chart.Builder#getMetadataBuilder()
   *
   * @see hapi.chart.MetadataOuterClass.Metadata.Builder
   */
  protected void installMetadata(final Chart.Builder chartBuilder, final InputStream stream) throws IOException {
    Objects.requireNonNull(chartBuilder);
    Objects.requireNonNull(stream);
    Metadata returnValue = null;
    final Map<?, ?> map = new Yaml(new SafeConstructor(), new Representer(), new DumperOptions(), new StringResolver()).load(stream);
    assert map != null;
    final Metadata.Builder metadataBuilder = chartBuilder.getMetadataBuilder();
    assert metadataBuilder != null;
    Metadatas.populateMetadataBuilder(metadataBuilder, map);
  }

  /**
   * {@linkplain
   * hapi.chart.ChartOuterClass.Chart.Builder#addTemplatesBuilder()
   * Creates a new} {@link
   * hapi.chart.TemplateOuterClass.Template.Builder} {@linkplain
   * hapi.chart.TemplateOuterClass.Template.Builder#setData(ByteString)
   * from the contents of the supplied <code>InputStream</code>},
   * {@linkplain
   * hapi.chart.TemplateOuterClass.Template.Builder#setName(String)
   * with the supplied <code>name</code>}, and returns it.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param chartBuilder a {@link
   * hapi.chart.ChartOuterClass.Chart.Builder} whose {@link
   * hapi.chart.ChartOuterClass.Chart.Builder#addTemplatesBuilder()}
   * method will be called to create the new {@link
   * hapi.chart.TemplateOuterClass.Template.Builder} instance; must
   * not be {@code null}
   *
   * @param stream an {@link InputStream} containing <a
   * href="https://docs.helm.sh/developing_charts/#template-files">valid
   * template contents</a> as defined by the <a
   * href="https://docs.helm.sh/developing_charts/#template-files">chart
   * specification</a>; must not be {@code null}
   *
   * @param name the name for the new {@link Template} that will
   * ultimately reside within the chart; must not be {@code null}
   *
   * @return a new {@link
   * hapi.chart.TemplateOuterClass.Template.Builder}; never {@code
   * null}
   *
   * @exception NullPointerException if {@code chartBuilder}, {@code
   * stream} or {@code name} is {@code null}
   *
   * @exception IOException if there was a problem reading from the
   * supplied {@link InputStream}
   *
   * @see hapi.chart.TemplateOuterClass.Template.Builder
   */
  protected Template.Builder createTemplateBuilder(final Chart.Builder chartBuilder, final InputStream stream, final String name) throws IOException {
    Objects.requireNonNull(chartBuilder);
    Objects.requireNonNull(stream);
    Objects.requireNonNull(name);
    final Template.Builder builder = chartBuilder.addTemplatesBuilder();
    assert builder != null;
    builder.setName(name);
    final ByteString data = ByteString.readFrom(stream);
    assert data != null;
    assert data.isValidUtf8();
    builder.setData(data);
    return builder;
  }

  /**
   * Installs an {@link Any} object, representing an arbitrary chart
   * file with the supplied {@code name} and represented by the
   * supplied {@link InputStream}, into the supplied {@link
   * hapi.chart.ChartOuterClass.Chart.Builder Chart.Builder}.
   *
   * @param chartBuilder the {@link
   * hapi.chart.ChartOuterClass.Chart.Builder Chart.Builder} to
   * affect; must not be {@code null}
   *
   * @param stream an {@link InputStream} representing <a
   * href="https://docs.helm.sh/developing_charts/">valid chart file
   * contents</a> as defined by <a
   * href="https://docs.helm.sh/developing_charts/">the chart
   * specification</a>; must not be {@code null}
   *
   * @param name the name of the file within the chart; must not be
   * {@code null}
   *
   * @exception NullPointerException if {@code chartBuilder} or {@code
   * stream} or {@code name} is {@code null}
   *
   * @exception IOException if there was a problem reading from the
   * supplied {@link InputStream}
   *
   * @see hapi.chart.ChartOuterClass.Chart.Builder#addFilesBuilder()
   */
  protected void installAny(final Chart.Builder chartBuilder, final InputStream stream, final String name) throws IOException {
    Objects.requireNonNull(chartBuilder);
    Objects.requireNonNull(stream);
    Objects.requireNonNull(name);
    Any returnValue = null;
    final Any.Builder builder = chartBuilder.addFilesBuilder();
    assert builder != null;
    builder.setTypeUrl(name);
    final ByteString fileContents = ByteString.readFrom(stream);
    assert fileContents != null;
    assert fileContents.isValidUtf8();
    builder.setValue(fileContents);
  }


  /*
   * Inner and nested classes.
   */


  /**
   * An {@link Iterable} implementation that {@linkplain #iterator()
   * returns an empty <code>Iterator</code>}.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   */
  static final class EmptyIterable implements Iterable<Entry<String, InputStream>> {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link EmptyIterable}.
     */
    EmptyIterable() {
      super();
    }


    /*
     * Instance methods.
     */
    

    /**
     * Returns the return value of the {@link
     * Collections#emptyIterator()} method when invoked.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return an empty {@link Iterator}; never {@code null}
     */
    @Override
    public final Iterator<Entry<String, InputStream>> iterator() {
      return Collections.emptyIterator();
    }
    
  }


  
  private static final class ChartPathComparator implements Comparator<String> {

    private ChartPathComparator() {
      super();
    }

    @Override
    public final int compare(final String chartPath1, final String chartPath2) {
      if (chartPath1 == null) {
        if (chartPath2 == null) {
          return 0;
        } else {
          return -1; // nulls go to the left
        }
      } else if (chartPath1.equals(chartPath2)) {
        return 0;
      } else if (chartPath2 == null) {
        return 1;
      } else {
        final int chartPath1Length = chartPath1.length();
        final int chartPath2Length = chartPath2.length();
        if (chartPath1Length == chartPath2Length) {
          return chartPath1.compareTo(chartPath2);
        } else if (chartPath1Length > chartPath2Length) {
          return 1;
        } else {
          return -1;
        }
      }
    }
    
  }
  
}
