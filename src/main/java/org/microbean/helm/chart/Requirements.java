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
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

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

/**
 * A specification of a <a
 * href="https://docs.helm.sh/developing_charts/#chart-dependencies">Helm
 * chart's dependencies</a>; not normally used directly by end users.
 *
 * <p>Helm charts support a {@code requirements.yaml} resource, in
 * YAML format, whose sole member is a {@code dependencies} list.
 * This class represents that resource.</p>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are <strong>not</strong> suitable for
 * concurrent access by multiple threads.</p>
 *
 * @author <a href="https://about.me/lairdnelson/"
 * target="_parent">Laird Nelson</a>
 */
public final class Requirements {


  /*
   * Instance fields.
   */


  /**
   * The {@link Collection} of {@link Dependency} instances that
   * comprises this {@link Requirements}.
   *
   * <p>This field may be {@code null}.</p>
   */
  private Collection<Dependency> dependencies;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link Requirements}.
   */
  public Requirements() {
    super();
  }


  /*
   * Instance methods.
   */
  

  /**
   * Returns {@code true} if this {@link Requirements} has no {@link
   * Dependency} instances.
   *
   * @return {@code true} if this {@link Requirements} is empty;
   * {@code false} otherwise
   */
  public final boolean isEmpty() {
    return this.dependencies == null || this.dependencies.isEmpty();
  }

  /**
   * Returns the {@link Collection} of {@link Dependency} instances
   * comprising this {@link Requirements}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @see #setDependencies(Collection)
   */
  public final Collection<Dependency> getDependencies() {
    return this.dependencies;
  }

  /**
   * Installs the {@link Collection} of {@link Dependency} instances
   * comprising this {@link Requirements}.
   *
   * @param dependencies the {@link Collection} of {@link Dependency}
   * instances that will comprise this {@link Requirements}; may be
   * {@code null}; not copied or cloned
   *
   * @see #getDependencies()
   */
  public final void setDependencies(final Collection<Dependency> dependencies) {
    this.dependencies = dependencies;
  }

  private final void applyEnablementRules(final Map<String, Object> values) {
    this.processTags(values);
    this.processConditions(values);
  }
  
  private final void processTags(final Map<String, Object> values) {
    final Collection<Dependency> dependencies = this.getDependencies();
    if (dependencies != null && !dependencies.isEmpty()) {
      for (final Dependency dependency : dependencies) {
        if (dependency != null) {
          dependency.processTags(values);
        }
      }
    }
    
  }

  private final void processConditions(final Map<String, Object> values) {
    final Collection<Dependency> dependencies = this.getDependencies();
    if (dependencies != null && !dependencies.isEmpty()) {
      for (final Dependency dependency : dependencies) {
        if (dependency != null) {
          dependency.processConditions(values);
        }
      }
    }
  }
  

  /*
   * Static methods.
   */


  /**
   * Applies rules around <a
   * href="https://docs.helm.sh/developing_charts/#importing-child-values-via-requirements-yaml">importing
   * subchart values into the parent chart's values</a>.
   *
   * @param chartBuilder the {@link Chart.Builder} to work on; must
   * not be {@code null}
   *
   * @exception NullPointerException if {@code chartBuilder} is {@code
   * null}
   */
  static final Chart.Builder processImportValues(final Chart.Builder chartBuilder) {
    Objects.requireNonNull(chartBuilder);
    final List<? extends Chart.Builder> flattenedCharts = Charts.flatten(chartBuilder);
    if (flattenedCharts != null) {
      assert !flattenedCharts.isEmpty();
      final ListIterator<? extends Chart.Builder> listIterator = flattenedCharts.listIterator(flattenedCharts.size());
      assert listIterator != null;
      while (listIterator.hasPrevious()) {
        final Chart.Builder chart = listIterator.previous();
        assert chart != null;
        processSingleChartImportValues(chart);
      }
    }
    return chartBuilder;
  }
  
  // Ported from requirements.go processImportValues().
  private static final Chart.Builder processSingleChartImportValues(final Chart.Builder chartBuilder) {
    Objects.requireNonNull(chartBuilder);

    Chart.Builder returnValue = null;

    final Map<String, Object> canonicalValues = Configs.toDefaultValuesMap(chartBuilder);
    
    Map<String, Object> combinedValues = new HashMap<>();
    final Requirements requirements = fromChartOrBuilder(chartBuilder);
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

              // Not clear why we build this and install it later; it
              // is never used.  See requirements.go's
              // processImportValues().
              final Collection<Object> newImportValues = new ArrayList<>(importValues.size());

              for (final Object importValue : importValues) {
                final String s;
                
                if (importValue instanceof Map) {
                  @SuppressWarnings("unchecked")
                  final Map<String, String> importValueMap = (Map<String, String>)importValue;
                  
                  final String importValueChild = importValueMap.get("child");
                  final String importValueParent = importValueMap.get("parent");

                  // Not clear to me why we build this and then
                  // install it; it's never used in the .go code.
                  final Map<String, String> newMap = new HashMap<>();
                  newMap.put("child", importValueChild);
                  newMap.put("parent", importValueParent);
                  
                  newImportValues.add(newMap);

                  final Map<String, Object> vv =
                    MapTree.newMapChain(importValueParent,
                                        getMap(canonicalValues,
                                               dependencyName + "." + importValueChild));
                  combinedValues = Values.coalesceMaps(vv, canonicalValues);
                  // OK
                  
                } else if (importValue instanceof String) {
                  final String importValueString = (String)importValue;
                  
                  final String importValueChild = "exports." + importValueString;

                  // Not clear to me why we build this and then
                  // install it; it's never used in the .go code.
                  final Map<String, String> newMap = new HashMap<>();
                  newMap.put("child", importValueChild);
                  newMap.put("parent", ".");
                  
                  newImportValues.add(newMap);
                  
                  combinedValues = Values.coalesceMaps(getMap(canonicalValues, dependencyName + "." + importValueChild), combinedValues);
                  // OK
                  
                }
              }
              // The .go code alters the dependency's importValues;
              // I'm not sure why, but we follow suit.
              dependency.setImportValues(newImportValues);            
            }
          }
        }
      }
    }
    combinedValues = Values.coalesceMaps(canonicalValues, combinedValues);
    assert combinedValues != null;
    final String yaml = new Yaml().dump(combinedValues);
    assert yaml != null;
    final Config.Builder configBuilder = chartBuilder.getValuesBuilder();
    assert configBuilder != null;
    configBuilder.setRaw(yaml);
    returnValue = chartBuilder;
    assert returnValue != null;
    return returnValue;
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
  /**
   * Creates a new {@link Requirements} from a top-level {@code
   * requirements.yaml} {@linkplain Any resource} present in the
   * supplied {@link ChartOrBuilder} and returns it.
   *
   * <p>This method may return {@code null} if the supplied {@link
   * ChartOrBuilder} is itself {@code null} or doesn't have a {@code
   * requirements.yaml} {@linkplain Any resource}.</p>
   *
   * @param chart the {@link ChartOrBuilder} housing a {@code
   * requirement.yaml} {@linkplain Any resource}; may be {@code null}
   * in which case {@code null} will be returned
   *
   * @return a new {@link Requirements} or {@code null}
   */
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
                assert returnValue != null;
              }
            }
          }
        }
      }
    }
    return returnValue;
  }

  /**
   * Applies a <a
   * href="https://docs.helm.sh/developing_charts/#alias-field-in-requirements-yaml">variety
   * of rules concerning subchart aliasing and enablement</a> to the
   * contents of the supplied {@code Chart.Builder}.
   *
   * <p>This method never returns {@code null}
   *
   * @param chartBuilder the {@link Chart.Builder} whose subcharts may
   * be affected; must not be {@code null}
   *
   * @param userSuppliedValues a {@link ConfigOrBuilder} representing
   * overriding values; may be {@code null}
   *
   * @return the supplied {@code chartBuilder} for convenience; never
   * {@code null}
   *
   * @exception NullPointerException if {@code chartBuilder} is {@code
   * null}
   */
  public static final Chart.Builder apply(final Chart.Builder chartBuilder, ConfigOrBuilder userSuppliedValues) {
    return apply(chartBuilder, userSuppliedValues, true /* top level, i.e. non-recursive call */);
  }

  /**
   * Applies a <a
   * href="https://docs.helm.sh/developing_charts/#alias-field-in-requirements-yaml">variety
   * of rules concerning subchart aliasing and enablement</a> to the
   * contents of the supplied {@code Chart.Builder}.
   *
   * <p>This method never returns {@code null}
   *
   * @param chartBuilder the {@link Chart.Builder} whose subcharts may
   * be affected; must not be {@code null}
   *
   * @param userSuppliedValues a {@link ConfigOrBuilder} representing
   * overriding values; may be {@code null}
   *
   * @param topLevel {@code true} if this is a non-recursive call, and
   * hence certain "top-level" processing should take place
   *
   * @return the supplied {@code chartBuilder} for convenience; never
   * {@code null}
   *
   * @exception NullPointerException if {@code chartBuilder} is {@code
   * null}
   */
  static final Chart.Builder apply(final Chart.Builder chartBuilder, final ConfigOrBuilder userSuppliedValues, final boolean topLevel) {
    Objects.requireNonNull(chartBuilder);

    final Requirements requirements = fromChartOrBuilder(chartBuilder);
    if (requirements != null && !requirements.isEmpty()) {
      
      final Collection<? extends Dependency> requirementsDependencies = requirements.getDependencies();
      if (requirementsDependencies != null && !requirementsDependencies.isEmpty()) {
        
        final List<? extends Chart.Builder> existingSubcharts = chartBuilder.getDependenciesBuilderList();
        if (existingSubcharts != null && !existingSubcharts.isEmpty()) { 

          Collection<Dependency> missingSubcharts = null;
          
          for (final Dependency dependency : requirementsDependencies) {
            if (dependency != null) {
              boolean dependencySelectsAtLeastOneSubchart = false;
              for (final Chart.Builder subchart : existingSubcharts) {
                if (subchart != null) {
                  dependencySelectsAtLeastOneSubchart = dependencySelectsAtLeastOneSubchart || dependency.selects(subchart);
                  dependency.adjustName(subchart);
                }
              }
              if (topLevel && !dependencySelectsAtLeastOneSubchart) {
                if (missingSubcharts == null) {
                  missingSubcharts = new ArrayList<>();
                }
                missingSubcharts.add(dependency);
              } else {
                dependency.setNameToAlias();
              }
              assert dependency.isEnabled();
            }
          }

          if (missingSubcharts != null && !missingSubcharts.isEmpty()) {
            throw new MissingDependenciesException(missingSubcharts);
          }

          // Combine the supplied values with the chart's default
          // values in the form of a Map.
          final Map<String, Object> chartValuesMap = Configs.toValuesMap(chartBuilder, userSuppliedValues);
          assert chartValuesMap != null;
          
          // Now disable certain Dependencies.  This might be because
          // the canonical value set contains tags or conditions
          // designating them for disablement.  We couldn't disable
          // them earlier because we didn't have values.
          requirements.applyEnablementRules(chartValuesMap);

          // Turn the values into YAML, because YAML is the only format
          // we have for setting the contents of a new Config.Builder object (see
          // Config.Builder#setRaw(String)).
          final String userSuppliedValuesYaml;
          if (chartValuesMap.isEmpty()) {
            userSuppliedValuesYaml = "";
          } else {
            userSuppliedValuesYaml = new Yaml().dump(chartValuesMap);
          }
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
            Requirements.apply(subchart, configBuilder, false /* not topLevel, i.e. this is recursive */); // <-- RECURSIVE CALL
          }
          
        }
      }
    }
    final Chart.Builder returnValue;
    if (topLevel) {
      returnValue = processImportValues(chartBuilder);
    } else {
      returnValue = chartBuilder;
    }
    return returnValue;
  }

  
  /*
   * Inner and nested classes.
   */

  
  /**
   * A {@link SimpleBeanInfo} describing the Java Bean properties for
   * the {@link Dependency} class; not normally used directly by end
   * users.
   *
   * @author <a href="https://about.me/lairdnelson/"
   * target="_parent">Laird Nelson</a>
   *
   * @see SimpleBeanInfo
   */
  public static final class DependencyBeanInfo extends SimpleBeanInfo {


    /*
     * Instance methods.
     */


    /**
     * The {@link Collection} of {@link PropertyDescriptor}s whose
     * contents will be returned by the {@link
     * #getPropertyDescriptors()} method.
     *
     * <p>This field is never {@code null}.</p>
     *
     * @see #getPropertyDescriptors() 
     */
    private final Collection<? extends PropertyDescriptor> propertyDescriptors;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link DependencyBeanInfo}.
     *
     * @exception IntrospectionException if there was a problem
     * creating a {@link PropertyDescriptor}
     *
     * @see #getPropertyDescriptors()
     */
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


    /*
     * Instance methods.
     */


    /**
     * Returns an array of {@link PropertyDescriptor}s describing the
     * {@link Dependency} class.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return a non-{@code null}, non-empty array of {@link
     * PropertyDescriptor}s
     */
    @Override
    public final PropertyDescriptor[] getPropertyDescriptors() {
      return this.propertyDescriptors.toArray(new PropertyDescriptor[this.propertyDescriptors.size()]);
    }
    
  }

  /**
   * A description of a subchart that should be present in a parent
   * Helm chart; not normally used directly by end users.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   *
   * @see Requirements
   */
  public static final class Dependency {


    /*
     * Static fields.
     */
    
    
    /**
     * An unanchored {@link Pattern} matching a sequence of zero or more
     * whitespace characters, followed by a comma, followed by zero or
     * more whitespace characters.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is used during {@link #processConditions(Map)}
     * method execution.</p>
     */
    private static final Pattern commaSplitPattern = Pattern.compile("\\s*,\\s*");
    

    /*
     * Instance fields.
     */


    /**
     * The name of the subchart being represented by this {@link
     * Requirements.Dependency}.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see #getName()
     *
     * @see #setName(String)
     */
    private String name;

    /**
     * The range of acceptable semantic versions of the subchart being
     * represented by this {@link Requirements.Dependency}.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see #getVersion()
     *
     * @see #setVersion(String)
     */
    private String versionRange;

    /**
     * A {@link String} representation of a URI which, when {@code
     * index.yaml} is appended to it, results in a URI designating a
     * Helm chart repository index.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see #getRepository()
     *
     * @see #setRepository(String)
     */
    private String repository;

    /**
     * A period-separated path that, when evaluated against a {@link
     * Map} of {@link Map}s representing user-supplied or default
     * values, will hopefully result in a value that can, in turn, be
     * evaluated as a truth-value to aid in the enabling and disabling
     * of subcharts.
     *
     * <p>This field may be {@code null}.</p>
     *
     * <p>This field may actually hold several such paths separated by
     * commas.  This is an artifact of the design of Helm's {@code
     * requirements.yaml} file.</p>
     *
     * @see #getCondition()
     *
     * @see #setCondition(String)
     */
    private String condition;

    /**
     * A {@link Collection} of tags that can be used to enable or
     * disable subcharts.
     *
     * <p>This field may be {@code null}.
     *
     * @see #getTags()
     *
     * @see #setTags(Collection)
     */
    private Collection<String> tags;

    /**
     * Whether the subchart that this {@link Requirements.Dependency}
     * identifies is to be considered enabled.
     *
     * <p>This field is set to {@code true} by default.</p>
     *
     * @see #isEnabled()
     *
     * @see #setEnabled(boolean)
     */
    private boolean enabled;

    /**
     * A {@link Collection} representing the contents of a {@code
     * requirements.yaml}'s <a
     * href="https://docs.helm.sh/developing_charts/#using-the-exports-format">{@code
     * import-values} section</a>.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see #getImportValues()
     *
     * @see #setImportValues(Collection)
     */
    private Collection<Object> importValues;

    /**
     * The alias to use for the subchart identified by this {@link
     * Requirements.Dependency}.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see #getAlias()
     *
     * @see #setAlias(String)
     */
    private String alias;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link Dependency}.
     */
    public Dependency() {
      super();
      this.setEnabled(true);
    }


    /*
     * Instance methods.
     */
    

    /**
     * Returns the name of the subchart being represented by this {@link
     * Requirements.Dependency}.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @return the name of the subchart being represented by this {@link
     * Requirements.Dependency}, or {@code null}
     *
     * @see #setName(String)
     */
    public final String getName() {
      return this.name;
    }

    /**
     * Sets the name of the subchart being represented by this {@link
     * Requirements.Dependency}.
     *
     * @param name the new name; may be {@code null}
     *
     * @see #getName()
     */
    public final void setName(final String name) {
      this.name = name;
    }

    /**
     * Returns the range of acceptable semantic versions of the
     * subchart being represented by this {@link
     * Requirements.Dependency}.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @return the range of acceptable semantic versions of the
     * subchart being represented by this {@link
     * Requirements.Dependency}, or {@code null}
     *
     * @see #setVersion(String)
     */
    public final String getVersion() {
      return this.versionRange;
    }

    /**
     * Sets the range of acceptable semantic versions of the subchart
     * being represented by this {@link Requirements.Dependency}.
     *
     * @param versionRange the new semantic version range; may be {@code
     * null}
     *
     * @see #getVersion()
     */
    public final void setVersion(final String versionRange) {
      this.versionRange = versionRange;
    }

    /**
     * Returns the {@link String} representation of a URI which, when
     * {@code index.yaml} is appended to it, results in a URI
     * designating a Helm chart repository index.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @return the {@link String} representation of a URI which, when
     * {@code index.yaml} is appended to it, results in a URI
     * designating a Helm chart repository index, or {@code null}
     *
     * @see #setRepository(String)
     */
    public final String getRepository() {
      return this.repository;
    }

    /**
     * Sets the {@link String} representation of a URI which, when
     * {@code index.yaml} is appended to it, results in a URI
     * designating a Helm chart repository index.
     *
     * @param repository the {@link String} representation of a URI
     * which, when {@code index.yaml} is appended to it, results in a
     * URI designating a Helm chart repository index, or {@code null}
     *
     * @see #getRepository()
     */
    public final void setRepository(final String repository) {
      this.repository = repository;
    }

    /**
     * Returns a period-separated path that, when evaluated against a
     * {@link Map} of {@link Map}s representing user-supplied or
     * default values, will hopefully result in a value that can, in
     * turn, be evaluated as a truth-value to aid in the enabling and
     * disabling of subcharts.
     *
     * <p>This method may return {@code null}.</p>
     *
     * <p>This method may return a value that actually holds several
     * such paths separated by commas.  This is an artifact of the
     * design of Helm's {@code requirements.yaml} file.</p>
     *
     * @return a period-separated path that, when evaluated against a
     * {@link Map} of {@link Map}s representing user-supplied or
     * default values, will hopefully result in a value that can, in
     * turn, be evaluated as a truth-value to aid in the enabling and
     * disabling of subcharts, or {@code null}
     *
     * @see #setCondition(String)
     */
    public final String getCondition() {
      return this.condition;
    }

    /**
     * Sets the period-separated path that, when evaluated against a
     * {@link Map} of {@link Map}s representing user-supplied or
     * default values, will hopefully result in a value that can, in
     * turn, be evaluated as a truth-value to aid in the enabling and
     * disabling of subcharts.
     *
     * @param condition a period-separated path that, when evaluated
     * against a {@link Map} of {@link Map}s representing
     * user-supplied or default values, will hopefully result in a
     * value that can, in turn, be evaluated as a truth-value to aid
     * in the enabling and disabling of subcharts, or {@code null}
     *
     * @see #getCondition()
     */
    public final void setCondition(final String condition) {
      this.condition = condition;
    }

    /**
     * Returns the {@link Collection} of tags that can be used to enable or
     * disable subcharts.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @return the {@link Collection} of tags that can be used to
     * enable or disable subcharts, or {@code null}
     *
     * @see #setTags(Collection)
     */
    public final Collection<String> getTags() {
      return this.tags;
    }

    /**
     * Sets the {@link Collection} of tags that can be used to enable
     * or disable subcharts.
     *
     * @param tags the {@link Collection} of tags that can be used to
     * enable or disable subcharts; may be {@code null}
     *
     * @see #getTags()
     */
    public final void setTags(final Collection<String> tags) {
      this.tags = tags;
    }

    /**
     * Returns {@code true} if the subchart this {@link
     * Requirements.Dependency} identifies is to be considered
     * enabled.
     *
     * @return {@code true} if the subchart this {@link
     * Requirements.Dependency} identifies is to be considered
     * enabled; {@code false} otherwise
     *
     * @see #setEnabled(boolean)
     */
    public final boolean isEnabled() {
      return this.enabled;
    }

    /**
     * Sets whether the subchart this {@link
     * Requirements.Dependency} identifies is to be considered
     * enabled.
     *
     * @param enabled whether the subchart this {@link
     * Requirements.Dependency} identifies is to be considered
     * enabled
     *
     * @see #isEnabled()
     */
    public final void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Returns a {@link Collection} representing the contents of a {@code
     * requirements.yaml}'s <a
     * href="https://docs.helm.sh/developing_charts/#using-the-exports-format">{@code
     * import-values} section</a>.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @return a {@link Collection} representing the contents of a {@code
     * requirements.yaml}'s <a
     * href="https://docs.helm.sh/developing_charts/#using-the-exports-format">{@code
     * import-values} section</a>, or {@code null}
     *
     * @see #setImportValues(Collection)
     */
    public final Collection<Object> getImportValues() {
      return this.importValues;
    }

    /**
     * Sets the {@link Collection} representing the contents of a {@code
     * requirements.yaml}'s <a
     * href="https://docs.helm.sh/developing_charts/#using-the-exports-format">{@code
     * import-values} section</a>.
     *
     * @param importValues the {@link Collection} representing the contents of a {@code
     * requirements.yaml}'s <a
     * href="https://docs.helm.sh/developing_charts/#using-the-exports-format">{@code
     * import-values} section</a>; may be {@code null}
     *
     * @see #getImportValues()
     */
    public final void setImportValues(final Collection<Object> importValues) {
      this.importValues = importValues;
    }

    /**
     * Returns the alias to use for the subchart identified by this {@link
     * Requirements.Dependency}.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @return the alias to use for the subchart identified by this {@link
     * Requirements.Dependency}, or {@code null}
     *
     * @see #setAlias(String)
     */
    public final String getAlias() {
      return this.alias;
    }

    /**
     * Sets the alias to use for the subchart identified by this {@link
     * Requirements.Dependency}.
     *
     * @param alias the alias to use for the subchart identified by this {@link
     * Requirements.Dependency}; may be {@code null}
     *
     * @see #getAlias()
     */
    public final void setAlias(final String alias) {
      this.alias = alias;
    }

    /**
     * Returns {@code true} if this {@link Requirements.Dependency}
     * identifies the given {@link ChartOrBuilder}.
     *
     * @param chart the {@link ChartOrBuilder} to check; may be {@code
     * null} in which case {@code false} will be returned
     *
     * @return {@code true} if this {@link Requirements.Dependency}
     * identifies the given {@link ChartOrBuilder}; {@code false}
     * otherwise
     */
    public final boolean selects(final ChartOrBuilder chart) {
      if (chart == null) {
        return false;
      }
      return this.selects(chart.getMetadata());
    }
    
    private final boolean selects(final MetadataOrBuilder metadata) {
      final boolean returnValue;
      if (metadata == null) {
        returnValue = false;
      } else {
        returnValue = this.selects(metadata.getName(), metadata.getVersion());
      }
      return returnValue;
    }

    private final boolean selects(final String name, final String versionString) {
      if (versionString == null) {
        return false;
      }
      
      final Object myName = this.getName();
      if (myName == null) {
        if (name != null) {
          return false;
        }
      } else if (!myName.equals(name)) {
        return false;
      }

      final String myVersionRange = this.getVersion();
      if (myVersionRange == null) {
        return false;
      }
      
      final Version version = Version.valueOf(versionString);
      assert version != null;
      return version.satisfies(ExpressionParser.newInstance().parse(myVersionRange));
    }

    final boolean adjustName(final Chart.Builder subchart) {
      boolean returnValue = false;
      if (subchart != null && this.selects(subchart)) {
        final String alias = this.getAlias();
        if (alias != null && !alias.isEmpty() && subchart.hasMetadata()) {
          final Metadata.Builder subchartMetadataBuilder = subchart.getMetadataBuilder();
          assert subchartMetadataBuilder != null;
          if (!alias.equals(subchartMetadataBuilder.getName())) {
            // Rename the chart to have our alias as its new name.
            subchartMetadataBuilder.setName(alias);
            returnValue = true;
          }
        }
      }
      return returnValue;
    }

    final boolean setNameToAlias() {
      boolean returnValue = false;
      final String alias = this.getAlias();
      if (alias != null && !alias.isEmpty() && !alias.equals(this.getName())) {        
        this.setName(alias);
        returnValue = true;
      }
      return returnValue;
    }

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

    /**
     * Returns a {@link String} representation of this {@link
     * Requirements.Dependency}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return a non-{@code null} {@link String} representation of
     * this {@link Requirements.Dependency}
     */
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
