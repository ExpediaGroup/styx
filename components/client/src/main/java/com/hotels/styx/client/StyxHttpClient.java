/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.ConnectionPoolProvider;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.netty.HttpRequestOperation;
import com.hotels.styx.client.retry.RetryNTimes;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import rx.Observable;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.client.stickysession.StickySessionCookie.newStickySessionCookie;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A configurable HTTP client that uses connection pooling, load balancing, etc.
 */
public final class StyxHttpClient implements HttpClient, Identifiable {
    private static final Logger LOGGER = getLogger(StyxHttpClient.class);

    private final BackendService backendService;
    private final Id id;
    private final RewriteRuleset rewriteRuleset;
    private final RetryPolicy retryPolicy;
    private final boolean flowControlEnabled;
    private final Transport transport;
    private final MetricRegistry metricsRegistry;
    private final boolean contentValidation;
    private final StyxHeaderConfig styxHeaderConfig;
    private final OriginStatsFactory originStatsFactory;

    private final ConnectionPoolProvider connectionPoolProvider;

    private StyxHttpClient(Builder builder) {

        this.backendService = builder.backendService;
        this.id = backendService.id();

        this.flowControlEnabled = builder.flowControlEnabled;

        HttpRequestOperationFactory requestOperationFactory = builder.requestOperationFactory != null
                ? builder.requestOperationFactory
                : request -> new HttpRequestOperation(request, builder.originStatsFactory, flowControlEnabled,
                backendService.responseTimeoutMillis(), builder.requestLoggingEnabled, builder.longFormat);


        this.retryPolicy = builder.retryPolicy != null
                ? builder.retryPolicy
                : new RetryNTimes(3);

        this.rewriteRuleset = new RewriteRuleset(builder.rewriteRules);
        this.transport = new Transport(requestOperationFactory, id, builder.styxHeaderConfig);

        this.metricsRegistry = builder.metricsRegistry;
        this.contentValidation = builder.contentValidation;

        this.styxHeaderConfig = builder.styxHeaderConfig;

        this.originStatsFactory = builder.originStatsFactory;

        this.connectionPoolProvider = builder.connectionPoolProvider;
    }

    /**
     * Create a new builder.
     *
     * @return a new builder
     */
    public static Builder newHttpClientBuilder(BackendService backendService) {
        return new Builder(backendService);
    }

    @Override
    public Id id() {
        return id;
    }

    RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    Transport transport() {
        return transport;
    }

    private static boolean isError(HttpResponseStatus status) {
        return status.code() >= 400;
    }

    private static boolean bodyNeedsToBeRemoved(HttpRequest request, HttpResponse response) {
        return isHeadRequest(request) || isBodilessResponse(response);
    }

    private static HttpResponse responseWithoutBody(HttpResponse response) {
        return response.newBuilder()
                .header(CONTENT_LENGTH, 0)
                .removeHeader(TRANSFER_ENCODING)
                .removeBody()
                .build();
    }

    private static boolean isBodilessResponse(HttpResponse response) {
        int status = response.status().code();
        return status == 204 || status == 304 || status / 100 == 1;
    }

    private static boolean isHeadRequest(HttpRequest request) {
        return request.method().equals(HEAD);
    }

    @Override
    public Observable<HttpResponse> sendRequest(HttpRequest request) {
        HttpRequest rewrittenRequest = rewriteUrl(request);
        Optional<ConnectionPool> pool = connectionPoolProvider.connectionPool(request);

        HttpTransaction txn = transport.send(rewrittenRequest, pool);

        RetryOnErrorHandler retryHandler = new RetryOnErrorHandler.Builder()
                .client(this)
                .attemptCount(0)
                .request(rewrittenRequest)
                .connectionPoolProvider(connectionPoolProvider)
                .previouslyUsedOrigin(pool.orElse(null))
                .transaction(txn)
                .build();

        return txn.response()
                .onErrorResumeNext(retryHandler)
                .map(this::addStickySessionIdentifier)
                .doOnError(throwable -> logError(rewrittenRequest, throwable))
                .doOnUnsubscribe(() -> {
                    pool.ifPresent(connectionPool -> originStatsFactory.originStats(connectionPool.getOrigin()).requestCancelled());
                    retryHandler.cancel();
                })
                .doOnNext(this::recordErrorStatusMetrics)
                .map(response -> removeUnexpectedResponseBody(request, response))
                .map(this::removeRedundantContentLengthHeader);
    }

