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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.nio.charset.StandardCharsets;

import java.util.Collection;
import java.util.Objects;

import com.google.protobuf.AnyOrBuilder;
import com.google.protobuf.ByteString;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ChartOuterClass.ChartOrBuilder;
import hapi.chart.ConfigOuterClass.ConfigOrBuilder;
import hapi.chart.MetadataOuterClass.Metadata;
import hapi.chart.MetadataOuterClass.MetadataOrBuilder;
import hapi.chart.TemplateOuterClass.Template;
import hapi.chart.TemplateOuterClass.TemplateOrBuilder;

import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;

import org.yaml.snakeyaml.Yaml;

/**
 * An {@link AbstractArchiveChartWriter} that saves {@link
 * ChartOrBuilder} objects to a {@linkplain
 * #TapeArchiveChartWriter(OutputStream) supplied
 * <code>OutputStream</code>} in <a
 * href="https://www.gnu.org/software/tar/manual/html_node/Standard.html">TAR
 * format</a>, using a {@link TarOutputStream} internally.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
public class TapeArchiveChartWriter extends AbstractArchiveChartWriter {


  /*
   * Instance fields.
   */


  /**
   * The {@link TarOutputStream} to write to.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #TapeArchiveChartWriter(OutputStream)
   */
  private final TarOutputStream outputStream;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link TapeArchiveChartWriter}.
   *
   * @param outputStream the {@link OutputStream} to write to; must
   * not be {@code null} and should be buffered at some level
   *
   * @see
   * AbstractArchiveChartWriter#AbstractArchiveChartWriter()
   *
   * @see TarOutputStream#TarOutputStream(OutputStream)
   */
  public TapeArchiveChartWriter(final OutputStream outputStream) {
    super();
    Objects.requireNonNull(outputStream);
    this.outputStream = new TarOutputStream(outputStream);
  }


  /*
   * Instance methods.
   */
  

  /**
   * Creates a new {@link TarHeader} and a {@link TarEntry} wrapping
   * it and writes it and the supplied {@code contents} to the
   * underlying {@link TarOutputStream}.
   *
   * @param context the {@link Context} describing the write operation
   * in effect; must not be {@code null}
   *
   * @param path the path within a tape archive to write; interpreted
   * as being relative to the current chart path; must not be {@code
   * null} or {@linkplain String#isEmpty() empty}
   *
   * @param contents the contents to write; must not be {@code null}
   *
   * @exception IOException if a write error occurs
   *
   * @exception NullPointerException if {@code context}, {@code path}
   * or {@code contents} is {@code null}
   *
   * @exception IllegalArgumentException if {@code path} {@linkplain
   * String#isEmpty() is empty}
   */
  @Override
  protected void writeEntry(final Context context, final String path, final String contents) throws IOException {
    Objects.requireNonNull(context);
    Objects.requireNonNull(path);
    Objects.requireNonNull(contents);
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path", new IllegalStateException("path.isEmpty()"));
    }

    final byte[] contentsBytes = contents.getBytes(StandardCharsets.UTF_8);
    final long size = contentsBytes.length;
    final TarHeader tarHeader =
      TarHeader.createHeader(new StringBuilder(context.get("path", String.class)).append(path).toString(),
                             size,
                             System.currentTimeMillis() / 1000L, // see https://github.com/microbean/microbean-helm/issues/200
                             false, // false == not a directory
                             0755);
    final TarEntry tarEntry = new TarEntry(tarHeader);
    this.outputStream.putNextEntry(tarEntry);
    this.outputStream.write(contentsBytes);
    this.outputStream.flush();
  }

  /**
   * Closes this {@link TapeArchiveChartWriter} by closing its
   * underlying {@link TarOutputStream}.  This {@link
   * TapeArchiveChartWriter} cannot be used again.
   *
   * @exception IOException if there was a problem closing the
   * underlying {@link TarOutputStream}
   */
  @Override
  public void close() throws IOException {
    this.outputStream.close();
  }
  
}
