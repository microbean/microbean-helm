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
package org.microbean.helm.chart;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.LinkOption; // for javadoc only
import java.nio.file.Path;

import java.nio.file.PathMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import java.util.function.Predicate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import java.util.stream.Collectors;

/**
 * A {@link PathMatcher} and a {@link Predicate Predicate&lt;Path&gt;}
 * that {@linkplain #matches(Path) matches} paths using the syntax of
 * a {@code .helmignore} file.
 *
 * <p>This class passes <a
 * href="https://github.com/kubernetes/helm/blob/v2.5.0/pkg/ignore/rules_test.go#L91-L121">all
 * of the unit tests present</a> in the <a
 * href="http://godoc.org/k8s.io/helm/pkg/ignore">Helm project's
 * package concerned with {@code .helmignore} files</a>.  It may
 * permit richer syntax, but there are no guarantees made regarding
 * the behavior of this class in such cases.</p>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is safe for concurrent use by multiple threads.</p>
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see <a href="http://godoc.org/k8s.io/helm/pkg/ignore">The Helm
 * project's package concerned with {@code .helmignore} files</a>
 */
public class HelmIgnorePathMatcher implements PathMatcher, Predicate<Path> {


  /*
   * Instance fields.
   */


  /**
   * A {@link Collection} of {@link Predicate Predicate&lt;Path&gt;}s,
   * one of which must {@linkplain #matches(Path) match} for the
   * {@link #matches(Path)} method to return {@code true}.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #addPatterns(Collection)
   */
  private final Collection<Predicate<Path>> rules;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link HelmIgnorePathMatcher}.
   */
  public HelmIgnorePathMatcher() {
    super();
    this.rules = new ArrayList<>();
    this.addPattern("templates/.?*");
  }

  /**
   * Creates a new {@link HelmIgnorePathMatcher}.
   *
   * @param stringPatterns a {@link Collection} of <a
   * href="http://godoc.org/k8s.io/helm/pkg/ignore">valid {@code
   * .helmignore} patterns</a>; may be {@code null}
   *
   * @exception PatternSyntaxException if any of the patterns is
   * invalid
   */
  public HelmIgnorePathMatcher(final Collection<? extends String> stringPatterns) {
    this();
    this.addPatterns(stringPatterns);
  }

  /**
   * Creates a new {@link HelmIgnorePathMatcher}.
   *
   * @param reader a {@link Reader} expected to provide access to a
   * logical collection of lines of text, each line of which is a <a
   * href="http://godoc.org/k8s.io/helm/pkg/ignore">valid {@code
   * .helmignore} pattern</a> (or blank line, or comment); may be
   * {@code null}; never {@linkplain Reader#close() closed}
   *
   * @exception IOException if an error related to the supplied {@code
   * reader} is encountered
   *
   * @exception PatternSyntaxException if any of the patterns is
   * invalid
   */
  public HelmIgnorePathMatcher(final Reader reader) throws IOException {
    this();
    if (reader != null) {
      final BufferedReader bufferedReader;
      if (reader instanceof BufferedReader) {
        bufferedReader = (BufferedReader)reader;
      } else {
        bufferedReader = new BufferedReader(reader);
      }
      assert bufferedReader != null;
      this.addPatterns(bufferedReader.lines().collect(Collectors.toList()));
    }
  }

  /**
   * Creates a new {@link HelmIgnorePathMatcher}.
   *
   * @param helmIgnoreFile a {@link Path} expected to provide access
   * to a logical collection of lines of text, each line of which is a
   * <a href="http://godoc.org/k8s.io/helm/pkg/ignore">valid {@code
   * .helmignore} pattern</a> (or blank line, or comment); may be
   * {@code null}; never {@linkplain Reader#close() closed}
   *
   * @exception IOException if an error related to the supplied {@code
   * helmIgnoreFile} is encountered
   *
   * @exception PatternSyntaxException if any of the patterns is
   * invalid
   *
   * @see #HelmIgnorePathMatcher(Reader)
   */
  public HelmIgnorePathMatcher(final Path helmIgnoreFile) throws IOException {
    this(helmIgnoreFile == null ? (Collection<? extends String>)null : Files.readAllLines(helmIgnoreFile, StandardCharsets.UTF_8));
  }


  /*
   * Instance methods.
   */


  /**
   * Calls the {@link #addPatterns(Collection)} method with a
   * {@linkplain Collections#singleton(Object) singleton
   * <code>Set</code>} consisting of the supplied {@code
   * stringPattern}.
   *
   * @param stringPattern a <a
   * href="http://godoc.org/k8s.io/helm/pkg/ignore">valid {@code
   * .helmignore} pattern</a>; may be {@code null} or {@linkplain
   * String#isEmpty() empty} or prefixed with a {@code #} character,
   * in which case no action will be taken
   *
   * @see #addPatterns(Collection)
   *
   * @see #matches(Path)
   */
  public final void addPattern(final String stringPattern) {
    this.addPatterns(stringPattern == null ? (Collection<? extends String>)null : Collections.singleton(stringPattern));
  }

  /**
   * Adds all of the <a
   * href="http://godoc.org/k8s.io/helm/pkg/ignore">valid {@code
   * .helmignore} patterns</a> present in the supplied {@link
   * Collection} of such patterns.
   *
   * <p>Overrides must not call {@link #addPattern(String)}.</p>
   *
   * @param stringPatterns a {@link Collection} of <a
   * href="http://godoc.org/k8s.io/helm/pkg/ignore">valid {@code
   * .helmignore} patterns</a>; may be {@code null} in which case no
   * action will be taken
   *
   * @see #matches(Path)
   */
  public void addPatterns(final Collection<? extends String> stringPatterns) {    
    if (stringPatterns != null && !stringPatterns.isEmpty()) {
      for (String stringPattern : stringPatterns) {
        if (stringPattern != null && !stringPattern.isEmpty()) {
          stringPattern = stringPattern.trim();
          if (!stringPattern.isEmpty() && !stringPattern.startsWith("#")) {

            if (stringPattern.equals("!") || stringPattern.equals("/")) {
              throw new IllegalArgumentException("invalid pattern: " + stringPattern);
            } else if (stringPattern.contains("**")) {
              throw new IllegalArgumentException("invalid pattern: " + stringPattern + " (double-star (**) syntax is not supported)"); // see rules.go
            }

            final boolean negate;
            if (stringPattern.startsWith("!")) {
              assert stringPattern.length() > 1;
              negate = true;
              stringPattern = stringPattern.substring(1);
            } else {
              negate = false;
            }

            final boolean requireDirectory;
            if (stringPattern.endsWith("/")) {
              assert stringPattern.length() > 1;
              requireDirectory = true;
              stringPattern = stringPattern.substring(0, stringPattern.length() - 1);
            } else {
              requireDirectory = false;
            }

            final boolean basename;
            final int firstSlashIndex = stringPattern.indexOf('/');
            if (firstSlashIndex < 0) {
              basename = true;
            } else {
              if (firstSlashIndex == 0) {
                assert stringPattern.length() > 1;
                stringPattern = stringPattern.substring(1);
              }
              basename = false;
            }

            final StringBuilder regex = new StringBuilder("^");
            final char[] chars = stringPattern.toCharArray();
            assert chars != null;
            assert chars.length > 0;
            final int length = chars.length;
            for (int i = 0; i < length; i++) {
              final char c = chars[i];
              switch (c) {
              case '.':
                regex.append("\\.");
                break;
              case '*':
        	  regex.append("[^").append(File.separator).append(File.separator).append("]*");
        	  break;
              case '?':
        	  regex.append("[^").append(File.separator).append(File.separator).append("]?");
        	  break;
              default:
                regex.append(c);
                break;
              }
            }
            regex.append("$");

            final Predicate<Path> rule = new RegexRule(Pattern.compile(regex.toString()), requireDirectory, basename);
            synchronized (this.rules) {
              this.rules.add(negate ? rule.negate() : rule);
            }
          }
        }
      }
    }
  }

  /**
   * Calls the {@link #matches(Path)} method with the supplied {@link
   * Path} and returns its results.
   *
   * @param path a {@link Path} to test; may be {@code null}
   *
   * @return {@code true} if the supplied {@code path} matches; {@code
   * false} otherwise
   *
   * @see #matches(Path)
   */
  @Override
  public final boolean test(final Path path) {
    return this.matches(path);
  }

  /**
   * Returns {@code true} if the supplied {@link Path} is neither
   * {@code null}, the empty path ({@code ""}) nor the "current
   * directory" path ("{@code .}" or "{@code ./}"), and if at least
   * one of the patterns added via the {@link
   * #addPatterns(Collection)} method logically matches it.
   *
   * @param path the {@link Path} to match; may be {@code null} in
   * which case {@code false} will be returned
   *
   * @return {@code true} if at least one of the patterns added via
   * the {@link #addPatterns(Collection)} method logically matches the
   * supplied {@link Path}; {@code false} otherwise
   */
  @Override
  public boolean matches(final Path path) {
    boolean returnValue = false;
    if (path != null) {
      final String pathString = path.toString();
      // See https://github.com/kubernetes/helm/issues/1776 and
      // https://github.com/kubernetes/helm/pull/3114
      if (!pathString.isEmpty() && !pathString.equals(".") && !pathString.equals("./")) {
        synchronized (this.rules) {
          for (final Predicate<Path> rule : this.rules) {
            if (rule != null && rule.test(path)) {
              returnValue = true;
              break;
            }
          }
        }
      }
    }
    return returnValue;
  }


  /*
   * Inner and nested classes.
   */
  

  /**
   * A {@link Predicate Predicate&lt;Path&gt;} that may also apply
   * {@link Path}-specific tests.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   */
  private static abstract class Rule implements Predicate<Path> {


    /*
     * Instance fields.
     */


    /**
     * Whether a {@link Path} must {@linkplain Files#isDirectory(Path,
     * LinkOption...)  be a directory} in order for this {@link Rule}
     * to match.
     */
    private final boolean requireDirectory;

    /**
     * Whether the {@linkplain Path#getFileName() final component in a
     * <code>Path</code>} is matched, or the entire {@link Path}.
     */
    private final boolean basename;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link Rule}.
     *
     * @param requireDirectory whether a {@link Path} must {@linkplain
     * Files#isDirectory(Path, LinkOption...) be a directory} in order
     * for this {@link Rule} to match
     *
     * @param basename hhether the {@linkplain Path#getFileName()
     * final component in a <code>Path</code>} is matched, or the
     * entire {@link Path}
     */
    protected Rule(final boolean requireDirectory, final boolean basename) {
      super();
      this.requireDirectory = requireDirectory;
      this.basename = basename;
    }

    /**
     * Returns a {@link Path} that can be tested, given a {@link Path}
     * and the application of the {@code requireDirectory} and {@code
     * basename} parameters passed to the constructor.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @param path the {@link Path} to normalize; may be {@code null}
     * in which case {@code null} will be returned
     *
     * @return a {@link Path} to be further tested; or {@code null}
     */
    protected final Path normalizePath(final Path path) {
      Path returnValue = path;
      if (path != null) {
        if (this.basename) {
          returnValue = path.getFileName();
        }
        if (this.requireDirectory && !Files.isDirectory(path)) {
          returnValue = null;
        }
      }
      return returnValue;
    }

  }

  /**
   * A {@link Rule} that uses regular expressions to match {@link Path}s.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   *
   * @see Pattern
   */
  private static final class RegexRule extends Rule {


    /*
     * Instance fields.
     */


    /**
     * The {@link Pattern} specifying what {@link Path} instances
     * should be matched.
     *
     * <p>This field may be {@code null}.</p>
     */
    private final Pattern pattern;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link RegexRule}.
     *
     * @param pattern the {@link Pattern} specifying what {@link Path}
     * instances should be matched; may be {@code null}
     *
     * @param requireDirectory whether only {@link Path} instances
     * that {@linkplain Files#isDirectory(Path, LinkOption...) are
     * directories} are subject to further matching
     *
     * @param basename whether only {@linkplain Path#getFileName() the
     * last component of a <code>Path</code>} is considered for
     * matching
     *
     * @see #test(Path)
     */
    private RegexRule(final Pattern pattern, final boolean requireDirectory, final boolean basename) {
      super(requireDirectory, basename);
      this.pattern = pattern;
    }


    /*
     * Instance methods.
     */


    /**
     * Tests the supplied {@link Path} to see if it matches the
     * conditions supplied at construction time.
     *
     * @param path the {@link Path} to test; may be {@code null} in
     * which case {@code false} will be returned
     *
     * @return {@code true} if this {@link RegexRule} matches the
     * supplied {@link Path}; {@code false} otherwise
     */
    @Override
    public final boolean test(Path path) {
      boolean returnValue = false;
      path = this.normalizePath(path);
      if (path != null) {
        if (this.pattern == null) {
          returnValue = true;
        } else {
          final Matcher matcher = this.pattern.matcher(path.toString());
          assert matcher != null;
          returnValue = matcher.matches();
        }
      }
      return returnValue;
    }
  }
  
}
