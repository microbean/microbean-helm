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
package org.microbean.helm.chart.repository;

import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

public class TestIssue80 {

  public TestIssue80() {
    super();
  }

  @Test
  public void testLoadIndex() throws IOException, URISyntaxException {
    final Path helmHome = new HelmHome().toPath();
    assertNotNull(helmHome);
    assumeTrue(Files.isDirectory(helmHome));
    final ChartRepository chartRepository = new ChartRepository("stable", new URI("https://kubernetes-charts.storage.googleapis.com/"));
    final ChartRepository.Index index = chartRepository.getIndex();
    assertNotNull(index);
  }
  
}
