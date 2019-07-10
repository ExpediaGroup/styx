/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.proxy;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.plugins.spi.PluginException;
import com.hotels.styx.server.HttpErrorStatusListener;

import java.net.InetSocketAddress;

import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static java.util.Objects.requireNonNull;

/**
 * An error listener that reports error metrics to a {@link MetricRegistry}.
 */
public class HttpErrorStatusMetrics implements HttpErrorStatusListener {
    private final MetricRegistry metricRegistry;
    private final Meter styxErrors;

    /**
     * Construct a reporter with a given registry to report to.
     *
     * @param metricRegistry registry to report to
     */
    public HttpErrorStatusMetrics(MetricRegistry metricRegistry) {
        this.metricRegistry = requireNonNull(metricRegistry);

        // This means we can find the expected metric names in the registry, even before the corresponding events have occurred
        preregisterMetrics();
        styxErrors = metricRegistry.meter("styx.errors");
    }

    @Override
    public void proxyErrorOccurred(HttpResponseStatus status, Throwable cause) {
        record(status);

        if (isError(status)) {
            incrementExceptionCounter(cause, status);
        }
    }

    @Override
    public void proxyErrorOccurred(Throwable cause) {
        incrementExceptionCounter(cause);
    }

    @Override
    public void proxyErrorOccurred(LiveHttpRequest request, InetSocketAddress clientAddress, HttpResponseStatus status, Throwable cause) {
        proxyErrorOccurred(status, cause);
    }

    @Override
    public void proxyWriteFailure(LiveHttpRequest request, LiveHttpResponse response, Throwable cause) {
        incrementExceptionCounter(cause, response.status());
    }

    @Override
    public void proxyingFailure(LiveHttpRequest request, LiveHttpResponse response, Throwable cause) {
        incrementExceptionCounter(cause, response.status());
    }

    private void incrementExceptionCounter(Throwable cause) {
        incrementExceptionCounter(cause, null);
    }

    private void incrementExceptionCounter(Throwable cause, HttpResponseStatus status) {
        if (!(cause instanceof PluginException)) {
            if (INTERNAL_SERVER_ERROR.equals(status)) {
                styxErrors.mark();
            }
            exceptionCounter(cause).inc();
        }
    }

    private Counter exceptionCounter(Throwable exception) {
        return metricRegistry.counter("styx.exception." + formattedExceptionName(exception.getClass()));
    }

    static String formattedExceptionName(Class<? extends Throwable> type) {
        return type.getName().replace('.', '_');
    }

    private static boolean isError(HttpResponseStatus status) {
        return status.code() >= 400;
    }

    private void record(HttpResponseStatus status) {
        if (isError(status)) {
            metricRegistry.counter("styx.response.status." + status.code()).inc();
        }
    }

    // we can't preregister every possible name in these categories, but getting the prefix there will make things easier
    private void preregisterMetrics() {
        metricRegistry.counter("styx.response.status.200");
        metricRegistry.counter("styx.exception." + formattedExceptionName(Exception.class));
    }
}
