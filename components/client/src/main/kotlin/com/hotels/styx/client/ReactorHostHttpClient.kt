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

import com.hotels.styx.api.Buffers
import com.hotels.styx.api.ByteStream
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpHeaders
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpResponseStatus.statusWithCode
import com.hotels.styx.api.HttpVersion.httpVersion
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.exceptions.OriginUnreachableException
import com.hotels.styx.api.exceptions.ResponseTimeoutException
import com.hotels.styx.api.exceptions.TransportLostException
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetric
import com.hotels.styx.client.ReactorHostHttpClient.ErrorType.REQUEST
import com.hotels.styx.client.ReactorHostHttpClient.ErrorType.RESPONSE
import com.hotels.styx.client.applications.OriginStats
import com.hotels.styx.client.connectionpool.LatencyTiming.finishRequestTiming
import com.hotels.styx.client.connectionpool.LatencyTiming.startResponseTiming
import com.hotels.styx.client.connectionpool.MaxPendingConnectionTimeoutException
import com.hotels.styx.client.connectionpool.MaxPendingConnectionsExceededException
import com.hotels.styx.common.logging.HttpRequestMessageLogger
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.metrics.Deleter
import com.hotels.styx.metrics.ReactorNettyMeterFilter
import com.hotels.styx.metrics.TimerMetric
import io.netty.channel.Channel
import io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS
import io.netty.channel.ChannelOption.SO_KEEPALIVE
import io.netty.channel.ChannelOption.TCP_NODELAY
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.ssl.SslHandshakeTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.resolver.dns.DnsNameResolverException
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory.getLogger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.ByteBufFlux
import reactor.netty.Connection
import reactor.netty.Metrics.REGISTRY
import reactor.netty.NettyInbound
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientConfig
import reactor.netty.http.client.HttpClientResponse
import reactor.netty.http.client.PrematureCloseException
import reactor.netty.internal.shaded.reactor.pool.PoolAcquirePendingLimitException
import reactor.netty.internal.shaded.reactor.pool.PoolAcquireTimeoutException
import reactor.netty.resources.ConnectionProvider
import reactor.netty.resources.LoopResources
import reactor.netty.tcp.SslProvider.SslContextSpec
import java.net.SocketException
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import javax.annotation.concurrent.ThreadSafe

/**
 * A Reactor HTTP Client for proxying to an individual origin host.
 */
