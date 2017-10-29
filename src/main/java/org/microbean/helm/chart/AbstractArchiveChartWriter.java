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

import java.io.IOException;

import java.util.Objects;

import com.google.protobuf.AnyOrBuilder;
import com.google.protobuf.ByteString;

import hapi.chart.ChartOuterClass.ChartOrBuilder;
import hapi.chart.ConfigOuterClass.ConfigOrBuilder;
import hapi.chart.MetadataOuterClass.MetadataOrBuilder;
import hapi.chart.TemplateOuterClass.TemplateOrBuilder;

/**
 * A partial {@link AbstractChartWriter} whose implementations save
 * {@link ChartOrBuilder} objects to a destination that can
 * be considered an archive of some sort.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
public abstract class AbstractArchiveChartWriter extends AbstractChartWriter {


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link AbstractArchiveChartWriter}.
   */
  protected AbstractArchiveChartWriter() {
    super();
  }


  /*
   * Instance methods.
   */


  /**
   * {@inheritDoc}
   *
   * <p>The {@link AbstractArchiveChartWriter} implementation stores a
   * {@link String} representing the required path layout under a
   * "{@code path}" key in the supplied {@link Context}.</p>
   */
  @Override
  protected void beginWrite(final Context context, final ChartOrBuilder parent, final ChartOrBuilder chartBuilder) throws IOException {
    Objects.requireNonNull(context);
    Objects.requireNonNull(chartBuilder);
    if (parent == chartBuilder) {
      throw new IllegalArgumentException("parent == chartBuilder");
    }
    final MetadataOrBuilder metadata = chartBuilder.getMetadataOrBuilder();
    if (metadata == null) {
      throw new IllegalArgumentException("chartBuilder", new IllegalStateException("chartBuilder.getMetadata() == null"));
    }
    final String chartName = metadata.getName();
    if (chartName == null) {
      throw new IllegalArgumentException("chartBuilder", new IllegalStateException("chartBuilder.getMetadata().getName() == null"));
    }
    
    if (parent == null) {
      context.put("path", new StringBuilder(chartName).append("/").toString());
    } else {
      final MetadataOrBuilder parentMetadata = parent.getMetadataOrBuilder();
      if (parentMetadata == null) {
        throw new IllegalArgumentException("parent", new IllegalStateException("parent.getMetadata() == null"));
      }
      final String parentChartName = parentMetadata.getName();
      if (parentChartName == null) {
        throw new IllegalArgumentException("parent", new IllegalStateException("parent.getMetadata().getName() == null"));
      }      
      context.put("path", new StringBuilder(context.get("path", String.class)).append("charts/").append(chartName).append("/").toString());
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>The {@link AbstractArchiveChartWriter} implementation writes
   * the {@linkplain #toYAML(Context, Object) YAML representation} of
   * the supplied {@link MetadataOrBuilder} to an appropriate archive
   * entry named {@code Chart.yaml} within the current chart path.</p>
   *
   * @exception NullPointerException if either {@code context} or
   * {@code metadata} is {@code null}
   */
  @Override
  protected void writeMetadata(final Context context, final MetadataOrBuilder metadata) throws IOException {
    Objects.requireNonNull(context);
    Objects.requireNonNull(metadata);

    final String yaml = this.toYAML(context, metadata);
    this.writeEntry(context, "Chart.yaml", yaml);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation writes the {@linkplain #toYAML(Context,
   * Object) YAML representation} of the supplied {@link
   * ConfigOrBuilder} to an appropriate archive entry named {@code
   * values.yaml} within the current chart path.</p>
   *
   * @exception NullPointerException if {@code context} is {@code
   * null}
   */
  @Override
  protected void writeConfig(final Context context, final ConfigOrBuilder config) throws IOException {
    Objects.requireNonNull(context);
    
    if (config != null) {
      final String raw = config.getRaw();
      final String yaml;
      if (raw == null || raw.isEmpty()) {
        yaml = "";
      } else {
        yaml = this.toYAML(context, raw);
      }
      this.writeEntry(context, "values.yaml", yaml);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation writes the {@linkplain
   * TemplateOrBuilder#getData() data} of the supplied {@link
   * TemplateOrBuilder} to an appropriate archive entry named in part
   * by the return value of the {@link TemplateOrBuilder#getName()}
   * method within the current chart path.</p>
   *
   * @exception NullPointerException if {@code context} is {@code
   * null}
   */
  @Override
  protected void writeTemplate(final Context context, final TemplateOrBuilder template) throws IOException {
    Objects.requireNonNull(context);
    
    if (template != null) {
      final String templateName = template.getName();
      if (templateName != null && !templateName.isEmpty()) {
        final ByteString data = template.getData();
        if (data != null && data.size() > 0) {
          final String dataString = data.toStringUtf8();
          assert dataString != null;
          assert !dataString.isEmpty();
          this.writeEntry(context, templateName, dataString);
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation writes the {@linkplain
   * AnyOrBuilder#getValue() contents} of the supplied {@link
   * AnyOrBuilder} to an appropriate archive entry named in part by
   * the return value of the {@link AnyOrBuilder#getTypeUrl()} method
   * within the current chart path.</p>
   *
   * @exception NullPointerException if {@code context} is {@code
   * null}
   */
  @Override
  protected void writeFile(final Context context, final AnyOrBuilder file) throws IOException {
    Objects.requireNonNull(context);
    
    if (file != null) {
      final String fileName = file.getTypeUrl();
      if (fileName != null && !fileName.isEmpty()) {
        final ByteString data = file.getValue();
        if (data != null && data.size() > 0) {
          final String dataString = data.toStringUtf8();
          assert dataString != null;
          assert !dataString.isEmpty();
          this.writeEntry(context, fileName, dataString);
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation ensures that the current chart path,
   * residing under the "{@code path}" key in the supplied {@link
   * AbstractChartWriter.Context}, is reset properly.</p>
   *
   * @exception NullPointerException if either {@code context} or
   * {@code chartBuilder} is {@code null}
   */
  @Override
  protected void endWrite(final Context context, final ChartOrBuilder parent, final ChartOrBuilder chartBuilder) throws IOException {
    Objects.requireNonNull(context);
    Objects.requireNonNull(chartBuilder);
    if (chartBuilder == parent) {
      throw new IllegalArgumentException("chartBuilder == parent");
    }
    
    if (parent == null) {
      context.remove("path");
    } else {
      final String path = context.get("path", String.class);
      assert path != null;
      final int chartsIndex = path.lastIndexOf("/charts/");
      assert chartsIndex > 0;
      context.put("path", path.substring(0, chartsIndex + 1));
    }
  }

  /**
   * Writes the supplied {@code contents} to an appropriate archive
   * entry that is expected to be suffixed with the supplied {@code
   * path} in the context of the write operation described by the
   * supplied {@link Context}.
   *
   * @param context the {@link Context} describing the write operation
   * in effect; must not be {@code null}
   *
   * @param path the path within an abstract archive to write;
   * interpreted as being relative to the current notional chart path,
   * whatever that might be; must not be {@code null} or {@linkplain
   * String#isEmpty() empty}
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
  protected abstract void writeEntry(final Context context, final String path, final String contents) throws IOException;

}
