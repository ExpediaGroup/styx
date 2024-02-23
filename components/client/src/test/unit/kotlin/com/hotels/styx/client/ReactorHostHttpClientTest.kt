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
import com.hotels.styx.api.HttpHeaderNames.CHUNKED
import com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.Id.GENERIC_APP
import com.hotels.styx.api.MicrometerRegistry
import com.hotels.styx.api.exceptions.ResponseTimeoutException
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.service.TlsSettings
import com.hotels.styx.client.ssl.SslContextFactory
import com.hotels.styx.common.logging.HttpRequestMessageLogger
import com.hotels.styx.metrics.CentralisedMetrics
import io.kotest.assertions.throwables.shouldNotThrowMessage
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.netty.channel.Channel
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlinx.coroutines.repackaged.net.bytebuddy.utility.RandomString
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import reactor.core.publisher.Mono
import reactor.netty.http.Http2SslContextSpec
import reactor.netty.http.HttpProtocol.H2
import reactor.netty.http.HttpProtocol.HTTP11
import reactor.netty.http.client.Http2AllocationStrategy
import reactor.netty.resources.ConnectionProvider
import reactor.netty.resources.LoopResources
import reactor.netty.tcp.SslProvider
import reactor.test.StepVerifier
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

class ReactorHostHttpClientTest : StringSpec() {
    private val context: HttpInterceptor.Context = mockk()
    private val httpRequestMessageLogger: HttpRequestMessageLogger = mockk(relaxed = true)
    private val connectionPool: ReactorConnectionPool = mockk(relaxed = true)
    private lateinit var h2MockServerWithAlpn: MockWebServer
    private lateinit var h2MockServerWithoutAlpn: MockWebServer
    private lateinit var h11MockServer: MockWebServer
    private lateinit var originStats: OriginStatsFactory
    private lateinit var meterRegistry: MicrometerRegistry
    private lateinit var metrics: CentralisedMetrics

