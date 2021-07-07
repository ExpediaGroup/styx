/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.common.SimpleCache;
import com.hotels.styx.metrics.CentralisedMetrics;
import io.micrometer.core.instrument.Counter;

import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.HEALTHY;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.UNHEALTHY;

/**
 * Health-check that works by making a request to a URL and ensuring that it gets an HTTP 200 OK code back.
 */
public class UrlRequestHealthCheck implements OriginHealthCheckFunction {
    private final String healthCheckUri;
    private final SimpleCache<Origin, Counter> meterCache;

    /**
     * Construct an instance.
     *
     * @param healthCheckUri URI to make health-check requests to
     */
    public UrlRequestHealthCheck(String healthCheckUri, CentralisedMetrics metrics) {
        this.healthCheckUri = uriWithInitialSlash(healthCheckUri);
        this.meterCache = metrics.proxy().client().originHealthCheckFailures();
    }

    private static String uriWithInitialSlash(String uri) {
        return uri.startsWith("/") ? uri : "/" + uri;
    }

    @Override
    public void check(HttpClient client, Origin origin, OriginHealthCheckFunction.Callback responseCallback) {
        HttpRequest request = newHealthCheckRequestFor(origin);

        client.send(request)
                .handle((response, cause) -> {
                    if (response != null) {
                        if (response.status().equals(OK)) {
                            responseCallback.originStateResponse(HEALTHY);
                        } else {
                            meterCache.get(origin).increment();
                            responseCallback.originStateResponse(UNHEALTHY);
                        }
                    } else if (cause != null) {
                        meterCache.get(origin).increment();
                        responseCallback.originStateResponse(UNHEALTHY);
                    }
                    return null;
                });
    }

    private HttpRequest newHealthCheckRequestFor(Origin origin) {
        return HttpRequest.get(healthCheckUri)
                .header(HOST, origin.hostAndPortString())
                .build();
    }
}
