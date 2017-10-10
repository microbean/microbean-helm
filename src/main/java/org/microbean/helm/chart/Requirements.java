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

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.zafarkhaja.semver.Parser;
import com.github.zafarkhaja.semver.Version;

import com.github.zafarkhaja.semver.expr.Expression;
import com.github.zafarkhaja.semver.expr.ExpressionParser;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ChartOuterClass.ChartOrBuilder;
import hapi.chart.ConfigOuterClass.Config;
import hapi.chart.ConfigOuterClass.ConfigOrBuilder;
import hapi.chart.MetadataOuterClass.Metadata;
import hapi.chart.MetadataOuterClass.MetadataOrBuilder;

import org.yaml.snakeyaml.Yaml;

/*
 * TODO: tests tests tests
 *
 * If you call toBuilder() on a Chart, and then say, for example,
 * getDependenciesBuilderList(), do you get subchart Chart.Builders
 * for each subchart?
 */

public final class Requirements {

  private static final Pattern commaSplitPattern = Pattern.compile("\\s*,\\s*");
  
  private Map<String, Dependency> dependenciesByName;

  public Requirements() {
    super();
  }

  public final boolean isEmpty() {
    return this.dependenciesByName == null || this.dependenciesByName.isEmpty();
  }

  public Collection<Dependency> getDependencies() {
    final Collection<Dependency> returnValue;
    if (this.dependenciesByName == null) {
      returnValue = null;
    } else {
      returnValue = this.dependenciesByName.values();
    }
    return returnValue;
  }

  public void setDependencies(final Collection<Dependency> dependencies) {
    if (dependencies == null) {
      this.dependenciesByName = null;
    } else if (dependencies.isEmpty()) {
      this.dependenciesByName = Collections.emptyMap();
    } else {
      this.dependenciesByName = new HashMap<>();
      for (final Dependency dependency : dependencies) {
        if (dependency != null) {
          final String name = dependency.getName();
          if (name != null) {
            this.dependenciesByName.put(name, dependency);
          }
        }
      }
    }
  }

  public Dependency getDependency(final String name) {
    final Dependency dependency;
    if (this.dependenciesByName == null) {
      dependency = null;
    } else {
      dependency = this.dependenciesByName.get(name);
    }
    return dependency;
  }

  static final Chart.Builder processImportValues(final Chart.Builder c) {
    Objects.requireNonNull(c);
    final List<? extends Chart.Builder> flattenedCharts = Charts.flatten(c);
    if (flattenedCharts != null) {
      assert !flattenedCharts.isEmpty();
      Collections.reverse(flattenedCharts);
      for (final Chart.Builder chart : flattenedCharts) {
        if (chart != null) {
          processSingleChartImportValues(chart);
        }
      }
    }
    return c;
  }
  
  // Ported from requirements.go processImportValues().
  private static final Chart.Builder processSingleChartImportValues(final Chart.Builder c) {
    Objects.requireNonNull(c);

    Chart.Builder returnValue = null;

    final Map<String, Object> canonicalValues = Configs.toDefaultValuesMap(c);
    
    Map<String, Object> b = new HashMap<>();
    final Requirements requirements = fromChartOrBuilder(c);
    if (requirements != null) {
      final Collection<Dependency> dependencies = requirements.getDependencies();
      if (dependencies != null && !dependencies.isEmpty()) {
        for (final Dependency dependency : dependencies) {
          if (dependency != null) {
            
            final String dependencyName = dependency.getName();
            if (dependencyName == null) {
              throw new IllegalStateException();
            }

            final Collection<?> importValues = dependency.getImportValues();
            if (importValues != null && !importValues.isEmpty()) {

              final Collection<Object> newImportValues = new ArrayList<>(importValues.size());

              for (final Object importValue : importValues) {
                final String s;
                
                if (importValue instanceof Map) {
                  @SuppressWarnings("unchecked")
                  final Map<String, String> importValueMap = (Map<String, String>)importValue;
                  
                  final String importValueChild = importValueMap.get("child");
                  final String importValueParent = importValueMap.get("parent");

                  final Map<String, String> newMap = new HashMap<>();
                  newMap.put("child", importValueChild);
                  newMap.put("parent", importValueParent);
                  
                  newImportValues.add(newMap);

                  final Map<String, Object> vv =
                    MapTree.newMapChain(importValueParent,
                                        getMap(canonicalValues,
                                               dependencyName + "." + importValueChild));
                  b = Values.coalesceMaps(vv, canonicalValues);
                  // OK
                  
                } else if (importValue instanceof String) {
                  final String importValueString = (String)importValue;
                  
                  final String importValueChild = "exports." + importValueString;
                  
                  final Map<String, String> newMap = new HashMap<>();
                  newMap.put("child", importValueChild);
                  newMap.put("parent", ".");
                  
                  newImportValues.add(newMap);
                  
                  b = Values.coalesceMaps(getMap(canonicalValues, dependencyName + "." + importValueChild), b);
                  // OK
                  
                }
              }
              dependency.setImportValues(newImportValues);            
            }
          }
        }
      }
    }
    b = Values.coalesceMaps(canonicalValues, b);
    assert b != null;
    final String yaml = new Yaml().dump(b);
    assert yaml != null;
    final Config.Builder configBuilder = c.getValuesBuilder();
    assert configBuilder != null;
    configBuilder.setRaw(yaml);
    returnValue = c;
    assert returnValue != null;
    return returnValue;
  }
  
