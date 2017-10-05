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
          targetMap = Values.coalesceMaps(sourceMap, targetMap);
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

  /**
   * One part of the general flattening of the values to be used
   * during a chart operation, this method adds an entry, one per
   * subchart, to the supplied {@code Map}, under that subchart's
   * name, containing its (flattened in turn) set of values.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>If the supplied {@link Map} already contains an entry under a
   * subchart's name, then its value must be {@code
   * null}&mdash;indicating that it does not yet have an entry for
   * this subchart&mdash;or a {@link Map} (or an {@link
   * IllegalArgumentException} will be thrown).</p>
   *
   * <p>Normally you would pass the return value of {@link
   * #toValuesMap(Map, Map)} as the second argument to this
   * method.</p>
   *
   * @param subcharts an {@link Iterable} of {@link ChartOrBuilder}
   * instances, each element of which represents a subchart in a
   * larger Helm chart; may be {@code null}
   *
   * @param returnValue a {@link Map} of values that will be treated
   * as primary, or overriding; may be {@code null} in which case a
   * new {@link Map} will be used instead
   *
   * @return {@code returnValue}, containing whatever it contained
   * before together with the flattened default values from the
   * supplied subcharts; never {@code null}
   *
   * @see #coalesceGlobals(Map, Map)
   *
   * @see #coalesce(ChartOrBuilder, Map)
   *
   * @see #toValuesMap(Map, Map)
   */
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

              // See if the user-supplied values have a key under
              // which values destined for a given subchart live.
              // E.g. you might see redis.frob = "boo"; in that case
              // we are hoping that the value indexed under "redis" is
              // a Map, one of whose keys would be "frob" (whose value
              // would be "boo").
              final Object subchartValuesObject = returnValue.get(subchartName);
              if (subchartValuesObject == null) {
                // We didn't find anything under "redis".  So go ahead
                // and put in an empty mutable Map under that key to
                // indicate that there are no dependent values for it
                // yet but there might be (this method ends up
                // indirectly calling itself recursively)
                subchartValuesMap = new HashMap<>();
                returnValue.put(subchartName, subchartValuesMap);
                
              } else if (subchartValuesObject instanceof Map) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> temp = (Map<String, Object>)subchartValuesObject;
                subchartValuesMap = temp;
                
              } else {
                throw new IllegalArgumentException("returnValue.get(" + subchartName + "): not a map: " + subchartValuesObject);
              }

              // Now that we've found, e.g., a Map indexed under
              // "redis", make sure our "flattened" map "receiver" has
              // access to global values...
              Values.coalesceGlobals(returnValue, subchartValuesMap);

              // ...then coalesce again (which calls this very method
              // recursively, but doesn't overwrite anything in
              // subchartValuesMap.  So this whole thing flattens all
              // the subchart default values and their globals into
              // one map.
              final Map<String, Object> temp = coalesce(subchart, subchartValuesMap);
              assert temp == subchartValuesMap;
              
              returnValue.put(subchartName, temp);
            }
          }
        }
      }
    }
    return returnValue;
  }
  
}
