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
package org.microbean.helm;

import java.io.Serializable; // for javadoc only

/**
 * An abstract {@link RuntimeException} indicating a general Helm- or
 * Tiller-related problem.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
public abstract class HelmException extends RuntimeException {


  /*
   * Static fields.
   */
  
  
  /**
   * The version of this class for {@linkplain Serializable
   * serialization purposes}.
   */
  private static final long serialVersionUID = 1L;


  /*
   * Constructors.
   */
  

  /**
   * Creates a new {@link HelmException}.
   */
  protected HelmException() {
    super();
  }

  /**
   * Creates a new {@link HelmException}.
   *
   * @param message a descriptive message; may be {@code null}
   */
  protected HelmException(final String message) {
    super(message);
  }

  /**
   * Creates a new {@link HelmException}.
   *
   * @param cause the {@link Throwable} responsible for this {@link
   * HelmException}; may be {@code null}
   */
  protected HelmException(final Throwable cause) {
    super(cause);
  }

  /**
   * Creates a new {@link HelmException}.
   *
   * @param message a descriptive message; may be {@code null}
   *
   * @param cause the {@link Throwable} responsible for this {@link
   * HelmException}; may be {@code null}
   */
  protected HelmException(final String message, final Throwable cause) {
    super(message, cause);
  }
  
}
