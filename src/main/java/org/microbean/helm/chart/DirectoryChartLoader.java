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

import java.nio.file.Files;
import java.nio.file.LinkOption; // for javadoc only
import java.nio.file.Path;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;

import java.util.stream.Stream;

import java.util.zip.GZIPInputStream;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import hapi.chart.ChartOuterClass.Chart; // for javadoc only

/**
 * A {@link StreamOrientedChartLoader
 * StreamOrientedChartLoader&lt;Path&gt;} that creates {@link Chart}
 * instances from filesystem directories represented as {@link Path}
 * objects.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #toNamedInputStreamEntries(Path)
 *
 * @see StreamOrientedChartLoader
 */
public class DirectoryChartLoader extends StreamOrientedChartLoader<Path> {


  /*
   * Constructors.
   */
  
  
  /**
   * Creates a new {@link DirectoryChartLoader}.
   */
  public DirectoryChartLoader() {
    super();
  }


  /*
   * Instance methods.
   */
  

  /**
   * Converts the supplied {@link Path}, which must be non-{@code
   * null} and {@linkplain Files#isDirectory(Path, LinkOption...) a
   * directory}, into an {@link Iterable} of {@link Entry} instances,
   * each of which consists of an {@link InputStream} associated with
   * a name.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method are not permitted to return {@code
   * null}.
   *
   * @param path the {@link Path} to read; must be non-{@code null}
   * and must be {@linkplain Files#isDirectory(Path, LinkOption...) a
   * directory} or an effectively empty {@link Iterable} will be
   * returned
   *
   * @return a non-{@code null} {@link Iterable} of {@link Entry}
   * instances representing named {@link InputStream}s
   *
   * @exception IOException if there is a problem reading from the
   * directory represented by the supplied {@link Path} or any of its
   * subdirectories or files
   */
  @Override
  protected Iterable<? extends Entry<? extends String, ? extends InputStream>> toNamedInputStreamEntries(final Path path) throws IOException {
    final Iterable<Entry<String, InputStream>> returnValue;
    if (path == null || !Files.isDirectory(path)) {
      returnValue = new EmptyIterable();
    } else {
      returnValue = new PathWalker(path);
    }
    return returnValue;
  }


  /*
   * Inner and nested classes.
   */

  
  private static final class PathWalker implements Iterable<Entry<String, InputStream>> {

    private final Path directoryParent;

    private final Stream<? extends Path> pathStream;
    
    private PathWalker(final Path directory) throws IOException {
      super();
      Objects.requireNonNull(directory);
      if (!Files.isDirectory(directory)) {
        throw new IllegalArgumentException("!Files.isDirectory(directory): " + directory);
      }
      final Path directoryParent = directory.getParent();
      if (directoryParent == null) {
        throw new IllegalArgumentException("directory.getParent() == null");
      }
      this.directoryParent = directoryParent;
      final Stream<Path> pathStream;
      final Path helmIgnore = directory.resolve(".helmIgnore");
      assert helmIgnore != null;
      // TODO: p in the filters below needs to be tested to see if
      // it's, for example, foo/charts/bar/.fred--that .-prefixed
      // directory and all of its files has to be ignored.
      if (!Files.exists(helmIgnore)) {
        pathStream = Files.walk(directory)
          .filter(p -> p != null && !Files.isDirectory(p));
      } else {
        final HelmIgnorePathMatcher helmIgnorePathMatcher = new HelmIgnorePathMatcher(helmIgnore);
        pathStream = Files.walk(directory)
          .filter(p -> p != null && !Files.isDirectory(p) && !helmIgnorePathMatcher.matches(p));
      }
      this.pathStream = pathStream;
    }

    @Override
    public final Iterator<Entry<String, InputStream>> iterator() {
      return new PathIterator(this.directoryParent, this.pathStream.iterator());
    }
    
  }

  private static final class PathIterator implements Iterator<Entry<String, InputStream>> {

    private final Path directoryParent;
    
    private final Iterator<? extends Path> pathIterator;

    private Entry<String, InputStream> currentEntry;
    
    private PathIterator(final Path directoryParent, final Iterator<? extends Path> pathIterator) {
      super();
      Objects.requireNonNull(directoryParent);
      Objects.requireNonNull(pathIterator);
      if (!Files.isDirectory(directoryParent)) {
        throw new IllegalArgumentException("!Files.isDirectory(directoryParent): " + directoryParent);
      }
      this.directoryParent = directoryParent;
      this.pathIterator = pathIterator;
    }

    @Override
    public final boolean hasNext() {
      if (this.currentEntry != null) {
        final InputStream oldStream = this.currentEntry.getValue();
        if (oldStream != null) {
          try {
            oldStream.close();
          } catch (final IOException ignore) {

          }
        }
        this.currentEntry = null;
      }      
      return this.pathIterator != null && this.pathIterator.hasNext();
    }

    @Override
    public final Entry<String, InputStream> next() {
      final Path originalFile = this.pathIterator.next();
      assert originalFile != null;
      assert !Files.isDirectory(originalFile);
      final Path relativeFile = this.directoryParent.relativize(originalFile);
      assert relativeFile != null;
      final String relativePathString = relativeFile.toString().replace('\\', '/');
      assert relativePathString != null;
      try {
        this.currentEntry = new SimpleImmutableEntry<>(relativePathString, new BufferedInputStream(Files.newInputStream(originalFile)));
      } catch (final IOException wrapMe) {
        throw (NoSuchElementException)new NoSuchElementException(wrapMe.getMessage()).initCause(wrapMe);
      }
      return this.currentEntry;
    }
    
  }
  
}