  // Ported slavishly from ProcessRequirementsTags
  final void processTags(final Map<String, Object> values) {
    final Collection<Dependency> dependencies = this.getDependencies();
    if (dependencies != null && !dependencies.isEmpty()) {
      for (final Dependency dependency : dependencies) {
        if (dependency != null) {
          dependency.processTags(values);
        }
      }
    }
    
  }

  // Ported from ProcessRequirementsConditions
  final void processConditions(final Map<String, Object> values) {
    final Collection<Dependency> dependencies = this.getDependencies();
    if (dependencies != null && !dependencies.isEmpty()) {
      for (final Dependency dependency : dependencies) {
        if (dependency != null) {
          dependency.processConditions(values);
        }
      }
    }
  }

  // ported from Table() in chartutil/values.go
  private static final Map<String, Object> getMap(Map<String, Object> map, final String dotSeparatedPath) {
    final Map<String, Object> returnValue;
    if (map == null || dotSeparatedPath == null || dotSeparatedPath.isEmpty() || map.isEmpty()) {
      returnValue = null;
    } else {
      returnValue = new MapTree(map).getMap(dotSeparatedPath);
    }
    return returnValue;
  }

  // Ported from LoadRequirements() in chartutil/requirements.go
  public static final Requirements fromChartOrBuilder(final ChartOrBuilder chart) {
    Requirements returnValue = null;
    if (chart != null) {
      final Collection<? extends Any> files = chart.getFilesList();
      if (files != null && !files.isEmpty()) {
        final Yaml yaml = new Yaml();
        for (final Any file : files) {
          if (file != null && "requirements.yaml".equals(file.getTypeUrl())) {
            final ByteString fileContents = file.getValue();
            if (fileContents != null) {
              final String yamlString = fileContents.toStringUtf8();
              if (yamlString != null) {
                returnValue = yaml.loadAs(yamlString, Requirements.class);
              }
            }
          }
        }
      }
    }
    return returnValue;
  }

  public static final Chart.Builder apply(final Chart.Builder chartBuilder, ConfigOrBuilder userSuppliedValues) {
    return apply(chartBuilder, userSuppliedValues, true);
  }

