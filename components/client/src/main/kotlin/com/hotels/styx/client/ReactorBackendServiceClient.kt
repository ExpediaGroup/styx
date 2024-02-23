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

import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpMethod
import com.hotels.styx.api.Id
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.exceptions.NoAvailableHostsException
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.RemoteHost
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.client.StyxHeaderConfig.ORIGIN_ID_DEFAULT
import com.hotels.styx.client.retry.RetryNTimes
import com.hotels.styx.client.stickysession.StickySessionCookie.newStickySessionCookie
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.ext.newRequest
import com.hotels.styx.ext.newResponse
import com.hotels.styx.metrics.CentralisedMetrics
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import java.util.Objects.nonNull
import java.util.Optional

/**
 * A configurable HTTP client with integration of Reactor Netty client
 */
class ReactorBackendServiceClient(
    private val id: Id,
    private val rewriteRuleset: RewriteRuleset,
    private val originsRestrictionCookieName: String?,
    private val stickySessionConfig: StickySessionConfig,
    private val originIdHeader: CharSequence,
    private val loadBalancer: LoadBalancer,
    private val retryPolicy: RetryPolicy,
    private val metrics: CentralisedMetrics,
    private val overrideHostHeader: Boolean,
) : BackendServiceClient {
    override fun sendRequest(
        request: LiveHttpRequest,
        context: HttpInterceptor.Context,
    ): Publisher<LiveHttpResponse> = sendRequest(rewriteUrl(request), emptyList(), 0, context)

    private fun sendRequest(
        request: LiveHttpRequest,
        previousOrigins: List<RemoteHost>,
        attempt: Int,
        context: HttpInterceptor.Context,
    ): Publisher<LiveHttpResponse> {
        if (attempt >= MAX_RETRY_ATTEMPTS) {
            return Mono.error(NoAvailableHostsException(id))
        }
        val remoteHost = selectOrigin(request)
        return if (remoteHost.isPresent) {
            val host = remoteHost.get()
            val updatedRequest = shouldOverrideHostHeader(host, request)
            val newPreviousOrigins = previousOrigins.toMutableList()
            newPreviousOrigins.add(host)
            Mono.from(host.hostClient().handle(updatedRequest, context))
                .doOnNext { recordErrorStatusMetrics(it) }
                .map { response ->
                    response.addStickySessionIdentifier(host.origin())
                        .removeUnexpectedResponseBody(updatedRequest)
                        .removeRedundantContentLengthHeader()
                        .addOriginId(host.id())
                        .let { LiveHttpResponse.Builder(it).request(updatedRequest).build() }
                }
                .onErrorResume { cause ->
                    val retryContext = RetryPolicyContext(id, attempt + 1, cause, updatedRequest, previousOrigins)
                    retry(updatedRequest, retryContext, newPreviousOrigins, attempt + 1, cause, context)
                }
        } else {
            val retryContext = RetryPolicyContext(id, attempt + 1, null, request, previousOrigins)
            retry(request, retryContext, previousOrigins, attempt + 1, NoAvailableHostsException(id), context)
        }
    }

    private fun recordErrorStatusMetrics(response: LiveHttpResponse) {
        val statusCode = response.status().code()
        if (statusCode.isErrorStatus()) {
            metrics.proxy.client.errorResponseFromOriginByStatus(statusCode).increment()
        }
    }

    private fun Int.isErrorStatus() = this >= 400

    private fun bodyNeedsToBeRemoved(
        request: LiveHttpRequest,
        response: LiveHttpResponse,
    ) = isHeadRequest(request) || isBodilessResponse(response)

    private fun responseWithoutBody(response: LiveHttpResponse) =
        response.newResponse {
            header(CONTENT_LENGTH, 0)
            removeHeader(TRANSFER_ENCODING)
            removeBody()
        }

    private fun isBodilessResponse(response: LiveHttpResponse): Boolean =
        when (val code = response.status().code()) {
            204, 304 -> true
            else -> code / 100 == 1
        }

    private fun isHeadRequest(request: LiveHttpRequest): Boolean = request.method() == HttpMethod.HEAD

    private fun shouldOverrideHostHeader(
        host: RemoteHost,
        request: LiveHttpRequest,
    ): LiveHttpRequest =
        if (overrideHostHeader && !host.origin().host().isNullOrBlank()) {
            request.newRequest { header(HOST, host.origin().host()) }
        } else {
            request
        }

    private fun LiveHttpResponse.addOriginId(originId: Id): LiveHttpResponse =
        newResponse {
            header(originIdHeader, originId)
        }

    private fun retry(
        request: LiveHttpRequest,
        retryContext: RetryPolicyContext,
        previousOrigins: List<RemoteHost>,
        attempt: Int,
        cause: Throwable,
        context: HttpInterceptor.Context,
    ): Mono<LiveHttpResponse> {
        val lbContext: LoadBalancer.Preferences =
            object : LoadBalancer.Preferences {
                override fun preferredOrigins(): Optional<String> = Optional.empty()

                override fun avoidOrigins(): List<Origin> = previousOrigins.map { it.origin() }
            }
        return if (retryPolicy.evaluate(retryContext, loadBalancer, lbContext).shouldRetry()) {
            Mono.from(sendRequest(request, previousOrigins, attempt, context))
        } else {
            Mono.error(cause)
        }
    }

    private fun LiveHttpResponse.removeUnexpectedResponseBody(request: LiveHttpRequest) =
        if (bodyNeedsToBeRemoved(request, this)) {
            responseWithoutBody(this)
        } else {
            this
        }

    private fun LiveHttpResponse.removeRedundantContentLengthHeader() =
        if (contentLength().isPresent && chunked()) {
            newResponse {
                removeHeader(CONTENT_LENGTH)
            }
        } else {
            this
        }

    private fun selectOrigin(rewrittenRequest: LiveHttpRequest): Optional<RemoteHost> {
        val preferences =
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

    private fun LiveHttpResponse.addStickySessionIdentifier(origin: Origin): LiveHttpResponse =
        if (loadBalancer is StickySessionLoadBalancingStrategy) {
            val maxAge = stickySessionConfig.stickySessionTimeoutSeconds()
            newResponse {
                addCookies(newStickySessionCookie(id, origin.id(), maxAge))
            }
        } else {
            this
        }

    private fun rewriteUrl(request: LiveHttpRequest): LiveHttpRequest = rewriteRuleset.rewrite(request)

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
            buildString {
                append("appId", appId)
                append(", retryCount", retryCount)
                append(", lastException", lastException)
                append(", request", request.url())
                append(", previouslyUsedOrigins", previouslyUsedOrigins)
            }

        fun hosts(): String = hosts(previouslyUsedOrigins)

        companion object {
            private fun hosts(origins: Iterable<RemoteHost>): String =
                origins.asSequence().map { it.origin().hostAndPortString() }.joinToString(", ")
        }
    }

    override fun toString(): String =
        buildString {
            append("id", id)
            append(", stickySessionConfig", stickySessionConfig)
            append(", retryPolicy", retryPolicy)
            append(", rewriteRuleset", rewriteRuleset)
            append(", loadBalancingStrategy", loadBalancer)
            append(", overrideHostHeader", overrideHostHeader)
        }

    /**
     * A builder for [ReactorBackendServiceClient].
     */
    class Builder(val id: Id) {
        private var originStatsFactory: OriginStatsFactory? = null
        private var loadBalancer: LoadBalancer? = null
        private var metrics: CentralisedMetrics? = null
        private var rewriteRuleset: RewriteRuleset = RewriteRuleset(emptyList())
        private var originsRestrictionCookieName: String? = null
        private var stickySessionConfig: StickySessionConfig = StickySessionConfig.stickySessionDisabled()
        private var originIdHeader: CharSequence = ORIGIN_ID_DEFAULT
        private var retryPolicy: RetryPolicy = RetryNTimes(3)
        private var overrideHostHeader: Boolean = false

        fun rewriteRules(rewriteRuleset: RewriteRuleset) =
            apply {
                this.rewriteRuleset = rewriteRuleset
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

        fun build(): ReactorBackendServiceClient =
            ReactorBackendServiceClient(
                id,
                rewriteRuleset,
                originsRestrictionCookieName,
                stickySessionConfig,
                originIdHeader,
                checkNotNull(loadBalancer) { "loadBalancer is required" },
                retryPolicy,
                checkNotNull(metrics) { "metrics is required" },
                overrideHostHeader,
            )
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3

        @JvmStatic fun newHttpClientBuilder(backendServiceId: Id): Builder = Builder(backendServiceId)
    }
}
