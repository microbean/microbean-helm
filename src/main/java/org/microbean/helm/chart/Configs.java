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
   * Given a set of overriding values in {@link ConfigOrBuilder} form,
   * and a {@link ChartOrBuilder} whose {@linkplain
   * ChartOrBuilder#getValues() default values} are being overridden,
   * flattens both {@link ConfigOrBuilder} instances into a {@link
   * Map}, such that the overriding values are dominant and the {@link
   * ChartOrBuilder}'s {@linkplain ChartOrBuilder#getValues() values}
   * are recessive.
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
  static final Map<String, Object> coalesceConfigs(final ChartOrBuilder chart, final ConfigOrBuilder config) {
    Map<String, Object> returnValue;
    if (config == null) {
      returnValue = new HashMap<>();
    } else {
      final String raw = config.getRaw();
      if (raw == null || raw.isEmpty()) {
        returnValue = new HashMap<>();
      } else {
        @SuppressWarnings("unchecked")
        final Map<String, Object> configAsMap = (Map<String, Object>)new Yaml().load(raw);
        assert configAsMap != null;
        returnValue = coalesce(chart, configAsMap);
      }
    }
    returnValue = coalesceDependencies(chart, returnValue);
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
   * 
   */
  private static final Map<String, Object> coalesceValues(final ChartOrBuilder chart, final Map<String, Object> overridingValues /* v */) {
    Map<String, Object> returnValue = overridingValues;
    if (chart != null && overridingValues != null) {
      final Config config = chart.getValues();
      if (config != null) {
        final String raw = config.getRaw();
        if (raw != null && !raw.isEmpty()) {
          // ReadValues
          @SuppressWarnings("unchecked")
          final Map<String, Object> chartValues = (Map<String, Object>)new Yaml().load(raw); // nv
          if (chartValues != null && !chartValues.isEmpty()) {
            final Set<Entry<String, Object>> chartValuesEntrySet = chartValues.entrySet();
            if (chartValuesEntrySet != null && !chartValuesEntrySet.isEmpty()) {
              for (final Entry<String, Object> chartValuesEntry : chartValuesEntrySet) {
                if (chartValuesEntry != null) {
                  final String key = chartValuesEntry.getKey();
                  final Object value = chartValuesEntry.getValue();
                  if (!overridingValues.containsKey(key)) {
                    overridingValues.put(key, value);
                  } else {
                    Object x = overridingValues.get(key);
                    if (x == null) {
                      overridingValues.remove(key);
                    } else if (x instanceof Map) {
                      @SuppressWarnings("unchecked")
                      final Map<String, Object> dest = (Map<String, Object>)x;
                      if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        final Map<String, Object> src = (Map<String, Object>)value;
                        coalesceMaps(src, dest);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    return returnValue;
  }
  
  private static final Map<String, Object> coalesce(final ChartOrBuilder chart, Map<String, Object> dest) {
    return coalesceDependencies(chart, coalesceValues(chart, dest));
  }
  
  private static final Map<String, Object> coalesceDependencies(final ChartOrBuilder chart, Map<String, Object> returnValue) {
    if (returnValue == null) {
      returnValue = new HashMap<>();
    }
    if (chart != null) {
      final Iterable<? extends ChartOrBuilder> subcharts = chart.getDependenciesList();
      if (subcharts != null) {
        for (final ChartOrBuilder subchart : subcharts) {
          if (subchart != null) {
            final Metadata subchartMetadata = subchart.getMetadata();
            if (subchartMetadata != null) {
              final String subchartName = subchartMetadata.getName();
              if (subchartName != null) {

                final Map<String, Object> subchartValuesMap;
                final Object x = returnValue.get(subchartName);
                if (x == null) {
                  subchartValuesMap = new HashMap<>();
                  returnValue.put(subchartName, subchartValuesMap);
                } else if (x instanceof Map) {
                  @SuppressWarnings("unchecked")
                  final Map<String, Object> temp = (Map<String, Object>)x;
                  subchartValuesMap = temp;
                } else {
                  throw new IllegalArgumentException("returnValue.get(" + subchartName + "): not a map: " + x);
                }
                
                coalesceGlobals(subchartValuesMap, returnValue);
                returnValue.put(subchartName, coalesce(subchart, subchartValuesMap));
              }
            }
          }
        }
      }
    }
    return returnValue;
  }

  private static final void coalesceGlobals(final Map<String, Object> dominantMap, final Map<String, Object> recessiveMap) {
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
        final Map<String, Object> recessiveGlobalsMap;
        if (recessiveGlobals instanceof Map) {
          @SuppressWarnings("unchecked")
          final Map<String, Object> temp = (Map<String, Object>)recessiveGlobals;
          recessiveGlobalsMap = temp;
        } else {
          recessiveGlobalsMap = null;
        }

        if (recessiveGlobalsMap != null && !recessiveGlobalsMap.isEmpty()) {
          final Set<Entry<String, Object>> recessiveEntrySet = recessiveGlobalsMap.entrySet();
          if (recessiveEntrySet != null && !recessiveEntrySet.isEmpty()) {
            for (final Entry<String, Object> recessiveEntry : recessiveEntrySet) {
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

  static final Map<String, Object> coalesceMaps(final Map<String, Object> sourceMap, final Map<String, Object> targetMap) {
    if (sourceMap != null && !sourceMap.isEmpty()) {
      final Set<Entry<String, Object>> sourceMapEntrySet = sourceMap.entrySet();
      if (sourceMapEntrySet != null && !sourceMapEntrySet.isEmpty()) {
        for (final Entry<String, Object> sourceMapEntry : sourceMapEntrySet) {
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
                final Map<String, Object> sourceMapValueMap = (Map<String, Object>)sourceMapValue;
                coalesceMaps(sourceMapValueMap, targetMapValueMap); // recursive
              }
            }
          }
        }
      }
    }
    return targetMap;
  }
  
}
