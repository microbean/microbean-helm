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

import java.io.Closeable;
import java.io.IOException;

import java.util.Iterator;
import java.util.Objects;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import hapi.chart.ChartOuterClass.Chart;

import hapi.release.ReleaseOuterClass.Release;

import hapi.services.tiller.ReleaseServiceGrpc.ReleaseServiceBlockingStub;
import hapi.services.tiller.ReleaseServiceGrpc.ReleaseServiceFutureStub;
import hapi.services.tiller.Tiller.GetHistoryRequest;
import hapi.services.tiller.Tiller.GetHistoryRequestOrBuilder;
import hapi.services.tiller.Tiller.GetHistoryResponse;
import hapi.services.tiller.Tiller.GetReleaseContentRequest;
import hapi.services.tiller.Tiller.GetReleaseContentRequestOrBuilder;
import hapi.services.tiller.Tiller.GetReleaseContentResponse;
import hapi.services.tiller.Tiller.GetReleaseStatusRequest;
import hapi.services.tiller.Tiller.GetReleaseStatusRequestOrBuilder;
import hapi.services.tiller.Tiller.GetReleaseStatusResponse;
import hapi.services.tiller.Tiller.InstallReleaseRequest;
import hapi.services.tiller.Tiller.InstallReleaseRequestOrBuilder;
import hapi.services.tiller.Tiller.InstallReleaseResponse;
import hapi.services.tiller.Tiller.ListReleasesRequest;
import hapi.services.tiller.Tiller.ListReleasesRequestOrBuilder;
import hapi.services.tiller.Tiller.ListReleasesResponse;
import hapi.services.tiller.Tiller.RollbackReleaseRequest;
import hapi.services.tiller.Tiller.RollbackReleaseRequestOrBuilder;
import hapi.services.tiller.Tiller.RollbackReleaseResponse;
import hapi.services.tiller.Tiller.TestReleaseRequest;
import hapi.services.tiller.Tiller.TestReleaseRequestOrBuilder;
import hapi.services.tiller.Tiller.TestReleaseResponse;
import hapi.services.tiller.Tiller.UninstallReleaseRequest;
import hapi.services.tiller.Tiller.UninstallReleaseRequestOrBuilder;
import hapi.services.tiller.Tiller.UninstallReleaseResponse;
import hapi.services.tiller.Tiller.UpdateReleaseRequest;
import hapi.services.tiller.Tiller.UpdateReleaseRequestOrBuilder;
import hapi.services.tiller.Tiller.UpdateReleaseResponse;

import org.microbean.helm.chart.MissingDependenciesException;
import org.microbean.helm.chart.Requirements;

/**
 * A manager of <a href="https://docs.helm.sh/glossary/#release">Helm releases</a>.
 *
 * @author <a href="https://about.me/lairdnelson/"
 * target="_parent">Laird Nelson</a>
 */
public class ReleaseManager implements Closeable {


  /*
   * Static fields.
   */

  
  /**
   * The maximum number of characters a <a
   * href="https://github.com/kubernetes/community/blob/master/contributors/design-proposals/architecture/identifiers.md#definitions">Kubernetes
   * identifier of type {@code DNS_SUBDOMAIN}</a> is permitted to contain
   * ({@value}).
   *
   * @see <a
   * href="https://github.com/kubernetes/community/blob/master/contributors/design-proposals/architecture/identifiers.md#definitions">Kubernetes
   * identifier definitions</a>
   */
  public static final int DNS_SUBDOMAIN_MAX_LENGTH = 253;

