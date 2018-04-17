/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2017-2018 microBean.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import hapi.chart.ChartOuterClass.ChartOrBuilder;
import hapi.chart.ConfigOuterClass.Config;
import hapi.chart.ConfigOuterClass.ConfigOrBuilder;
import hapi.chart.ConfigOuterClass.ValueOrBuilder;
import hapi.chart.MetadataOuterClass.Metadata;

import hapi.services.tiller.Tiller.InstallReleaseRequest; // for javadoc only

import org.yaml.snakeyaml.Yaml;

import org.yaml.snakeyaml.constructor.SafeConstructor;

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
   * <p>This method calls the {@link
   * #toValuesMap(ChartOuterClass.ChartOrBuilder,
   * ConfigOuterClass.ConfigOrBuilder)} with {@code chart} as its
   * first argument and {@code null} as its second argument and
   * returns its return value.</p>
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
   * @see #toValuesMap(ChartOuterClass.ChartOrBuilder, ConfigOuterClass.ConfigOrBuilder)
   */
  static final Map<String, Object> toDefaultValuesMap(final ChartOrBuilder chart) {
    return toValuesMap(chart, (ConfigOrBuilder)null);
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
    final Map<String, Object> configAsMap;
    if (config == null) {
      configAsMap = null;
    } else {
      configAsMap = toMap(config);
    }
    final Map<String, Object> map = toValuesMap(chart, configAsMap);
    assert map != null;
    final Map<String, Object> returnValue = coalesceDependencies(chart, map);
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
   * {@link Yaml#loadAs(String, Class)} method, and then passes that
   * {@link Map} as the first parameter&mdash;and the {@code
   * targetMap} as the second parameter&mdash;to the {@link
   * Values#coalesceMaps(Map, Map)} method and returns its result.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method does not consider subcharts or global values in
   * any way.</p>
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
   * @see Values#coalesceMaps(Map, Map)
   *
   * @see ChartOrBuilder#getValues()
   *
   * @see ConfigOrBuilder#getRaw()
   *
   * @see Yaml#loadAs(String, Class)
   */
  private static final Map<String, Object> computeEffectiveValues(final ChartOrBuilder chart, Map<String, Object> targetMap) {
    if (targetMap == null) {
      targetMap = new HashMap<>();
    }
    if (chart != null) {
      final ConfigOrBuilder config = chart.getValues();
      if (config != null) {
        targetMap = computeEffectiveValues(config, targetMap);
      }
    }
    return targetMap;
  }

  static final Map<String, Object> toMap(final ConfigOrBuilder config) {
    return computeEffectiveValues(config, null);
  }
  
  private static final Map<String, Object> computeEffectiveValues(final ConfigOrBuilder config, Map<String, Object> targetMap) {
    if (targetMap == null) {
      targetMap = new HashMap<>();
    }
    if (config != null) {
      final Map<String, Object> sourceMap;
      final String raw = config.getRaw();
      if (raw == null || raw.isEmpty()) {
        final Map<? extends String, ? extends ValueOrBuilder> valuesMap = config.getValuesMap();
        if (valuesMap == null || valuesMap.isEmpty()) {
          sourceMap = null;
        } else {
          sourceMap = new HashMap<>();
          final Collection<? extends Entry<? extends String, ? extends ValueOrBuilder>> entrySet = valuesMap.entrySet();
          assert entrySet != null;
          assert !entrySet.isEmpty();
          for (final Entry<? extends String, ? extends ValueOrBuilder> entry : entrySet) {
            if (entry != null) {
              final String name = entry.getKey();
              if (name != null) {
                final ValueOrBuilder value = entry.getValue();
                if (value == null) {
                  sourceMap.put(name, null);
                } else {
                  sourceMap.put(name, value.getValue());
                }
              }
            }
          }
        }
      } else {
        @SuppressWarnings("unchecked")
        final Map<String, Object> temp = new Yaml(new SafeConstructor()).load(raw);
        sourceMap = temp;
      }
      targetMap = Values.coalesceMaps(sourceMap, targetMap);
    }
    return targetMap;
  }

  /**
   * First gets the "right" values to use by blending the supplied
   * {@link Map} of typically user-supplied values with the
   * {@linkplain ChartOrBuilder#getValues() default values present in
   * the supplied <code>ChartOrBuilder</code>}, and then calls {@link
   * #coalesceDependencies(ChartOuterClass.ChartOrBuilder, Map)} on
   * the results.
   *
   * <p>This method first calls {@link
   * #computeEffectiveValues(ChartOuterClass.ChartOrBuilder, Map)},
   * producing a {@link Map} that combines user-specified and default
   * values, and then passes the supplied {@code chart} and the values
   * {@link Map} to the {@link
   * #coalesceDependencies(ChartOuterClass.ChartOrBuilder, Map)}
   * method and returns its result.
   *
   * @param chart a {@link ChartOrBuilder}
   *
   * @param suppliedValues the {@link Map} that will ultimately be
   * modified and returned
   *
   * @return {@code suppliedValues}
   *
   * @see #coalesceDependencies(ChartOuterClass.ChartOrBuilder, Map)
   *
   * @see #computeEffectiveValues(ChartOuterClass.ChartOrBuilder, Map)
   */
  private static final Map<String, Object> toValuesMap(final ChartOrBuilder chart, Map<String, Object> suppliedValues) {
    final Map<String, Object> effectiveValues = computeEffectiveValues(chart, suppliedValues);
    assert suppliedValues == null || effectiveValues == suppliedValues;
    assert effectiveValues != null;
    final Map<String, Object> returnValue = coalesceDependencies(chart, effectiveValues);
    assert returnValue == effectiveValues;
    return returnValue;
  }

  private static final Map<String, Object> coalesceDependencies(final ChartOrBuilder chart) {
    final Map<String, Object> effectiveValues = computeEffectiveValues(chart, new HashMap<>());
    assert effectiveValues != null;
    return coalesceDependencies(chart, effectiveValues);
  }
  
  private static final Map<String, Object> coalesceDependencies(final ChartOrBuilder chart, Map<String, Object> returnValue) {
    if (chart != null) {
      returnValue = coalesceDependencies(chart.getDependenciesList(), returnValue);
      assert returnValue != null;
    }    
    return returnValue;
  }

  /**
   * One specific part of the general flattening of the values to be
   * used during a chart operation, this method adds an entry, one per
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
   * @param subcharts an {@link Iterable} of {@link ChartOrBuilder}
   * instances, each element of which represents a <em>subchart</em>
   * in a larger Helm chart; may be {@code null}
   *
   * @param returnValue a {@link Map} of values that will be treated
   * as primary, or overriding; may be {@code null} in which case a
   * new {@link Map} will be used instead
   *
   * @return {@code returnValue}, containing whatever it contained
   * before together with the flattened default values from the
   * supplied subcharts; never {@code null}
   *
   * @see Values#coalesceGlobals(Map, Map)
   *
   * @see #toValuesMap(ChartOuterClass.ChartOrBuilder, Map)
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

              // ...then call toValuesMap() on it (which calls
              // this very method recursively, but doesn't overwrite
              // anything in subchartValuesMap.  So this whole thing
              // flattens all the subchart default values and their
              // globals into one map.
              final Map<String, Object> temp = toValuesMap(subchart, subchartValuesMap);
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
