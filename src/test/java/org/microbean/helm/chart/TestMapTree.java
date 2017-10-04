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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TestMapTree {

  public TestMapTree() {
    super();
  }

  @Test
  public void testGet() {
    Map<String, Object> map = new HashMap<>();
    final MapTree mapTree = new MapTree(map);
    mapTree.put("a.b.c", Integer.valueOf(3));
    final Integer result = mapTree.get("a.b.c", Integer.class);
    assertEquals(Integer.valueOf(3), result);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testPutOnEmptyMapTree() {
    Map<String, Object> map = new HashMap<>();
    final MapTree mapTree = new MapTree(map);
    final Object returnValue = mapTree.put("a.b.c", Integer.valueOf(3));
    assertNull(returnValue);
    assertTrue(map.get("a") instanceof Map);
    assertEquals(1, map.size());
    map = (Map<String, Object>)map.get("a");
    assertNotNull(map);
    assertTrue(map.get("b") instanceof Map);
    assertEquals(1, map.size());
    map = (Map<String, Object>)map.get("b");
    assertNotNull(map);
    assertEquals(Integer.valueOf(3), map.get("c"));
  }

  @Test
  public void testPutOnPopulatedMapTree() {
    Map<String, Object> map = new HashMap<>();
    Map<String, Object> secondLevelMap = new HashMap<>();
    secondLevelMap.put("b", "delete me");
    secondLevelMap.put("x", "don't delete me");
    map.put("a", secondLevelMap);
    final MapTree mapTree = new MapTree(map);
    final Object returnValue = mapTree.put("a.b.c", Integer.valueOf(3));
    assertNull(returnValue);
    assertSame(secondLevelMap, map.get("a"));
    assertEquals(1, map.size());
    @SuppressWarnings("unchecked")
    final Map<String, Object> temp = (Map<String, Object>)map.get("a");
    map = temp;
    assertNotNull(map);
    assertTrue(map.get("b") instanceof Map);
    assertFalse(map.containsValue("delete me"));
    assertEquals(2, map.size());
    @SuppressWarnings("unchecked")
    final Map<String, Object> temp2 = (Map<String, Object>)map.get("b");
    map = temp2;
    assertNotNull(map);
    assertEquals(Integer.valueOf(3), map.get("c"));
  }

  
}
