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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A {@link StreamOrientedChartLoader
 * StreamOrientedChartLoader&lt;ZipInputStream&gt;} that creates
 * {@link Chart} instances from {@link ZipInputStream} instances.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #toNamedInputStreamEntries(ZipInputStream)
 *
 * @see StreamOrientedChartLoader
 */
public class ZipInputStreamChartLoader extends StreamOrientedChartLoader<ZipInputStream> {

  
  /*
   * Constructors.
   */

  
  /**
   * Creates a new {@link ZipInputStreamChartLoader}.
   */
  public ZipInputStreamChartLoader() {
    super();
  }


  /*
   * Instance methods.
   */


  /**
   * Converts the supplied {@link ZipInputStream} into an {@link
   * Iterable} of {@link Entry} instances, each of which consists of
   * an {@link InputStream} representing an entry within the archive
   * together with its name.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method are not permitted to return {@code
   * null}.
   *
   * @param stream the {@link ZipInputStream} to read; must be
   * non-{@code null} or an effectively empty {@link Iterable} will be
   * returned
   *
   * @return a non-{@code null} {@link Iterable} of {@link Entry}
   * instances representing named {@link InputStream}s
   *
   * @exception IOException if there is a problem reading from the
   * supplied {@link ZipInputStream}
   */
  @Override
  protected Iterable<? extends Entry<? extends String, ? extends InputStream>> toNamedInputStreamEntries(final ZipInputStream stream) throws IOException {
    if (stream == null) {
      return new EmptyIterable();
    } else {
      return new Iterable<Entry<String, InputStream>>() {
        @Override
        public Iterator<Entry<String, InputStream>> iterator() {
          return new Iterator<Entry<String, InputStream>>() {
            private ZipEntry currentEntry;
            
            {
              try {
                this.currentEntry = stream.getNextEntry();
              } catch (final IOException ignore) {
                this.currentEntry = null;
              }
            }
            
            @Override
            public boolean hasNext() {
              return this.currentEntry != null;
            }
            
            @Override
            public Entry<String, InputStream> next() {
              if (this.currentEntry == null) {
                throw new NoSuchElementException();
              }
              ByteArrayInputStream bais = null;
              try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                int bytesRead = 0;
                final byte bytes[] = new byte[4096];
                while((bytesRead = stream.read(bytes)) >= 0) {
                  baos.write(bytes, 0, bytesRead);
                }
                baos.flush();
                bais = new ByteArrayInputStream(baos.toByteArray());
              } catch (final IOException wrapMe) {
                throw (NoSuchElementException)new NoSuchElementException(wrapMe.getMessage()).initCause(wrapMe);
              }
              final Entry<String, InputStream> returnValue = new SimpleImmutableEntry<>(this.currentEntry.getName(), bais);
              try {
                this.currentEntry = stream.getNextEntry();
              } catch (final IOException ignore) {
                this.currentEntry = null;
              }
              return returnValue;
            }
          };
        }
      };
    }
  }

  /**
   * Does nothing on purpose.
   *
   * @exception IOException if a subclass has overridden this method
   * and an error occurs
   */
  @Override
  public void close() throws IOException {

  }

  
}
