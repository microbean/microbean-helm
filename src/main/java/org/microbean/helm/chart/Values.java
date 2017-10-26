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

final class Values {

  private Values() {
    super();
  }

  // Ported from
  // https://github.com/kubernetes/helm/blob/v2.7.0/pkg/chartutil/values.go#L206-L254,
  // but reversing the order of the arguments.
  /**
   * 
   */
  static final void coalesceGlobals(final Map<?, ?> sourceMap, final Map<String, Object> targetMap) {
    if (targetMap != null) {

      // Get whatever is indexed under the "global" key in the
      // targetMap.  We hope it's a Map.  If there's nothing there,
      // we'll stuff a new Map in under that key.  If whatever is
      // there is non-null but not a Map (like, say, an Integer or
      // something), we do nothing.
      final Object targetGlobals = targetMap.get("global");
      final Map<String, Object> targetGlobalsMap;
      if (targetGlobals == null) {
        targetGlobalsMap = new HashMap<>();
        targetMap.put("global", targetGlobalsMap);
      } else if (targetGlobals instanceof Map) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> temp = (Map<String, Object>)targetGlobals;
        targetGlobalsMap = temp;
      } else {
        targetGlobalsMap = null;
      }
      
      if (targetGlobalsMap != null) {

        // Get whatever is indexed under the "global" key in the
        // sourceMap.  We hope it's a Map.  If it isn't or it's
        // empty, we do nothing.
        final Object defaultGlobals = sourceMap.get("global");
        final Map<? extends String, ?> defaultGlobalsMap;
        if (defaultGlobals instanceof Map) {
          @SuppressWarnings("unchecked")
          final Map<? extends String, ?> temp = (Map<? extends String, ?>)defaultGlobals;
          defaultGlobalsMap = temp;
        } else {
          defaultGlobalsMap = null;
        }

        if (defaultGlobalsMap != null && !defaultGlobalsMap.isEmpty()) {
          final Set<? extends Entry<? extends String, ?>> defaultGlobalsEntrySet = defaultGlobalsMap.entrySet();
          if (defaultGlobalsEntrySet != null && !defaultGlobalsEntrySet.isEmpty()) {
            for (final Entry<? extends String, ?> defaultGlobalsEntry : defaultGlobalsEntrySet) {
              if (defaultGlobalsEntry != null) {

                // For every default (source) value...
                
                final String defaultKey = defaultGlobalsEntry.getKey();
                Object defaultValue = defaultGlobalsEntry.getValue();
                if (defaultValue instanceof Map) {
                  @SuppressWarnings("unchecked")
                  final Map<String, Object> defaultValueMap = new HashMap<>((Map<String, Object>)defaultValue);
                  
                  // ...if it's a Map, see if the target also has a
                  // Map under the same key.
                  
                  final Object targetAnalog = targetGlobalsMap.get(defaultKey);
                  if (targetAnalog instanceof Map) {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> targetAnalogMap = (Map<String, Object>)targetAnalog;

                    // If the target has a Map under the same key,
                    // coalesce the two maps, using defaultValueMap as
                    // the primary map, and the target Map as the
                    // source of defaults.  The Go code says:
                    //
                    //   Basically, we reverse order of coalesce here
                    //   to merge top-down.
                    //
                    // Bear in mind in the Java code here our argument
                    // order is reversed too.

                    coalesceMaps(targetAnalogMap, defaultValueMap);
                  }
                }
                targetGlobalsMap.put(defaultKey, defaultValue);
              }
            }
          }
        }
      }
    }
  }

  // Ported from
  // https://github.com/kubernetes/helm/blob/v2.7.0/pkg/chartutil/values.go#L310-L332
  // but reversing the order of the arguments.
  // targetMap values override sourceMap values.
  /**
   * Combines {@code sourceMap} and {@code targetMap} together
   * recursively and returns the result such that values in {@code
   * targetMap} will override values in {@code sourceMap}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method returns {@code targetMap} (not a copy).  If {@code
   * targetMap} is {@code null}, then this method returns a new {@link
   * Map} implementation.</p>
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
   * may be {@code null}
   *
   * @param targetMap the {@link Map} that will contain all the
   * results of this logical operation; may be {@code null}
   *
   * @return {@code targetMap}, not a copy, that will normally be
   * changed to incorporate the results of this operation, or a new
   * {@link Map} containing the results of this operation if {@code
   * targetMap} was originally {@code null}
   */
  static final Map<String, Object> coalesceMaps(final Map<? extends String, ?> sourceMap, Map<String, Object> targetMap) {
    if (targetMap == null) {
      targetMap = new HashMap<>();
    }
    if (sourceMap != null && !sourceMap.isEmpty()) {
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