    init {
        beforeSpec {
            val localhostCertificate =
                HeldCertificate.Builder()
                    .addSubjectAlternativeName(ORIGIN_1.host())
                    .addSubjectAlternativeName(ORIGIN_2.host())
                    .addSubjectAlternativeName(ORIGIN_3.host())
                    .build()
            val serverCertificates =
                HandshakeCertificates.Builder()
                    .heldCertificate(localhostCertificate)
                    .build()

            h11MockServer = MockWebServer()
            h11MockServer.useHttps(serverCertificates.sslSocketFactory(), false)
            h11MockServer.protocols = listOf(Protocol.HTTP_1_1)
            h11MockServer.start(ORIGIN_1.port())

            h2MockServerWithAlpn = MockWebServer()
            h2MockServerWithAlpn.useHttps(serverCertificates.sslSocketFactory(), false)
            h2MockServerWithAlpn.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
            h2MockServerWithAlpn.start(ORIGIN_2.port())

            h2MockServerWithoutAlpn = MockWebServer()
            h2MockServerWithoutAlpn.useHttps(serverCertificates.sslSocketFactory(), false)
            h2MockServerWithoutAlpn.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
            h2MockServerWithoutAlpn.protocolNegotiationEnabled = false
            h2MockServerWithoutAlpn.start(ORIGIN_3.port())
        }

        beforeTest {
            meterRegistry = MicrometerRegistry(SimpleMeterRegistry())
            metrics = CentralisedMetrics(meterRegistry)
            originStats = OriginStatsFactory.CachingOriginStatsFactory(metrics)
        }

        afterTest { clearAllMocks() }

        afterSpec {
            h11MockServer.shutdown()
            h2MockServerWithAlpn.shutdown()
            h2MockServerWithoutAlpn.shutdown()
            LOOP_RESOURCES.dispose()
        }

        "HttpClient is created with connectionProvider passed" {
            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = H11_SSL_HANDLER,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            reactorHostClient.configuration().connectionProvider() shouldBe connectionProvider
        }

        "init sets responseTimeoutMillis" {
            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = H11_SSL_HANDLER,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            reactorHostClient.configuration().responseTimeout()!!.toMillis() shouldBe 1000
        }

        "init sets protocols" {
            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(H2, HTTP11)
            every { connectionPool.isHttp2() } returns true

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_2,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = H2_SSL_PROVIDER,
                    h11SslHandler = null,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            reactorHostClient.configuration().protocols() shouldBe arrayOf(H2, HTTP11)
        }

        "init sets decoder configs" {
            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = H11_SSL_HANDLER,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            reactorHostClient.configuration().decoder().maxInitialLineLength() shouldBe HTTP_CONFIG.maxInitialLength()
            reactorHostClient.configuration().decoder().maxHeaderSize() shouldBe HTTP_CONFIG.maxHeadersSize()
        }

        "init disables default retry" {
            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = H11_SSL_HANDLER,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            reactorHostClient.configuration().isRetryDisabled shouldBe true
        }

        "init sets event loop resources" {
            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = H11_SSL_HANDLER,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            reactorHostClient.configuration().loopResources() shouldBe LOOP_RESOURCES
        }

        "sendRequest calls an ALPN enabled http2 origin and returns a response using http2" {
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("X-Id", "123")
                    .setBody("xyz")
            h2MockServerWithAlpn.enqueue(mockResponse)

            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(H2, HTTP11)
            every { connectionPool.isHttp2() } returns true

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_2,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = H2_SSL_PROVIDER,
                    h11SslHandler = null,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            StepVerifier.create(reactorHostClient.sendRequest(mockRequest, context))
                .assertNext {
                    it.status().code() shouldBe 200
                    it.header("X-Id").orElse(null) shouldBe "123"
                    it.header("x-http2-stream-id").isPresent shouldBe true
                }
                .verifyComplete()
        }

        "sendRequest calls an ALPN disabled http2 origin and returns a response which fallbacks to http1.1" {
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("X-Id", "123")
                    .setBody("xyz")
            h2MockServerWithoutAlpn.enqueue(mockResponse)

            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(H2, HTTP11)
            every { connectionPool.isHttp2() } returns true

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_3,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = H2_SSL_PROVIDER,
                    h11SslHandler = null,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            StepVerifier.create(reactorHostClient.sendRequest(mockRequest, context))
                .assertNext {
                    it.status().code() shouldBe 200
                    it.header("X-Id").orElse(null) shouldBe "123"
                    it.header("x-http2-stream-id").isEmpty shouldBe true
                }
                .verifyComplete()
        }

        "sendRequest throws errors when hitting response timeout during a request to an http2 origin" {
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("X-Id", "123")
                    .setBody("xyz")
                    .setBodyDelay(5, TimeUnit.SECONDS)
            h2MockServerWithAlpn.enqueue(mockResponse)

            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(H2, HTTP11)
            every { connectionPool.isHttp2() } returns true

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_2,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = H2_SSL_PROVIDER,
                    h11SslHandler = null,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            val response = Mono.from(reactorHostClient.sendRequest(mockRequest, context)).block()
            StepVerifier.create(response!!.body())
                .verifyError(ResponseTimeoutException::class.java)
        }

        "sendRequest calls an ALPN enabled http2 origin and returns a response using http1.1" {
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("X-Id", "123")
                    .setBody("xyz")
            h2MockServerWithAlpn.enqueue(mockResponse)

            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_2,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = H11_SSL_HANDLER,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            StepVerifier.create(reactorHostClient.sendRequest(mockRequest, context))
                .assertNext {
                    it.status().code() shouldBe 200
                    it.header("X-Id").orElse(null) shouldBe "123"
                    it.header("x-http2-stream-id").isEmpty shouldBe true
                }
                .verifyComplete()
        }

        "sendRequest calls an ALPN disabled http2 origin and returns a response using http1.1" {
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("X-Id", "123")
                    .setBody("xyz")
            h2MockServerWithoutAlpn.enqueue(mockResponse)

            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_3,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = H11_SSL_HANDLER,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            StepVerifier.create(reactorHostClient.sendRequest(mockRequest, context))
                .assertNext {
                    it.status().code() shouldBe 200
                    it.header("X-Id").orElse(null) shouldBe "123"
                    it.header("x-http2-stream-id").isEmpty shouldBe true
                }
                .verifyComplete()
        }

        "sendRequest calls an http1.1 origin and returns a response" {
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("X-Id", "123")
                    .setBody("xyz")
            h11MockServer.enqueue(mockResponse)

            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = H11_SSL_HANDLER,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            StepVerifier.create(reactorHostClient.sendRequest(mockRequest, context))
                .assertNext {
                    it.status().code() shouldBe 200
                    it.header("X-Id").orElse(null) shouldBe "123"
                    it.header("x-http2-stream-id").isEmpty shouldBe true
                }
                .verifyComplete()
        }

        "sendRequest throws errors when hitting response timeout during a request to an http1.1 origin" {
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("X-Id", "123")
                    .setBody("xyz")
                    .setBodyDelay(5, TimeUnit.SECONDS)
            h11MockServer.enqueue(mockResponse)

            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = H11_SSL_HANDLER,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            val response = Mono.from(reactorHostClient.sendRequest(mockRequest, context)).block()
            StepVerifier.create(response!!.body())
                .verifyError(ResponseTimeoutException::class.java)
        }

        "closes connection pool when the host http client is closed" {
            val mockConnectionProvider: ConnectionProvider = mockk(relaxed = true)

            every { connectionPool.getConnectionProvider(any()) } returns mockConnectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = mockk(relaxed = true),
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = H2_SSL_PROVIDER,
                    h11SslHandler = null,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = mockk(relaxed = true),
                    originStatsFactory = mockk(relaxed = true),
                    metrics = mockk(relaxed = true),
                    eventLoopGroup = mockk(relaxed = true),
                )

            every { connectionPool.supportedHttpProtocols() } returns arrayOf(H2, HTTP11)
            every { connectionPool.isHttp2() } returns true

            reactorHostClient.close()

            verify(exactly = 1) {
                mockConnectionProvider.dispose()
            }
        }

