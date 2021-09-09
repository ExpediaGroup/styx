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
package com.hotels.styx.proxy;

import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.PluginException;
import com.hotels.styx.metrics.CentralisedMetrics;
import com.hotels.styx.server.HttpErrorStatusListener;
import io.micrometer.core.instrument.MeterRegistry;

import java.net.InetSocketAddress;

import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.proxy.ExceptionMetricsKt.countBackendFault;
import static java.util.Objects.requireNonNull;

/**
 * An error listener that reports error metrics to a {@link MeterRegistry}.
 */
public class HttpErrorStatusMetrics implements HttpErrorStatusListener {
    private final CentralisedMetrics metrics;

    /**
     * Construct a reporter with a given registry to report to.
     *
     * @param metrics registry to report to
     */
    public HttpErrorStatusMetrics(CentralisedMetrics metrics) {
        this.metrics = requireNonNull(metrics);
    }

    @Override
    public void proxyErrorOccurred(HttpResponseStatus status, Throwable cause) {
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
                metrics.proxy().styxErrors().increment();
            } else if (status != null && status.code() > 500) {
                countBackendFault(metrics, cause);
            }
        }
    }

    private static boolean isError(HttpResponseStatus status) {
        return status.code() >= 400;
    }
}