  /**
   * A {@link Pattern} specifying the constraints that a non-{@code
   * null}, non-{@linkplain String#isEmpty() empty} Helm release name
   * should satisfy.
   *
   * <p>Because Helm release names are often used in hostnames, they
   * should conform to <a
   * href="https://tools.ietf.org/html/rfc1123#page-13">RFC 1123</a>.
   * This {@link Pattern} reifies those constraints.</p>
   *
   * @see #validateReleaseName(String)
   *
   * @see <a href="https://tools.ietf.org/html/rfc1123#page-13">RFC
   * 1123</a>
   *
   * @see <a
   * href="https://github.com/kubernetes/community/blob/master/contributors/design-proposals/architecture/identifiers.md#definitions">Kubernetes
   * identifier definitions</a>
   */
  public static final Pattern DNS_SUBDOMAIN_PATTERN = Pattern.compile("^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$");

  /**
   * The maximum number of characters a <a
   * href="https://github.com/kubernetes/community/blob/master/contributors/design-proposals/architecture/identifiers.md#definitions">Kubernetes
   * identifier of type {@code DNS_LABEL}</a> is permitted to contain
   * ({@value}).
   *
   * @see <a
   * href="https://github.com/kubernetes/community/blob/master/contributors/design-proposals/architecture/identifiers.md#definitions">Kubernetes
   * identifier definitions</a>
   */
  public static final int DNS_LABEL_MAX_LENGTH = 63;

  /**
   * A {@link Pattern} specifying the constraints that a non-{@code
   * null}, non-{@linkplain String#isEmpty() empty} Kubernetes
   * namespace should satisfy.
   *
   * @see #validateNamespace(String)
   *
   * @see <a href="https://tools.ietf.org/html/rfc1123#page-13">RFC
   * 1123</a>
   *
   * @see <a
   * href="https://github.com/kubernetes/community/blob/master/contributors/design-proposals/architecture/identifiers.md#definitions">Kubernetes
   * identifier definitions</a>
   */
  public static final Pattern DNS_LABEL_PATTERN = Pattern.compile("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$");

  /**
   * The maximum number of characters a Helm release name is permitted
   * to contain ({@value}).
   *
   * @see <a href="https://github.com/kubernetes/helm/pull/1560">Helm pull request #1560</a>
   */
  public static final int HELM_RELEASE_NAME_MAX_LENGTH = 53;
  
  /**
   * An alias for the {@link #DNS_SUBDOMAIN_PATTERN} field.
   *
   * @see #DNS_SUBDOMAIN_PATTERN
   */
  public static final Pattern RFC_1123_PATTERN = DNS_SUBDOMAIN_PATTERN;


