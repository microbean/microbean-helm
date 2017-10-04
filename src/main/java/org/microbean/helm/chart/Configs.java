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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ChartOuterClass.ChartOrBuilder;
import hapi.chart.ConfigOuterClass.Config;
import hapi.chart.ConfigOuterClass.ConfigOrBuilder;
import hapi.chart.MetadataOuterClass.Metadata;

import hapi.services.tiller.Tiller.InstallReleaseRequest; // for javadoc only

import org.yaml.snakeyaml.Yaml;

/**
 * Replicates the intended behavior of the <a
 * href="https://godoc.org/k8s.io/helm/pkg/chartutil#CoalesceValues">{@code
 * CoalesceValues}</a> function found in Helm's {@code chartutil}
 * package.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see <a
 * href="https://godoc.org/k8s.io/helm/pkg/chartutil#CoalesceValues">{@code
 * CoalesceValues}</a>
 */
final class Configs {


  /*
   * Constructors.
   */

  
  /**
   * Creates a new {@link Configs}.
   */
  private Configs() {
    super();
  }


  /*
   * Static methods.
   */
  
 /**
   * Given a {@link ChartOrBuilder}, flattens its
   * {@linkplain ChartOrBuilder#getValues() default values} into
   * a {@link Map}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method calls the {@link #toValuesMap(ChartOrBuilder,
   * ConfigOrBuilder)} with {@code chart} as its first argument and
   * {@code null} as its second argument and returns its return
   * value.</p>
   *
   * <p>The {@link Map} returned by this method may have nested {@link
   * Map Map&lt;String, Object&gt;}s as its values.  It is, in other
   * words, a {@link Map} representation of YAML.</p>
   *
   * @param chart the {@link ChartOrBuilder} whose {@linkplain
   * ChartOrBuilder#getValues() values} will be taken into
   * consideration; may be {@code null}
   *
   * @return a {@link Map} of values that, for example, can be
   * {@linkplain Yaml#dump(Object) marshalled to YAML} and passed to,
   * for example, {@link
   * InstallReleaseRequest.Builder#setValues(Config)}; never {@code
   * null}
   *
   * @see #toValuesMap(ChartOrBuilder, ConfigOrBuilder)
   */
  static final Map<String, Object> toDefaultValuesMap(final ChartOrBuilder chart) {
    return toValuesMap(chart, null);
  }
  
  /**
   * Given an optional set of overriding values in {@link
   * ConfigOrBuilder} form, and a {@link ChartOrBuilder} whose
   * {@linkplain ChartOrBuilder#getValues() default values} are being
   * overridden, flattens both {@link ConfigOrBuilder} instances into
   * a {@link Map}, such that the overriding values are dominant and
   * the {@link ChartOrBuilder}'s {@linkplain
   * ChartOrBuilder#getValues() values} are recessive.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>The {@link Map} returned by this method may have nested {@link
   * Map Map&lt;String, Object&gt;}s as its values.  It is, in other
   * words, a {@link Map} representation of YAML.</p>
   *
   * @param chart the {@link ChartOrBuilder} whose {@linkplain
   * ChartOrBuilder#getValues() values} will be taken into
   * consideration; may be {@code null}
   *
   * @param config the overriding values; may be {@code null}
   *
   * @return a {@link Map} of values that, for example, can be
   * {@linkplain Yaml#dump(Object) marshalled to YAML} and passed to,
   * for example, {@link
   * InstallReleaseRequest.Builder#setValues(Config)}; never {@code
   * null}
   */
  static final Map<String, Object> toValuesMap(final ChartOrBuilder chart, final ConfigOrBuilder config) {
    final Map<String, Object> map;
    if (config == null) {
      map = null;
    } else {
      final String raw = config.getRaw();
      if (raw == null || raw.isEmpty()) {
        map = null;
      } else {
        @SuppressWarnings("unchecked")
        final Map<String, Object> configAsMap = (Map<String, Object>)new Yaml().load(raw);
        assert configAsMap != null;
        map = coalesce(chart, configAsMap);
      }
    }
    final Map<String, Object> returnValue = coalesceDependencies(chart, map);
    return returnValue;
  }

