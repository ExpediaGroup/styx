/*
  Copyright (C) 2013-2020 Expedia Inc.

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
import com.hotels.styx.server.HttpErrorStatusListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.net.InetSocketAddress;

import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;

/**
 * An error listener that reports error metrics to a {@link MeterRegistry}.
 */
public class HttpErrorStatusMetrics implements HttpErrorStatusListener {

    public static final String ERROR = "styx.error";
    public static final String EXCEPTION = "styx.exception";
    public static final String RESPONSE_STATUS = "styx.response.status";

    public static final String STATUS_TAG = "status";
    public static final String TYPE_TAG = "type";

    private final MeterRegistry meterRegistry;
    private final Counter styxErrors;

    /**
     * Construct a reporter with a given registry to report to.
     *
     * @param meterRegistry registry to report to
     */
    public HttpErrorStatusMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = requireNonNull(meterRegistry);

        // This means we can find the expected metric names in the registry, even before the corresponding events have occurred
        preregisterMetrics();
        styxErrors = meterRegistry.counter(ERROR);
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
                styxErrors.increment();
            }
            exceptionCounter(cause.getClass()).increment();
        }
    }

    private Counter exceptionCounter(Class<? extends Throwable> exceptionClass) {
        return meterRegistry.counter(EXCEPTION, TYPE_TAG, formattedExceptionName(exceptionClass));
    }

    private Counter statusCounter(int statusCode) {
        return meterRegistry.counter(RESPONSE_STATUS, STATUS_TAG, valueOf(statusCode));
    }

    static String formattedExceptionName(Class<? extends Throwable> type) {
        return type.getName().replace('.', '_');
    }

    private static boolean isError(HttpResponseStatus status) {
        return status.code() >= 400;
    }

    private void record(HttpResponseStatus status) {
        if (isError(status)) {
            statusCounter(status.code()).increment();
        }
    }

    // we can't preregister every possible name in these categories, but getting the prefix there will make things easier
    private void preregisterMetrics() {
        statusCounter(200);
        exceptionCounter(Exception.class);
    }
}
