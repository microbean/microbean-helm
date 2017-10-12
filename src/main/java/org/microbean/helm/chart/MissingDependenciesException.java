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

import java.io.Serializable;

import java.util.Collection;
import java.util.Collections;

import org.microbean.helm.chart.Requirements.Dependency;

/**
 * A {@link ChartException} indicating that a Helm chart contained a
 * {@code requirements.yaml} resource, but did not contain at least
 * one subchart referenced by that resource.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
public class MissingDependenciesException extends ChartException {


  /*
   * Static fields.
   */


  /**
   * The platform's line separator.
   */
  private static final String LS = System.getProperty("line.separator", "\n");

  /**
   * The version of this class for {@linkplain Serializable
   * serialization purposes}.
   */
  private static final long serialVersionUID = 1L;


  /*
   * Instance fields.
   */


  /**
   * The {@link Dependency} instances representing the entries within
   * a {@code requirements.yaml} resource that did not select any
   * subcharts.
   *
   * <p>This field will never be {@code null}.</p>
   *
   * @see #getMissingDependencies()
   */
  private final Collection<? extends Dependency> missingDependencies;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link MissingDependenciesException}.
   */
  public MissingDependenciesException() {
    super();
    this.missingDependencies = Collections.emptySet();
  }

  /**
   * Creates a new {@link MissingDependenciesException}.
   *
   * @param missingDependencies a {@link Collection} of {@link
   * Dependency} instances identifying subcharts that were not found;
   * may be {@code null}
   *
   * @see #getMissingDependencies()
   */
  public MissingDependenciesException(final Collection<? extends Dependency> missingDependencies) {
    super(createMessage(missingDependencies));
    if (missingDependencies == null) {
      this.missingDependencies = Collections.emptySet();
    } else {
      this.missingDependencies = Collections.unmodifiableCollection(missingDependencies);
    }
  }

  /**
   * Creates a new {@link MissingDependenciesException}.
   *
   * @param message a descriptive message; may be {@code null}
   */
  public MissingDependenciesException(final String message) {
    super(message);
    this.missingDependencies = Collections.emptySet();
  }

  /**
   * Creates a new {@link MissingDependenciesException}.
   *
   * @param cause the {@link Throwable} responsible for this {@link
   * MissingDependenciesException}; may be {@code null}
   */
  public MissingDependenciesException(final Throwable cause) {
    super(cause);
    this.missingDependencies = Collections.emptySet();
  }

  /**
   * Creates a new {@link MissingDependenciesException}.
   *
   * @param message a descriptive message; may be {@code null}
   *
   * @param cause the {@link Throwable} responsible for this {@link
   * MissingDependenciesException}; may be {@code null}
   */
  public MissingDependenciesException(final String message, final Throwable cause) {
    super(message, cause);
    this.missingDependencies = Collections.emptySet();
  }


  /*
   * Instance methods.
   */


  /**
   * Returns a {@link Collection} of {@link Dependency} instances that
   * represent entries found in a {@code requirements.yaml} resource
   * that identify subcharts that were not present.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return a non-{@code null} {@link Collection} of {@link
   * Dependency} instances that represent entries found in a {@code
   * requirements.yaml} resource that identify subcharts that were not
   * present
   *
   * @see #MissingDependenciesException(Collection)
   */
  public final Collection<? extends Dependency> getMissingDependencies() {
    return this.missingDependencies;
  }


  /*
   * Static methods.
   */


  /**
   * Creates a {@link String} representation of the supplied {@link
   * Collection} of {@link Dependency} instances.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @param dependencies the {@link Collection} to represent; may be
   * {@code null}
   *
   * @return a {@link String} representation of the supplied {@code
   * dependencies}, or {@code null}
   */
  private static final String createMessage(final Collection<? extends Dependency> dependencies) {
    String returnValue = null;
    if (dependencies != null && !dependencies.isEmpty()) {
      final StringBuilder sb = new StringBuilder();
      for (final Dependency dependency : dependencies) {
        if (dependency != null) {
          sb.append(dependency).append(LS);
        }
      }
      returnValue = sb.toString();
    }
    return returnValue;
  }
  
}
