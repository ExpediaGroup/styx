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
package com.hotels.styx.client;

import com.hotels.styx.api.HttpHeaderNames;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.RequestCookie;
import com.hotels.styx.api.ResponseEventListener;
import com.hotels.styx.api.exceptions.NoAvailableHostsException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.extension.service.RewriteRule;
import com.hotels.styx.api.extension.service.StickySessionConfig;
import com.hotels.styx.client.OriginStatsFactory.CachingOriginStatsFactory;
import com.hotels.styx.client.retry.RetryNTimes;
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy;
import com.hotels.styx.metrics.CentralisedMetrics;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpMethod.HEAD;
import static com.hotels.styx.api.extension.service.StickySessionConfig.stickySessionDisabled;
import static com.hotels.styx.client.StyxHeaderConfig.ORIGIN_ID_DEFAULT;
import static com.hotels.styx.client.stickysession.StickySessionCookie.newStickySessionCookie;
import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A configurable HTTP client that uses connection pooling, load balancing, etc.
 */
public final class StyxBackendServiceClient implements BackendServiceClient {
    private static final Logger LOGGER = getLogger(StyxBackendServiceClient.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final Id id;
    private final RewriteRuleset rewriteRuleset;
    private final LoadBalancer loadBalancer;
    private final RetryPolicy retryPolicy;
    private final OriginStatsFactory originStatsFactory;
    private final String originsRestrictionCookieName;
    private final StickySessionConfig stickySessionConfig;
    private final CharSequence originIdHeader;
    private final CentralisedMetrics metrics;
    private final boolean overrideHostHeader;

    private StyxBackendServiceClient(Builder builder) {
        this.id = requireNonNull(builder.backendServiceId);

        this.stickySessionConfig = requireNonNull(builder.stickySessionConfig);

        this.originStatsFactory = requireNonNull(builder.originStatsFactory);

        this.loadBalancer = requireNonNull(builder.loadBalancer);

        this.retryPolicy = builder.retryPolicy != null
                ? builder.retryPolicy
                : new RetryNTimes(3);

        this.rewriteRuleset = new RewriteRuleset(builder.rewriteRules);

        this.metrics = builder.metrics;
        this.originsRestrictionCookieName = builder.originsRestrictionCookieName;
        this.originIdHeader = builder.originIdHeader;
        this.overrideHostHeader = builder.overrideHostHeader;
    }

    @Override
    public Publisher<LiveHttpResponse> sendRequest(LiveHttpRequest request, HttpInterceptor.Context context) {
        return sendRequest(rewriteUrl(request), new ArrayList<>(), 0, context);
    }

    /**
     * Create a new builder.
     *
     * @return a new builder
     */
    public static Builder newHttpClientBuilder(Id backendServiceId) {
        return new Builder(backendServiceId);
    }

    private static boolean isError(HttpResponseStatus status) {
        return status.code() >= 400;
    }

    private static boolean bodyNeedsToBeRemoved(LiveHttpRequest request, LiveHttpResponse response) {
        return isHeadRequest(request) || isBodilessResponse(response);
    }

    private static LiveHttpResponse responseWithoutBody(LiveHttpResponse response) {
        return response.newBuilder()
                .header(CONTENT_LENGTH, 0)
                .removeHeader(TRANSFER_ENCODING)
                .removeBody()
                .build();
    }

    private static boolean isBodilessResponse(LiveHttpResponse response) {
        int status = response.status().code();
        return status == 204 || status == 304 || status / 100 == 1;
    }

    private static boolean isHeadRequest(LiveHttpRequest request) {
        return request.method().equals(HEAD);
    }

    private Publisher<LiveHttpResponse> sendRequest(LiveHttpRequest request, List<RemoteHost> previousOrigins, int attempt, HttpInterceptor.Context context) {
        if (attempt >= MAX_RETRY_ATTEMPTS) {
            return Flux.error(new NoAvailableHostsException(this.id));
        }

        Optional<RemoteHost> remoteHost = selectOrigin(request);
        if (remoteHost.isPresent()) {
            RemoteHost host = remoteHost.get();

            LiveHttpRequest withHostHeaderUpdated = request;

            if (host.origin() != null && !host.origin().host().isBlank() && overrideHostHeader) {
                withHostHeaderUpdated = request.newBuilder()
                        .header(HttpHeaderNames.HOST, host.origin().host())
                        .build();
            }

            final LiveHttpRequest updatedRequest = withHostHeaderUpdated;

            List<RemoteHost> newPreviousOrigins = new ArrayList<>(previousOrigins);
            newPreviousOrigins.add(host);

            return ResponseEventListener.from(
                            host.hostClient().handle(updatedRequest, context).map(response ->
                                    addStickySessionIdentifier(response, host.origin()))
                    )
                    .whenResponseError(cause -> logError(updatedRequest, cause))
                    .whenCancelled(() -> originStatsFactory.originStats(host.origin()).requestCancelled())
                    .apply()
                    .doOnNext(this::recordErrorStatusMetrics)
                    .map(response -> removeUnexpectedResponseBody(updatedRequest, response))
                    .map(StyxBackendServiceClient::removeRedundantContentLengthHeader)
                    .onErrorResume(cause -> {
                        RetryPolicyContext retryContext = new RetryPolicyContext(this.id, attempt + 1, cause, updatedRequest, previousOrigins);
                        return retry(updatedRequest, retryContext, newPreviousOrigins, attempt + 1, cause, context);
                    })
                    .map(response -> addOriginId(host.id(), response));
        } else {
            RetryPolicyContext retryContext = new RetryPolicyContext(this.id, attempt + 1, null, request, previousOrigins);
            return retry(request, retryContext, previousOrigins, attempt + 1, new NoAvailableHostsException(this.id), context);
        }
    }

    private LiveHttpResponse addOriginId(Id originId, LiveHttpResponse response) {
        return response.newBuilder()
                .header(originIdHeader, originId)
                .build();
    }

    private Flux<LiveHttpResponse> retry(
            LiveHttpRequest request,
            RetryPolicyContext retryContext,
            List<RemoteHost> previousOrigins,
            int attempt,
            Throwable cause,
            HttpInterceptor.Context context) {
        LoadBalancer.Preferences lbContext = new LoadBalancer.Preferences() {
            @Override
            public Optional<String> preferredOrigins() {
                return Optional.empty();
            }

            @Override
            public List<Origin> avoidOrigins() {
                return previousOrigins.stream()
                        .map(RemoteHost::origin)
                        .collect(Collectors.toList());
            }
        };

        if (this.retryPolicy.evaluate(retryContext, loadBalancer, lbContext).shouldRetry()) {
            return Flux.from(sendRequest(request, previousOrigins, attempt, context));
        } else {
            return Flux.error(cause);
        }
    }

    private static final class RetryPolicyContext implements RetryPolicy.Context {
        private final Id appId;
        private final int retryCount;
        private final Throwable lastException;
        private final LiveHttpRequest request;
        private final Iterable<RemoteHost> previouslyUsedOrigins;

        RetryPolicyContext(Id appId, int retryCount, Throwable lastException, LiveHttpRequest request,
                           Iterable<RemoteHost> previouslyUsedOrigins) {
            this.appId = appId;
            this.retryCount = retryCount;
            this.lastException = lastException;
            this.request = request;
            this.previouslyUsedOrigins = previouslyUsedOrigins;
        }

        @Override
        public Id appId() {
            return appId;
        }

        @Override
        public int currentRetryCount() {
            return retryCount;
        }

        @Override
        public Optional<Throwable> lastException() {
            return Optional.ofNullable(lastException);
        }

        @Override
        public LiveHttpRequest currentRequest() {
            return request;
        }

        @Override
        public Iterable<RemoteHost> previousOrigins() {
            return previouslyUsedOrigins;
        }

        @Override
        public String toString() {
            return new StringBuilder(160)
                    .append(this.getClass().getSimpleName())
                    .append("{appId=")
                    .append(appId)
                    .append(", retryCount=")
                    .append(retryCount)
                    .append(", lastException=")
                    .append(lastException)
                    .append(", request=")
                    .append(request.url())
                    .append(", previouslyUsedOrigins=")
                    .append(hosts(previouslyUsedOrigins))
                    .append('}')
                    .toString();
        }

        private static String hosts(Iterable<RemoteHost> origins) {
            return stream(origins.spliterator(), false)
                    .map(host -> host.origin().hostAndPortString())
                    .collect(joining(", "));
        }
    }

    private static void logError(LiveHttpRequest request, Throwable throwable) {
        LOGGER.error("Error Handling request={} exceptionClass={} exceptionMessage=\"{}\"",
                request, throwable.getClass().getName(), throwable.getMessage());
    }

    private static LiveHttpResponse removeUnexpectedResponseBody(LiveHttpRequest request, LiveHttpResponse response) {
        if (bodyNeedsToBeRemoved(request, response)) {
            return responseWithoutBody(response);
        } else {
            return response;
        }
    }

    private static LiveHttpResponse removeRedundantContentLengthHeader(LiveHttpResponse response) {
        if (response.contentLength().isPresent() && response.chunked()) {
            return response.newBuilder()
                    .removeHeader(CONTENT_LENGTH)
                    .build();
        }
        return response;
    }

    private void recordErrorStatusMetrics(LiveHttpResponse response) {
        if (isError(response.status())) {
            metrics.proxy().client().errorResponseFromOriginByStatus(response.status().code()).increment();
        }
    }

    private Optional<RemoteHost> selectOrigin(LiveHttpRequest rewrittenRequest) {
        LoadBalancer.Preferences preferences = new LoadBalancer.Preferences() {
            @Override
            public Optional<String> preferredOrigins() {
                if (nonNull(originsRestrictionCookieName)) {
                    return rewrittenRequest.cookie(originsRestrictionCookieName)
                            .map(RequestCookie::value)
                            .or(() -> rewrittenRequest.cookie("styx_origin_" + id).map(RequestCookie::value));
                } else {
                    return rewrittenRequest.cookie("styx_origin_" + id).map(RequestCookie::value);
                }
            }

            @Override
            public List<Origin> avoidOrigins() {
                return Collections.emptyList();
            }
        };
        return loadBalancer.choose(preferences);
    }

    private LiveHttpResponse addStickySessionIdentifier(LiveHttpResponse httpResponse, Origin origin) {
        if (this.loadBalancer instanceof StickySessionLoadBalancingStrategy) {
            int maxAge = stickySessionConfig.stickySessionTimeoutSeconds();
            return httpResponse.newBuilder()
                    .addCookies(newStickySessionCookie(id, origin.id(), maxAge))
                    .build();
        } else {
            return httpResponse;
        }
    }

    private LiveHttpRequest rewriteUrl(LiveHttpRequest request) {
        return rewriteRuleset.rewrite(request);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(160);
        sb.append(this.getClass().getSimpleName());
        sb.append("{id=");
        sb.append(id);
        sb.append(", stickySessionConfig=");
        sb.append(stickySessionConfig);
        sb.append(", retryPolicy=");
        sb.append(retryPolicy);
        sb.append(", rewriteRuleset=");
        sb.append(rewriteRuleset);
        sb.append(", overrideHostHeader=");
        sb.append(overrideHostHeader);
        sb.append(", loadBalancer=");
        sb.append(loadBalancer);
        return sb.append('}').toString();
    }

    /**
     * A builder for {@link StyxBackendServiceClient}.
     */
    public static class Builder {
        private final Id backendServiceId;
        private CentralisedMetrics metrics;
        private List<RewriteRule> rewriteRules = emptyList();
        private RetryPolicy retryPolicy = new RetryNTimes(3);
        private LoadBalancer loadBalancer;
        private OriginStatsFactory originStatsFactory;
        private String originsRestrictionCookieName;
        private StickySessionConfig stickySessionConfig = stickySessionDisabled();
        private CharSequence originIdHeader = ORIGIN_ID_DEFAULT;
        private boolean overrideHostHeader = false;

        public Builder(Id backendServiceId) {
            this.backendServiceId = requireNonNull(backendServiceId);
        }

        public Builder stickySessionConfig(StickySessionConfig stickySessionConfig) {
            this.stickySessionConfig = requireNonNull(stickySessionConfig);
            return this;
        }

        public Builder metrics(CentralisedMetrics metrics) {
            this.metrics = requireNonNull(metrics);
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = requireNonNull(retryPolicy);
            return this;
        }

        public Builder rewriteRules(List<? extends RewriteRule> rewriteRules) {
            this.rewriteRules = List.copyOf(rewriteRules);
            return this;
        }

        public Builder loadBalancer(LoadBalancer loadBalancer) {
            this.loadBalancer = requireNonNull(loadBalancer);
            return this;
        }

        public Builder originStatsFactory(OriginStatsFactory originStatsFactory) {
            this.originStatsFactory = originStatsFactory;
            return this;
        }

        public Builder originsRestrictionCookieName(String originsRestrictionCookieName) {
            this.originsRestrictionCookieName = originsRestrictionCookieName;
            return this;
        }

        public Builder originIdHeader(CharSequence originIdHeader) {
            this.originIdHeader = requireNonNull(originIdHeader);
            return this;
        }

        public Builder overrideHostHeader(boolean overrideHostHeader) {
            this.overrideHostHeader = overrideHostHeader;
            return this;
        }

        public StyxBackendServiceClient build() {
            if (originStatsFactory == null) {
                originStatsFactory = new CachingOriginStatsFactory(metrics);
            }
            if (metrics == null) {
                throw new IllegalStateException("metrics property is required");
            }
            return new StyxBackendServiceClient(this);
        }
    }
}
