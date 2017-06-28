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

public class TapeArchiveChartLoader implements ChartLoader<TarInputStream> {

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
            config = createConfig(stream);
            break;
          case "Chart.yaml":
            metadata = createMetadata(stream);
            break;
          default:
            if (relativeChartFile.startsWith("templates/")) {
              templates.add(createTemplate(stream, relativeChartFile));
            } else if (relativeChartFile.startsWith("charts/")) {
              final Chart subchart = this.load(stream, entry, new StringBuilder(chartName).append("/charts/").length()); // recursive
              if (subchart != null) {
                subcharts.add(subchart);
              }
            } else {
              files.add(createAny(stream, relativeChartFile));
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

  private static final Template createTemplate(final InputStream stream, final String name) throws IOException {
    Template returnValue = null;
    if (stream != null && name != null) {
      final Template.Builder builder = Template.newBuilder();
      builder.setName(name);
      final ByteString data = ByteString.readFrom(stream);
      assert data != null;
      assert data.isValidUtf8();
      builder.setData(data);
      returnValue = builder.build();
    }
    return returnValue;
  }
  
  private static final Metadata createMetadata(final InputStream stream) throws IOException {
    Metadata returnValue = null;
    if (stream != null) {
      final Map<?, ?> map = new Yaml().loadAs(stream, Map.class);
      assert map != null;
      final Metadata.Builder builder = Metadata.newBuilder();
      assert builder != null;
      @SuppressWarnings("unchecked")
      final Iterable<String> keywords = (Iterable<String>)map.get("keywords");
      if (keywords != null) {
        builder.addAllKeywords(keywords);
      }
      @SuppressWarnings("unchecked")
      final Iterable<? extends Map<?, ?>> maintainers = (Iterable<? extends Map<?, ?>>)map.get("maintainers");
      if (maintainers != null) {
        for (final Map<?, ?> maintainer : maintainers) {
          if (maintainer != null) {
            final Maintainer.Builder maintainerBuilder = Maintainer.newBuilder();
            assert maintainerBuilder != null;
            maintainerBuilder.setName((String)maintainer.get("name"));
            maintainerBuilder.setEmail((String)maintainer.get("email"));
            builder.addMaintainers(maintainerBuilder);
          }
        }
      }
      @SuppressWarnings("unchecked")
      final Iterable<String> sources = (Iterable<String>)map.get("sources");
      if (sources != null) {
        builder.addAllSources(sources);
      }
      final String name = (String)map.get("name");
      if (name != null) {
        builder.setName(name);
      }
      final String version = (String)map.get("version");
      if (version != null) {
        builder.setVersion(version);
      }
      final String description = (String)map.get("description");
      if (description != null) {
        builder.setDescription(description);
      }
      final String engine = (String)map.get("engine");
      if (engine != null) {
        builder.setEngine(engine);
      }
      final String icon = (String)map.get("icon");
      if (icon != null) {
        builder.setIcon(icon);
      }
      final String appVersion = (String)map.get("appVersion");
      if (appVersion != null) {
        builder.setAppVersion(appVersion);
      }
      final String tillerVersion = (String)map.get("tillerVersion");
      if (tillerVersion != null) {
        builder.setTillerVersion(tillerVersion);
      }
      builder.setDeprecated("true".equals(String.valueOf(map.get("deprecated"))));
      returnValue = builder.build();
    }
    return returnValue;
  }
  
  private static final Config createConfig(final InputStream stream) throws IOException {
    Config returnValue = null;
    if (stream != null) {
      final Config.Builder builder = Config.newBuilder();
      assert builder != null;
      final ByteString rawBytes = ByteString.readFrom(stream);
      assert rawBytes != null;
      builder.setRawBytes(rawBytes);
      returnValue = builder.build();
    }
    return returnValue;
  }
  
  private static final Any createAny(final InputStream stream, final String name) throws IOException {
    Any returnValue = null;
    if (stream != null && name != null) {
      final Any.Builder builder = Any.newBuilder();
      assert builder != null;
      builder.setTypeUrl(name);
      final ByteString fileContents = ByteString.readFrom(stream);
      assert fileContents != null;
      assert fileContents.isValidUtf8();
      builder.setValue(fileContents);
      returnValue = builder.build();
    }
    return returnValue;
  }
  
}
