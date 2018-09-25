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
import com.hotels.styx.api.FullHttpRequest;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.common.SimpleCache;

import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.HEALTHY;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.UNHEALTHY;
import static java.lang.String.format;

/**
 * Health-check that works by making a request to a URL and ensuring that it gets an HTTP 200 OK code back.
 */
public class UrlRequestHealthCheck implements OriginHealthCheckFunction {
    private static final MeterFormat DEPRECATED_METER_FORMAT = new MeterFormat("origins.healthcheck.failure.%s");
    private static final MeterFormat CORRECTED_METER_FORMAT = new MeterFormat("origins.%s.healthcheck.failure");

    private final String healthCheckUri;
    private final SimpleCache<Origin, FailureMeter> meterCache;

    /**
     * Construct an instance.
     *
     * @param healthCheckUri URI to make health-check requests to
     * @param metricRegistry metric registry
     */
    public UrlRequestHealthCheck(String healthCheckUri, MetricRegistry metricRegistry) {
        this.healthCheckUri = uriWithInitialSlash(healthCheckUri);
        this.meterCache = new SimpleCache<>(origin -> new FailureMeter(origin, metricRegistry));
    }

    private static String uriWithInitialSlash(String uri) {
        return uri.startsWith("/") ? uri : "/" + uri;
    }

    @Override
    public void check(HttpClient client, Origin origin, OriginHealthCheckFunction.Callback responseCallback) {
        FullHttpRequest request = newHealthCheckRequestFor(origin);

        client.sendRequest(request)
                .handle((response, cause) -> {
                    if (response != null) {
                        if (response.status().equals(OK)) {
                            responseCallback.originStateResponse(HEALTHY);
                        } else {
                            meterCache.get(origin).mark();
                            responseCallback.originStateResponse(UNHEALTHY);
                        }
                    } else if (cause != null) {
                        meterCache.get(origin).mark();
                        responseCallback.originStateResponse(UNHEALTHY);
                    }
                    return null;
                });
    }

    private FullHttpRequest newHealthCheckRequestFor(Origin origin) {
        return FullHttpRequest.get(healthCheckUri)
                .header(HOST, origin.hostAndPortString())
                .build();
    }

    private static final class FailureMeter {
        private final Meter deprecatedMeter;
        private final Meter correctedMeter;

        FailureMeter(Origin origin, MetricRegistry metricRegistry) {
            this.deprecatedMeter = DEPRECATED_METER_FORMAT.meter(origin, metricRegistry);
            this.correctedMeter = CORRECTED_METER_FORMAT.meter(origin, metricRegistry);
        }

        void mark() {
            deprecatedMeter.mark();
            correctedMeter.mark();
        }
    }

    private static final class MeterFormat {
        private final String format;

        MeterFormat(String format) {
            this.format = format;
        }

        public Meter meter(Origin origin, MetricRegistry metricRegistry) {
            String name = format(format, origin.applicationId());

            return metricRegistry.meter(name);
        }
    }
}
