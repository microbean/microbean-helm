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
package org.microbean.helm.chart.repository;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.Instant;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.TreeMap;

import java.util.zip.GZIPInputStream;

import com.github.zafarkhaja.semver.ParseException;
import com.github.zafarkhaja.semver.Version;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.MetadataOuterClass.Metadata;
import hapi.chart.MetadataOuterClass.MetadataOrBuilder;

import org.kamranzafar.jtar.TarInputStream;

import org.microbean.development.annotation.Experimental;

import org.microbean.helm.chart.Metadatas;
import org.microbean.helm.chart.TapeArchiveChartLoader;

import org.microbean.helm.chart.resolver.ChartResolver;

import org.yaml.snakeyaml.Yaml;

@Experimental
public class ChartRepository extends ChartResolver {

  private final Path certificateAuthorityPath;

  private final Path archiveCacheDirectory;

  private final Path indexCacheDirectory;
  
  private final Path cachedIndexPath;

  private final Path certificatePath;

  private final Path keyPath;

  private final String name;

  private final URI uri;

  private transient Index index;

  public ChartRepository(final String name, final URI uri, final Path cachedIndexPath, final Path certificateAuthorityPath, final Path certificatePath, final Path keyPath) {
    this(name, uri, null, null, cachedIndexPath, certificateAuthorityPath, certificatePath, keyPath);
  }
  
  public ChartRepository(final String name, final URI uri, final Path archiveCacheDirectory, Path indexCacheDirectory, final Path cachedIndexPath, final Path certificateAuthorityPath, final Path certificatePath, final Path keyPath) {
    super();
    Objects.requireNonNull(name);
    Objects.requireNonNull(uri);
    Objects.requireNonNull(cachedIndexPath);
    
    if (!uri.isAbsolute()) {
      throw new IllegalArgumentException("!uri.isAbsolute(): " + uri);
    }
    
    Path helmHome = null;

    if (archiveCacheDirectory == null) {
      helmHome = getHelmHome();
      assert helmHome != null;
      this.archiveCacheDirectory = helmHome.resolve("cache/archive");
      assert this.archiveCacheDirectory.isAbsolute();
    } else if (archiveCacheDirectory.toString().isEmpty()) {
      throw new IllegalArgumentException("archiveCacheDirectory.toString().isEmpty(): " + archiveCacheDirectory);
    } else if (!archiveCacheDirectory.isAbsolute()) {
      throw new IllegalArgumentException("!archiveCacheDirectory.isAbsolute(): " + archiveCacheDirectory);
    } else {
      this.archiveCacheDirectory = archiveCacheDirectory;
    }
    if (!Files.isDirectory(this.archiveCacheDirectory)) {
      throw new IllegalArgumentException("!Files.isDirectory(this.archiveCacheDirectory): " + this.archiveCacheDirectory);
    }

    if (cachedIndexPath.toString().isEmpty()) {
      throw new IllegalArgumentException("cachedIndexPath.toString().isEmpty(): " + cachedIndexPath);
    }
    this.cachedIndexPath = cachedIndexPath;
    if (cachedIndexPath.isAbsolute()) {
      this.indexCacheDirectory = null;
    } else {
      if (indexCacheDirectory == null) {
        if (helmHome == null) {
          helmHome = getHelmHome();
          assert helmHome != null;
        }
        this.indexCacheDirectory = helmHome.resolve("repository/cache");
        assert this.indexCacheDirectory.isAbsolute();
      } else if (!indexCacheDirectory.isAbsolute()) {
        throw new IllegalArgumentException("!indexCacheDirectory.isAbsolute(): " + indexCacheDirectory);
      } else {
        this.indexCacheDirectory = indexCacheDirectory;
      }
      if (!Files.isDirectory(indexCacheDirectory)) {
        throw new IllegalArgumentException("!Files.isDirectory(indexCacheDirectory): " + indexCacheDirectory);
      }
    }
    
    this.name = name;
    this.uri = uri;
    this.certificateAuthorityPath = certificateAuthorityPath;
    this.certificatePath = certificatePath;
    this.keyPath = keyPath;
  }

