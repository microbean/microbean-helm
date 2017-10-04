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

  /**
   * A {@link Pattern} matching the shortest-length occurrence of a
   * sequence of non-periods followed by an period.
   *
   * <p>Given {@code a.b.c}, this should yield {@code a}, {@code 
   */
  private static final Pattern keyPattern = Pattern.compile("([^.]+)\\.?");
  
  private final Map<String, Object> map;
  
  MapTree(final Map<String, Object> map) {
    super();
    this.map = map;
  }

  /**
   * Given a {@link Map} named {@code data} conceptually representing
   * a leaf node in a tree structure, and a dot-separated {@code path}
   * to that leaf node, returns a new {@link Map} of {@link Map}s
   * reflecting the path in question with the supplied {@code data}
   * {@link Map} as the leaf node in the return value.
   *
   * <p>This method may return {@code null} in certain
   * circumstances.</p>
   *
   * <p>As an example, given a {@code path} parameter value of "{@code
   * a.b}" and a {@code data} {@link Map} containing one key, "{@code
   * c}", whose value is "{@code d}", the return value will be a
   * {@link Map} with one key, "{@code a}".  The value of that key
   * will be a {@link Map} with one key, "{@code b}".  The value of
   * that key will be the supplied {@code data} {@code Map}.</p>
   *
   * @param path a period ("{@code .}")-separated path; must not be
   * {@code null}
   *
   * @param data the {@link Map} serving as the terminal leaf; may be
   * {@code null}
   *
   * @return a {@link Map} reflecting the new structure, or {@code
   * null} if {@code path} is non-{@code null} but {@linkplain
   * String#isEmpty() empty}, or if {@code data} is {@code null} and
   * {@code path} is equal to "{@code .}"
   *
   * @exception NullPointerException if {@code path} is {@code null}
   */
  static final Map<String, Object> newMapChain(final String path, final Map<String, Object> data) {
    Objects.requireNonNull(path);
    Map<String, Object> returnValue = null;
    if (path.equals(".")) {
      returnValue = data;
    } else if (!path.isEmpty()) {

      /*
        Ported from pkg/chartutil/requirements.go.pathToMap().

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
        without ever being used!

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
      
      final Matcher matcher = keyPattern.matcher(path);
      assert matcher != null;

      Map<String, Object> priorMap = null;

      while (matcher.find()) {
        // While there are still keys in the path...

        
        final String key = matcher.group(1);
        assert key != null;
        
        final Map<String, Object> newMap = new HashMap<>();

        if (matcher.hitEnd()) {
          // If we are on the last key in the path, the value of the
          // key will be the supplied data Map.
          newMap.put(key, data);
        } else {
          // If we are not on the last key in the path, the value of
          // the key is not yet known.
          newMap.put(key, null);
        }
        
        if (priorMap == null) {
          // We're working on the first (possibly only) key in the
          // path.  The Map housing it is by definition the Map to be
          // returned.
          assert returnValue == null;
          returnValue = newMap;
        } else {
          // We're working on a key deep in the path.  The returnValue
          // must have already been set.
          assert returnValue != null;
          assert priorMap.size() == 1;
          priorMap.entrySet().iterator().next().setValue(newMap);
        }
        priorMap = newMap;
      }
    }
    return returnValue;
  }

  /**
   * Calls the {@link #get(String, Class)} method with the supplied
   * {@code path} and {@link Map Map.class} as parameter values and
   * returns the result.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <p>This method deliberately&mdash;unsafely&mdash;casts the result
   * to a <code>Map&lt;String, Object&gt;</code>.</p>
   *
   * @param path a period ("{@code .}")-separated path of {@link
   * String}s that will serve as keys in a {@link Map}; must not be
   * {@code null}
   *
   * @return a {@link Map}, or {@code null}
   *
   * @exception NullPointerException if {@code path} is {@code null}
   *
   * @see #get(String, Class)
   */
  final Map<String, Object> getMap(final String path) {
    final Map<?, ?> map = this.get(path, Map.class);
    if (map == null) {
      return null;
    }
    @SuppressWarnings("unchecked")
    final Map<String, Object> returnValue = (Map<String, Object>)map;
    return returnValue;
  }

  /**
   * Given a dot-separated {@code path} and a {@code type} to cast the
   * return value to, traverses the path and returns the terminal
   * result, provided it {@linkplain Class#isInstance(Object) is an
   * instance} of the supplied {@code type}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @param path a period ("{@code .}")-separated path of {@link
   * String}s that will serve as keys in a {@link Map}; must not be
   * {@code null}
   *
   * @param type the {@link Class} to attempt to cast the terminal
   * result to; must not be {@code null}; if the terminal result is
   * not {@linkplain Class#isInstance(Object) an instance of} this
   * {@link Class} then {@code null} will be returned
   *
   * @return the result of traversing the supplied {@code path},
   * provided that it exists and is {@linkplain
   * Class#isInstance(Object) an instance of} the supplied {@code
   * type}, or {@code null}
   *
   * @exception NullPointerException if either {@code path} or {@code
   * type} is {@code null}
   */
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

  final Object put(final String path, final Object value) {
    Map<String, Object> map = this.map;
    Object returnValue = null;
    if (path == null || path.isEmpty()) {
      if (map != null) {
        returnValue = map.put(path, value);
      }
    } else {
      final Matcher matcher = keyPattern.matcher(path);
      assert matcher != null;
      while (map != null && matcher.find()) {
        final String key = matcher.group(1);
        assert key != null;
        assert !key.isEmpty();
        final Object object = map.get(key);
        if (object instanceof Map) {
          @SuppressWarnings("unchecked")
          final Map<String, Object> temp = (Map<String, Object>)object;
          map = temp;
        } else if (matcher.hitEnd()) {
          returnValue = map.put(key, value);
        } else {
          final Map<String, Object> newMap = new HashMap<>();
          map.put(key, newMap); // destructive operation
          map = newMap;
        }
      }
    }
    return returnValue;
  }
  
}
