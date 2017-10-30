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

/**
 * A utility class for working with {@link Metadata} instances.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see hapi.chart.MetadataOuterClass.Metadata.Builder
 */
public final class Metadatas {


  /*
   * Constructors.
   */

  
  /**
   * Creates a new {@link Metadatas}.
   *
   * @deprecated This constructor originally did not exist explicitly
   * in the source code.  That means a default no-argument {@code
   * public} constructor was available to end users.  Consequently,
   * this constructor cannot now be made {@code private} without
   * breaking API compatibility.  This constructor is slated for
   * removal.
   */
  @Deprecated
  public Metadatas() {
    super();
  }
  

  /*
   * Static methods.
   */


  /**
   * Given a {@link Map} assumed to represent a YAML document, calls
   * certain mutating methods on the supplied {@link
   * hapi.chart.MetadataOuterClass.Metadata.Builder}.
   *
   * @param metadataBuilder the {@link
   * hapi.chart.MetadataOuterClass.Metadata.Builder} to populate; may
   * be {@code null} in which case no action will be taken
   *
   * @param yamlMap a {@link Map} representing a YAML representation
   * of a {@link Metadata}; may be {@code null} or {@linkplain
   * Map#isEmpty() empty} in which case no action will be taken; if
   * non-{@code null} expected to contain zero or more keys drawn from
   * the following set: {@code annotations}, {@code apiVersion},
   * {@code appVersion}, {@code condition}, {@code deprecated}, {@code
   * description}, {@code engine}, {@code home}, {@code icon}, {@code
   * keywords}, {@code maintainers}, {@code name}, {@code sources},
   * {@code tags}, {@code tillerVersion}, {@code version}
   *
   * @exception ClassCastException if the supplied {@code yamlMap}
   * does not actually represent a YAML representation of a {@link
   * Metadata}
   */
  public static final void populateMetadataBuilder(Metadata.Builder metadataBuilder, final Map<?, ?> yamlMap) {
    if (metadataBuilder != null && yamlMap != null && !yamlMap.isEmpty()) {

      @SuppressWarnings("unchecked")
      final Map<String, String> annotationsMap = (Map<String, String>)yamlMap.get("annotations");
      if (annotationsMap != null) {
        metadataBuilder.putAllAnnotations(annotationsMap);
      }
      
      final String apiVersion = (String)yamlMap.get("apiVersion");
      if (apiVersion != null) {
        metadataBuilder.setApiVersion(apiVersion);
      }
      
      final String appVersion = (String)yamlMap.get("appVersion");
      if (appVersion != null) {
        metadataBuilder.setAppVersion(appVersion);
      }

      final String condition = (String)yamlMap.get("condition");
      if (condition != null) {
        metadataBuilder.setCondition(condition);
      }
      
      metadataBuilder.setDeprecated("true".equals(String.valueOf(yamlMap.get("deprecated"))));
      
      final String description = (String)yamlMap.get("description");
      if (description != null) {
        metadataBuilder.setDescription(description);
      }
      
      final String engine = (String)yamlMap.get("engine");
      if (engine != null) {
        metadataBuilder.setEngine(engine);
      }

      final String home = (String)yamlMap.get("home");
      if (home != null) {
        metadataBuilder.setHome(home);
      }
      
      final String icon = (String)yamlMap.get("icon");
      if (icon != null) {
        metadataBuilder.setIcon(icon);
      }
      
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

      final String name = (String)yamlMap.get("name");
      if (name != null) {
        metadataBuilder.setName(name);
      }
      
      @SuppressWarnings("unchecked")
      final Iterable<String> sources = (Iterable<String>)yamlMap.get("sources");
      if (sources != null) {
        metadataBuilder.addAllSources(sources);
      }

      final String tags = (String)yamlMap.get("tags");
      if (tags != null) {
        metadataBuilder.setTags(tags);
      }

      final String tillerVersion = (String)yamlMap.get("tillerVersion");
      if (tillerVersion != null) {
        metadataBuilder.setTillerVersion(tillerVersion);
      }
      
      final String version = (String)yamlMap.get("version");
      if (version != null) {
        metadataBuilder.setVersion(version);
      }

    }
  }
  
}
