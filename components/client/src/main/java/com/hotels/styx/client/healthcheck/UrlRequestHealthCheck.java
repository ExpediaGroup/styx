/*
  Copyright (C) 2013-2018 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.client.healthcheck;

import com.codahale.metrics.Meter;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.common.SimpleCache;
import io.netty.buffer.ByteBuf;
import rx.Observer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.HEALTHY;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.UNHEALTHY;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.util.ReferenceCountUtil.release;

/**
 * Health-check that works by making a request to a URL and ensuring that it gets an HTTP 200 OK code back.
 */
public class UrlRequestHealthCheck implements OriginHealthCheckFunction {
    private static final Observer<ByteBuf> RELEASE_BUFFER = new DoNothingObserver();

    private final String healthCheckUri;
    private final HttpClient client;
    private final SimpleCache<Origin, Meter> meterCache;

    /**
     * Construct an instance.
     *
     * @param healthCheckUri URI to make health-check requests to
     * @param client         HTTP client to make health-check requests with
     * @param metricRegistry metric registry
     */
    public UrlRequestHealthCheck(String healthCheckUri, HttpClient client, MetricRegistry metricRegistry) {
        this.healthCheckUri = uriWithInitialSlash(healthCheckUri);
        this.client = checkNotNull(client);
        this.meterCache = new SimpleCache<>(origin -> metricRegistry.meter("origins.healthcheck.failure." + origin.applicationId()));
    }

    private static String uriWithInitialSlash(String uri) {
        return uri.startsWith("/") ? uri : "/" + uri;
    }

    @Override
    public void check(Origin origin, OriginHealthCheckFunction.Callback responseCallback) {
        HttpRequest request = newHealthCheckRequestFor(origin);

        client.sendRequest(request).subscribe(response -> {
            if (response.status().equals(OK)) {
                responseCallback.originStateResponse(HEALTHY);
            } else {
                meterCache.get(origin).mark();
                responseCallback.originStateResponse(UNHEALTHY);
            }

            response.body().content().subscribe(RELEASE_BUFFER);
        }, error -> {
            meterCache.get(origin).mark();
            responseCallback.originStateResponse(UNHEALTHY);
        });
    }

    private HttpRequest newHealthCheckRequestFor(Origin origin) {
        return get(healthCheckUri)
                .header(HOST, origin.hostAsString())
                .build();
    }

    // Note: this differs from just calling Observable.subscribe with no arguments, because it ignores errors too, whereas subscribe() throws an exception
    private static class DoNothingObserver implements Observer<ByteBuf> {
        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onNext(ByteBuf byteBuf) {
            release(byteBuf);
        }
    }
}
