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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.List;

import hapi.chart.ChartOuterClass.Chart;

import org.microbean.helm.Tiller;

/**
 * A fa&ccedil;ade class for common {@link Chart}-related operations.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see Tiller
 */
final class Charts {

  static final List<Chart.Builder> flatten(final Chart.Builder c) {
    return flatten(c, null);
  }

  // Ported slavishly from requirements.go getParents()
  private static final List<Chart.Builder> flatten(final Chart.Builder c, List<Chart.Builder> out) {
    Objects.requireNonNull(c);
    if (out == null) {
      out = new ArrayList<>();
      out.add(c);
    } else if (out.isEmpty()) {
      out.add(c);
    }
    final Collection<? extends Chart.Builder> subcharts = c.getDependenciesBuilderList();
    if (subcharts != null && !subcharts.isEmpty()) {
      for (final Chart.Builder subchart : subcharts) {
        if (subchart != null) {
          final int subSubchartCount = subchart.getDependenciesCount();
          if (subSubchartCount > 0) {
            out.add(subchart);
            out = flatten(subchart, out);
          }
        }
      }
    }
    return out;
  }

}
