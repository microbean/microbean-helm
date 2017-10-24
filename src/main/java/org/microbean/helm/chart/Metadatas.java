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

import java.util.Map;

import hapi.chart.MetadataOuterClass.Maintainer;
import hapi.chart.MetadataOuterClass.Metadata;

public final class Metadatas {

  public static final void populateMetadataBuilder(Metadata.Builder metadataBuilder, final Map<?, ?> yamlMap) {
    if (metadataBuilder != null && yamlMap != null && !yamlMap.isEmpty()) {
      @SuppressWarnings("unchecked")
      final Iterable<String> keywords = (Iterable<String>)yamlMap.get("keywords");
      if (keywords != null) {
        metadataBuilder.addAllKeywords(keywords);
      }
      @SuppressWarnings("unchecked")
      final Iterable<? extends Map<?, ?>> maintainers = (Iterable<? extends Map<?, ?>>)yamlMap.get("maintainers");
      if (maintainers != null) {
        for (final Map<?, ?> maintainer : maintainers) {
          if (maintainer != null) {
            final Maintainer.Builder maintainerBuilder = metadataBuilder.addMaintainersBuilder();
            assert maintainerBuilder != null;
            final String maintainerName = (String)maintainer.get("name");
            if (maintainerName != null) {
              maintainerBuilder.setName(maintainerName);
            }
            final String maintainerEmail = (String)maintainer.get("email");
            if (maintainerEmail != null) {
              maintainerBuilder.setEmail(maintainerEmail);
            }
          }
        }
      }
      @SuppressWarnings("unchecked")
      final Iterable<String> sources = (Iterable<String>)yamlMap.get("sources");
      if (sources != null) {
        metadataBuilder.addAllSources(sources);
      }
      final String name = (String)yamlMap.get("name");
      if (name != null) {
        metadataBuilder.setName(name);
      }
      final String version = (String)yamlMap.get("version");
      if (version != null) {
        metadataBuilder.setVersion(version);
      }
      final String description = (String)yamlMap.get("description");
      if (description != null) {
        metadataBuilder.setDescription(description);
      }
      final String engine = (String)yamlMap.get("engine");
      if (engine != null) {
        metadataBuilder.setEngine(engine);
      }
      final String icon = (String)yamlMap.get("icon");
      if (icon != null) {
        metadataBuilder.setIcon(icon);
      }
      final String appVersion = (String)yamlMap.get("appVersion");
      if (appVersion != null) {
        metadataBuilder.setAppVersion(appVersion);
      }
      final String tillerVersion = (String)yamlMap.get("tillerVersion");
      if (tillerVersion != null) {
        metadataBuilder.setTillerVersion(tillerVersion);
      }
      metadataBuilder.setDeprecated("true".equals(String.valueOf(yamlMap.get("deprecated"))));
    }
  }
  
}
