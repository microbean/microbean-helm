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

import java.util.Objects;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import hapi.chart.ChartOuterClass.Chart;

import hapi.release.ReleaseOuterClass.Release;

import hapi.services.tiller.ReleaseServiceGrpc.ReleaseServiceFutureStub;
import hapi.services.tiller.Tiller.InstallReleaseRequest;
import hapi.services.tiller.Tiller.InstallReleaseResponse;
import hapi.services.tiller.Tiller.UpdateReleaseRequest;
import hapi.services.tiller.Tiller.UpdateReleaseResponse;

import org.microbean.helm.chart.Requirements;

public class ReleaseManager implements Closeable {

  private final Tiller tiller;

  public ReleaseManager(final Tiller tiller) {
    super();
    Objects.requireNonNull(tiller);
    this.tiller = tiller;
  }

  @Override
  public void close() throws IOException {
    this.tiller.close();
  }

  public Future<Release> update(final UpdateReleaseRequest.Builder requestBuilder,
                                final Chart.Builder chartBuilder)
    throws IOException {
    Objects.requireNonNull(requestBuilder);
    Objects.requireNonNull(chartBuilder);

    requestBuilder.setChart(Requirements.apply(chartBuilder, requestBuilder.getValues()));

    final ReleaseServiceFutureStub stub = this.tiller.getReleaseServiceFutureStub();
    assert stub != null;
    
    final UpdateReleaseRequest request = requestBuilder.build();
    assert request != null;
    
    final Future<UpdateReleaseResponse> responseFuture = stub.updateRelease(request);
    
    final FutureTask<Release> returnValue;
    if (responseFuture == null) {
      returnValue = new FutureTask<>(() -> {}, null);
    } else {
      returnValue = new FutureTask<>(() -> {
          final Release rv;
          final UpdateReleaseResponse response = responseFuture.get();
          if (response == null) {
            rv = null;
          } else {
            rv = response.getRelease();
          }
          return rv;
        });
    }
    
    returnValue.run();
    
    return returnValue;    
  }
  
  public Future<Release> install(final InstallReleaseRequest.Builder requestBuilder,
                                 final Chart.Builder chartBuilder)
    throws IOException {
    Objects.requireNonNull(requestBuilder);
    Objects.requireNonNull(chartBuilder);

    requestBuilder.setChart(Requirements.apply(chartBuilder, requestBuilder.getValues()));
    
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
    
    final InstallReleaseRequest request = requestBuilder.build();
    assert request != null;
    
    final Future<InstallReleaseResponse> responseFuture = stub.installRelease(request);
    
    final FutureTask<Release> returnValue;
    if (responseFuture == null) {
      returnValue = new FutureTask<>(() -> {}, null);
    } else {
      returnValue = new FutureTask<>(() -> {
          final Release rv;
          final InstallReleaseResponse response = responseFuture.get();
          if (response == null) {
            rv = null;
          } else {
            rv = response.getRelease();
          }
          return rv;
        });
    }
    
    returnValue.run();
    
    return returnValue;
  }

}