        "sendRequest calls an http1.1 origin with POST and returns a response with body" {
            val requestBody = RandomString.make(100_000)
            val responseBody = RandomString.make(100_000)
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("X-Id", "123")
                    .setBody(responseBody)
            h11MockServer.enqueue(mockResponse)

            var request =
                HttpRequest.post("/")
                    .header(TRANSFER_ENCODING, CHUNKED)
                    .body(requestBody, StandardCharsets.UTF_8)
                    .build()
                    .stream()

            val requestBodySize = AtomicInteger()
            request =
                request.newBuilder()
                    .body { body -> body.doOnEach { requestBodySize.addAndGet(it.get()?.size() ?: 0) } }
                    .build()

            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = H11_SSL_HANDLER,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            StepVerifier.create(reactorHostClient.sendRequest(request, context))
                .assertNext {
                    it.status().code() shouldBe 200
                    it.header("X-Id").orElse(null) shouldBe "123"
                    Mono.from(it.body()).map(Buffers::toByteBuf)
                        .map { chunk -> chunk.toString(StandardCharsets.UTF_8) }
                        .doOnNext { body -> body shouldBe responseBody }
                        .subscribe()
                }
                .verifyComplete()

            requestBodySize.get() shouldBe 100_000
        }

        "SSL handler for HTTP/1.1 is added to channel if h11SslHandler is passed" {
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("X-Id", "123")
                    .setBody("xyz")
            h11MockServer.enqueue(mockResponse)

            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val sslHandler = mockk<Consumer<Channel>>()

            val reactorHostClient =
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = sslHandler,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )

            runCatching {
                Mono.from(reactorHostClient.sendRequest(mockRequest, context)).block()
            }