  static final Config toConfig(final String yaml) {
    final Config returnValue;
    if (yaml == null || yaml.isEmpty()) {
      returnValue = null;
    } else {
      final Config.Builder builder = Config.newBuilder();
      assert builder != null;
      builder.setRaw(yaml);
      returnValue = builder.build();
    }
    return returnValue;
  }

  static final String toYAML(final Config config) {
    final String returnValue;
    if (config == null) {
      returnValue = "";
    } else {
      final String rawValue = config.getRaw();
      if (rawValue == null) {
        returnValue = "";
      } else {
        returnValue = rawValue;
      }
    }
    return returnValue;
  }
  
  static final String toYAML(final Map<String, Object> map) {
    final String returnValue;
    if (map == null || map.isEmpty()) {
      returnValue = "";
    } else {
      returnValue = new Yaml().dump(map);
    }
    return returnValue;
  }


  /*
   * Private static methods.
   */

  

  /**
   * Gets the supplied {@link ChartOrBuilder}'s {@link
   * ChartOrBuilder#getValues() ConfigOrBuilder} representing its
   * default values, grabs its {@linkplain ConfigOrBuilder#getRaw()
   * YAML representation}, marshals it into a {@link Map} using the
   * {@link Yaml#load(String)} method, and then passes that {@link
   * Map} as the first parameter&mdash;and the {@code targetMap} as
   * the second parameter&mdash;to the {@link #coalesceMaps(Map, Map)}
   * method and returns its result.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param chart the {@link ChartOrBuilder} whose default values
   * should be harvested; may be {@code null}
   *
   * @param targetMap the {@link Map} of typically user-supplied
   * values that will be (possibly) modified and returned (if
   * non-{@code null})
   *
   * @return {@code targetMap}, with possibly changed contents, if it
   * is non-{@code null}, or a new {@link Map}
   *
   * @see #coalesceMaps(Map, Map)
   *
   * @see ChartOrBuilder#getValues()
   *
   * @see ConfigOrBuilder#getRaw()
   *
   * @see Yaml#load(String)
   */
  private static final Map<String, Object> computeEffectiveValues(final ChartOrBuilder chart, Map<String, Object> targetMap /* v */) {
    if (targetMap == null) {
      targetMap = new HashMap<>();
    }
    if (chart != null) {
      final ConfigOrBuilder config = chart.getValues();
      if (config != null) {
        final String raw = config.getRaw();
        if (raw != null && !raw.isEmpty()) {
          @SuppressWarnings("unchecked")            
          final Map<String, Object> sourceMap = (Map<String, Object>)new Yaml().load(raw); // nv
          assert sourceMap != null;
          targetMap = coalesceMaps(sourceMap, targetMap);
        }
      }
    }
    return targetMap;
  }

  /**
   * First gets the "right" values to use by blending the supplied
   * {@link Map} of typically user-supplied values with the
   * {@linkplain ChartOrBuilder#getValues() default values present in
   * the supplied <code>ChartOrBuilder</code>}, and then calls {@link
   * #coalesceDependencies(ChartOrBuilder, Map)} on the results.
   *
   * <p>This method first calls {@link #computeEffectiveValues(ChartOrBuilder,
   * Map)}, producing a {@link Map} that combines user-specified and
   * default values, and then passes the supplied {@code chart} and
   * the values {@link Map} to the {@link
   * #coalesceDependencies(ChartOrBuilder, Map)} method and returns
   * its result.
   *
   * @param chart a {@link ChartOrBuilder}
   *
   * @param suppliedValues the {@link Map} that will ultimately be modified and returned
   *
   * @return {@code suppliedValues}
   *
   * @see #coalesceDependencies(ChartOrBuilder, Map)
   *
   * @see #computeEffectiveValues(ChartOrBuilder, Map)
   */
  private static final Map<String, Object> coalesce(final ChartOrBuilder chart, Map<String, Object> suppliedValues) {
    return coalesceDependencies(chart, computeEffectiveValues(chart, suppliedValues));
  }

