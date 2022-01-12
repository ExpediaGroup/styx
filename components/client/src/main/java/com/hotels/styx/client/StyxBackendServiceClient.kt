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
package com.hotels.styx.client

import com.hotels.styx.api.HttpHeaderNames
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpMethod
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.Id
import com.hotels.styx.api.LiveHttpResponse
import reactor.core.publisher.Flux
import com.hotels.styx.api.exceptions.NoAvailableHostsException
import com.hotels.styx.api.ResponseEventListener
import java.util.stream.Collectors
import java.util.stream.StreamSupport
import com.hotels.styx.api.RequestCookie
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.RemoteHost
import com.hotels.styx.api.extension.service.RewriteRule
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.client.stickysession.StickySessionCookie
import com.hotels.styx.client.retry.RetryNTimes
import com.hotels.styx.client.OriginStatsFactory.CachingOriginStatsFactory
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import java.lang.StringBuilder
import java.util.Objects
import java.util.Optional

/**
 * A configurable HTTP client that uses connection pooling, load balancing, etc.
 */
class StyxBackendServiceClient(builder: Builder) : BackendServiceClient {
    private val id: Id = Objects.requireNonNull(builder.backendServiceId)
    private val stickySessionConfig: StickySessionConfig = Objects.requireNonNull(builder.stickySessionConfig)
    private val originStatsFactory: OriginStatsFactory = Objects.requireNonNull(builder.originStatsFactory)!!
    private val loadBalancer: LoadBalancer = Objects.requireNonNull(builder.loadBalancer)!!
    private val retryPolicy: RetryPolicy = builder.retryPolicy ?: RetryNTimes(3)
    private val rewriteRuleset: RewriteRuleset = RewriteRuleset(builder.rewriteRules)
    private val originsRestrictionCookieName: String? = builder.originsRestrictionCookieName
    private val originIdHeader: CharSequence = builder.originIdHeader
    private val metrics: CentralisedMetrics? = builder.metrics
    private val overrideHostHeader: Boolean = builder.overrideHostHeader

    private fun shouldOverrideHostHeader(host: RemoteHost, request: LiveHttpRequest): LiveHttpRequest {
        return if (overrideHostHeader && !host.origin().host().isNullOrBlank()) {
            request.newBuilder().header(HttpHeaderNames.HOST, host.origin().host()).build()
        } else request
    }

    override fun sendRequest(
        request: LiveHttpRequest,
        context: HttpInterceptor.Context
    ): Publisher<LiveHttpResponse> {
        return sendRequest(rewriteUrl(request), emptyList(), 0, context)
    }

    private fun isError(status: HttpResponseStatus): Boolean = status.code() >= 400


    private fun bodyNeedsToBeRemoved(request: LiveHttpRequest, response: LiveHttpResponse): Boolean =
        isHeadRequest(request) || isBodilessResponse(response)


    private fun responseWithoutBody(response: LiveHttpResponse): LiveHttpResponse = response.newBuilder()
        .header(HttpHeaderNames.CONTENT_LENGTH, 0)
        .removeHeader(HttpHeaderNames.TRANSFER_ENCODING)
        .removeBody()
        .build()


    private fun isBodilessResponse(response: LiveHttpResponse): Boolean {
        val status = response.status().code()
        return status == 204 || status == 304 || status / 100 == 1
    }

    private fun isHeadRequest(request: LiveHttpRequest): Boolean = request.method() == HttpMethod.HEAD