  /*
   * Instance fields.
   */

  
  /**
   * The {@link Tiller} instance used to communicate with Helm's
   * back-end Tiller component.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see Tiller
   */
  private final Tiller tiller;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link ReleaseManager}.
   *
   * @param tiller the {@link Tiller} instance representing a
   * connection to the <a
   * href="https://docs.helm.sh/architecture/#components">Tiller
   * server</a>; must not be {@code null}
   *
   * @exception NullPointerException if {@code tiller} is {@code null}
   *
   * @see Tiller
   */
  public ReleaseManager(final Tiller tiller) {
    super();
    Objects.requireNonNull(tiller);
    this.tiller = tiller;
  }


  /*
   * Instance methods.
   */


  /**
   * Returns the {@link Tiller} instance used to communicate with
   * Helm's back-end Tiller component.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return a non-{@code null} {@link Tiller}
   *
   * @see #ReleaseManager(Tiller)
   *
   * @see Tiller
   */
  protected final Tiller getTiller() {
    return this.tiller;
  }
  
  /**
   * Calls {@link Tiller#close() close()} on the {@link Tiller}
   * instance {@linkplain #ReleaseManager(Tiller) supplied at
   * construction time}.
   *
   * @exception IOException if an error occurs
   */
  @Override
  public void close() throws IOException {
    this.getTiller().close();
  }

  /**
   * Returns the content that made up a given Helm release.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param request the {@link GetReleaseContentRequest} describing
   * the release; must not be {@code null}
   *
   * @return a {@link Future} containing a {@link
   * GetReleaseContentResponse} that has the information requested;
   * never {@code null}
   *
   * @exception NullPointerException if {@code request} is {@code
   * null}
   */
  public Future<GetReleaseContentResponse> getContent(final GetReleaseContentRequest request) throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.getTiller().getReleaseServiceFutureStub();
    assert stub != null;
    return stub.getReleaseContent(request);
  }

  /**
   * Returns the history of a given Helm release.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param request the {@link GetHistoryRequest}
   * describing the release; must not be {@code null}
   *
   * @return a {@link Future} containing a {@link
   * GetHistoryResponse} that has the information requested;
   * never {@code null}
   *
   * @exception NullPointerException if {@code request} is {@code
   * null}
   */
  public Future<GetHistoryResponse> getHistory(final GetHistoryRequest request) throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.getTiller().getReleaseServiceFutureStub();
    assert stub != null;
    return stub.getHistory(request);
  }

  /**
   * Returns the status of a given Helm release.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param request the {@link GetReleaseStatusRequest} describing the
   * release; must not be {@code null}
   *
   * @return a {@link Future} containing a {@link
   * GetReleaseStatusResponse} that has the information requested;
   * never {@code null}
   *
   * @exception NullPointerException if {@code request} is {@code
   * null}
   */
  public Future<GetReleaseStatusResponse> getStatus(final GetReleaseStatusRequest request) throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.getTiller().getReleaseServiceFutureStub();
    assert stub != null;
    return stub.getReleaseStatus(request);
  }   
  
  /**
   * Installs a release.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param requestBuilder the {@link
   * hapi.services.tiller.Tiller.InstallReleaseRequest.Builder} representing the
   * installation request; must not be {@code null} and must
   * {@linkplain #validate(Tiller.InstallReleaseRequestOrBuilder) pass
   * validation}; its {@link
   * hapi.services.tiller.Tiller.InstallReleaseRequest.Builder#setChart(hapi.chart.ChartOuterClass.Chart.Builder)}
   * method will be called with the supplied {@code chartBuilder} as
   * its argument value
   *
   * @param chartBuilder a {@link
   * hapi.chart.ChartOuterClass.Chart.Builder} representing the Helm
   * chart to install; must not be {@code null}
   *
   * @return a {@link Future} containing a {@link
   * InstallReleaseResponse} that has the information requested; never
   * {@code null}
   *
   * @exception MissingDependenciesException if the supplied {@code
   * chartBuilder} has a {@code requirements.yaml} resource in it that
   * mentions subcharts that it does not contain
   * 
   * @exception NullPointerException if {@code request} is {@code
   * null}
   *
   * @see org.microbean.helm.chart.AbstractChartLoader
   */
  public Future<InstallReleaseResponse> install(final InstallReleaseRequest.Builder requestBuilder,
                                                final Chart.Builder chartBuilder)
    throws IOException {
    Objects.requireNonNull(requestBuilder);
    Objects.requireNonNull(chartBuilder);
    validate(requestBuilder);

    // Note that the mere act of calling getValuesBuilder() has the
    // convenient if surprising side effect of initializing the
    // values-related innards of requestBuilder if they haven't yet
    // been set such that, for example, requestBuilder.getValues()
    // will no longer return null under any circumstances.  If instead
    // here we called requestBuilder.getValues(), null *would* be
    // returned.  For *our* code, this is fine, but Tiller's code
    // crashes when there's a null in the values slot.
    requestBuilder.setChart(Requirements.apply(chartBuilder, requestBuilder.getValuesBuilder()));
    
    String releaseNamespace = requestBuilder.getNamespace();
    if (releaseNamespace == null || releaseNamespace.isEmpty()) {
      final io.fabric8.kubernetes.client.Config configuration = this.getTiller().getConfiguration();
      if (configuration == null) {
        requestBuilder.setNamespace("default");
      } else {
        releaseNamespace = configuration.getNamespace();
        if (releaseNamespace == null || releaseNamespace.isEmpty()) {
          requestBuilder.setNamespace("default");
        } else {
          this.validateNamespace(releaseNamespace);
          requestBuilder.setNamespace(releaseNamespace);
        }
      }
    } else {
      this.validateNamespace(releaseNamespace);
    }
    
    final ReleaseServiceFutureStub stub = this.getTiller().getReleaseServiceFutureStub();
    assert stub != null;
    return stub.installRelease(requestBuilder.build());
  }

  /**
   * Returns information about Helm releases.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param request the {@link ListReleasesRequest} describing the
   * releases to be returned; must not be {@code null}
   *
   * @return an {@link Iterator} of {@link ListReleasesResponse}
   * objects comprising the information requested; never {@code null}
   *
   * @exception NullPointerException if {@code request} is {@code
   * null}
   *
   * @exception PatternSyntaxException if the {@link
   * ListReleasesRequestOrBuilder#getFilter()} return value is
   * non-{@code null}, non-{@linkplain String#isEmpty() empty} but not
   * a {@linkplain Pattern#compile(String) valid regular expression}
   */
  public Iterator<ListReleasesResponse> list(final ListReleasesRequest request) {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceBlockingStub stub = this.getTiller().getReleaseServiceBlockingStub();
    assert stub != null;
    return stub.listReleases(request);
  }

  /**
   * Rolls back a previously installed release.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param request the {@link RollbackReleaseRequest} describing the
   * release; must not be {@code null}
   *
   * @return a {@link Future} containing a {@link
   * RollbackReleaseResponse} that has the information requested;
   * never {@code null}
   *
   * @exception NullPointerException if {@code request} is {@code
   * null}
   */
  public Future<RollbackReleaseResponse> rollback(final RollbackReleaseRequest request)
    throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.getTiller().getReleaseServiceFutureStub();
    assert stub != null;
    return stub.rollbackRelease(request);
  }

  /**
   * Returns information about tests run on a given Helm release.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param request the {@link TestReleaseRequest} describing the
   * release to be tested; must not be {@code null}
   *
   * @return an {@link Iterator} of {@link TestReleaseResponse}
   * objects comprising the information requested; never {@code null}
   *
   * @exception NullPointerException if {@code request} is {@code
   * null}
   */
  public Iterator<TestReleaseResponse> test(final TestReleaseRequest request) {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceBlockingStub stub = this.getTiller().getReleaseServiceBlockingStub();
    assert stub != null;
    return stub.runReleaseTest(request);
  }

  /**
   * Uninstalls (deletes) a previously installed release.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param request the {@link UninstallReleaseRequest} describing the
   * release; must not be {@code null}
   *
   * @return a {@link Future} containing a {@link
   * UninstallReleaseResponse} that has the information requested;
   * never {@code null}
   *
   * @exception NullPointerException if {@code request} is {@code
   * null}
   */
  public Future<UninstallReleaseResponse> uninstall(final UninstallReleaseRequest request)
    throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.getTiller().getReleaseServiceFutureStub();
    assert stub != null;
    return stub.uninstallRelease(request);
  }

  /**
   * Updates a release.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param requestBuilder the {@link
   * hapi.services.tiller.Tiller.UpdateReleaseRequest.Builder}
   * representing the installation request; must not be {@code null}
   * and must {@linkplain
   * #validate(Tiller.UpdateReleaseRequestOrBuilder) pass validation};
   * its {@link
   * hapi.services.tiller.Tiller.UpdateReleaseRequest.Builder#setChart(hapi.chart.ChartOuterClass.Chart.Builder)}
   * method will be called with the supplied {@code chartBuilder} as
   * its argument value
   *
   * @param chartBuilder a {@link
   * hapi.chart.ChartOuterClass.Chart.Builder} representing the Helm
   * chart with which to update the release; must not be {@code null}
   *
   * @return a {@link Future} containing a {@link
   * UpdateReleaseResponse} that has the information requested; never
   * {@code null}
   *
   * @exception NullPointerException if {@code request} is {@code
   * null}
   *
   * @see org.microbean.helm.chart.AbstractChartLoader
   */
  public Future<UpdateReleaseResponse> update(final UpdateReleaseRequest.Builder requestBuilder,
                                              final Chart.Builder chartBuilder)
    throws IOException {
    Objects.requireNonNull(requestBuilder);
    Objects.requireNonNull(chartBuilder);
    validate(requestBuilder);
    
    // Note that the mere act of calling getValuesBuilder() has the
    // convenient if surprising side effect of initializing the
    // values-related innards of requestBuilder if they haven't yet
    // been set such that, for example, requestBuilder.getValues()
    // will no longer return null under any circumstances.  If instead
    // here we called requestBuilder.getValues(), null *would* be
    // returned.  For *our* code, this is fine, but Tiller's code
    // crashes when there's a null in the values slot.
    requestBuilder.setChart(Requirements.apply(chartBuilder, requestBuilder.getValuesBuilder()));

    final ReleaseServiceFutureStub stub = this.getTiller().getReleaseServiceFutureStub();
    assert stub != null;
    return stub.updateRelease(requestBuilder.build());
  }

  /**
   * Validates the supplied {@link GetReleaseContentRequestOrBuilder}.
   *
   * @param request the request to validate
   *
   * @exception NullPointerException if {@code request} is {@code null}
   *
   * @exception IllegalArgumentException if {@code request} is invalid
   *
   * @see #validateReleaseName(String)
   */
  protected void validate(final GetReleaseContentRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  /**
   * Validates the supplied {@link GetHistoryRequestOrBuilder}.
   *
   * @param request the request to validate
   *
   * @exception NullPointerException if {@code request} is {@code null}
   *
   * @exception IllegalArgumentException if {@code request} is invalid
   *
   * @see #validateReleaseName(String)
   */
  protected void validate(final GetHistoryRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  /**
   * Validates the supplied {@link GetReleaseStatusRequestOrBuilder}.
   *
   * @param request the request to validate
   *
   * @exception NullPointerException if {@code request} is {@code null}
   *
   * @exception IllegalArgumentException if {@code request} is invalid
   *
   * @see #validateReleaseName(String)
   */
  protected void validate(final GetReleaseStatusRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  /**
   * Validates the supplied {@link InstallReleaseRequestOrBuilder}.
   *
   * @param request the request to validate
   *
   * @exception NullPointerException if {@code request} is {@code null}
   *
   * @exception IllegalArgumentException if {@code request} is invalid
   *
   * @see #validateReleaseName(String)
   */
  protected void validate(final InstallReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  /**
   * Validates the supplied {@link ListReleasesRequestOrBuilder}.
   *
   * @param request the request to validate
   *
   * @exception NullPointerException if {@code request} is {@code null}
   *
   * @exception IllegalArgumentException if {@code request} is invalid
   *
   * @see #validateReleaseName(String)
   */
  protected void validate(final ListReleasesRequestOrBuilder request) {
    Objects.requireNonNull(request);
    final String filter = request.getFilter();
    if (filter != null && !filter.isEmpty()) {
      Pattern.compile(filter);
    }
  }

  /**
   * Validates the supplied {@link RollbackReleaseRequestOrBuilder}.
   *
   * @param request the request to validate
   *
   * @exception NullPointerException if {@code request} is {@code null}
   *
   * @exception IllegalArgumentException if {@code request} is invalid
   *
   * @see #validateReleaseName(String)
   */
  protected void validate(final RollbackReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  /**
   * Validates the supplied {@link TestReleaseRequestOrBuilder}.
   *
   * @param request the request to validate
   *
   * @exception NullPointerException if {@code request} is {@code null}
   *
   * @exception IllegalArgumentException if {@code request} is invalid
   *
   * @see #validateReleaseName(String)
   */
  protected void validate(final TestReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  /**
   * Validates the supplied {@link UninstallReleaseRequestOrBuilder}.
   *
   * @param request the request to validate
   *
   * @exception NullPointerException if {@code request} is {@code null}
   *
   * @exception IllegalArgumentException if {@code request} is invalid
   *
   * @see #validateReleaseName(String)
   */
  protected void validate(final UninstallReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  /**
   * Validates the supplied {@link UpdateReleaseRequestOrBuilder}.
   *
   * @param request the request to validate
   *
   * @exception NullPointerException if {@code request} is {@code null}
   *
   * @exception IllegalArgumentException if {@code request} is invalid
   *
   * @see #validateReleaseName(String)
   */
  protected void validate(final UpdateReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  /**
   * Ensures that the supplied {@code name} is a valid Helm release
   * name.
   *
   * <p>Because frequently Helm releases are not required to be named
   * by the end user, a {@code null} or {@linkplain String#isEmpty()
   * empty} {@code name} is valid.</p>
   *
   * <p>Because Helm release names are often used in hostnames, they
   * should conform to <a
   * href="https://tools.ietf.org/html/rfc1123#page-13">RFC 1123</a>.
   * This method performs that validation by default, using the {@link
   * #DNS_SUBDOMAIN_PATTERN} field.</p>
   *
   * @param name the name to validate; may be {@code null} or
   * {@linkplain String#isEmpty()} since Tiller will generate a valid
   * name in such a case using the <a
   * href="https://github.com/technosophos/moniker">{@code
   * moniker}</a> project; if non-{@code null} must match the pattern
   * represented by the value of the {@link #DNS_SUBDOMAIN_PATTERN} field
   *
   * @see #DNS_SUBDOMAIN_PATTERN
   *
   * @see <a href="https://tools.ietf.org/html/rfc1123#page-13">RFC
   * 1123</a>
   */
  protected void validateReleaseName(final String name) {    
    if (name != null) {
      final int nameLength = name.length();
      if (nameLength > HELM_RELEASE_NAME_MAX_LENGTH) {
        throw new IllegalArgumentException("Invalid release name: " + name + "; length is greater than " + HELM_RELEASE_NAME_MAX_LENGTH + " characters: " + nameLength);
      } else if (nameLength > 0) {
        final Matcher matcher = DNS_SUBDOMAIN_PATTERN.matcher(name);
        assert matcher != null;
        if (!matcher.matches()) {
          throw new IllegalArgumentException("Invalid release name: " + name + "; must match " + DNS_SUBDOMAIN_PATTERN.toString());
        }
      }
    }
  }

  /**
   * Ensures that the supplied {@code namespace} is a valid namespace.
   *
   * <p>Namespaces <a
   * href="https://github.com/kubernetes/community/blob/master/contributors/design-proposals/architecture/identifiers.md#general-design">must
   * conform</a> to <a
   * href="https://tools.ietf.org/html/rfc1123#page-13">RFC 1123</a>.
   * This method performs that validation by default, using the {@link
   * #DNS_SUBDOMAIN_PATTERN} field.</p>
   *
   * @param namespace the namespace to validate; may be {@code null} or
   * {@linkplain String#isEmpty()}
   *
   * @see #DNS_LABEL_PATTERN
   *
   * @see <a href="https://tools.ietf.org/html/rfc1123#page-13">RFC
   * 1123</a>
   */
  protected void validateNamespace(final String namespace) {
    if (namespace != null) {
      final int namespaceLength = namespace.length();
      if (namespaceLength > DNS_LABEL_MAX_LENGTH) {
        throw new IllegalArgumentException("Invalid namespace: " + namespace + "; length is greater than " + DNS_LABEL_MAX_LENGTH + " characters: " + namespaceLength);
      } else if (namespaceLength > 0) {
        final Matcher matcher = DNS_LABEL_PATTERN.matcher(namespace);
        assert matcher != null;
        if (!matcher.matches()) {
          throw new IllegalArgumentException("Invalid namespace: " + namespace + "; must match " + DNS_LABEL_PATTERN.toString());
        }
      }
    }
  }

}