  public final String getName() {
    return this.name;
  }

  public final URI getUri() {
    return this.uri;
  }

  public final Path getCachedIndexPath() {
    return this.cachedIndexPath;
  }

  public final Path getCertificateAuthorityPath() {
    return this.certificateAuthorityPath;
  }

  public final Path getCertificatePath() {
    return this.certificatePath;
  }

  public final Path getKeyPath() {
    return this.keyPath;
  }

  public final Index getIndex() throws IOException, URISyntaxException {
    return this.getIndex(false);
  }
  
  public final Index getIndex(final boolean forceDownload) throws IOException, URISyntaxException {
    if (this.index == null) {
      final Path cachedIndexPath = this.getCachedIndexPath();
      assert cachedIndexPath != null;
      if (forceDownload || this.getCachedIndexExpired()) {
        this.downloadIndex(cachedIndexPath);
      }
      this.index = Index.load(cachedIndexPath);
      assert this.index != null;
    }
    return this.index;
  }

  public boolean getCachedIndexExpired() {
    final Path cachedIndexPath = this.getCachedIndexPath();
    assert cachedIndexPath != null;
    return !Files.isRegularFile(cachedIndexPath);
  }

  public final void clearIndex() {
    this.index = null;
  }

  public final Path downloadIndex() throws IOException {
    return this.downloadIndex(this.getCachedIndexPath());
  }
  
