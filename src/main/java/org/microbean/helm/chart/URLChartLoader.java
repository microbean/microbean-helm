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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.IdentityHashMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;

import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import hapi.chart.ChartOuterClass.Chart; // for javadoc only

import org.kamranzafar.jtar.TarInputStream;

/**
 * A {@link StreamOrientedChartLoader StreamOrientedChartLoader&lt;URL&gt;} that creates
 * {@link Chart} instances from {@link URL} instances.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is safe for concurrent use by multiple threads.</p>
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #toNamedInputStreamEntries(URL)
 *
 * @see StreamOrientedChartLoader
 */
public class URLChartLoader extends StreamOrientedChartLoader<URL> {


  /**
   * Resources to be closed by the {@link #close()} method.
   *
   * <p>This field is never {@code null}.</p>
   */
  private final IdentityHashMap<AutoCloseable, Void> closeables;
  

  /*
   * Constructors.
   */

  
  /**
   * Creates a new {@link URLChartLoader}.
   */
  public URLChartLoader() {
    super();
    this.closeables = new IdentityHashMap<>();
  }


  /*
   * Instance methods.
   */


  /**
   * Converts the supplied {@link URL} into an {@link Iterable} of
   * {@link Entry} instances, each of which consists of an {@link
   * InputStream} representing a resource within a Helm chart together
   * with its (relative to the chart) name.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method are not permitted to return {@code
   * null}.
   *
   * @param url the {@link URL} to dereference; must be non-{@code
   * null} or an effectively empty {@link Iterable} will be returned
   *
   * @return a non-{@code null} {@link Iterable} of {@link Entry}
   * instances representing named {@link InputStream}s
   *
   * @exception IOException if there is a problem reading from the
   * supplied {@link URL}
   */
  @Override
  protected Iterable<? extends Entry<? extends String, ? extends InputStream>> toNamedInputStreamEntries(final URL url) throws IOException {
    Objects.requireNonNull(url);
    final String scheme = url.getProtocol();
    Path path = null;
    if ("file".equals(scheme)) {
      URI uri = null;
      try {
        uri = url.toURI();
      } catch (final URISyntaxException wrapMe) {
        throw new IllegalArgumentException(wrapMe.getMessage(), wrapMe);
      }
      assert uri != null;
      try {
        path = Paths.get(uri);
      } catch (final IllegalArgumentException notAFile) {
        path = null;
      }
    }
    final Iterable<? extends Entry<? extends String, ? extends InputStream>> returnValue;
    if (path == null || !Files.isDirectory(path)) {
      final String urlString = url.toString();
      assert urlString != null;
      if (urlString.endsWith(".zip") || urlString.endsWith(".jar")) {
        final ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(this.openStream(url)));
        this.closeables.put(zipInputStream, null);
        final ZipInputStreamChartLoader loader = new ZipInputStreamChartLoader();
        this.closeables.put(loader, null);
        returnValue = loader.toNamedInputStreamEntries(zipInputStream);
      } else {
        final TarInputStream tarInputStream = new TarInputStream(new GZIPInputStream(new BufferedInputStream(this.openStream(url))));
        this.closeables.put(tarInputStream, null);
        final TapeArchiveChartLoader loader = new TapeArchiveChartLoader();
        this.closeables.put(loader, null);
        returnValue = loader.toNamedInputStreamEntries(tarInputStream);
      }
    } else {
      final DirectoryChartLoader loader = new DirectoryChartLoader();
      this.closeables.put(loader, null);
      returnValue = loader.toNamedInputStreamEntries(path);
    }
    return returnValue;
  }

  /**
   * Returns an {@link InputStream} corresponding to the supplied
   * {@link URL}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <p>Overrides of this method are permitted to return {@code
   * null}.</p>
   *
   * @param url the {@link URL} whose affiliated {@link InputStream}
   * should be returned; may be {@code null} in which case {@code
   * null} will be returned
   *
   * @return an {@link InputStream} appropriate for the supplied
   * {@link URL}, or {@code null}
   *
   * @exception IOException if an error occurs while connecting to the
   * supplied {@link URL}
   */
  protected InputStream openStream(final URL url) throws IOException {
    InputStream returnValue = null;
    if (url != null) {
      final URLConnection urlConnection = url.openConnection();
      assert urlConnection != null;
      urlConnection.setRequestProperty("User-Agent", "microbean-helm");
      returnValue = urlConnection.getInputStream();
    }
    return returnValue;
  }

  /**
   * Closes resources opened by this {@link URLChartLoader}'s {@link
   * #toNamedInputStreamEntries(URL)} method.
   *
   * @exception IOException if a subclass has overridden this method
   * and an error occurs
   */
  @Override
  public void close() throws IOException {
    if (!this.closeables.isEmpty()) {
      final Collection<? extends AutoCloseable> keys = this.closeables.keySet();
      if (keys != null && !keys.isEmpty()) {
        final Iterator<? extends AutoCloseable> iterator = keys.iterator();
        if (iterator != null) {
          while (iterator.hasNext()) {
            final AutoCloseable closeable = iterator.next();
            if (closeable != null) {
              try {
                closeable.close();
              } catch (final IOException | RuntimeException throwMe) {
                throw throwMe;
              } catch (final Exception willNeverHappen) {
                throw new AssertionError(willNeverHappen);
              }
            }
            iterator.remove();
          }
        }
      }
    }
    assert this.closeables.isEmpty();
  }
  
}
