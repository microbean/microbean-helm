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

import java.io.Closeable;
import java.io.IOException;

import java.lang.reflect.Array;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.Set;

import com.google.protobuf.AnyOrBuilder;

import hapi.chart.ChartOuterClass.ChartOrBuilder;
import hapi.chart.ConfigOuterClass.ConfigOrBuilder;
import hapi.chart.MetadataOuterClass.MaintainerOrBuilder;
import hapi.chart.MetadataOuterClass.MetadataOrBuilder;
import hapi.chart.TemplateOuterClass.TemplateOrBuilder;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import org.yaml.snakeyaml.constructor.Constructor;

import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.MethodProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;

import org.yaml.snakeyaml.representer.Representer;

/**
 * An object capable of writing or serializing or otherwise
 * representing a {@link ChartOrBuilder}.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #write(ChartOuterClass.ChartOrBuilder)
 */
public abstract class AbstractChartWriter implements Closeable {


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link AbstractChartWriter}.
   */
  protected AbstractChartWriter() {
    super();
  }


  /*
   * Instance methods.
   */

  
  /**
   * Writes or serializes or otherwise represents the supplied {@link
   * ChartOrBuilder}.
   *
   * @param chartBuilder the {@link ChartOrBuilder} to write; must not
   * be {@code null}
   *
   * @exception IOException if a write error occurs
   *
   * @exception NullPointerException if {@code chartBuilder} is {@code
   * null}
   *
   * @exception IllegalArgumentException if the {@link
   * ChartOrBuilder#getMetadata()} method returns {@code null}, or if
   * the {@link MetadataOrBuilder#getName()} method returns {@code
   * null}, or if the {@link MetadataOrBuilder#getVersion()} method
   * returns {@code null}
   *
   * @exception IllegalStateException if a subclass has overridden the
   * {@link #createYaml()} method to return {@code null} and calls it
   *
   * @see #write(Context, ChartOuterClass.ChartOrBuilder,
   * ChartOuterClass.ChartOrBuilder)
   */
  public final void write(final ChartOrBuilder chartBuilder) throws IOException {
    this.write(null, null, Objects.requireNonNull(chartBuilder));
  }

  /**
   * Writes or serializes or otherwise represents the supplied {@code
   * chartBuilder} as a subchart of the supplied {@code parent} (which
   * may be, and often is, {@code null}).
   *
   * @param context the {@link Context} representing the write
   * operation; may be {@code null}
   *
   * @param parent the {@link ChartOrBuilder} functioning as the
   * parent chart; may be, and often is, {@code null}; must not be
   * identical to the {@code chartBuilder} parameter value
   *
   * @param chartBuilder the {@link ChartOrBuilder} to actually write;
   * must not be {@code null}; must not be identical to the {@code
   * parent} parameter value
   *
   * @exception IOException if a write error occurs
   *
   * @exception NullPointerException if {@code chartBuilder} is {@code null}
   *
   * @exception IllegalArgumentException if {@code parent} is
   * identical to {@code chartBuilder}, or if the {@link
   * ChartOrBuilder#getMetadata()} method returns {@code null}, or if
   * the {@link MetadataOrBuilder#getName()} method returns {@code
   * null}, or if the {@link MetadataOrBuilder#getVersion()} method
   * returns {@code null}
   *
   * @exception IllegalStateException if a subclass has overridden the
   * {@link #createYaml()} method to return {@code null} and calls it
   *
   * @see #beginWrite(Context, ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)
   *
   * @see #writeMetadata(Context, MetadataOuterClass.MetadataOrBuilder)
   *
   * @see #writeConfig(Context, ConfigOuterClass.ConfigOrBuilder)
   *
   * @see #writeTemplate(Context, TemplateOuterClass.TemplateOrBuilder)
   *
   * @see #writeFile(Context, AnyOrBuilder)
   *
   * @see #writeSubchart(Context, ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)
   *
   * @see #endWrite(Context, ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)
   */
  protected void write(Context context, final ChartOrBuilder parent, final ChartOrBuilder chartBuilder) throws IOException {
    Objects.requireNonNull(chartBuilder);
    if (parent == chartBuilder) {
      throw new IllegalArgumentException("parent == chartBuilder");
    }
    final MetadataOrBuilder metadata = chartBuilder.getMetadataOrBuilder();
    if (metadata == null) {
      throw new IllegalArgumentException("chartBuilder", new IllegalStateException("chartBuilder.getMetadata() == null"));
    } else if (metadata.getName() == null) {
      throw new IllegalArgumentException("chartBuilder", new IllegalStateException("chartBuilder.getMetadata().getName() == null"));
    } else if (metadata.getVersion() == null) {
      throw new IllegalArgumentException("chartBuilder", new IllegalStateException("chartBuilder.getMetadata().getVersion() == null"));
    }

    if (context == null) {
      final Map<Object, Object> map = new HashMap<>(13);
      context = new Context() {
          @Override
          public final <T> T get(final Object key, final Class<T> type) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(type);
            return type.cast(map.get(key));
          }
          
          @Override
          public final void put(final Object key, final Object value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            map.put(key, value);
          }
          
          @Override
          public final boolean containsKey(final Object key) {
            return map.containsKey(key);
          }

          @Override
          public final void remove(final Object key) {
            map.remove(key);
          }
        };
    }

    this.beginWrite(context, parent, chartBuilder);
    
    this.writeMetadata(context, metadata);

    this.writeConfig(context, chartBuilder.getValuesOrBuilder());

    final Collection<? extends TemplateOrBuilder> templates = chartBuilder.getTemplatesOrBuilderList();
    if (templates != null && !templates.isEmpty()) {
      for (final TemplateOrBuilder template : templates) {
        this.writeTemplate(context, template);
      }
    }

    final Collection<? extends AnyOrBuilder> files = chartBuilder.getFilesOrBuilderList();
    if (files != null && !files.isEmpty()) {
      for (final AnyOrBuilder file : files) {
        this.writeFile(context, file);
      }
    }

    final Collection<? extends ChartOrBuilder> subcharts = chartBuilder.getDependenciesOrBuilderList();
    if (subcharts != null && !subcharts.isEmpty()) {
      for (final ChartOrBuilder subchart : subcharts) {
        if (subchart != null) {
          this.writeSubchart(context, chartBuilder, subchart);
        }
      }
    }

    this.endWrite(context, parent, chartBuilder);
    
  }

  /**
   * Creates and returns a new {@link Yaml} instance for (optional)
   * use in writing {@link ConfigOrBuilder} and {@link
   * MetadataOrBuilder} objects.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * <p>Behavior is undefined if overrides of this method interact
   * with other methods defined by this class.</p>
   *
   * @return a non-{@code null} {@link Yaml} instance
   */
  protected Yaml createYaml() {
    final Representer representer = new TerseRepresenter();
    representer.setPropertyUtils(new CustomPropertyUtils());
    final DumperOptions options = new DumperOptions();
    options.setAllowReadOnlyProperties(true);
    return new Yaml(new Constructor(), representer, options);
  }

  /**
   * Marshals the supplied {@link Object} to YAML in the context of
   * the supplied {@link Context} and returns the result.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method may call the {@link #createYaml()} method.</p>
   *
   * @param context the {@link Context} representing the write
   * operation; must not be {@code null}
   *
   * @param data the {@link Object} to convert to its YAML
   * representation; may be {@code null}
   *
   * @return a non-{@code null} {@link String} consisting of the
   * appropriate YAML represesentation of the supplied {@code data}
   *
   * @exception IOException if a YAML serialization error occurs
   *
   * @exception NullPointerException if {@code context} is {@code
   * null}
   *
   * @see #createYaml()
   *
   * @see Yaml#dumpAsMap(Object)
   */
  protected final String toYAML(final Context context, final Object data) throws IOException {
    Objects.requireNonNull(context);
    Yaml yaml = context.get(Yaml.class.getName(), Yaml.class);
    if (yaml == null) {
      yaml = this.createYaml();
      if (yaml == null) {
        throw new IllegalStateException("createYaml() == null");
      }
      context.put(Yaml.class.getName(), yaml);
    }
    return yaml.dumpAsMap(data);
  }

  /**
   * A callback method invoked when the {@link #write(Context,
   * ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)}
   * method has been invoked.
   *
   * <p>The default implementation of this method does nothing.</p>
   *
   * @param context the {@link Context} representing the write
   * operation; must not be {@code null}
   *
   * @param parent the {@link ChartOrBuilder} functioning as the
   * parent chart; may be, and often is, {@code null}; must not be
   * identical to the {@code chartBuilder} parameter value
   *
   * @param chartBuilder the {@link ChartOrBuilder} to actually write;
   * must not be {@code null}; must not be identical to the {@code
   * parent} parameter value
   *
   * @exception IOException if a write error occurs
   *
   * @exception NullPointerException if either {@code context} or {@code chartBuilder} is {@code null}
   *
   * @exception IllegalArgumentException if {@code parent} is
   * identical to {@code chartBuilder}
   *
   * @exception IllegalStateException if a subclass has overridden the
   * {@link #createYaml()} method to return {@code null} and calls it
   * from this method for some reason
   *
   */
  protected void beginWrite(final Context context, final ChartOrBuilder parent, final ChartOrBuilder chartBuilder) throws IOException {
    Objects.requireNonNull(context);
    Objects.requireNonNull(chartBuilder);
    if (parent == chartBuilder) {
      throw new IllegalArgumentException("parent == chartBuilder");
    }
  }

  /**
   * A callback method invoked when the {@link #write(Context,
   * ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)} method has been invoked and it
   * is time to write a relevant {@link MetadataOrBuilder} object.
   *
   * @param context the {@link Context} representing the write
   * operation; must not be {@code null}
   *
   * @param metadata the {@link MetadataOrBuilder} to write; must not
   * be {@code null}
   *
   * @exception IOException if a write error occurs
   *
   * @exception NullPointerException if either {@code context} or
   * {@code metadata} is {@code null}
   *
   * @exception IllegalStateException if a subclass has overridden the
   * {@link #createYaml()} method to return {@code null} and calls it
   * from this method
   */
  protected abstract void writeMetadata(final Context context, final MetadataOrBuilder metadata) throws IOException;

  /**
   * A callback method invoked when the {@link #write(Context,
   * ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)} method has been invoked and it
   * is time to write a relevant {@link ConfigOrBuilder} object.
   *
   * @param context the {@link Context} representing the write
   * operation; must not be {@code null}
   *
   * @param config the {@link ConfigOrBuilder} to write; must not
   * be {@code null}
   *
   * @exception IOException if a write error occurs
   *
   * @exception NullPointerException if either {@code context} or
   * {@code config} is {@code null}
   *
   * @exception IllegalStateException if a subclass has overridden the
   * {@link #createYaml()} method to return {@code null} and calls it
   * from this method
   */
  protected abstract void writeConfig(final Context context, final ConfigOrBuilder config) throws IOException;

  /**
   * A callback method invoked when the {@link #write(Context,
   * ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)} method has been invoked and it
   * is time to write a relevant {@link TemplateOrBuilder} object.
   *
   * @param context the {@link Context} representing the write
   * operation; must not be {@code null}
   *
   * @param template the {@link TemplateOrBuilder} to write; must not
   * be {@code null}
   *
   * @exception IOException if a write error occurs
   *
   * @exception NullPointerException if either {@code context} or
   * {@code template} is {@code null}
   *
   * @exception IllegalStateException if a subclass has overridden the
   * {@link #createYaml()} method to return {@code null} and calls it
   * from this method for some reason
   */
  protected abstract void writeTemplate(final Context context, final TemplateOrBuilder template) throws IOException;

  /**
   * A callback method invoked when the {@link #write(Context,
   * ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)} method has been invoked and it
   * is time to write a relevant {@link AnyOrBuilder} object
   * (representing an otherwise undifferentiated Helm chart file).
   *
   * @param context the {@link Context} representing the write
   * operation; must not be {@code null}
   *
   * @param file the {@link AnyOrBuilder} to write; must not be {@code
   * null}
   *
   * @exception IOException if a write error occurs
   *
   * @exception NullPointerException if either {@code context} or
   * {@code file} is {@code null}
   *
   * @exception IllegalStateException if a subclass has overridden the
   * {@link #createYaml()} method to return {@code null} and calls it
   * from this method for some reason
   */
  protected abstract void writeFile(final Context context, final AnyOrBuilder file) throws IOException;

  /**
   * A callback method invoked when the {@link #write(Context,
   * ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)} method has been invoked and it
   * is time to write a relevant {@link ChartOrBuilder} object
   * (representing a subchart within an encompassing parent Helm
   * chart).
   *
   * <p>The default implementation of this method calls the {@link
   * #write(Context, ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)} method.</p>
   *
   * @param context the {@link Context} representing the write
   * operation; must not be {@code null}
   *
   * @param parent the {@link ChartOrBuilder} representing the Helm
   * chart that parents the {@code subchart} parameter value; must not
   * be {@code null}
   *
   * @param subchart the {@link ChartOrBuilder} representing the
   * subchart to write; must not be {@code null}
   *
   * @exception IOException if a write error occurs
   *
   * @exception NullPointerException if either {@code context} or
   * {@code parent} or {@code subchart} is {@code null}
   *
   * @exception IllegalArgumentException if {@code parent} is
   * identical to {@code subchart}, or if the {@link
   * ChartOrBuilder#getMetadata()} method returns {@code null} when
   * invoked on either non-{@code null} {@link ChartOrBuilder}, or if
   * the {@link MetadataOrBuilder#getName()} method returns {@code
   * null}, or if the {@link MetadataOrBuilder#getVersion()} method
   * returns {@code null}
   *
   * @exception IllegalStateException if a subclass has overridden the
   * {@link #createYaml()} method to return {@code null} and calls it
   * from this method for some reason
   *
   * @see #write(Context, ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)
   */
  protected void writeSubchart(final Context context, final ChartOrBuilder parent, final ChartOrBuilder subchart) throws IOException {
    this.write(Objects.requireNonNull(context), Objects.requireNonNull(parent), Objects.requireNonNull(subchart));
  }

  /**
   * A callback method invoked when the {@link #write(Context,
   * ChartOuterClass.ChartOrBuilder, ChartOuterClass.ChartOrBuilder)} method has been invoked and it
   * is time to end the write operation.
   *
   * <p>The default implementation of this method does nothing.</p>
   *
   * @param context the {@link Context} representing the write
   * operation; must not be {@code null}
   *
   * @param parent the {@link ChartOrBuilder} representing the Helm
   * chart that parents the {@code chartBuilder} parameter value; may be,
   * and often is, {@code null}
   *
   * @param chartBuilder the {@link ChartOrBuilder} representing the
   * chart currently involved in the write operation; must not be
   * {@code null}
   *
   * @exception IOException if a write error occurs
   *
   * @exception NullPointerException if either {@code context} or
   * {@code chartBuilder} is {@code null}
   *
   * @exception IllegalArgumentException if {@code parent} is
   * identical to {@code chartBuilder}
   *
   * @exception IllegalStateException if a subclass has overridden the
   * {@link #createYaml()} method to return {@code null} and calls it
   * from this method for some reason
   */
  protected void endWrite(final Context context, final ChartOrBuilder parent, final ChartOrBuilder chartBuilder) throws IOException {
    Objects.requireNonNull(context);
    Objects.requireNonNull(chartBuilder);
    if (parent == chartBuilder) {
      throw new IllegalArgumentException("parent == chartBuilder");
    }
  }


  /*
   * Inner and nested classes.
   */


  /**
   * A class representing the state of a write operation.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   */
  protected static abstract class Context {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link Context}.
     */
    private Context() {
      super();
    }


    /*
     * Instance methods.
     */

    /**
     * Returns the object indexed under the supplied {@code key}, if
     * any, {@linkplain Class#cast(Object) cast to the proper
     * <code>Class</code>}.
     *
     * <p>Implementations of this method may return {@code null}.</p>
     *
     * @param <T> the type of object expected
     *
     * @param key the key under which something is hopefully stored;
     * may be {@code null}
     *
     * @param type the {@link Class} to cast the result to; must not
     * be {@code null}
     *
     * @return the object in question, or {@code null}
     *
     * @exception NullPointerException if {@code type} is {@code null}
     *
     * @see #put(Object, Object)
     */
    public abstract <T> T get(final Object key, final Class<T> type);

    /**
     * Stores the supplied {@code value} under the supplied {@code key}.
     *
     * @param key the key under which the supplied {@code value} will
     * be stored; may be {@code null}
     *
     * @param value the object to store; may be {@code null}
     *
     * @see #get(Object, Class)
     */
    public abstract void put(final Object key, final Object value);

    /**
     * Returns {@code true} if this {@link Context} implementation
     * contains an object indexed under an {@link Object} {@linkplain
     * Object#equals(Object) equal to} the supplied {@code key}.
     *
     * @param key the key in question; may be {@code null}
     *
     * @return {@code true} if this {@link Context} implementation
     * contains an object indexed under an {@link Object} {@linkplain
     * Object#equals(Object) equal to} the supplied {@code key};
     * {@code false} otherwise
     */
    public abstract boolean containsKey(final Object key);

    /**
     * Removes any object indexed under an {@link Object} {@linkplain
     * Object#equals(Object) equal to} the supplied {@code key}.
     *
     * @param key the key in question; may be {@code null}
     */
    public abstract void remove(final Object key);
    
  }

  /**
   * A {@link Representer} that attempts not to output default values
   * or YAML tags.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   */
  private static final class TerseRepresenter extends Representer {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link TerseRepresenter}.
     */
    private TerseRepresenter() {
      super();
    }


    /*
     * Instance methods.
     */


    /**
     * Represents a Java bean normally, but without any YAML tag
     * information.
     *
     * @param properties a {@link Set} of {@link Property} instances
     * indicating what facets of the supplied {@code bean} should be
     * represented; ignored by this implementation
     *
     * @param bean the {@link Object} to represent; may be {@code null}
     *
     * @return the result of invoking {@link
     * Representer#representJavaBean(Set, Object)}, but after adding
     * {@link Tag#MAP} as a {@linkplain Representer#addClassTag(Class,
     * Tag) class tag} for the supplied {@code bean}'s class
     */
    @Override
    protected final MappingNode representJavaBean(final Set<Property> properties, final Object bean) {
      if (bean != null) {
        final Class<?> beanClass = bean.getClass();
        if (this.getTag(beanClass, null) == null) {
          this.addClassTag(beanClass, Tag.MAP);
        }
      }
      return super.representJavaBean(properties, bean);      
    }

    /**
     * Overrides the {@link
     * Representer#representJavaBeanProperty(Object, Property, Object,
     * Tag)} method to return {@code null} when the given property
     * value can be omitted from its YAML representation without loss
     * of information.
     *
     * @param bean the Java bean whose property value is being
     * represented; may be {@code null}
     *
     * @param property the {@link Property} whose value is being
     * represented; may be {@code null}
     *
     * @param value the value being represented; may be {@code null}
     *
     * @param tag the {@link Tag} in effect; may be {@code null}
     *
     * @return {@code null} or the result of invoking the {@link
     * Representer#representJavaBeanProperty(Object, Property, Object,
     * Tag)} method with the supplied values
     */
    @Override
    protected final NodeTuple representJavaBeanProperty(final Object bean, final Property property, final Object value, final Tag tag) {
      final NodeTuple returnValue;
      if (value == null || value.equals(Boolean.FALSE)) {
        returnValue = null;
      } else if (value instanceof CharSequence) {
        if (((CharSequence)value).length() <= 0) {
          returnValue = null;
        } else {
          returnValue = super.representJavaBeanProperty(bean, property, value, tag);
        }
      } else if (value instanceof Collection) {
        if (((Collection<?>)value).isEmpty()) {
          returnValue = null;
        } else {
          returnValue = super.representJavaBeanProperty(bean, property, value, tag);
        }
      } else if (value instanceof Map) {
        if (((Map<?, ?>)value).isEmpty()) {
          returnValue = null;
        } else {
          returnValue = super.representJavaBeanProperty(bean, property, value, tag);
        }
      } else if (value.getClass().isArray()) {
        if (Array.getLength(value) <= 0) {
          returnValue = null;
        } else {
          returnValue = super.representJavaBeanProperty(bean, property, value, tag);
        }
      } else {
        returnValue = super.representJavaBeanProperty(bean, property, value, tag);
      }
      return returnValue;
    }
    
  }

  /**
   * A {@link PropertyUtils} that knows how to represent certain
   * properties of certain Helm-related objects for the purposes of
   * serialization to YAML.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   */
  private static final class CustomPropertyUtils extends PropertyUtils {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link CustomPropertyUtils}.
     */
    private CustomPropertyUtils() {
      super();
    }


    /*
     * Instance methods.
     */
    

    /**
     * Returns a {@link Set} of {@link Property} instances that will
     * represent Java objects of the supplied {@code type} during YAML
     * serialization.
     *
     * <p>This implementation overrides the {@link
     * PropertyUtils#createPropertySet(Class, BeanAccess)} method to
     * build explicit representations for {@link MetadataOrBuilder},
     * {@link MaintainerOrBuilder} and {@link ConfigOrBuilder}
     * interfaces.</p>
     *
     * @param type a {@link Class} for which a {@link Set} of
     * representational {@link Property} instances should be returned;
     * may be {@code null}
     *
     * @param beanAccess ignored by this implementation
     *
     * @return a {@link Set} of {@link Property} instances; never
     * {@code null}
     */
    @Override
    protected final Set<Property> createPropertySet(final Class<?> type, final BeanAccess beanAccess) {
      final Set<Property> returnValue;
      if (MetadataOrBuilder.class.isAssignableFrom(type)) {
        returnValue = new TreeSet<>();
        try {
          returnValue.add(new MethodProperty(new PropertyDescriptor("apiVersion", type, "getApiVersion", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("appVersion", type, "getAppVersion", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("condition", type, "getCondition", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("deprecated", type, "getDeprecated", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("description", type, "getDescription", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("engine", type, "getEngine", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("home", type, "getHome", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("icon", type, "getIcon", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("keywords", type, "getKeywordsList", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("maintainers", type, "getMaintainersOrBuilderList", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("name", type, "getName", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("sources", type, "getSourcesList", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("tags", type, "getTags", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("tillerVersion", type, "getTillerVersion", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("version", type, "getVersion", null)));
        } catch (final IntrospectionException introspectionException) {
          throw new IllegalStateException(introspectionException.getMessage(), introspectionException);
        }
      } else if (MaintainerOrBuilder.class.isAssignableFrom(type)) {
        returnValue = new TreeSet<>();
        try {
          returnValue.add(new MethodProperty(new PropertyDescriptor("name", type, "getName", null)));
          returnValue.add(new MethodProperty(new PropertyDescriptor("email", type, "getEmail", null)));
        } catch (final IntrospectionException introspectionException) {
          throw new IllegalStateException(introspectionException.getMessage(), introspectionException);
        }
      } else if (ConfigOrBuilder.class.isAssignableFrom(type)) {
        returnValue = new TreeSet<>();
        try {
          returnValue.add(new MethodProperty(new PropertyDescriptor("raw", type, "getRaw", null)));
        } catch (final IntrospectionException introspectionException) {
          throw new IllegalStateException(introspectionException.getMessage(), introspectionException);
        }
      } else {
        returnValue = super.createPropertySet(type, beanAccess);
      }
      return returnValue;
    }
    
  }
  
}