  static final Chart.Builder apply(final Chart.Builder chartBuilder, final ConfigOrBuilder userSuppliedValues, final boolean processImportValues) {
    Objects.requireNonNull(chartBuilder);

    final Requirements requirements = fromChartOrBuilder(chartBuilder);
    if (requirements != null && !requirements.isEmpty()) {
      
      final Collection<? extends Dependency> requirementsDependencies = requirements.getDependencies();
      if (requirementsDependencies != null && !requirementsDependencies.isEmpty()) {
        
        final List<? extends Chart.Builder> existingSubcharts = chartBuilder.getDependenciesBuilderList();
        if (existingSubcharts != null && !existingSubcharts.isEmpty()) { 

          for (final Dependency dependency : requirementsDependencies) {
            if (dependency != null) {
              for (final Chart.Builder subchart : existingSubcharts) {
                if (subchart != null) {
                  dependency.adjustName(subchart);
                }
              }
              dependency.setNameToAlias();
              assert dependency.isEnabled();
            }
          }

          // Combine the supplied values with the chart's default
          // values in the form of a Map.
          final Map<String, Object> chartValuesMap = Configs.toValuesMap(chartBuilder, userSuppliedValues);
          assert chartValuesMap != null;
          
          // Now disable certain Dependencies.  This might be because
          // the canonical value set contains tags designating them
          // for disablement.  We couldn't disable them earlier
          // because we didn't have values.
          requirements.processTags(chartValuesMap);
          
          // Do the same thing, but work with conditions instead of tags.
          requirements.processConditions(chartValuesMap);

          // Turn the values into YAML, because YAML is the only format
          // we have for setting the contents of a new Config.Builder object (see
          // Config.Builder#setRaw(String)).  Then make a 
          final String userSuppliedValuesYaml = Configs.toYAML(chartValuesMap);
          assert userSuppliedValuesYaml != null;

          final Config.Builder configBuilder = Config.newBuilder();
          assert configBuilder != null;  
          configBuilder.setRaw(userSuppliedValuesYaml);
          
          // Very carefully remove subcharts that have been disabled.
          // Note the recursive call contained below.
          ITERATION:
          for (int i = 0; i < chartBuilder.getDependenciesCount(); i++) {
            final Chart.Builder subchart = chartBuilder.getDependenciesBuilder(i);
            for (final Dependency dependency : requirementsDependencies) {
              if (dependency != null && !dependency.isEnabled() && dependency.selects(subchart)) {
                chartBuilder.removeDependencies(i--);
                continue ITERATION;
              }
            }
            
            // If we get here, this is an enabled subchart.
            Requirements.apply(subchart, configBuilder, false); // <-- RECURSIVE CALL
          }
          
        }
      }
    }
    final Chart.Builder returnValue;
    if (processImportValues) {
      returnValue = processImportValues(chartBuilder);
    } else {
      returnValue = chartBuilder;
    }
    return returnValue;
  }

  
  /*
   * Inner and nested classes.
   */

  
  public static final class DependencyBeanInfo extends SimpleBeanInfo {

    private final Collection<? extends PropertyDescriptor> propertyDescriptors;
    
    public DependencyBeanInfo() throws IntrospectionException {
      super();
      final Collection<PropertyDescriptor> propertyDescriptors = new ArrayList<>();
      propertyDescriptors.add(new PropertyDescriptor("name", Dependency.class));
      propertyDescriptors.add(new PropertyDescriptor("version", Dependency.class));
      propertyDescriptors.add(new PropertyDescriptor("repository", Dependency.class));
      propertyDescriptors.add(new PropertyDescriptor("condition", Dependency.class));
      propertyDescriptors.add(new PropertyDescriptor("tags", Dependency.class));
      propertyDescriptors.add(new PropertyDescriptor("import-values", Dependency.class, "getImportValues", "setImportValues"));
      propertyDescriptors.add(new PropertyDescriptor("alias", Dependency.class));
      this.propertyDescriptors = propertyDescriptors;
    }

    @Override
    public final PropertyDescriptor[] getPropertyDescriptors() {
      return this.propertyDescriptors.toArray(new PropertyDescriptor[this.propertyDescriptors.size()]);
    }
    
  }
  
  public static final class Dependency {

    private String name;

    private String version;

    private String repository; // apending "index.yaml" to this should result in a URL that can be used to fetch the repository index

    /**
     * A YAML path that resolves to a boolean value, used for enabling
     * or disabling subcharts.
     */
    private String condition;

    private Collection<String> tags;

    private boolean enabled;

    private Collection<Object> importValues;
    
    private String alias;

    public Dependency() {
      super();
      this.setEnabled(true);
    }

    public final String getName() {
      return this.name;
    }

    public final void setName(final String name) {
      this.name = name;
    }

    public final String getVersion() {
      return this.version;
    }

    public final void setVersion(final String version) {
      this.version = version;
    }

    public final String getRepository() {
      return this.repository;
    }

    public final void setRepository(final String repository) {
      this.repository = repository;
    }

    public final String getCondition() {
      return this.condition;
    }

    public final void setCondition(final String condition) {
      this.condition = condition;
    }

    public final Collection<String> getTags() {
      return this.tags;
    }

    public final void setTags(final Collection<String> tags) {
      this.tags = tags;
    }

    public final boolean isEnabled() {
      return this.enabled;
    }

    public final void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public final Collection<Object> getImportValues() {
      return this.importValues;
    }

    public final void setImportValues(final Collection<Object> importValues) {
      this.importValues = importValues;
    }
    
    public final String getAlias() {
      return this.alias;
    }

    public final void setAlias(final String alias) {
      this.alias = alias;
    }

    public final boolean selects(final ChartOrBuilder chart) {
      if (chart == null) {
        return false;
      }
      return this.selects(chart.getMetadata());
    }
    
