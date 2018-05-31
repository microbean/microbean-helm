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
package org.microbean.helm.chart.repository;

import java.io.IOException;

import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Set;

/**
 * Represents the directory structure that <a href="">{@code helm}</a>
 * uses for local storage of various artifacts.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
final class HelmHome {

  
  /*
   * Static fields.
   */

  
  /**
   * A {@link FileAttribute} representing the POSIX {@code 0755} file
   * permissions.
   *
   * <p>This field is never {@code null}.</p>
   */
  private static final FileAttribute<Set<PosixFilePermission>> permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x"));


  /*
   * Instance fields.
   */
  

  /**
   * The root of the {@code helm} home hierarchy, represented as a
   * {@link Path}.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #toPath()
   */
  private final Path path;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link HelmHome} by invoking the {@link
   * #HelmHome(Path)} constructor with {@code null} as its sole
   * argument.
   *
   * @see #HelmHome(Path)
   */
  HelmHome() {
    this(null);
  }

  /**
   * Creates a new {@link HelmHome}.
   *
   * @param helmHomePath a {@link Path} representing the root of the
   * directory structure; may be {@code null} in which case the {@code
   * helm.home} System property and then the {@code HELM_HOME}
   * environment variable will be used instead&mdash;if these are
   * {@code null} then a value comprised of the {@code user.home}
   * System property value and {@code .helm} will be used
   *
   * @see #toPath()
   */
  HelmHome(final Path helmHomePath) {
    super();
    if (helmHomePath == null) {
      String helmHome = System.getProperty("helm.home", System.getenv("HELM_HOME"));
      if (helmHome == null) {
        helmHome = Paths.get(System.getProperty("user.home")).resolve(".helm").toString();
        assert helmHome != null;
      }
      this.path = Paths.get(helmHome);
    } else {
      this.path = helmHomePath;
    }
  }

  /**
   * Returns a {@link Path} representing the root of the directory
   * structure represented by this {@link HelmHome}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return a {@link Path} representing the root of the directory
   * structure represented by this {@link HelmHome}; never {@code
   * null}
   *
   * @see #HelmHome(Path)
   */
  final Path toPath() {
    return this.path;
  }

  /**
   * Attempts to create the Helm home directory structure.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Following {@code helm} itself, all directories are created
   * with {@code 0755} POSIX permissions.</p>
   *
   * @return the root of the directory structure; never {@code null}
   *
   * @exception IOException if an input/output error occurs,
   * particularly if the current user lacks permission to create
   * directories
   */
  final Path reify() throws IOException {
    
    final Path helmHome = Files.createDirectories(toPath(), permissions);
    assert helmHome != null;

    final Path cache = helmHome.resolve("cache");
    assert cache != null;
    try {
      Files.createDirectory(cache, permissions);
    } catch (final FileAlreadyExistsException thatsFine) {
      if (!Files.isDirectory(cache)) {
        throw thatsFine;
      }
    }

    final Path cacheArchive = cache.resolve("archive");
    assert cacheArchive != null;
    try {
      Files.createDirectory(cacheArchive, permissions);
    } catch (final FileAlreadyExistsException thatsFine) {
      if (!Files.isDirectory(cacheArchive)) {
        throw thatsFine;
      }
    }

    final Path plugins = helmHome.resolve("plugins");
    assert plugins != null;
    try {
      Files.createDirectory(plugins, permissions);
    } catch (final FileAlreadyExistsException thatsFine) {
      if (!Files.isDirectory(plugins)) {
        throw thatsFine;
      }
    }

    final Path repository = helmHome.resolve("repository");
    assert repository != null;
    try {
      Files.createDirectory(repository, permissions);
    } catch (final FileAlreadyExistsException thatsFine) {
      if (!Files.isDirectory(repository)) {
        throw thatsFine;
      }
    }
    
    final Path repositoryCache = repository.resolve("cache");
    assert repositoryCache != null;
    try {
      Files.createDirectory(repositoryCache, permissions);
    } catch (final FileAlreadyExistsException thatsFine) {
      if (!Files.isDirectory(repositoryCache)) {
        throw thatsFine;
      }
    }

    final Path repositoryLocal = repository.resolve("local");
    assert repositoryLocal != null;
    try {
      Files.createDirectory(repositoryLocal, permissions);
    } catch (final FileAlreadyExistsException thatsFine) {
      if (!Files.isDirectory(repositoryLocal)) {
        throw thatsFine;
      }
    }

    final Path starters = helmHome.resolve("starters");
    assert starters != null;
    try {
      Files.createDirectory(starters, permissions);
    } catch (final FileAlreadyExistsException thatsFine) {
      if (!Files.isDirectory(starters)) {
        throw thatsFine;
      }
    }

    return helmHome;
  }
  
}
