/*
  Copyright (C) 2013-2024 Expedia Inc.

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
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpMethod
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.Id
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.ResponseEventListener
import com.hotels.styx.api.exceptions.NoAvailableHostsException
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.RemoteHost
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy
import com.hotels.styx.api.extension.service.RewriteRule
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.client.retry.RetryNTimes
import com.hotels.styx.client.stickysession.StickySessionCookie
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.metrics.CentralisedMetrics
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import java.lang.StringBuilder
import java.util.Objects.nonNull
import java.util.Optional

/**
 * A configurable HTTP client that uses connection pooling, load balancing, etc.
 * @deprecated Use {@link ReactorBackendServiceClient} instead.
 */
@Deprecated("Use ReactorBackendServiceClient instead", ReplaceWith("ReactorBackendServiceClient"))
class StyxBackendServiceClient(
    private val id: Id,
    rewriteRules: List<RewriteRule>,
    private val originStatsFactory: OriginStatsFactory,
    private val originsRestrictionCookieName: String?,
    private val stickySessionConfig: StickySessionConfig,
    private val originIdHeader: CharSequence,
    private val loadBalancer: LoadBalancer,
    private val retryPolicy: RetryPolicy,
    private val metrics: CentralisedMetrics,
    private val overrideHostHeader: Boolean,
) : BackendServiceClient {
    private val rewriteRuleset: RewriteRuleset = RewriteRuleset(rewriteRules)

    private constructor(builder: Builder) : this(
        id = builder.id,
        originStatsFactory = requireNotNull(builder.originStatsFactory) { "originStatsFactory is required" },
        loadBalancer = requireNotNull(builder.loadBalancer) { "loadBalancer is required" },
        metrics = requireNotNull(builder.metrics) { "metrics is required" },
        rewriteRules = builder.rewriteRules,
        originsRestrictionCookieName = builder.originsRestrictionCookieName,
        stickySessionConfig = builder.stickySessionConfig,
        originIdHeader = builder.originIdHeader,
        retryPolicy = builder.retryPolicy,
        overrideHostHeader = builder.overrideHostHeader,
    )

    /**
     * A builder for [StyxBackendServiceClient].
     */
    class Builder(
        var id: Id,
    ) {
        var originStatsFactory: OriginStatsFactory? = null
        var loadBalancer: LoadBalancer? = null
        var metrics: CentralisedMetrics? = null
        var rewriteRules: List<RewriteRule> = emptyList()
        var originsRestrictionCookieName: String? = null
        var stickySessionConfig: StickySessionConfig = StickySessionConfig.stickySessionDisabled()
        var originIdHeader: CharSequence = StyxHeaderConfig.ORIGIN_ID_DEFAULT
        var retryPolicy: RetryPolicy = RetryNTimes(3)
        var overrideHostHeader: Boolean = false

        fun id(id: Id) =
            apply {
                this.id = id
            }

        fun rewriteRules(rewriteRules: List<RewriteRule>) =
            apply {
                this.rewriteRules = rewriteRules
            }

        fun originStatsFactory(originStatsFactory: OriginStatsFactory) =
            apply {
                this.originStatsFactory = originStatsFactory
            }

        fun originsRestrictionCookieName(originsRestrictionCookieName: String?) =
            apply {
                this.originsRestrictionCookieName = originsRestrictionCookieName
            }

        fun stickySessionConfig(stickySessionConfig: StickySessionConfig) =
            apply {
                this.stickySessionConfig = stickySessionConfig
            }

        fun originIdHeader(originIdHeader: CharSequence) =
            apply {
                this.originIdHeader = originIdHeader
            }

        fun loadBalancer(loadBalancer: LoadBalancer) =
            apply {
                this.loadBalancer = loadBalancer
            }

        fun retryPolicy(retryPolicy: RetryPolicy) =
            apply {
                this.retryPolicy = retryPolicy
            }

        fun metrics(metrics: CentralisedMetrics) =
            apply {
                this.metrics = metrics
            }

        fun overrideHostHeader(overrideHostHeader: Boolean) =
            apply {
                this.overrideHostHeader = overrideHostHeader
            }

        fun build(): StyxBackendServiceClient {
            if (loadBalancer == null) {
                throw IllegalStateException("load balancer property is required")
            }
            if (metrics == null) {
                throw IllegalStateException("metrics property is required")
            }
            if (originStatsFactory == null) {
                originStatsFactory = OriginStatsFactory.CachingOriginStatsFactory(metrics)
            }
            return StyxBackendServiceClient(this)
        }
    }

    override fun sendRequest(
        request: LiveHttpRequest,
        context: HttpInterceptor.Context,
    ): Publisher<LiveHttpResponse> = sendRequest(rewriteUrl(request), emptyList(), 0, context)

    private fun isError(status: HttpResponseStatus): Boolean = status.code() >= 400

    private fun bodyNeedsToBeRemoved(
        request: LiveHttpRequest,
        response: LiveHttpResponse,
    ): Boolean = isHeadRequest(request) || isBodilessResponse(response)

    private fun responseWithoutBody(response: LiveHttpResponse): LiveHttpResponse =
        response.newBuilder()
            .header(HttpHeaderNames.CONTENT_LENGTH, 0)
            .removeHeader(HttpHeaderNames.TRANSFER_ENCODING)
            .removeBody()
            .build()

    private fun isBodilessResponse(response: LiveHttpResponse): Boolean =
        response.status().code() == 204 || response.status().code() == 304 || response.status().code() / 100 == 1

    private fun isHeadRequest(request: LiveHttpRequest): Boolean = request.method() == HttpMethod.HEAD

    private fun shouldOverrideHostHeader(
        host: RemoteHost,
        request: LiveHttpRequest,
    ): LiveHttpRequest =
        if (overrideHostHeader && !host.origin().host().isNullOrBlank()) {
            request.newBuilder().header(HttpHeaderNames.HOST, host.origin().host()).build()
        } else {
            request
        }

    private fun sendRequest(
        request: LiveHttpRequest,
        previousOrigins: List<RemoteHost>,
        attempt: Int,
        context: HttpInterceptor.Context,
    ): Publisher<LiveHttpResponse> {
        if (attempt >= MAX_RETRY_ATTEMPTS) {
            return Flux.error(NoAvailableHostsException(id))
        }
        val remoteHost = selectOrigin(request)
        return if (remoteHost.isPresent) {
            val host = remoteHost.get()
            val updatedRequest = shouldOverrideHostHeader(host, request)
            val newPreviousOrigins = previousOrigins.toMutableList()
            newPreviousOrigins.add(host)
            ResponseEventListener.from(
                host.hostClient().handle(updatedRequest, context)
                    .map { addStickySessionIdentifier(it, host.origin()) },
            )
                .whenResponseError { logError(updatedRequest, it) }
                .whenCancelled { originStatsFactory.originStats(host.origin()).requestCancelled() }
                .apply()
                .doOnNext { recordErrorStatusMetrics(it) }
                .map { removeUnexpectedResponseBody(updatedRequest, it) }
                .map { removeRedundantContentLengthHeader(it) }
                .onErrorResume { cause ->
                    val retryContext = RetryPolicyContext(id, attempt + 1, cause, updatedRequest, previousOrigins)
                    retry(updatedRequest, retryContext, newPreviousOrigins, attempt + 1, cause, context)
                }
                .map { addOriginId(host.id(), it) }
                .map { LiveHttpResponse.Builder(it).request(updatedRequest).build() }
        } else {
            val retryContext = RetryPolicyContext(id, attempt + 1, null, request, previousOrigins)
            retry(request, retryContext, previousOrigins, attempt + 1, NoAvailableHostsException(id), context)
        }
    }

    private fun addOriginId(
        originId: Id,
        response: LiveHttpResponse,
    ): LiveHttpResponse =
        response.newBuilder()
            .header(originIdHeader, originId)
            .build()

    private fun retry(
        request: LiveHttpRequest,
        retryContext: RetryPolicyContext,
        previousOrigins: List<RemoteHost>,
        attempt: Int,
        cause: Throwable,
        context: HttpInterceptor.Context,
    ): Flux<LiveHttpResponse> {
        val lbContext: LoadBalancer.Preferences =
            object : LoadBalancer.Preferences {
                override fun preferredOrigins(): Optional<String> = Optional.empty()

                override fun avoidOrigins(): List<Origin> = previousOrigins.map { it.origin() }
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
        private val previouslyUsedOrigins: Iterable<RemoteHost>,
    ) : RetryPolicy.Context {
        override fun appId(): Id = appId

        override fun currentRetryCount(): Int = retryCount

        override fun lastException(): Optional<Throwable> = Optional.ofNullable(lastException)

        override fun currentRequest(): LiveHttpRequest = request

        override fun previousOrigins(): Iterable<RemoteHost> = previouslyUsedOrigins

        override fun toString(): String =
            StringBuilder()
                .append("appId", appId)
                .append(", retryCount", retryCount)
                .append(", lastException", lastException)
                .append(", request", request.url())
                .append(", previouslyUsedOrigins", previouslyUsedOrigins)
                .toString()

        fun hosts(): String = hosts(previouslyUsedOrigins)

        companion object {
            private fun hosts(origins: Iterable<RemoteHost>): String =
                origins.asSequence().map { it.origin().hostAndPortString() }.joinToString(", ")
        }
    }

    private fun logError(
        request: LiveHttpRequest,
        throwable: Throwable,
    ) = LOGGER.error(
        "Error Handling request={} exceptionClass={} exceptionMessage=\"{}\"",
        request,
        throwable.javaClass.name,
        throwable.message,
    )

    private fun removeUnexpectedResponseBody(
        request: LiveHttpRequest,
        response: LiveHttpResponse,
    ): LiveHttpResponse =
        if (bodyNeedsToBeRemoved(request, response)) {
            responseWithoutBody(response)
        } else {
            response
        }

    private fun removeRedundantContentLengthHeader(response: LiveHttpResponse): LiveHttpResponse =
        if (response.contentLength().isPresent && response.chunked()) {
            response.newBuilder()
                .removeHeader(HttpHeaderNames.CONTENT_LENGTH)
                .build()
        } else {
            response
        }

    private fun recordErrorStatusMetrics(response: LiveHttpResponse) {
        if (isError(response.status())) {
            metrics.proxy.client.errorResponseFromOriginByStatus(response.status().code()).increment()
        }
    }

    private fun selectOrigin(rewrittenRequest: LiveHttpRequest): Optional<RemoteHost> {
        val preferences: LoadBalancer.Preferences =
            object : LoadBalancer.Preferences {
                override fun preferredOrigins(): Optional<String> {
                    return if (nonNull(originsRestrictionCookieName)) {
                        rewrittenRequest.cookie(originsRestrictionCookieName)
                            .map { it.value() }
                            .or { rewrittenRequest.cookie("styx_origin_$id").map { it.value() } }
                    } else {
                        rewrittenRequest.cookie("styx_origin_$id").map { it.value() }
                    }
                }

                override fun avoidOrigins(): List<Origin> = emptyList()
            }
        return loadBalancer.choose(preferences)
    }

    private fun addStickySessionIdentifier(
        httpResponse: LiveHttpResponse,
        origin: Origin,
    ): LiveHttpResponse =
        if (loadBalancer is StickySessionLoadBalancingStrategy) {
            val maxAge = stickySessionConfig.stickySessionTimeoutSeconds()
            httpResponse.newBuilder()
                .addCookies(StickySessionCookie.newStickySessionCookie(id, origin.id(), maxAge))
                .build()
        } else {
            httpResponse
        }

    private fun rewriteUrl(request: LiveHttpRequest): LiveHttpRequest = rewriteRuleset.rewrite(request)

    override fun toString(): String =
        StringBuilder()
            .append("id", id)
            .append(", stickySessionConfig", stickySessionConfig)
            .append(", retryPolicy", retryPolicy)
            .append(", rewriteRuleset", rewriteRuleset)
            .append(", loadBalancingStrategy", loadBalancer)
            .append(", overrideHostHeader", overrideHostHeader)
            .toString()

    companion object {
        private val LOGGER = LoggerFactory.getLogger(StyxBackendServiceClient::class.java)
        private const val MAX_RETRY_ATTEMPTS = 3

        @JvmStatic fun newHttpClientBuilder(backendServiceId: Id): Builder = Builder(backendServiceId)
    }
}