    public final boolean selects(final MetadataOrBuilder metadata) {
      final boolean returnValue;
      if (metadata == null) {
        returnValue = this.selects(null, null);
      } else {
        returnValue = this.selects(metadata.getName(), metadata.getVersion());
      }
      return returnValue;
    }

    public final boolean selects(final String name, final String versionString) {
      final Object myName = this.getName();
      if (myName == null) {
        if (name != null) {
          return false;
        }
      } else if (!myName.equals(name)) {
        return false;
      }

      final String myVersion = this.getVersion();
      if (myVersion == null) {
        if (versionString != null) {
          return false;
        }
      } else {
        final Version version = Version.valueOf(myVersion);
        assert version != null;
        final Parser<Expression> parser = ExpressionParser.newInstance();
        assert parser != null;
        final Expression semVerConstraint = parser.parse(versionString);
        assert semVerConstraint != null;
        if (!version.satisfies(semVerConstraint)) {
          return false;
        }
      }
      return true;
    }

    final void adjustName(final Chart.Builder subchart) {
      if (subchart != null && this.selects(subchart)) {
        final String alias = this.getAlias();
        if (alias != null && !alias.isEmpty() && subchart.hasMetadata()) {
          final Metadata.Builder subchartMetadataBuilder = subchart.getMetadataBuilder();
          assert subchartMetadataBuilder != null;
          // Rename the chart to have our alias as its new name.
          subchartMetadataBuilder.setName(alias);
        }
      }
    }

    final void setNameToAlias() {
      final String alias = this.getAlias();
      if (alias != null && !alias.isEmpty()) {
        this.setName(alias);
      }
    }

    // Ported from ProcessRequirementsTags
    final void processTags(final Map<String, Object> values) {
      if (values != null) {
        final Object tagsObject = values.get("tags");
        if (tagsObject instanceof Map) {
          final Map<?, ?> tags = (Map<?, ?>)tagsObject;
          final Collection<? extends String> myTags = this.getTags();
          if (myTags != null && !myTags.isEmpty()) {
            boolean explicitlyTrue = false;
            boolean explicitlyFalse = false;
            for (final String myTag : myTags) {
              final Object tagValue = tags.get(myTag);
              if (Boolean.TRUE.equals(tagValue)) {
                explicitlyTrue = true;
              } else if (Boolean.FALSE.equals(tagValue)) {
                explicitlyFalse = true;
              } else {
                // Not a Boolean at all; just skip it
              }
            }
            
            // Note that this block looks different from the analogous
            // block in processConditions() below.  It is this way in the
            // Go code as well.
            if (explicitlyFalse) {
              if (!explicitlyTrue) {
                this.setEnabled(false);
              }
            } else {
              this.setEnabled(explicitlyTrue);
            }
          }
        }
      }
    }
    
    final void processConditions(final Map<String, Object> values) {
      if (values != null && !values.isEmpty()) {
        final MapTree mapTree = new MapTree(values);
        boolean explicitlyTrue = false;
        boolean explicitlyFalse = false;
        String conditionString = this.getCondition();
        if (conditionString != null) {
          conditionString = conditionString.trim();
          final String[] conditions = commaSplitPattern.split(conditionString);
          if (conditions != null && conditions.length > 0) {
            for (final String condition : conditions) {
              if (condition != null && !condition.isEmpty()) {
                final Object conditionValue = mapTree.get(condition, Object.class);
                if (Boolean.TRUE.equals(conditionValue)) {
                  explicitlyTrue = true;
                } else if (Boolean.FALSE.equals(conditionValue)) {
                  explicitlyFalse = true;
                } else if (conditionValue != null) {
                  break;
                }
              }
            }
          }
        }
        
        // Note that this block looks different from the analogous block
        // in processTags() above.  It is this way in the Go code as
        // well.
        if (explicitlyFalse) {
          if (!explicitlyTrue) {
            this.setEnabled(false);
          }
        } else if (explicitlyTrue) {
          this.setEnabled(true);
        }
      }
    }

    @Override
    public final String toString() {
      final StringBuilder sb = new StringBuilder();
      final Object name = this.getName();
      if (name == null) {
        sb.append("Unnamed");
      } else {
        sb.append(name);
      }
      final String alias = this.getAlias();
      if (alias != null && !alias.isEmpty()) {
        sb.append(" (").append(alias).append(")");
      }
      sb.append(" ");
      sb.append(this.getVersion());
      return sb.toString();
    }

  }
  
}