    private static void logError(HttpRequest rewrittenRequest, Throwable throwable) {
        LOGGER.error("Error Handling request={} exceptionClass={} exceptionMessage=\"{}\"",
                new Object[]{rewrittenRequest, throwable.getClass().getName(), throwable.getMessage()});
    }

    private HttpResponse removeUnexpectedResponseBody(HttpRequest request, HttpResponse response) {
        if (contentValidation && bodyNeedsToBeRemoved(request, response)) {
            return responseWithoutBody(response);
        } else {
            return response;
        }
    }

    private HttpResponse removeRedundantContentLengthHeader(HttpResponse response) {
        if (contentValidation && response.contentLength().isPresent() && response.chunked()) {
            return response.newBuilder()
                    .removeHeader(CONTENT_LENGTH)
                    .build();
        }
        return response;
    }

    private void recordErrorStatusMetrics(HttpResponse response) {
        if (isError(response.status())) {
            metricsRegistry.counter("origins.response.status." + response.status().code()).inc();
        }
    }

    private HttpResponse addStickySessionIdentifier(HttpResponse httpResponse) {
        if (backendService.stickySessionConfig().stickySessionEnabled()) {
            Optional<String> originId = httpResponse.header(styxHeaderConfig.originIdHeaderName());
            if (originId.isPresent()) {
                int maxAge = backendService.stickySessionConfig().stickySessionTimeoutSeconds();
                return httpResponse.newBuilder()
                        .addCookie(newStickySessionCookie(id, Id.id(originId.get()), maxAge))
                        .build();
            }
        }
        return httpResponse;
    }

    private HttpRequest rewriteUrl(HttpRequest request) {
        return rewriteRuleset.rewrite(request);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("id", id)
                .add("stickySessionConfig", backendService.stickySessionConfig())
                .add("retryPolicy", retryPolicy)
                .add("rewriteRuleset", rewriteRuleset)
                .add("flowControlEnabled", flowControlEnabled)
                .toString();
    }

    /**
     * A builder for {@link com.hotels.styx.client.StyxHttpClient}.
     */
    public static class Builder {
        private final BackendService backendService;
        private MetricRegistry metricsRegistry = new CodaHaleMetricRegistry();
        private boolean flowControlEnabled;
        private List<RewriteRule> rewriteRules = emptyList();
        private HttpRequestOperationFactory requestOperationFactory;
        private RetryPolicy retryPolicy;
        private boolean contentValidation;
        private boolean requestLoggingEnabled;
        private boolean longFormat;
        private StyxHeaderConfig styxHeaderConfig = new StyxHeaderConfig();
        private OriginStatsFactory originStatsFactory;
        private ConnectionPoolProvider connectionPoolProvider;

        public Builder(BackendService backendService) {
            this.backendService = checkNotNull(backendService);
        }

        public Builder metricsRegistry(MetricRegistry metricsRegistry) {
            this.metricsRegistry = checkNotNull(metricsRegistry);
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = checkNotNull(retryPolicy);
            return this;
        }

        public Builder flowControlEnabled(boolean enabled) {
            this.flowControlEnabled = enabled;
            return this;
        }

        public Builder rewriteRules(List<? extends RewriteRule> rewriteRules) {
            this.rewriteRules = ImmutableList.copyOf(rewriteRules);
            return this;
        }

        @VisibleForTesting
        Builder requestOperationFactory(HttpRequestOperationFactory requestOperationFactory) {
            this.requestOperationFactory = requestOperationFactory;
            return this;
        }

        public Builder requestLoggingEnabled(boolean requestLoggingEnabled) {
            this.requestLoggingEnabled = requestLoggingEnabled;
            return this;
        }

        public Builder longFormat(boolean longFormat) {
            this.longFormat = longFormat;
            return this;
        }

        public Builder styxHeaderNames(StyxHeaderConfig styxHeaderConfig) {
            this.styxHeaderConfig = requireNonNull(styxHeaderConfig);
            return this;
        }

        public Builder originStatsFactory(OriginStatsFactory originStatsFactory) {
            this.originStatsFactory = originStatsFactory;
            return this;
        }

        public Builder enableContentValidation() {
            contentValidation = true;
            return this;
        }

        public Builder connectionPoolProvider(ConnectionPoolProvider connectionPoolProvider) {
            this.connectionPoolProvider = connectionPoolProvider;
            return this;
        }

        public StyxHttpClient build() {
            if (metricsRegistry == null) {
                metricsRegistry = new CodaHaleMetricRegistry();
            }

            return new StyxHttpClient(this);
        }
    }
}