            verify {
                sslHandler.accept(any())
            }
        }

        "SSL provider for HTTP/2 is used if h2SslProvider is passed" {
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("X-Id", "123")
                    .setBody("xyz")
            h11MockServer.enqueue(mockResponse)

            every { connectionPool.getConnectionProvider(any()) } returns connectionProvider
            every { connectionPool.connectTimeoutMillis } returns 500
            every { connectionPool.supportedHttpProtocols() } returns arrayOf(HTTP11)

            val sslProvider = mockk<Consumer<SslProvider.SslContextSpec>>(relaxed = true)

            runCatching {
                val reactorHostClient =
                    ReactorHostHttpClient.create(
                        origin = ORIGIN_2,
                        connectionPool = connectionPool,
                        httpConfig = HTTP_CONFIG,
                        h2SslProvider = sslProvider,
                        h11SslHandler = null,
                        responseTimeoutMillis = 1000,
                        httpRequestMessageLogger = httpRequestMessageLogger,
                        originStatsFactory = originStats,
                        metrics = metrics,
                        eventLoopGroup = LOOP_RESOURCES,
                    )

                reactorHostClient.configuration().sslProvider() shouldBe sslProvider.accept(SslProvider.builder())
            }
        }

        "Only 1 ssl context can be passed" {
            val sslProvider = mockk<Consumer<SslProvider.SslContextSpec>>()
            val sslHandler = mockk<Consumer<Channel>>()

            shouldThrowWithMessage<IllegalArgumentException>("There can only be one type of SSL context") {
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = sslProvider,
                    h11SslHandler = sslHandler,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )
            }
        }

        "Supports http without any sslContext" {
            shouldNotThrowMessage("There can only be one type of SSL context") {
                ReactorHostHttpClient.create(
                    origin = ORIGIN_1,
                    connectionPool = connectionPool,
                    httpConfig = HTTP_CONFIG,
                    h2SslProvider = null,
                    h11SslHandler = null,
                    responseTimeoutMillis = 1000,
                    httpRequestMessageLogger = httpRequestMessageLogger,
                    originStatsFactory = originStats,
                    metrics = metrics,
                    eventLoopGroup = LOOP_RESOURCES,
                )
            }
        }
    }

    companion object {
        private val ORIGIN_1 =
            Origin.newOriginBuilder("localhost", 58887)
                .applicationId(GENERIC_APP)
                .id("app-01")
                .build()
        private val ORIGIN_2 =
            Origin.newOriginBuilder("localhost", 58888)
                .applicationId(GENERIC_APP)
                .id("app-01")
                .build()
        private val ORIGIN_3 =
            Origin.newOriginBuilder("localhost", 58889)
                .applicationId(GENERIC_APP)
                .id("app-01")
                .build()
        private var mockRequest = HttpRequest.get("/").build().stream()
        private val connectionProvider =
            ConnectionProvider.builder("app-01")
                .allocationStrategy(
                    Http2AllocationStrategy.builder()
                        .maxConcurrentStreams(10)
                        .maxConnections(1)
                        .build(),
                )
                .evictionPredicate { _, connectionMetadata ->
                    connectionMetadata.lifeTime() >= 300000 || connectionMetadata.idleTime() >= 60000
                }
                .pendingAcquireTimeout(Duration.ofMillis(5000))
                .pendingAcquireMaxCount(1000)
                .disposeTimeout(Duration.ofMillis(1000))
                .build()
        private val LOOP_RESOURCES = LoopResources.create("app-01", 1, 1, true)
        private val HTTP_CONFIG = HttpConfig.defaultHttpConfig()
        private val H11_SSL_HANDLER: Consumer<Channel> =
            Consumer<Channel> { channel ->
                SslProvider.builder()
                    .sslContext(SslContextFactory.get(TlsSettings.Builder().build()))
                    .build()
                    .addSslHandler(channel, null, false)
            }
        private val H2_SSL_PROVIDER: Consumer<SslProvider.SslContextSpec> =
            Consumer<SslProvider.SslContextSpec> { sslContextSpec ->
                sslContextSpec.sslContext(
                    Http2SslContextSpec.forClient()
                        .configure { sslContextBuilder: SslContextBuilder ->
                            sslContextBuilder.trustManager(
                                InsecureTrustManagerFactory.INSTANCE,
                            )
                        }
                        .sslContext(),
                )
            }
    }
}