    private fun sendRequest(
        request: LiveHttpRequest,
        previousOrigins: List<RemoteHost>,
        attempt: Int,
        context: HttpInterceptor.Context
    ): Publisher<LiveHttpResponse> {
        if (attempt >= MAX_RETRY_ATTEMPTS) {
            return Flux.error(NoAvailableHostsException(id))
        }
        val remoteHost = selectOrigin(request)
        return if (remoteHost.isPresent) {
            val host = remoteHost.get()
            val updatedRequest = shouldOverrideHostHeader(host, request)
            val newPreviousOrigins: MutableList<RemoteHost> = ArrayList(previousOrigins)
            newPreviousOrigins.add(host)
            ResponseEventListener.from(
                host.hostClient().handle(updatedRequest, context)
                    .map { response: LiveHttpResponse -> addStickySessionIdentifier(response, host.origin()) }
            )
                .whenResponseError { cause: Throwable -> logError(updatedRequest, cause) }
                .whenCancelled { originStatsFactory.originStats(host.origin()).requestCancelled() }
                .apply()
                .doOnNext { response: LiveHttpResponse -> recordErrorStatusMetrics(response) }
                .map { response: LiveHttpResponse -> removeUnexpectedResponseBody(updatedRequest, response) }
                .map { response: LiveHttpResponse -> removeRedundantContentLengthHeader(response) }
                .onErrorResume { cause: Throwable? ->
                    val retryContext = RetryPolicyContext(id, attempt + 1, cause, updatedRequest, previousOrigins)
                    retry(updatedRequest, retryContext, newPreviousOrigins, attempt + 1, cause, context)
                }
                .map { response: LiveHttpResponse -> addOriginId(host.id(), response) }
        } else {
            val retryContext = RetryPolicyContext(id, attempt + 1, null, request, previousOrigins)
            retry(request, retryContext, previousOrigins, attempt + 1, NoAvailableHostsException(id), context)
        }
    }

    private fun addOriginId(originId: Id, response: LiveHttpResponse): LiveHttpResponse {
        return response.newBuilder()
            .header(originIdHeader, originId)
            .build()
    }

    private fun retry(
        request: LiveHttpRequest,
        retryContext: RetryPolicyContext,
        previousOrigins: List<RemoteHost>,
        attempt: Int,
        cause: Throwable?,
        context: HttpInterceptor.Context
    ): Flux<LiveHttpResponse> {
        val lbContext: LoadBalancer.Preferences = object : LoadBalancer.Preferences {
            override fun preferredOrigins(): Optional<String> {
                return Optional.empty()
            }

            override fun avoidOrigins(): List<Origin> {
                return previousOrigins.stream()
                    .map { obj: RemoteHost -> obj.origin() }
                    .collect(Collectors.toList())
            }
        }
        return if (retryPolicy.evaluate(retryContext, loadBalancer, lbContext).shouldRetry()) {
            Flux.from(sendRequest(request, previousOrigins, attempt, context))
        } else {
            Flux.error(cause)
        }
    }

    private class RetryPolicyContext(
        private val appId: Id,
        private val retryCount: Int,
        private val lastException: Throwable?,
        private val request: LiveHttpRequest,
        private val previouslyUsedOrigins: Iterable<RemoteHost>
    ) : RetryPolicy.Context {
        override fun appId(): Id {
            return appId
        }

        override fun currentRetryCount(): Int {
            return retryCount
        }

        override fun lastException(): Optional<Throwable> {
            return Optional.ofNullable(lastException)
        }

        override fun currentRequest(): LiveHttpRequest {
            return request
        }

        override fun previousOrigins(): Iterable<RemoteHost> {
            return previouslyUsedOrigins
        }

        override fun toString(): String {
            return StringBuilder(160)
                .append(this.javaClass.simpleName)
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
                .toString()
        }

        companion object {
            private fun hosts(origins: Iterable<RemoteHost>): String {
                return StreamSupport.stream(origins.spliterator(), false)
                    .map { host: RemoteHost -> host.origin().hostAndPortString() }
                    .collect(Collectors.joining(", "))
            }
        }
    }

    private fun logError(request: LiveHttpRequest, throwable: Throwable) {
        LOGGER.error(
            "Error Handling request={} exceptionClass={} exceptionMessage=\"{}\"",
            request, throwable.javaClass.name, throwable.message
        )
    }

    private fun removeUnexpectedResponseBody(
        request: LiveHttpRequest,
        response: LiveHttpResponse
    ): LiveHttpResponse {
        return if (bodyNeedsToBeRemoved(request, response)) {
            responseWithoutBody(response)
        } else {
            response
        }
    }

    private fun removeRedundantContentLengthHeader(response: LiveHttpResponse): LiveHttpResponse {
        return if (response.contentLength().isPresent && response.chunked()) {
            response.newBuilder()
                .removeHeader(HttpHeaderNames.CONTENT_LENGTH)
                .build()
        } else response
    }

    private fun recordErrorStatusMetrics(response: LiveHttpResponse) {
        if (isError(response.status())) {
            metrics!!.proxy.client.errorResponseFromOriginByStatus(response.status().code()).increment()
        }
    }

