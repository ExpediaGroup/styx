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
package com.hotels.styx.client;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.RequestCookie;
import com.hotels.styx.api.exceptions.NoAvailableHostsException;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.RewriteRule;
import com.hotels.styx.client.retry.RetryNTimes;
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy;
import com.hotels.styx.server.HttpInterceptorContext;
import org.slf4j.Logger;
import rx.Observable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.collect.Lists.newArrayList;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.StyxInternalObservables.toRxObservable;
import static com.hotels.styx.client.stickysession.StickySessionCookie.newStickySessionCookie;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A configurable HTTP client that uses connection pooling, load balancing, etc.
 */
public final class StyxHttpClient implements HttpClient {
    private static final Logger LOGGER = getLogger(StyxHttpClient.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final Id id;
    private final RewriteRuleset rewriteRuleset;
    private final LoadBalancer loadBalancer;
    private final RetryPolicy retryPolicy;
    private final OriginStatsFactory originStatsFactory;
    private final BackendService backendService;
    private final MetricRegistry metricsRegistry;
    private final boolean contentValidation;
    private final String originsRestrictionCookieName;

    private StyxHttpClient(Builder builder) {
        this.backendService = requireNonNull(builder.backendService);
        this.id = backendService.id();

        this.originStatsFactory = requireNonNull(builder.originStatsFactory);

        this.loadBalancer = requireNonNull(builder.loadBalancer);

        this.retryPolicy = builder.retryPolicy != null
                ? builder.retryPolicy
                : new RetryNTimes(3);

        this.rewriteRuleset = new RewriteRuleset(builder.rewriteRules);

        this.metricsRegistry = builder.metricsRegistry;
        this.contentValidation = builder.contentValidation;
        this.originsRestrictionCookieName = builder.originsRestrictionCookieName;
    }

    @Override
    public Observable<HttpResponse> sendRequest(HttpRequest request) {
        return sendRequest(rewriteUrl(request), new ArrayList<>(), 0);
    }

    public boolean isHttps() {
        return backendService.tlsSettings().isPresent();
    }

    /**
     * Create a new builder.
     *
     * @return a new builder
     */
    public static Builder newHttpClientBuilder(BackendService backendService) {
        return new Builder(backendService);
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

    private Observable<HttpResponse> sendRequest(HttpRequest request, List<RemoteHost> previousOrigins, int attempt) {
        if (attempt >= MAX_RETRY_ATTEMPTS) {
            return Observable.error(new NoAvailableHostsException(this.id));
        }

        Optional<RemoteHost> remoteHost = selectOrigin(request);
        if (remoteHost.isPresent()) {
            RemoteHost host = remoteHost.get();
            List<RemoteHost> newPreviousOrigins = newArrayList(previousOrigins);
            newPreviousOrigins.add(remoteHost.get());
            AtomicBoolean completed = new AtomicBoolean(false);

            return toRxObservable(host.hostClient().handle(request, HttpInterceptorContext.create()))
                    .map(response -> addStickySessionIdentifier(response, host.origin()))
                    .doOnError(throwable -> logError(request, throwable))
                    .doOnCompleted(() -> completed.set(true))
                    .doOnUnsubscribe(() -> {
                        if (!completed.get()) {
                            originStatsFactory.originStats(host.origin()).requestCancelled();
                        }
                    })
                    .doOnNext(this::recordErrorStatusMetrics)
                    .map(response -> removeUnexpectedResponseBody(request, response))
                    .map(this::removeRedundantContentLengthHeader)
                    .onErrorResumeNext(cause -> {
                        RetryPolicyContext retryContext = new RetryPolicyContext(this.id, attempt + 1, cause, request, previousOrigins);
                        return retry(request, retryContext, newPreviousOrigins, attempt + 1, cause);
                    });
        } else {
            RetryPolicyContext retryContext = new RetryPolicyContext(this.id, attempt + 1, null, request, previousOrigins);
            return retry(request, retryContext, previousOrigins, attempt + 1, new NoAvailableHostsException(this.id));
        }
    }

    Observable<HttpResponse> retry(HttpRequest request, RetryPolicyContext retryContext, List<RemoteHost> previousOrigins, int attempt, Throwable cause) {
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
            return sendRequest(request, previousOrigins, attempt);
        } else {
            return Observable.error(cause);
        }

    }

    private static final class RetryPolicyContext implements RetryPolicy.Context {
        private final Id appId;
        private final int retryCount;
        private final Throwable lastException;
        private final HttpRequest request;
        private final Iterable<RemoteHost> previouslyUsedOrigins;

        RetryPolicyContext(Id appId, int retryCount, Throwable lastException, HttpRequest request,
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
        public HttpRequest currentRequest() {
            return request;
        }

        @Override
        public Iterable<RemoteHost> previousOrigins() {
            return previouslyUsedOrigins;
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("appId", appId)
                    .add("retryCount", retryCount)
                    .add("lastException", lastException)
                    .add("request", request.url())
                    .add("previouslyUsedOrigins", hosts(previouslyUsedOrigins))
                    .toString();
        }

        private static String hosts(Iterable<RemoteHost> origins) {
            return stream(origins.spliterator(), false)
                    .map(host -> host.origin().hostAsString())
                    .collect(joining(", "));
        }
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

    private Optional<RemoteHost> selectOrigin(HttpRequest rewrittenRequest) {


        LoadBalancer.Preferences preferences = new LoadBalancer.Preferences() {
            @Override
            public Optional<String> preferredOrigins() {
                if (nonNull(originsRestrictionCookieName)) {
                    return rewrittenRequest.cookie(originsRestrictionCookieName)
                            .map(RequestCookie::value)
                            .map(Optional::of)
                            .orElse(rewrittenRequest.cookie("styx_origin_" + id).map(RequestCookie::value));
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

    private HttpResponse addStickySessionIdentifier(HttpResponse httpResponse, Origin origin) {
        if (this.loadBalancer instanceof StickySessionLoadBalancingStrategy) {
            int maxAge = backendService.stickySessionConfig().stickySessionTimeoutSeconds();
            return httpResponse.newBuilder()
                    .addCookies(newStickySessionCookie(id, origin.id(), maxAge))
                    .build();
        } else {
            return httpResponse;
        }
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
                .add("loadBalancingStrategy", loadBalancer)
                .toString();
    }

    /**
     * A builder for {@link com.hotels.styx.client.StyxHttpClient}.
     */
    public static class Builder {
        private final BackendService backendService;
        private MetricRegistry metricsRegistry = new CodaHaleMetricRegistry();
        private List<RewriteRule> rewriteRules = emptyList();
        private RetryPolicy retryPolicy = new RetryNTimes(3);
        private LoadBalancer loadBalancer;
        private boolean contentValidation;
        private OriginStatsFactory originStatsFactory;
        private String originsRestrictionCookieName;

        public Builder(BackendService backendService) {
            this.backendService = requireNonNull(backendService);
        }

        public Builder metricsRegistry(MetricRegistry metricsRegistry) {
            this.metricsRegistry = requireNonNull(metricsRegistry);
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = requireNonNull(retryPolicy);
            return this;
        }

        public Builder rewriteRules(List<? extends RewriteRule> rewriteRules) {
            this.rewriteRules = ImmutableList.copyOf(rewriteRules);
            return this;
        }


        public Builder loadBalancer(LoadBalancer loadBalancer) {
            this.loadBalancer = requireNonNull(loadBalancer);
            return this;
        }

        public Builder enableContentValidation() {
            contentValidation = true;
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

        public StyxHttpClient build() {
            if (originStatsFactory == null) {
                originStatsFactory = new OriginStatsFactory(metricsRegistry);
            }
            return new StyxHttpClient(this);
        }
    }
}
