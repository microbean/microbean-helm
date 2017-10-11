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

import java.io.Closeable;
import java.io.IOException;

import hapi.chart.ChartOuterClass.Chart; // for javadoc only
import hapi.chart.ChartOuterClass.Chart.Builder;

/**
 * An abstract class whose implementations are capable of reading in
 * the raw materials for a {@linkplain Chart Helm chart} from some
 * kind of <em>source</em> and creating new {@link Chart} instances
 * from such raw materials.
 *
 * <p><strong>Implementations should pay close attention to any
 * potential resource leaks and control them in their implementation
 * of the {@link Closeable#close()} method.</strong></p>
 *
 * @param <T> the type of source from which {@link Chart}s may be
 * loaded
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #load(Object)
 *
 * @see #load(hapi.chart.ChartOuterClass.Chart.Builder, Object)
 *
 * @see Chart
 */
public abstract class AbstractChartLoader<T> implements Closeable {


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link AbstractChartLoader}.
   */
  protected AbstractChartLoader() {
    super();
  }


  /*
   * Instance methods.
   */
  
  
  /**
   * Creates a new "top-level" {@link Builder} from the supplied
   * {@code source} and returns it.
   *
   * <p>Implementations of this method must not return {@code null}.</p>
   *
   * @param source the source from which a new {@link Builder} should be
   * created; must not be {@code null}
   *
   * @return a new {@link Builder}; never {@code null}
   *
   * @exception NullPointerException if {@code source} is {@code null}
   *
   * @exception IOException if reading the supplied {@code source}
   * could not complete normally
   *
   * @see #load(ChartOuterClass.Chart.Builder, Object)
   */
  public final Builder load(final T source) throws IOException {
    return this.load(null, source);
  }
  
  /**
   * Creates a new {@link Builder} from the supplied {@code source}
   * and returns it.
   *
   * <p>Implementations of this method must not return {@code null}
   * and must not return {@code parent}.</p>
   *
   * @param parent the {@link Builder} that will serve as the parent
   * of the {@link Builder} that will be returned; may be (and often
   * is) {@code null}, indicating that the {@link Builder} being
   * returned does not represent a subchart; must not be what is
   * returned
   *
   * @param source the source from which a new {@link Builder} should
   * be created; must not be {@code null}
   *
   * @return a new {@link Builder}; never {@code null}; never {@code
   * parent}
   *
   * @exception NullPointerException if {@code source} is {@code null}
   *
   * @exception IOException if reading the supplied {@code source}
   * could not complete normally
   */
  public abstract Builder load(final Builder parent, final T source) throws IOException;

}