  private static final Map<String, Object> coalesceDependencies(final ChartOrBuilder chart) {
    return coalesceDependencies(chart, computeEffectiveValues(chart, new HashMap<>()));
  }
  
  private static final Map<String, Object> coalesceDependencies(final ChartOrBuilder chart, Map<String, Object> returnValue) {
    if (chart != null) {
      returnValue = coalesceDependencies(chart.getDependenciesList(), returnValue);
    }
    return returnValue;
  }

  private static final Map<String, Object> coalesceDependencies(final Iterable<? extends ChartOrBuilder> subcharts, Map<String, Object> returnValue) {
    if (returnValue == null) {
      returnValue = new HashMap<>();
    }
    if (subcharts != null) {
      for (final ChartOrBuilder subchart : subcharts) {
        if (subchart != null) {
          final Metadata subchartMetadata = subchart.getMetadata();
          if (subchartMetadata != null) {
            final String subchartName = subchartMetadata.getName();
            if (subchartName != null) {
              
              final Map<String, Object> subchartValuesMap;
              final Object subchartValuesObject = returnValue.get(subchartName);
              if (subchartValuesObject == null) {
                subchartValuesMap = new HashMap<>();
                returnValue.put(subchartName, subchartValuesMap);
              } else if (subchartValuesObject instanceof Map) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> temp = (Map<String, Object>)subchartValuesObject;
                subchartValuesMap = temp;
              } else {
                throw new IllegalArgumentException("returnValue.get(" + subchartName + "): not a map: " + subchartValuesObject);
              }
              
              coalesceGlobals(subchartValuesMap, returnValue);
              returnValue.put(subchartName, coalesce(subchart, subchartValuesMap));
            }
          }
        }
      }
    }
    return returnValue;
  }

  private static final void coalesceGlobals(final Map<String, Object> dominantMap, final Map<?, ?> recessiveMap) {
    if (dominantMap != null) {

      // Get whatever is indexed under the "global" key in the
      // dominantMap.  We hope it's a Map.  If there's nothing there,
      // we'll stuff a new Map in under that key.  If whatever is
      // there is non-null but not a Map (like, say, an Integer or
      // something), we do nothing.
      final Object dominantGlobals = dominantMap.get("global");
      final Map<String, Object> dominantGlobalsMap;
      if (dominantGlobals == null) {
        dominantGlobalsMap = new HashMap<>();
        dominantMap.put("global", dominantGlobalsMap);
      } else if (dominantGlobals instanceof Map) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> temp = (Map<String, Object>)dominantGlobals;
        dominantGlobalsMap = temp;
      } else {
        dominantGlobalsMap = null;
      }
      
      if (dominantGlobalsMap != null) {

        // Get whatever is indexed under the "global" key in the
        // recessiveMap.  We hope it's a Map.  If it isn't or it's
        // empty, we do nothing.
        final Object recessiveGlobals = recessiveMap.get("global");
        final Map<? extends String, ?> recessiveGlobalsMap;
        if (recessiveGlobals instanceof Map) {
          @SuppressWarnings("unchecked")
          final Map<? extends String, ?> temp = (Map<? extends String, ?>)recessiveGlobals;
          recessiveGlobalsMap = temp;
        } else {
          recessiveGlobalsMap = null;
        }

        if (recessiveGlobalsMap != null && !recessiveGlobalsMap.isEmpty()) {
          final Set<? extends Entry<? extends String, ?>> recessiveEntrySet = recessiveGlobalsMap.entrySet();
          if (recessiveEntrySet != null && !recessiveEntrySet.isEmpty()) {
            for (final Entry<? extends String, ?> recessiveEntry : recessiveEntrySet) {
              if (recessiveEntry != null) {
                
                final String recessiveKey = recessiveEntry.getKey();
                Object recessiveValue = recessiveEntry.getValue();
                if (recessiveValue instanceof Map) {
                  @SuppressWarnings("unchecked")
                  final Map<String, Object> recessiveValueMap = new HashMap<>((Map<String, Object>)recessiveValue);
                  recessiveValue = recessiveValueMap;
                  final Object dominantAnalog = dominantGlobalsMap.get(recessiveKey);
                  if (dominantAnalog instanceof Map) {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> dominantAnalogMap = (Map<String, Object>)dominantAnalog;
                    coalesceMaps(dominantAnalogMap, recessiveValueMap);
                  }
                }
                dominantGlobalsMap.put(recessiveKey, recessiveValue);
                
              }
            }
          }
        }
      }
    }
  }

  // Ported from
  // https://github.com/kubernetes/helm/blob/v2.6.1/pkg/chartutil/values.go#L310-L332
  // but reversing the order of the arguments.
  // targetMap values override sourceMap values.


  /**
   * Combines {@code sourceMap} and {@code targetMap} together
   * recursively and returns the result such that values in {@code
   * targetMap} will override values in {@code sourceMap}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method returns {@code targetMap} (not a copy).</p>
   *
   * <p>This method may modify {@code targetMap}'s contents if {@code
   * sourceMap} contains entries that {@code targetMap} does not
   * have.</p>
   *
   * <p>If any given entry in {@code sourceMap} is a {@link Map}
   * itself, and if {@code targetMap} also contains a {@link Map}
   * under the same key, then those {@link Map}s are supplied
   * recursively to this method as {@code sourceMap} and {@code
   * targetMap} respectively.</p>
   *
   * @param sourceMap the {@link Map} that will contribute entries to
   * the {@code targetMap} only if they are not already contained;
   * must not be {@code null}
   *
   * @param targetMap the {@link Map} that will contain all the
   * results of this logical operation; must not be {@code null}
   *
   * @return {@code targetMap}, not a copy, that will normally be
   * changed to incorporate the results of this operation
   *
   * @exception NullPointerException if either {@code sourceMap} or
   * {@code targetMap} is {@code null}
   */
  static final Map<String, Object> coalesceMaps(final Map<? extends String, ?> sourceMap, final Map<String, Object> targetMap) {
    Objects.requireNonNull(sourceMap);
    Objects.requireNonNull(targetMap);
    if (!sourceMap.isEmpty()) {
      final Set<? extends Entry<? extends String, ?>> sourceMapEntrySet = sourceMap.entrySet();
      if (sourceMapEntrySet != null && !sourceMapEntrySet.isEmpty()) {
        for (final Entry<? extends String, ?> sourceMapEntry : sourceMapEntrySet) {
          if (sourceMapEntry != null) {
            final String sourceMapKey = sourceMapEntry.getKey();
            final Object sourceMapValue = sourceMapEntry.getValue();
            if (!targetMap.containsKey(sourceMapKey)) {
              targetMap.put(sourceMapKey, sourceMapValue);
            } else if (sourceMapValue instanceof Map) {
              final Object targetMapValue = targetMap.get(sourceMapKey);
              if (targetMapValue instanceof Map) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> targetMapValueMap = (Map<String, Object>)targetMapValue;
                @SuppressWarnings("unchecked")
                final Map<? extends String, ?> sourceMapValueMap = (Map<? extends String, ?>)sourceMapValue;

                // Recursive call; alters targetMap's contents in
                // place
                coalesceMaps(sourceMapValueMap, targetMapValueMap);

              }
            }
          }
        }
      }
    }
    return targetMap;
  }
  
}
