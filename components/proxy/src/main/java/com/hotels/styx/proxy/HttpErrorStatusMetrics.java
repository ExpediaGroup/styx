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
package com.hotels.styx.proxy;

import com.codahale.metrics.Counter;
import com.hotels.styx.server.HttpErrorStatusListener;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.plugins.spi.PluginException;

import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static java.util.Objects.requireNonNull;

/**
 * An error listener that reports error metrics to a {@link MetricRegistry}.
 */
public class HttpErrorStatusMetrics implements HttpErrorStatusListener {
    private final MetricRegistry metricRegistry;

    /**
     * Construct a reporter with a given registry to report to.
     *
     * @param metricRegistry registry to report to
     */
    public HttpErrorStatusMetrics(MetricRegistry metricRegistry) {
        this.metricRegistry = requireNonNull(metricRegistry);
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
    public void proxyErrorOccurred(HttpRequest request, HttpResponseStatus status, Throwable cause) {
        proxyErrorOccurred(status, cause);
    }

    @Override
    public void proxyWriteFailure(HttpRequest request, HttpResponse response, Throwable cause) {
        incrementExceptionCounter(cause, response.status());
    }

    @Override
    public void proxyingFailure(HttpRequest request, HttpResponse response, Throwable cause) {
        incrementExceptionCounter(cause, response.status());
    }

    private void incrementExceptionCounter(Throwable cause) {
        incrementExceptionCounter(cause, null);
    }

    private void incrementExceptionCounter(Throwable cause, HttpResponseStatus status) {
        if (!(cause instanceof PluginException)) {
            if (INTERNAL_SERVER_ERROR.equals(status)) {
                metricRegistry.meter("styx.errors").mark();
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

}
