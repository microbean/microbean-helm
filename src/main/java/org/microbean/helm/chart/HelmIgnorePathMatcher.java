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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;

import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.file.PathMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import java.util.function.Predicate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.stream.Collectors;

public class HelmIgnorePathMatcher implements PathMatcher, Predicate<Path> {

  private final Collection<Rule> rules;
  
  public HelmIgnorePathMatcher() {
    super();
    this.rules = new ArrayList<>();
  }

  public HelmIgnorePathMatcher(final Collection<? extends String> stringPatterns) {
    this();
    this.addPatterns(stringPatterns);
  }

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
      final Collection<String> lines = new ArrayList<>();
      String line = null;
      while ((line = bufferedReader.readLine()) != null) {
        lines.add(line);
      }
      if (!lines.isEmpty()) {
        this.addPatterns(lines);
      }
    }
  }
  
  public HelmIgnorePathMatcher(final Path helmIgnoreFile) throws IOException {
    this();
    if (helmIgnoreFile != null) {
      final Collection<? extends String> lines = Files.lines(helmIgnoreFile).collect(Collectors.toList());
      if (lines != null && !lines.isEmpty()) {
        this.addPatterns(lines);
      }
    }
  }

  public void addPattern(final String stringPattern) {
    this.addPatterns(Collections.singleton(stringPattern));
  }
  
  public void addPatterns(final Collection<? extends String> stringPatterns) {
    if (stringPatterns != null && !stringPatterns.isEmpty()) {
      for (String stringPattern : stringPatterns) {
        if (stringPattern != null && !stringPattern.isEmpty()) {
          stringPattern = stringPattern.trim();
          if (!stringPattern.isEmpty() && !stringPattern.startsWith("#")) {
            if (stringPattern.contains("**")) {
              throw new IllegalArgumentException("double-star (**) syntax is not supported"); // see rules.go
            }

            final boolean negate;
            if (stringPattern.startsWith("!")) {
              if (stringPattern.length() <= 1) {
                throw new IllegalArgumentException(stringPattern);
              }
              negate = true;
              stringPattern = stringPattern.substring(1);
            } else {
              negate = false;
            }

            final boolean requireDirectory;
            if (stringPattern.endsWith("/")) {
              if (stringPattern.length() <= 1) {
                throw new IllegalArgumentException(stringPattern);
              }
              stringPattern = stringPattern.substring(0, stringPattern.length() - 1);
              requireDirectory = true;
            } else {
              requireDirectory = false;
            }

            final boolean basename;
            final int slashIndex = stringPattern.indexOf('/');
            if (slashIndex == 0) {
              if (stringPattern.length() <= 1) {
                stringPattern = "";
              } else {
                stringPattern = stringPattern.substring(1);
              }
              basename = false;
            } else if (slashIndex > 0) {
              basename = false;
            } else {
              basename = true;
            }

            final StringBuilder regex = new StringBuilder();
            // regex.append("^"); // From Go's Filepath.Match: "Match requires pattern to match all of name, not just a substring."

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
                // "matches any sequence of non-Separator characters"
                regex.append("[^").append(File.separator).append("]*");
                break;
              case '?':
                regex.append("[^").append(File.separator).append("]");
                break;
              case '\\':
                if (i + 1 == length) {
                  throw new IllegalArgumentException(stringPattern);
                }
                regex.append(String.valueOf(chars[++i]));
                break;
              default:
                regex.append(String.valueOf(c));
                break;
              }
            }

            // regex.append("$"); // From Go's Filepath.Match: "Match requires pattern to match all of name, not just a substring."
            final Pattern pattern = Pattern.compile(regex.toString());
            this.rules.add(new Rule(pattern, negate, requireDirectory, basename));
          }
        }
      }
    }
  }
  
  @Override
  public boolean test(final Path path) {
    return this.matches(path);
  }
  
  @Override
  public boolean matches(final Path path) {
    if (path != null) {
      // https://github.com/kubernetes/helm/issues/1776
      final String pathString = path.toString();
      if (pathString.equals(".") || pathString.equals("./")) {
        return false;
      }
    }
    for (final Rule rule : this.rules) {
      if (rule != null && rule.test(path)) {
        return true;
      }
    }
    return false;
  }
  
  private static final class Rule implements Predicate<Path> {

    private final Pattern pattern;

    private final boolean negate;

    private final boolean requireDirectory;

    private final boolean basename;
    
    private Rule(final Pattern pattern, final boolean negate, final boolean requireDirectory, final boolean basename) {
      super();
      this.pattern = pattern;
      this.negate = negate;
      this.requireDirectory = requireDirectory;
      this.basename = basename;
    }

    @Override
    public boolean test(Path path) {
      boolean returnValue = false;
      if (path != null) {
        if (this.basename) {
          path = path.getFileName();
        }
        
        if (!this.requireDirectory || Files.isDirectory(path)) {
          if (this.pattern == null) {
            returnValue = true;
          } else {
            final String pathString = path.toString();
            assert pathString != null;
            final Matcher matcher = this.pattern.matcher(pathString);
            assert matcher != null;
            returnValue = matcher.matches();
          }
        }
      }
      if (this.negate) {
        returnValue = !returnValue;
      }
      return returnValue;
    }
    
  }
  
}