  public Path downloadIndex(Path path) throws IOException {
    final URI baseUri = this.getUri();
    if (baseUri == null) {
      throw new IllegalStateException("getUri() == null");
    }
    final URI indexUri = baseUri.resolve("index.yaml");
    assert indexUri != null;
    final URL indexUrl = indexUri.toURL();
    assert indexUrl != null;
    if (path == null) {
      path = this.getCachedIndexPath();
    }
    assert path != null;
    if (!path.isAbsolute()) {
      assert this.indexCacheDirectory != null;
      assert this.indexCacheDirectory.isAbsolute();
      path = this.indexCacheDirectory.resolve(path);
      assert path != null;
      assert path.isAbsolute();
    }
    final Path temporaryPath = Files.createTempFile(new StringBuilder(this.getName()).append("-index-").toString(), ".yaml");
    assert temporaryPath != null;
    try (final BufferedInputStream stream = new BufferedInputStream(indexUrl.openStream())) {
      Files.copy(stream, temporaryPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (final IOException throwMe) {
      try {
        Files.deleteIfExists(temporaryPath);
      } catch (final IOException suppressMe) {
        throwMe.addSuppressed(suppressMe);
      }
      throw throwMe;
    }
    return Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE);
  }

  public Index loadIndex() throws IOException, URISyntaxException {
    Path path = this.getCachedIndexPath();
    assert path != null;
    if (!path.isAbsolute()) {
      assert this.indexCacheDirectory != null;
      assert this.indexCacheDirectory.isAbsolute();
      path = this.indexCacheDirectory.resolve(path);
      assert path != null;
      assert path.isAbsolute();
    }
    return Index.load(this.getCachedIndexPath());
  }

  public final Path getCachedChartPath(final String chartName, String chartVersion) throws IOException, URISyntaxException {
    Objects.requireNonNull(chartName);
    Path returnValue = null;
    if (chartVersion == null) {
      final Index index = this.getIndex(false);
      assert index != null;
      final Index.Entry entry = index.getFirstEntry(chartName);
      if (entry != null) {
        chartVersion = entry.getVersion();
      }
    }
    if (chartVersion != null) {
      assert this.archiveCacheDirectory != null;
      final StringBuilder chartKey = new StringBuilder(chartName).append("-").append(chartVersion);
      final String chartFilename = new StringBuilder(chartKey).append(".tgz").toString();
      final Path cachedChartPath = this.archiveCacheDirectory.resolve(chartFilename);
      assert cachedChartPath != null;
      if (!Files.isRegularFile(cachedChartPath)) {
        final Index index = this.getIndex(true);
        assert index != null;
        final Index.Entry entry = index.getEntry(chartName, chartVersion);
        if (entry != null) {
          final URI chartUri = entry.getFirstUri();
          if (chartUri != null) {
            final URL chartUrl = chartUri.toURL();
            assert chartUrl != null;
            final Path temporaryPath = Files.createTempFile(chartKey.append("-").toString(), ".tgz");
            assert temporaryPath != null;
            try (final InputStream stream = new BufferedInputStream(chartUrl.openStream())) {
              Files.copy(stream, temporaryPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException throwMe) {
              try {
                Files.deleteIfExists(temporaryPath);
              } catch (final IOException suppressMe) {
                throwMe.addSuppressed(suppressMe);
              }
              throw throwMe;
            }
            Files.move(temporaryPath, cachedChartPath, StandardCopyOption.ATOMIC_MOVE);
          }
        }
      }
      returnValue = cachedChartPath;
    }
    return returnValue;
  }

  @Override
  public Chart.Builder resolve(final String chartName, String chartVersion) throws IOException, URISyntaxException {
    Objects.requireNonNull(chartName);
    Chart.Builder returnValue = null;
    final Path cachedChartPath = this.getCachedChartPath(chartName, chartVersion);
    if (cachedChartPath != null && Files.isRegularFile(cachedChartPath)) {
      try (final TapeArchiveChartLoader loader = new TapeArchiveChartLoader()) {
        returnValue = loader.load(new TarInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(cachedChartPath)))));
      }
    }
    return returnValue;
  }

  static final Path getHelmHome() {
    String helmHome = System.getProperty("helm.home", System.getenv("HELM_HOME"));
    if (helmHome == null) {
      helmHome = Paths.get(System.getProperty("user.home")).resolve(".helm").toString();
      assert helmHome != null;
    }
    return Paths.get(helmHome);
  }


  /*
   * Inner and nested classes.
   */

  
  @Experimental
  public static final class Index {

    private final Instant generationInstant;

    private final SortedMap<String, SortedSet<Entry>> entries;
    
    Index() {
      this(null, null);
    }

    Index(final Map<? extends String, ? extends SortedSet<Entry>> entries) {
      this(entries, null);
    }
    
    Index(final Map<? extends String, ? extends SortedSet<Entry>> entries, final Instant generationInstant) {
      super();
      this.generationInstant = generationInstant;
      if (entries == null || entries.isEmpty()) {
        this.entries = Collections.emptySortedMap();
      } else {
        this.entries = Collections.unmodifiableSortedMap(new TreeMap<>(entries));
      }
    }

    public final Map<String, SortedSet<Entry>> getEntries() {
      return this.entries;
    }

    public final Entry getEntry(final String name, final String versionString) {
      Objects.requireNonNull(name);
      Entry returnValue = null;
      final Map<String, SortedSet<Entry>> entries = this.getEntries();
      if (entries != null && !entries.isEmpty()) {
        final SortedSet<Entry> entrySet = entries.get(name);
        if (entrySet != null && !entrySet.isEmpty()) {
          if (versionString == null) {
            returnValue = entrySet.first();
          } else {
            for (final Entry entry : entrySet) {
              // XXX TODO FIXME: probably want to make this a
              // constraint match, not just an equality comparison
              if (entry != null && versionString.equals(entry.getVersion())) {
                returnValue = entry;
                break;
              }
            }
          }
        }
      }
      return returnValue;
    }
    
    public final Entry getFirstEntry(final String name) {
      return getEntry(name, null);
    }

    public final Instant getGenerationInstant() {
      return this.generationInstant;
    }

    public static final Index load(final Path path) throws IOException, URISyntaxException {
      Objects.requireNonNull(path);
      final Index returnValue;
      try (final BufferedInputStream stream = new BufferedInputStream(Files.newInputStream(path))) {
        returnValue = load(stream);
      }
      return returnValue;
    }

    public static final Index load(final InputStream stream) throws IOException, URISyntaxException {
      Objects.requireNonNull(stream);
      final Index returnValue;
      final Map<?, ?> yamlMap = new Yaml().loadAs(stream, Map.class);
      if (yamlMap == null || yamlMap.isEmpty()) {
        returnValue = new Index();
      } else {
        final SortedMap<String, SortedSet<Index.Entry>> sortedEntryMap = new TreeMap<>();
        @SuppressWarnings("unchecked")
          final Map<? extends String, ? extends Collection<? extends Map<?, ?>>> entriesMap = (Map<? extends String, ? extends Collection<? extends Map<?, ?>>>)yamlMap.get("entries");
        if (entriesMap != null && !entriesMap.isEmpty()) {
          final Collection<? extends Map.Entry<? extends String, ? extends Collection<? extends Map<?, ?>>>> entries = entriesMap.entrySet();
          if (entries != null && !entries.isEmpty()) {
            for (final Map.Entry<? extends String, ? extends Collection<? extends Map<?, ?>>> mapEntry : entries) {
              if (mapEntry != null) {
                final String entryName = mapEntry.getKey();
                if (entryName != null) {
                  final Collection<? extends Map<?, ?>> entryContents = mapEntry.getValue();
                  if (entryContents != null && !entryContents.isEmpty()) {
                    for (final Map<?, ?> entryMap : entryContents) {
                      if (entryMap != null && !entryMap.isEmpty()) {
                        final Metadata.Builder metadataBuilder = Metadata.newBuilder();
                        assert metadataBuilder != null;
                        Metadatas.populateMetadataBuilder(metadataBuilder, entryMap);
                        final Date creationDate = (Date)entryMap.get("created");
                        final Instant creationInstant = creationDate == null ? null : creationDate.toInstant();
                        final Date removalDate = (Date)entryMap.get("removed");
                        final Instant removalInstant = removalDate == null ? null : removalDate.toInstant();        
                        final String digest = (String)entryMap.get("digest");
                        @SuppressWarnings("unchecked")
                          final Collection<? extends String> uriStrings = (Collection<? extends String>)entryMap.get("urls");
                        Set<URI> uris = new LinkedHashSet<>();
                        if (uriStrings != null && !uriStrings.isEmpty()) {
                          for (final String uriString : uriStrings) {
                            if (uriString != null && !uriString.isEmpty()) {
                              uris.add(new URI(uriString));
                            }
                          }
                        }
                        SortedSet<Index.Entry> entryObjects = sortedEntryMap.get(entryName);
                        if (entryObjects == null) {
                          entryObjects = new TreeSet<>(Collections.reverseOrder());
                          sortedEntryMap.put(entryName, entryObjects);
                        }
                        entryObjects.add(new Index.Entry(metadataBuilder, uris, creationInstant, removalInstant, digest));
                      }
                    }
                  }
                }
              }
            }
          }      
        }
        final Date generationDate = (Date)yamlMap.get("generated");
        final Instant generationInstant = generationDate == null ? null : generationDate.toInstant();
        returnValue = new Index(sortedEntryMap, generationInstant);
      }
      return returnValue;
    }


    /*
     * Inner and nested classes.
     */

    
    @Experimental
    public static final class Entry implements Comparable<Entry> {

      private final MetadataOrBuilder metadata;

      private final Set<URI> uris;

      private final Instant creationInstant;

      private final Instant removalInstant;

      private final String digest;

      Entry(final MetadataOrBuilder metadata,
            final Collection<? extends URI> uris,
            final Instant creationInstant,
            final Instant removalInstant,
            final String digest) {
        super();
        this.metadata = Objects.requireNonNull(metadata);
        if (uris == null || uris.isEmpty()) {
          this.uris = Collections.emptySet();
        } else {
          this.uris = new LinkedHashSet<>(uris);
        }
        this.creationInstant = creationInstant;
        this.removalInstant = removalInstant;
        this.digest = digest;
      }

      @Override
      public final int compareTo(final Entry her) {
        Objects.requireNonNull(her); // see Comparable documentation
        
        final String myName = this.getName();
        final String herName = her.getName();
        if (myName == null) {
          if (herName != null) {
            return -1;
          }
        } else if (herName == null) {
          return 1;
        } else {
          final int nameComparison = myName.compareTo(herName);
          if (nameComparison != 0) {
            return nameComparison;
          }
        }
        
        final String myVersionString = this.getVersion();
        final String herVersionString = her.getVersion();
        if (myVersionString == null) {
          if (herVersionString != null) {
            return -1;
          }
        } else if (herVersionString == null) {
          return 1;
        } else {
          Version myVersion = null;
          try {
            myVersion = Version.valueOf(myVersionString);
          } catch (final IllegalArgumentException | ParseException badVersion) {
            myVersion = null;
          }
          Version herVersion = null;
          try {
            herVersion = Version.valueOf(herVersionString);
          } catch (final IllegalArgumentException | ParseException badVersion) {
            herVersion = null;
          }
          if (myVersion == null) {
            if (herVersion != null) {
              return -1;
            }
          } else if (herVersion == null) {
            return 1;
          } else {
            return myVersion.compareTo(herVersion);
          }
        }

        return 0;
      }

      @Override
      public final int hashCode() {
        int hashCode = 17;

        final Object name = this.getName();
        int c = name == null ? 0 : name.hashCode();
        hashCode = 37 * hashCode + c;

        final Object version = this.getVersion();
        c = version == null ? 0 : version.hashCode();
        hashCode = 37 * hashCode + c;

        return hashCode;
      }

      @Override
      public final boolean equals(final Object other) {
        if (other == this) {
          return true;
        } else if (other instanceof Entry) {
          final Entry her = (Entry)other;

          final Object myName = this.getName();
          if (myName == null) {
            if (her.getName() != null) {
              return false;
            }
          } else if (!myName.equals(her.getName())) {
            return false;
          }

          final Object myVersion = this.getVersion();
          if (myVersion == null) {
            if (her.getVersion() != null) {
              return false;
            }
          } else if (!myVersion.equals(her.getVersion())) {
            return false;
          }

          return true;
        } else {
          return false;
        }
      }
      
      public final MetadataOrBuilder getMetadataOrBuilder() {
        return this.metadata;
      }

      public final String getName() {
        final MetadataOrBuilder metadata = this.getMetadataOrBuilder();
        assert metadata != null;
        return metadata.getName();
      }

      public final String getVersion() {
        final MetadataOrBuilder metadata = this.getMetadataOrBuilder();
        assert metadata != null;
        return metadata.getVersion();
      }

      public final Set<URI> getUris() {
        return this.uris;
      }

      public final URI getFirstUri() {
        final Set<URI> uris = this.getUris();
        final URI returnValue;
        if (uris == null || uris.isEmpty()) {
          returnValue = null;
        } else {
          final Iterator<URI> iterator = uris.iterator();
          if (iterator == null || !iterator.hasNext()) {
            returnValue = null;
          } else {
            returnValue = iterator.next();
          }
        }
        return returnValue;
      }

      public final Instant getCreationInstant() {
        return this.creationInstant;
      }

      public final Instant getRemovalInstant() {
        return this.removalInstant;
      }

      public final String getDigest() {
        return this.digest;
      }

      @Override
      public final String toString() {
        String name = this.getName();
        if (name == null || name.isEmpty()) {
          name = "unnamed";
        }
        return new StringBuilder(name).append(" ").append(this.getVersion()).toString();
      }
      
    }
    
  }
  
}
