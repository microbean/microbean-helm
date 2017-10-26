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

import java.io.Serializable; // for javadoc only

/**
 * An exception thrown by an {@link AbstractChartResolver}
 * implementation's {@link AbstractChartResolver#resolve(String,
 * String)} method indicating there was a problem with resolving a <a
 * href="https://docs.helm.sh/developing_charts/#charts">Helm
 * chart</a>.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see AbstractChartResolver#resolve(String, String)
 */
public class ChartResolverException extends Exception {


  /*
   * Static fields.
   */


  /**
   * The version of this class for {@linkplain Serializable
   * serialization} purposes.
   */
  private static final long serialVersionUID = 1L;


  /*
   * Constructors.
   */

  
  /**
   * Creates a {@link ChartResolverException}.
   */
  public ChartResolverException() {
    super();
  }

  /**
   * Creates a {@link ChartResolverException}.
   *
   * @param message a detail message; may be {@code null}
   */
  public ChartResolverException(final String message) {
    super(message);
  }

  /**
   * Creates a {@link ChartResolverException}.
   *
   * @param cause the {@link Throwable} causing this {@link
   * ChartResolverException} to be created; may be {@code null}
   */
  public ChartResolverException(final Throwable cause) {
    super(cause);
  }

  /**
   * Creates a {@link ChartResolverException}.
   *
   * @param message a detail message; may be {@code null}
   * 
   * @param cause the {@link Throwable} causing this {@link
   * ChartResolverException} to be created; may be {@code null}
   */
  public ChartResolverException(final String message, final Throwable cause) {
    super(message, cause);
  }

}
