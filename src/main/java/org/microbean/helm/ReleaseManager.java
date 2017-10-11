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
import hapi.services.tiller.Tiller.GetVersionRequest;
import hapi.services.tiller.Tiller.GetVersionRequestOrBuilder;
import hapi.services.tiller.Tiller.GetVersionResponse;
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

import org.microbean.helm.chart.Requirements;

public class ReleaseManager implements Closeable {


  /*
   * Static fields.
   */

  
  /**
   * A {@link Pattern} specifying the constraints that a Helm release
   * name should satisfy.
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
   */
  public static final Pattern RFC_1123_PATTERN = Pattern.compile("^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$");


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

  
  public ReleaseManager(final Tiller tiller) {
    super();
    Objects.requireNonNull(tiller);
    this.tiller = tiller;
  }


  /*
   * Instance methods.
   */

  
  /**
   * Calls {@link Tiller#close() close()} on the {@link Tiller}
   * instance {@linkplain #ReleaseManager(Tiller) supplied at
   * construction time}.
   *
   * @exception IOException if an error occurs
   */
  @Override
  public void close() throws IOException {
    this.tiller.close();
  }

  public Future<GetReleaseContentResponse> getContent(final GetReleaseContentRequestOrBuilder request)
    throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.tiller.getReleaseServiceFutureStub();
    assert stub != null;
    final Future<GetReleaseContentResponse> returnValue;
    if (request instanceof GetReleaseContentRequest.Builder) {
      returnValue = stub.getReleaseContent(((GetReleaseContentRequest.Builder)request).build());
    } else {
      assert request instanceof GetReleaseContentRequest;
      returnValue = stub.getReleaseContent((GetReleaseContentRequest)request);
    }
    return returnValue;
  }

  public Future<GetHistoryResponse> getHistory(final GetHistoryRequestOrBuilder request)
    throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.tiller.getReleaseServiceFutureStub();
    assert stub != null;
    final Future<GetHistoryResponse> returnValue;
    if (request instanceof GetHistoryRequest.Builder) {
      returnValue = stub.getHistory(((GetHistoryRequest.Builder)request).build());
    } else {
      assert request instanceof GetHistoryRequest;
      returnValue = stub.getHistory((GetHistoryRequest)request);
    }
    return returnValue;
  }

  public Future<GetVersionResponse> getVersion(final GetVersionRequestOrBuilder request)
    throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.tiller.getReleaseServiceFutureStub();
    assert stub != null;
    final Future<GetVersionResponse> returnValue;
    if (request instanceof GetVersionRequest.Builder) {
      returnValue = stub.getVersion(((GetVersionRequest.Builder)request).build());
    } else {
      assert request instanceof GetVersionRequest;
      returnValue = stub.getVersion((GetVersionRequest)request);
    }
    return returnValue;
  }   

  public Future<GetReleaseStatusResponse> getStatus(final GetReleaseStatusRequestOrBuilder request)
    throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.tiller.getReleaseServiceFutureStub();
    assert stub != null;
    final Future<GetReleaseStatusResponse> returnValue;
    if (request instanceof GetReleaseStatusRequest.Builder) {
      returnValue = stub.getReleaseStatus(((GetReleaseStatusRequest.Builder)request).build());
    } else {
      assert request instanceof GetReleaseStatusRequest;
      returnValue = stub.getReleaseStatus((GetReleaseStatusRequest)request);
    }
    return returnValue;
  }   
  
  
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
      final io.fabric8.kubernetes.client.Config configuration = this.tiller.getConfiguration();
      if (configuration == null) {
        requestBuilder.setNamespace("default");
      } else {
        releaseNamespace = configuration.getNamespace();
        if (releaseNamespace == null || releaseNamespace.isEmpty()) {
          requestBuilder.setNamespace("default");
        } else {
          requestBuilder.setNamespace(releaseNamespace);
        }
      }
    }
    
    final ReleaseServiceFutureStub stub = this.tiller.getReleaseServiceFutureStub();
    assert stub != null;
    return stub.installRelease(requestBuilder.build());
  }

  /**
   * @exception PatternSyntaxException if the {@link
   * Tiller.ListReleasesRequestOrBuilder#getFilter()} return value is
   * non-{@code null}, non-{@linkplain String#isEmpty() empty} but not
   * a {@linkplain Pattern#compile(String) valid regular expression}
   */
  public Iterator<ListReleasesResponse> list(final ListReleasesRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceBlockingStub stub = this.tiller.getReleaseServiceBlockingStub();
    assert stub != null;
    final Iterator<ListReleasesResponse> returnValue;
    if (request instanceof ListReleasesRequest.Builder) {
      returnValue = stub.listReleases(((ListReleasesRequest.Builder)request).build());
    } else {
      assert request instanceof ListReleasesRequest;
      returnValue = stub.listReleases((ListReleasesRequest)request);
    }
    return returnValue;
  }

  public Future<RollbackReleaseResponse> rollback(final RollbackReleaseRequestOrBuilder request)
    throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.tiller.getReleaseServiceFutureStub();
    assert stub != null;
    final Future<RollbackReleaseResponse> returnValue;
    if (request instanceof RollbackReleaseRequest.Builder) {
      returnValue = stub.rollbackRelease(((RollbackReleaseRequest.Builder)request).build());
    } else {
      assert request instanceof RollbackReleaseRequest;
      returnValue = stub.rollbackRelease((RollbackReleaseRequest)request);
    }
    return returnValue;
  }

  public Iterator<TestReleaseResponse> test(final TestReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceBlockingStub stub = this.tiller.getReleaseServiceBlockingStub();
    assert stub != null;
    final Iterator<TestReleaseResponse> returnValue;
    if (request instanceof TestReleaseRequest.Builder) {
      returnValue = stub.runReleaseTest(((TestReleaseRequest.Builder)request).build());
    } else {
      assert request instanceof TestReleaseRequest;
      returnValue = stub.runReleaseTest((TestReleaseRequest)request);
    }
    return returnValue;
  }
  
  public Future<UninstallReleaseResponse> uninstall(final UninstallReleaseRequestOrBuilder request)
    throws IOException {
    Objects.requireNonNull(request);
    validate(request);

    final ReleaseServiceFutureStub stub = this.tiller.getReleaseServiceFutureStub();
    assert stub != null;
    final Future<UninstallReleaseResponse> returnValue;
    if (request instanceof UninstallReleaseRequest.Builder) {
      returnValue = stub.uninstallRelease(((UninstallReleaseRequest.Builder)request).build());
    } else {
      assert request instanceof UninstallReleaseRequest;
      returnValue = stub.uninstallRelease((UninstallReleaseRequest)request);
    }
    return returnValue;
  }
  
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

    final ReleaseServiceFutureStub stub = this.tiller.getReleaseServiceFutureStub();
    assert stub != null;
    return stub.updateRelease(requestBuilder.build());
  }

  protected void validate(final GetReleaseContentRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  protected void validate(final GetHistoryRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  protected void validate(final GetReleaseStatusRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }
  
  protected void validate(final GetVersionRequestOrBuilder request) {
    Objects.requireNonNull(request);
  }
  
  protected void validate(final InstallReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  protected void validate(final ListReleasesRequestOrBuilder request) {
    Objects.requireNonNull(request);
    final String filter = request.getFilter();
    if (filter != null && !filter.isEmpty()) {
      Pattern.compile(filter);
    }
  }

  protected void validate(final RollbackReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  protected void validate(final TestReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }
  
  protected void validate(final UninstallReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  protected void validate(final UpdateReleaseRequestOrBuilder request) {
    Objects.requireNonNull(request);
    validateReleaseName(request.getName());
  }

  protected void validateReleaseName(final String name) {
    if (name != null && !name.isEmpty()) {
      final Matcher matcher = RFC_1123_PATTERN.matcher(name);
      assert matcher != null;
      if (!matcher.matches()) {
        throw new IllegalArgumentException("Invalid release name: " + name + "; must match " + RFC_1123_PATTERN.toString());
      }
    }
  }

}
