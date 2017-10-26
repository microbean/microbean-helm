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
package org.microbean.helm.chart.resolver;

import java.io.IOException;

import java.net.URISyntaxException;

import hapi.chart.ChartOuterClass.Chart;

import org.microbean.development.annotation.Experimental;

/**
 * A resolver of <a href="https://docs.helm.sh/developing_charts/#charts">Helm charts</a>.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
@Experimental
public abstract class AbstractChartResolver {


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link AbstractChartResolver}.
   */
  protected AbstractChartResolver() {
    super();
  }


  /*
   * Instance methods.
   */
  

  /**
   * Uses the supplied {@code chartName} and {@code chartVersion}
   * parameters to find an appropriate <a
   * href="https://docs.helm.sh/developing_charts/#charts">Helm
   * chart</a> and returns it in the form of a {@link Chart.Builder}
   * object.
   *
   * <p>Implementations of this method may return {@code null}.</p>
   *
   * @param chartName the name of the chart to resolve; must not be
   * {@code null}
   *
   * @param chartVersion the version of the chart to resolve; may be
   * {@code null} which normally implies "latest" semantics
   *
   * @return the {@link Chart.Builder} representing a suitable Helm
   * chart, or {@code null} if no such chart could be found
   *
   * @exception ChartResolverException if there was a problem with
   * resolution
   *
   * @exception NullPointerException if {@code chartName} is {@code null}
   *
   * @exception IllegalArgumentException if either parameter value is
   * incorrect in some way
   */
  public abstract Chart.Builder resolve(final String chartName, final String chartVersion) throws ChartResolverException;
  
}
