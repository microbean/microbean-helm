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
import java.util.HashMap;
import java.util.Objects;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MapTree {

  private static final Pattern keyPattern = Pattern.compile("([^.]*)\\.?");
  
  private final Map<String, Object> map;
  
  MapTree(final Map<String, Object> map) {
    super();
    this.map = map;
  }

  // ported from requirements.go pathToMap which is utter madness
  static final Map<String, Object> newMapChain(final String path, final Map<String, Object> data) {
    Objects.requireNonNull(path);
    Map<String, Object> returnValue = null;
    if (path.equals(".")) {
      returnValue = data;
    } else if (!path.isEmpty()) {
      final Matcher matcher = keyPattern.matcher(path);
      assert matcher != null;

      /*
        The Go code in pkg/chartutil/requirements.go (pathToMap())
        allocates too many objects only to throw them away.  For a
        path of "A.B.C", it does this:

         n[0]: [A]->m0
         n[1]: [B]->m1
         n[2]: [C]->m2

        That is, a list, n, of maps contains (in this case) three
        maps: n0, n1 and n2 at positions 0, 1 and 2.
        
        Each map has one key.  The key is initially pointed to a new
        map, m0, m1 and m2, which is allocated and then thrown away
        without ever being used.

        Then the list is walked.  i is the number of the map being
        looked at. z is the next index.  k is the key under
        consideration.

         n0: [A]->m0 (i=0) (A is the sole key; k=A) (z=1)
         n1: [B]->m1 (i=1) (B is the sole key; k=B) (z=2)
         n2: [C]->m2 (i=2) (C is the sole key; k=C) (z=3)

        If z == 3, then we're looking at n[2] and so n[2][C] = data.
        Otherwise: n[0][A] = n[1] = [B]->m1 and n[1][B] = n[2] = [C]->m2

        We can do lots better by simply using a regular expression and
        tracking when it is done, setting our links as we go along.
       */

      Map<String, Object> priorMap = null;
      while (matcher.find()) {
        final String key = matcher.group(1);
        assert key != null;
        final Map<String, Object> newMap = new HashMap<>();
        if (matcher.hitEnd()) {
          newMap.put(key, data);
        } else {
          newMap.put(key, null);
        }
        if (priorMap == null) {
          assert returnValue == null;
          returnValue = newMap;
        } else {
          assert returnValue != null;
          assert priorMap.size() == 1;
          priorMap.entrySet().iterator().next().setValue(newMap);
        }
        priorMap = newMap;
      }
    }
    return returnValue;
  }
  
  final Map<String, Object> getMap(final String path) {
    final Map<?, ?> map = this.get(path, Map.class);
    if (map == null) {
      return null;
    }
    @SuppressWarnings("unchecked")
    final Map<String, Object> returnValue = (Map<String, Object>)map;
    return returnValue;
  }
  
  final <V> V get(final String path, final Class<V> type) {
    Objects.requireNonNull(path);
    Objects.requireNonNull(type);
    Object returnValue = null;
    Map<String, Object> map = this.map;
    if (map != null && !map.isEmpty() && !path.isEmpty()) {
      final Matcher matcher = keyPattern.matcher(path);
      assert matcher != null;
      while (map != null && matcher.find()) {
        final String key = matcher.group(1);
        assert key != null;
        returnValue = map.get(key);
        if (returnValue instanceof Map) {
          @SuppressWarnings("unchecked")
          final Map<String, Object> temp = (Map<String, Object>)returnValue;
          map = temp;
        } else {
          if (!matcher.hitEnd()) {
            returnValue = null;
          }
          map = null;
        }
      }
    }
    if (returnValue == null || !type.isInstance(returnValue)) {
      return null;
    } else {
      return type.cast(returnValue);
    }
  }
  
}
