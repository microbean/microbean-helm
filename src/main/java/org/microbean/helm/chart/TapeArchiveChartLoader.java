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
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ConfigOuterClass.Config;
import hapi.chart.MetadataOuterClass.Maintainer;
import hapi.chart.MetadataOuterClass.Metadata;
import hapi.chart.TemplateOuterClass.Template;

import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarInputStream;

import org.yaml.snakeyaml.Yaml;

public class TapeArchiveChartLoader extends StreamOrientedChartLoader<TarInputStream> {

  public TapeArchiveChartLoader() {
    super();
  }
  
  @Override
  public Chart load(final TarInputStream stream) throws IOException {
    return this.load(stream, null, 0);
  }

  Chart load(final TarInputStream stream, TarEntry entry, final int offset) throws IOException {
    Chart returnValue = null;
    if (stream != null) {

      final List<Template> templates = new ArrayList<>();
      final List<Chart> subcharts = new ArrayList<>();
      Config config = null;
      Metadata metadata = null;
      final List<Any> files = new ArrayList<>();

      if (entry == null) {
        entry = stream.getNextEntry();
      }
      while (entry != null) {
        if (!entry.isDirectory()) {
          String entryFullName = entry.getName();
          if (entryFullName == null || entryFullName.isEmpty() || entryFullName.startsWith("/")) {
            throw new IllegalArgumentException("bad chart entry: " + entryFullName);
          }
          if (offset > 0) {
            entryFullName = entryFullName.substring(offset);
          }
          if (entryFullName == null || entryFullName.isEmpty() || entryFullName.startsWith("/")) {
            throw new IllegalArgumentException("bad chart entry: " + entryFullName);
          }
          int firstSlashIndex = entryFullName.indexOf('/');
          if (firstSlashIndex <= 0 || firstSlashIndex + 1 == entryFullName.length()) {
            throw new IllegalArgumentException("bad chart entry: " + entryFullName);
          }
          final String chartName = entryFullName.substring(0, firstSlashIndex);
          assert chartName != null;
          assert !chartName.isEmpty();
          final String relativeChartFile = entryFullName.substring(firstSlashIndex + 1);
          assert relativeChartFile != null;
          assert !relativeChartFile.isEmpty();
          switch (relativeChartFile) {
          case "values.yaml":
            config = this.createConfig(stream);
            break;
          case "Chart.yaml":
            metadata = this.createMetadata(stream);
            break;
          default:
            if (relativeChartFile.startsWith("templates/")) {
              if (relativeChartFile.length() > 10) { // 10 == "templates/".length()
                templates.add(this.createTemplate(stream, relativeChartFile));
              }
            } else if (relativeChartFile.startsWith("charts/")) {
              if (relativeChartFile.length() > 7) { // 7 == "charts/".length()
                final char c = relativeChartFile.charAt(7);
                if (c != '.' && c != '_') {
                  final Chart subchart = this.load(stream, entry, new StringBuilder(chartName).append("/charts/").length()); // recursive
                  if (subchart != null) {
                    subcharts.add(subchart);
                  }
                }
              }
            } else {
              files.add(this.createAny(stream, relativeChartFile));
            }
            break;
          }
        }
        entry = stream.getNextEntry();
      }

      final Chart.Builder builder = Chart.newBuilder();
      assert builder != null;
      builder.setMetadata(metadata);
      builder.setValues(config);
      builder.addAllTemplates(templates);
      builder.addAllFiles(files);
      builder.addAllDependencies(subcharts);

      returnValue = builder.build();
    }
    return returnValue;
  }

}
