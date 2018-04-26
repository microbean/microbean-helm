/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018 microBean.
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

import org.microbean.development.annotation.Issue;

import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;

import org.yaml.snakeyaml.resolver.Resolver;

/**
 * A {@link Resolver} that forces scalars to be {@link String}s.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see <a
 * href="https://github.com/microbean/microbean-helm/issues/131">Issue
 * 131</a>
 */
@Issue(
  id = "131",
  uri = "https://github.com/microbean/microbean-helm/issues/131"
)
public final class StringResolver extends Resolver {


  /*
   * Constructors.
   */

  
  /**
   * Creates a new {@link StringResolver}.
   */
  public StringResolver() {
    super();
  }


  /*
   * Instance methods.
   */
  

  /**
   * Overrides the {@link Resolver#resolve(NodeId, String, boolean)}
   * method so that all implicit scalar non-{@code null} YAML node
   * values are resolved as {@link Tag#STR}.
   *
   * @param kind the kind of YAML node being processed; may be {@code
   * null}
   *
   * @param value the value of the node; may be {@code null}
   *
   * @param implicit whether the typing is implicit or explicit
   *
   * @return a {@link Tag} instance representing the YAML node type
   *
   * @see Resolver#resolve(NodeId, String, boolean)
   */
  @Override
  public final Tag resolve(final NodeId kind, final String value, final boolean implicit) {
    final Tag returnValue;
    if (implicit && kind != null && value != null && NodeId.scalar.equals(kind) && !value.isEmpty()) {
      returnValue = Tag.STR;
    } else {
      returnValue = super.resolve(kind, value, implicit);
    }
    return returnValue;
  }
  
}
    
