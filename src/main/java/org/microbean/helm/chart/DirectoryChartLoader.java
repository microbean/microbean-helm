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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.net.URI;
import java.net.URL;

import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;

import java.nio.file.attribute.BasicFileAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.zip.GZIPInputStream;

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

public class DirectoryChartLoader extends StreamOrientedChartLoader<Path> {

  @Override
  public Chart load(final Path path) throws IOException {
    Chart returnValue = null;
    if (path != null) {
      if (Files.isDirectory(path)) {
        returnValue = loadDirectory(path);
      } else {
        final URI pathUri = path.toUri();
        assert pathUri != null;
        final URL pathUrl = pathUri.toURL();
        assert pathUrl != null;
        try (final TarInputStream stream = new TarInputStream(new BufferedInputStream(new GZIPInputStream(pathUrl.openStream())))) {
          returnValue = new TapeArchiveChartLoader().load(stream);
        }
      }
    }
    return returnValue;
  }

  private Chart loadDirectory(final Path path) throws IOException {
    Chart returnValue = null;
    if (path != null) {
      if (Files.isDirectory(path)) {
        final Chart[] chartHolder = new Chart[1];
        Files.walkFileTree(path, new FileVisitor<Path>() {
            
            private Path startPath;

            private PathMatcher helmIgnorePathMatcher;
            
            private final List<Template> templates = new ArrayList<>();

            private final List<Chart> subcharts = new ArrayList<>();

            final List<Any> files = new ArrayList<>();

            private Config config;

            private Metadata metadata;

            private ChartDirectoryType chartDirectoryType = ChartDirectoryType.NORMAL;

            @Override
            public FileVisitResult preVisitDirectory(final Path directory, final BasicFileAttributes attributes) throws IOException {
              Objects.requireNonNull(directory);
              Objects.requireNonNull(attributes);
              assert attributes.isDirectory();
              FileVisitResult returnValue = FileVisitResult.CONTINUE;
              if (this.startPath == null) {
                assert !isTemplateDirectory(directory);
                assert !isSubchartDirectory(directory);
                assert ChartDirectoryType.NORMAL.equals(this.chartDirectoryType);
                this.startPath = directory;
                final Path helmIgnorePath = directory.resolve(".helmignore");
                assert helmIgnorePath != null;
                if (Files.isRegularFile(helmIgnorePath)) {
                  this.helmIgnorePathMatcher = new HelmIgnorePathMatcher(helmIgnorePath);
                } else {
                  this.helmIgnorePathMatcher = null;
                }
              } else if (this.ignore(directory)) {
                returnValue = FileVisitResult.SKIP_SUBTREE;
              } else if (isTemplateDirectory(directory)) {
                // Entering "templates/".
                this.chartDirectoryType = ChartDirectoryType.TEMPLATE;
              } else if (isSubchartDirectory(directory)) {
                // Entering "charts/".
                this.chartDirectoryType = ChartDirectoryType.SUBCHART;
              } else if (ChartDirectoryType.TEMPLATE.equals(this.chartDirectoryType)) {
                // Entering a subdirectory of "templates/".  Carry on.
              } else if (ChartDirectoryType.SUBCHART.equals(this.chartDirectoryType)) {
                // Entering a subdirectory of "charts/".  Recursively
                // load it and skip its entry.
                this.subcharts.add(loadDirectory(directory));
                returnValue = FileVisitResult.SKIP_SUBTREE;
              } else {
                // Entering a directory that is part of neither the
                // "templates/" nor the "charts/" tree.  By definition
                // it's normal.
                this.chartDirectoryType = ChartDirectoryType.NORMAL;
              }
              return returnValue;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path directory, final IOException exception) throws IOException {
              if (exception != null) {
                throw exception;
              }
              Objects.requireNonNull(directory);
              this.chartDirectoryType = ChartDirectoryType.NORMAL;
              if (directory != null && directory.equals(this.startPath)) {
                assert chartHolder[0] == null;
                final Chart.Builder chartBuilder = Chart.newBuilder();
                assert chartBuilder != null;
                if (this.metadata != null) {
                  chartBuilder.setMetadata(this.metadata);
                }
                if (this.config != null) {
                  chartBuilder.setValues(this.config);
                }
                chartBuilder.addAllTemplates(this.templates);
                chartBuilder.addAllFiles(this.files);
                chartBuilder.addAllDependencies(this.subcharts);
                chartHolder[0] = chartBuilder.build();
              }
              return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) throws IOException {
              Objects.requireNonNull(file);
              Objects.requireNonNull(attributes);
              assert !attributes.isDirectory();
              if (!this.ignore(file)) {

                if (ChartDirectoryType.SUBCHART.equals(this.chartDirectoryType)) {
                  // When we enter the charts/ directory, we do not
                  // descend into it, but rather call ourselves
                  // recursively (see above).  So we should never
                  // encounter a file *inside* the charts/ directory
                  // *as a subchart*.
                  throw new IllegalStateException();
                }
                
                assert this.startPath != null;

                final Path relativePath = this.startPath.relativize(file);
                assert relativePath != null;

                try (final InputStream stream = new BufferedInputStream(file.toUri().toURL().openStream())) {
                  if (ChartDirectoryType.TEMPLATE.equals(this.chartDirectoryType)) {
                    this.templates.add(createTemplate(stream, relativePath.toString()));
                  } else if (isValuesFile(file)) {
                    if (this.config == null) {
                      this.config = createConfig(stream);
                    }
                  } else if (isChartFile(file)) {
                    if (this.metadata == null) {
                      this.metadata = createMetadata(stream);
                    }
                  } else {
                    this.files.add(createAny(stream, relativePath.toString()));
                  }
                }
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException exception) throws IOException {
              if (exception != null) {
                throw exception;
              }
              return FileVisitResult.CONTINUE;
            }

            private final boolean ignore(final Path path) {
              return
                DirectoryChartLoader.this.ignore(path) ||
                this.helmIgnorePathMatcher == null ? false : this.helmIgnorePathMatcher.matches(path);
            }

          });
        returnValue = chartHolder[0];        
      }
    }
    return returnValue;
  }

  protected boolean isChartFile(final Path path) {
    return path != null && path.endsWith("Chart.yaml");
  }
  
  protected boolean isValuesFile(final Path path) {
    return path != null && path.endsWith("values.yaml");
  }

  protected boolean isSubchartDirectory(final Path path) {
    return path != null && path.endsWith("charts");
  }

  protected boolean isTemplateDirectory(final Path path) {
    return path != null && path.endsWith("templates");
  }
  
  protected boolean ignore(final Path file) {
    return false;
  }

  private static enum ChartDirectoryType {
    NORMAL, SUBCHART, TEMPLATE
  }
            

  
}
