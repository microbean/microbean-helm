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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TestValues {

  public TestValues() {
    super();
  }

  @Test
  public void testCoalesceMapsEnsuringTargetMapValuesOverrideSourceMapValues() {
    final Map<String, Object> source = new HashMap<>();
    source.put("key", "sourceValue");
    final Map<String, Object> target = new HashMap<>();
    target.put("key", "targetValue");
    final Map<String, Object> returnValue = Values.coalesceMaps(source, target);
    assertNotNull(returnValue);
    assertSame(returnValue, target);
    assertEquals(1, returnValue.size());
    assertEquals("targetValue", returnValue.get("key"));
  }

  @Test
  public void testCoalesceMapsEnsuringUniqueSourceMapEntriesAppearInTargetMap() {
    final Map<String, Object> source = new HashMap<>();
    source.put("sharedKey", "sourceValue");
    source.put("uniqueSourceKey", "sourceValue");
    final Map<String, Object> target = new HashMap<>();
    target.put("sharedKey", "targetValue");
    target.put("uniqueTargetKey", "targetValue");
    final Map<String, Object> returnValue = Values.coalesceMaps(source, target);
    assertSame(target, returnValue);
    assertEquals(3, returnValue.size());
    assertEquals("targetValue", returnValue.get("sharedKey"));
    assertEquals("sourceValue", returnValue.get("uniqueSourceKey"));
    assertEquals("targetValue", returnValue.get("uniqueTargetKey"));
  }

  @Test
  public void testCoalesceMapsEnsuringMapValuesAreHandledRecursively() {
    final Map<String, Object> source = new HashMap<>();
    final Map<String, Object> sourceMapValue = new HashMap<>();
    sourceMapValue.put("uniqueSourceKey", "uniqueSourceValue");
    sourceMapValue.put("sharedCoalescedKey", "sourceValue");
    source.put("sharedKey", sourceMapValue);
    
    final Map<String, Object> target = new HashMap<>();
    final Map<String, Object> targetMapValue = new HashMap<>();
    targetMapValue.put("uniqueTargetKey", "uniqueTargetValue");
    targetMapValue.put("sharedCoalescedKey", "targetValue");
    target.put("sharedKey", targetMapValue);

    final Map<String, Object> returnValue = Values.coalesceMaps(source, target);
    assertSame(target, returnValue);
    final Object rawValue = returnValue.get("sharedKey");
    assertTrue(rawValue instanceof Map);
    @SuppressWarnings("unchecked")
    final Map<String, Object> coalescedMap = (Map<String, Object>)rawValue;
    assertEquals("uniqueSourceValue", coalescedMap.get("uniqueSourceKey"));
    assertEquals("uniqueTargetValue", coalescedMap.get("uniqueTargetKey"));
    assertEquals("targetValue", coalescedMap.get("sharedCoalescedKey"));
  }
  
  @Test(expected = NullPointerException.class)
  public void testCoalesceMapsEnsuringNullSourceMapCausesNullPointerException() {
    Values.coalesceMaps(null, null);
  }
  
}
