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

import java.io.FilterInputStream;
import java.io.InputStream;

/**
 * An exceptionally special-purpose {@link FilterInputStream} whose
 * {@link #close()} method performs no action.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see FilterInputStream
 */
final class NonClosingInputStream extends FilterInputStream {


  /*
   * Constructors.
   */

  
  /**
   * Creates a new {@link NonClosingInputStream}.
   *
   * @param in the {@link InputStream} to delegate all operations
   * except {@link #close()} to; must not be {@code null}
   *
   * @exception NullPointerException if {@code in} is {@code null}
   *
   * @see FilterInputStream#FilterInputStream(InputStream)
   */
  NonClosingInputStream(final InputStream in) {
    super(in);
  }


  /*
   * Instance methods.
   */

  
  /**
   * Does nothing when invoked.
   */
  @Override
  public final void close() {

  }
  
}