    private fun selectOrigin(rewrittenRequest: LiveHttpRequest): Optional<RemoteHost> {
        val preferences: LoadBalancer.Preferences = object : LoadBalancer.Preferences {
            override fun preferredOrigins(): Optional<String> {
                return if (Objects.nonNull(originsRestrictionCookieName)) {
                    rewrittenRequest.cookie(originsRestrictionCookieName)
                        .map { obj: RequestCookie -> obj.value() }
                        .or { rewrittenRequest.cookie("styx_origin_$id").map { obj: RequestCookie -> obj.value() } }
                } else {
                    rewrittenRequest.cookie("styx_origin_$id").map { obj: RequestCookie -> obj.value() }
                }
            }

            override fun avoidOrigins(): List<Origin> {
                return emptyList()
            }
        }
        return loadBalancer.choose(preferences)
    }

    private fun addStickySessionIdentifier(httpResponse: LiveHttpResponse, origin: Origin): LiveHttpResponse {
        return if (loadBalancer is StickySessionLoadBalancingStrategy) {
            val maxAge = stickySessionConfig.stickySessionTimeoutSeconds()
            httpResponse.newBuilder()
                .addCookies(StickySessionCookie.newStickySessionCookie(id, origin.id(), maxAge))
                .build()
        } else {
            httpResponse
        }
    }

    private fun rewriteUrl(request: LiveHttpRequest): LiveHttpRequest {
        return rewriteRuleset.rewrite(request)
    }

    override fun toString(): String {
        return StringBuilder(160)
            .append(this.javaClass.simpleName)
            .append("{id=")
            .append(id)
            .append(", stickySessionConfig=")
            .append(stickySessionConfig)
            .append(", retryPolicy=")
            .append(retryPolicy)
            .append(", rewriteRuleset=")
            .append(rewriteRuleset)
            .append(", overrideHostHeader=")
            .append(overrideHostHeader)
            .append(", loadBalancer=")
            .append(loadBalancer)
            .append('}')
            .toString()
    }

    /**
     * A builder for [StyxBackendServiceClient].
     */
    class Builder(backendServiceId: Id) {
        val backendServiceId: Id = Objects.requireNonNull(backendServiceId)
        var metrics: CentralisedMetrics? = null
        var rewriteRules: List<RewriteRule> = emptyList()
        var retryPolicy: RetryPolicy? = RetryNTimes(3)
        var loadBalancer: LoadBalancer? = null
        var originStatsFactory: OriginStatsFactory? = null
        var originsRestrictionCookieName: String? = null
        var stickySessionConfig: StickySessionConfig = StickySessionConfig.stickySessionDisabled()
        var originIdHeader: CharSequence = StyxHeaderConfig.ORIGIN_ID_DEFAULT
        var overrideHostHeader: Boolean = false


        fun stickySessionConfig(stickySessionConfig: StickySessionConfig?): Builder {
            this.stickySessionConfig = Objects.requireNonNull(stickySessionConfig)!!
            return this
        }

        fun metrics(metrics: CentralisedMetrics?): Builder {
            this.metrics = Objects.requireNonNull(metrics)
            return this
        }

        fun retryPolicy(retryPolicy: RetryPolicy?): Builder {
            this.retryPolicy = Objects.requireNonNull(retryPolicy)
            return this
        }

        fun rewriteRules(rewriteRules: List<RewriteRule>?): Builder {
            this.rewriteRules = java.util.List.copyOf(rewriteRules)
            return this
        }

        fun loadBalancer(loadBalancer: LoadBalancer?): Builder {
            this.loadBalancer = Objects.requireNonNull(loadBalancer)
            return this
        }

        fun originStatsFactory(originStatsFactory: OriginStatsFactory?): Builder {
            this.originStatsFactory = originStatsFactory
            return this
        }

        fun originsRestrictionCookieName(originsRestrictionCookieName: String?): Builder {
            this.originsRestrictionCookieName = originsRestrictionCookieName
            return this
        }

        fun originIdHeader(originIdHeader: CharSequence): Builder {
            this.originIdHeader = Objects.requireNonNull(originIdHeader)
            return this
        }

        fun overrideHostHeader(overrideHostHeader: Boolean): Builder {
            this.overrideHostHeader = overrideHostHeader
            return this
        }

        fun build(): StyxBackendServiceClient {
            if (originStatsFactory == null) {
                originStatsFactory = CachingOriginStatsFactory(metrics)
            }
            checkNotNull(metrics) { "metrics property is required" }
            return StyxBackendServiceClient(this)
        }

    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(StyxBackendServiceClient::class.java)
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
