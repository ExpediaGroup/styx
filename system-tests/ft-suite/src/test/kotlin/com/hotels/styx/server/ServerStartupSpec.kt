/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import ch.qos.logback.classic.Level
import com.hotels.styx.StyxServer
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.plugins.spi.Plugin
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.adminHostHeader
import com.hotels.styx.support.matchers.LoggingTestSupport
import com.hotels.styx.support.testClient
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.eventually
import io.kotlintest.matchers.types.shouldBeNull
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier


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

    override fun afterSpec(spec: Spec) {
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

                eventually(2.seconds, AssertionError::class.java) {
                    testClient.send(get("/")
                            .addHeader(HOST, styxServer().adminHostHeader())
                            .build())
                            .wait()
                            .status() shouldBe OK
                }
            }

            scenario("Startup fails due to plugin error") {
                val logger = LoggingTestSupport(StyxServer::class.java)

                try {
                    styxServer.restart(
                            loggingConfig = null,
                            additionalPlugins = mapOf(
                            "plug-y" to FailsToStartPlugin()
                    ))
                } catch (e: IllegalStateException) {
                    // Pass
                }

                logger.lastMessage().let {
                    event ->
                    event!!.level == Level.ERROR
                    event.formattedMessage.shouldBe("Failed to start service= [FAILED] cause={}")
                }
            }

            scenario("Startup fails due incorrect configuration") {
                val logger = LoggingTestSupport(StyxServer::class.java)

                try {
                    styxServer.restart(
                            loggingConfig = null,
                            configuration = """
                            ---
                            proxy:
                              connectors:
                                http:
                                  port: -1
                    
                            admin:
                              connectors:
                                http:
                                  port: 0
            
                            httpPipeline:
                              type: StaticResponseHandler
                              config:
                                status: 200
                            """.trimIndent()
                    )
                } catch (e: IllegalStateException) {
                    // Pass
                }

                logger.lastMessage().let {
                    event ->
                    event!!.level == Level.ERROR
                    event.formattedMessage.shouldBe("Failed to start service=NettyServer [FAILED] cause={}")
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
}

private class FailsToStartPlugin() : Plugin {
    override fun intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain) = chain.proceed(request)

    override fun styxStarting() {
        throw RuntimeException("Plugin failed.")
    }
}
