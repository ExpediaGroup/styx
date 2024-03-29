/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.server

import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpHeaderValues.PLAIN_TEXT
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.plugins.spi.Plugin
import com.hotels.styx.common.http.handler.HttpAggregator
import com.hotels.styx.common.http.handler.StaticBodyHttpHandler
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.adminHostHeader
import com.hotels.styx.support.testClient
import com.hotels.styx.support.wait
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import kotlin.time.Duration.Companion.seconds


class ServerStartupSpec : FeatureSpec() {
    val styxServer = StyxServerProvider(
            defaultConfig = """
                ---
                proxy:
                  connectors:
                    http:
                      port: 0

                admin:
                  connectors:
                    http:
                      port: 0

                httpPipeline:
                  type: StaticResponseHandler
                  config:
                    status: 200
                """.trimIndent())

    override suspend fun afterSpec(spec: Spec) {
        styxServer.stop()
    }

    init {
        feature("Styx HTTP servers") {
            scenario("Start accepting traffic after plugins are loaded") {
                val barrier = CyclicBarrier(2);
                val latch2 = CountDownLatch(1);

                styxServer.restartAsync(additionalPlugins = mapOf(
                        "plug-x" to SlowlyStartingPlugin(barrier, latch2)
                ))

                barrier.await()

                Thread.sleep(100)
                styxServer().proxyHttpAddress().shouldBeNull()

                latch2.countDown()

                eventually(2.seconds) {
                    testClient.send(get("/")
                            .addHeader(HOST, styxServer().adminHostHeader())
                            .build())
                            .wait()
                            .status() shouldBe OK
                }
            }
        }
    }
}

private class SlowlyStartingPlugin(val barrier: CyclicBarrier, val latch2: CountDownLatch) : Plugin {
    override fun intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain) = chain.proceed(request)

    override fun styxStarting() {
        barrier.await()
        latch2.await();
    }

    override fun adminInterfaceHandlers() = mapOf<String, HttpHandler>(
            "/path/one" to HttpAggregator(StaticBodyHttpHandler(PLAIN_TEXT, "X: Response from first admin interface")),
            "/path/two" to HttpAggregator(StaticBodyHttpHandler(PLAIN_TEXT, "X: Response from second admin interface"))
    )
}