@ThreadSafe
class ReactorHostHttpClient private constructor(
    private val origin: Origin,
    private val connectionPool: ReactorConnectionPool,
    private val httpConfig: HttpConfig,
    private val h2SslProvider: Consumer<SslContextSpec>?,
    private val h11SslHandler: Consumer<Channel>?,
    private val responseTimeoutMillis: Int,
    private val httpRequestMessageLogger: HttpRequestMessageLogger?,
    private val originStatsFactory: OriginStatsFactory,
    private val metrics: CentralisedMetrics,
    private val eventLoopGroup: LoopResources,
    private val doOnResponse: ((HttpClientResponse, HttpInterceptor.Context?) -> Unit)? = null,
) : HostHttpClient {
    private val httpClient: HttpClient
    private val ongoingRequestCount: AtomicInteger = AtomicInteger()
    private val pendingAcquireCount: AtomicInteger = AtomicInteger()
    private val stats: Stats = Stats()
    private val connectionProvider: ConnectionProvider = connectionPool.getConnectionProvider(origin)

    @Volatile
    private var ongoingRequestsDeleter: Deleter? = null

    @Volatile
    private var originStats: OriginStats? = null

    init {
        require(h2SslProvider == null || h11SslHandler == null) {
            "There can only be one type of SSL context"
        }

        REGISTRY.config().meterFilter(ReactorNettyMeterFilter(origin))
        httpClient = HttpClient.create(connectionProvider).init()
        httpClient.warmup().block()
    }

    override fun sendRequest(
        request: LiveHttpRequest,
        context: HttpInterceptor.Context?,
    ): Publisher<LiveHttpResponse> =
        httpClient.addListeners(request, context)
            .sendRequest(request, context)
            .addStyxResponseListeners(request)

    override fun close() {
        connectionProvider.dispose()
        ongoingRequestsDeleter?.delete()
    }

    override fun loadBalancingMetric(): LoadBalancingMetric = LoadBalancingMetric(stats.ongoingRequestCount())

    internal fun configuration(): HttpClientConfig = httpClient.configuration()

    private fun HttpClient.init(): HttpClient =
        this.host(origin.host())
            .port(origin.port())
            .option(CONNECT_TIMEOUT_MILLIS, connectionPool.connectTimeoutMillis)
            .option(TCP_NODELAY, true)
            .option(SO_KEEPALIVE, true)
            .protocol(*connectionPool.supportedHttpProtocols())
            .addSslContext()
            .compress(httpConfig.compress())
            .httpResponseDecoder {
                it.maxInitialLineLength(httpConfig.maxInitialLength())
                    .maxHeaderSize(httpConfig.maxHeadersSize())
                    .maxChunkSize(httpConfig.maxChunkSize())
            }
            .responseTimeout(Duration.ofMillis(responseTimeoutMillis.toLong()))
            .metrics(true) { _ -> origin.id().toString() }
            .runOn(eventLoopGroup)
            .disableRetry(true)
            .resolver {
                it.cacheMaxTimeToLive(Duration.ofSeconds(DNS_MAX_CACHE_TIME_TO_LIVE_SECONDS))
                    .cacheNegativeTimeToLive(Duration.ofSeconds(DNS_NEGATIVE_TIME_TO_LIVE_SECONDS))
            }
            .doOnChannelInit { _, _, _ ->
                if (ongoingRequestsDeleter == null) {
                    ongoingRequestsDeleter =
                        metrics.proxy.client.ongoingRequests(origin)
                            .register { loadBalancingMetric().ongoingActivities() }
                }
            }
            .doOnConnect { pendingAcquireCount.incrementAndGet() }
            .doOnConnected { pendingAcquireCount.decrementAndGet() }

    private fun HttpClient.addSslContext(): HttpClient =
        if (connectionPool.isHttp2() && h2SslProvider != null) {
            secure(h2SslProvider)
        } else if (!connectionPool.isHttp2() && h11SslHandler != null) {
            doOnChannelInit { _, channel, _ ->
                h11SslHandler.accept(channel)
            }
        } else {
            this
        }

    private fun HttpClient.addListeners(
        request: LiveHttpRequest,
        context: HttpInterceptor.Context?,
    ): HttpClient {
        var requestLatencyTiming: TimerMetric.Stopper? = null
        var timeToFirstByteTiming: TimerMetric.Stopper? = null

        if (originStats == null) {
            originStats = originStatsFactory.originStats(origin)
        }

        return this
            .doOnRequest { _, _ ->
                httpRequestMessageLogger?.logRequest(request, origin)
                requestLatencyTiming = originStats!!.requestLatencyTimer().startTiming()
                timeToFirstByteTiming = originStats!!.timeToFirstByteTimer().startTiming()
            }
            .doAfterRequest { _, _ ->
                // Request timing started at Styx Server first receiving requests
                finishRequestTiming(context)
                requestLatencyTiming?.stop()
            }
            .doOnRequestError { _, throwable ->
                logError(REQUEST, request, throwable)
                requestLatencyTiming?.stop()
            }
            .doOnResponse { response, _ ->
                updateHttpResponseCounters(originStats!!, response.status().code())
                timeToFirstByteTiming?.stop()
                doOnResponse?.invoke(response, context)
            }
            .doAfterResponseSuccess { _, _ ->
                // Response timing stopping at Styx Server returning response to end users
                startResponseTiming(metrics, context)
            }
            .doOnResponseError { _, throwable ->
                logError(RESPONSE, request, throwable)
                timeToFirstByteTiming?.stop()
            }
    }

    private fun HttpClient.sendRequest(
        request: LiveHttpRequest,
        context: HttpInterceptor.Context?,
    ): Mono<LiveHttpResponse> {
        context?.add(ORIGINID_CONTEXT_KEY, origin.id())
        return this
            .headers {
                request.headers().forEach { name, value ->
                    it.add(name, value)
                }
                if (!request.header(HOST).isPresent) {
                    it.add(HOST, origin.hostAndPortString())
                }
            }
            .request(HttpMethod(request.method().name()))
            .uri(request.url().toString())
            .send(
                ByteBufFlux.fromInbound(
                    Flux.from(request.body())
                        .map(Buffers::toByteBuf)
                        .flatMapSequential { Flux.just(it) },
                ).doOnCancel { originStats?.requestCancelled() },
            )
            .responseConnection { res: HttpClientResponse, conn: Connection ->
                toStyxResponse(res, conn.inbound())
            }
            .single()
            .onErrorMap { toStyxExceptions(it) }
    }

    private fun toStyxResponse(
        response: HttpClientResponse,
        nettyInbound: NettyInbound,
    ): Mono<LiveHttpResponse> {
        val headersBuilder =
            HttpHeaders.Builder().apply {
                response.responseHeaders().forEach { add(it.key, it.value) }
            }
        val body = toStyxByteStream(nettyInbound)
        return Mono.just(
            LiveHttpResponse.Builder()
                .status(statusWithCode(response.status().code(), response.status().toString()))
                .version(httpVersion(response.version().text()))
                .headers(headersBuilder.build())
                .body(body)
                .build(),
        )
    }

    private fun toStyxByteStream(nettyInbound: NettyInbound): ByteStream =
        ByteStream(
            nettyInbound
                .receive()
                .retain()
                .map(Buffers::fromByteBuf)
                .doOnCancel { originStats?.requestCancelled() }
                .onErrorMap { toStyxExceptions(it) },
        )

    private fun Mono<LiveHttpResponse>.addStyxResponseListeners(request: LiveHttpRequest): Mono<LiveHttpResponse> =
        this.doOnNext { httpRequestMessageLogger?.logResponse(request, it) }
            .doOnSubscribe { ongoingRequestCount.incrementAndGet() }
            .doFinally { ongoingRequestCount.decrementAndGet() }

    private fun logError(
        type: ErrorType,
        request: LiveHttpRequest,
        throwable: Throwable,
    ) = LOGGER.error(
        """
                |Error Handling ${type.name} request=$request exceptionClass=${throwable.javaClass.name} exceptionMessage=\"${throwable.message}\"
        """.trimMargin(),
    )

    private fun updateHttpResponseCounters(
        originStats: OriginStats,
        statusCode: Int,
    ) {
        if (isServerError(statusCode)) {
            originStats.requestError()
        } else {
            originStats.requestSuccess()
        }
        originStats.responseWithStatusCode(statusCode)
    }

    private fun isServerError(status: Int) = status >= 500

    private fun toStyxExceptions(throwable: Throwable): Throwable =
        when (throwable) {
            is PoolAcquireTimeoutException ->
                MaxPendingConnectionTimeoutException(origin, connectionPool.pendingAcquireTimeoutMillis)

            is PoolAcquirePendingLimitException -> {
                pendingAcquireCount.decrementAndGet()
                MaxPendingConnectionsExceededException(
                    origin,
                    stats.pendingAcquireCount(),
                    connectionPool.pendingAcquireMaxCount,
                )
            }

            is ReadTimeoutException -> ResponseTimeoutException(origin)

            is SslHandshakeTimeoutException, is DnsNameResolverException, is UnknownHostException ->
                OriginUnreachableException(origin, throwable.cause)

            is SocketException, is PrematureCloseException ->
                TransportLostException(configuration().remoteAddress().get(), origin)

            is DecoderException, is IllegalArgumentException ->
                BadHttpResponseException(origin, throwable.cause)

            else -> throwable
        }

    inner class Stats {
        fun ongoingRequestCount(): Int = ongoingRequestCount.get()

        fun pendingAcquireCount(): Int = pendingAcquireCount.get()
    }

    private enum class ErrorType {
        REQUEST,
        RESPONSE,
    }

    /**
     * A factory for creating ReactorHostHttpClient instances.
     */
    fun interface Factory {
        fun create(
            origin: Origin,
            connectionPool: ReactorConnectionPool,
            httpConfig: HttpConfig,
            h2SslProvider: Consumer<SslContextSpec>?,
            h11SslHandler: Consumer<Channel>?,
            responseTimeoutMillis: Int,
            httpRequestMessageLogger: HttpRequestMessageLogger?,
            originStatsFactory: OriginStatsFactory,
            metrics: CentralisedMetrics,
            eventLoopGroup: LoopResources,
            doOnResponse: ((HttpClientResponse, HttpInterceptor.Context?) -> Unit)?,
        ): ReactorHostHttpClient
    }

    companion object : Factory {
        private val LOGGER = getLogger(this::class.java)
        private const val ORIGINID_CONTEXT_KEY = "styx.originid"
        private const val X_HTTP2_STREAM_ID = "x-http2-stream-id"
        private const val DNS_MAX_CACHE_TIME_TO_LIVE_SECONDS = 30L
        private const val DNS_NEGATIVE_TIME_TO_LIVE_SECONDS = 5L

        override fun create(
            origin: Origin,
            connectionPool: ReactorConnectionPool,
            httpConfig: HttpConfig,
            h2SslProvider: Consumer<SslContextSpec>?,
            h11SslHandler: Consumer<Channel>?,
            responseTimeoutMillis: Int,
            httpRequestMessageLogger: HttpRequestMessageLogger?,
            originStatsFactory: OriginStatsFactory,
            metrics: CentralisedMetrics,
            eventLoopGroup: LoopResources,
            doOnResponse: ((HttpClientResponse, HttpInterceptor.Context?) -> Unit)?,
        ): ReactorHostHttpClient =
            ReactorHostHttpClient(
                origin,
                connectionPool,
                httpConfig,
                h2SslProvider,
                h11SslHandler,
                responseTimeoutMillis,
                httpRequestMessageLogger,
                originStatsFactory,
                metrics,
                eventLoopGroup,
                doOnResponse,
            )
    }
}
