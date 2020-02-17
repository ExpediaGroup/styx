/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import com.google.common.net.MediaType
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpHeaderNames.HOST
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

//    init {
//        feature("Styx HTTP servers") {
//            scenario("Start accepting traffic after plugins are loaded") {
//                val barrier = CyclicBarrier(2);
//                val latch2 = CountDownLatch(1);
//
//                styxServer.restartAsync(additionalPlugins = mapOf(
//                        "plug-x" to SlowlyStartingPlugin(barrier, latch2)
//                ))
//
//                barrier.await()
//
//                Thread.sleep(100)
//                styxServer().proxyHttpAddress().shouldBeNull()
//
//                latch2.countDown()
//
//                eventually(2.seconds, AssertionError::class.java) {
//                    testClient.send(get("/")
//                            .addHeader(HOST, styxServer().adminHostHeader())
//                            .build())
//                            .wait()
//                            .status() shouldBe OK
//                }
//            }
//        }
//    }
}

private class SlowlyStartingPlugin(val barrier: CyclicBarrier, val latch2: CountDownLatch) : Plugin {
    override fun intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain) = chain.proceed(request)

    override fun styxStarting() {
        barrier.await()
        latch2.await();
    }

    override fun adminInterfaceHandlers() = mapOf<String, HttpHandler>(
            "/path/one" to HttpAggregator(StaticBodyHttpHandler(MediaType.PLAIN_TEXT_UTF_8, "X: Response from first admin interface")),
            "/path/two" to HttpAggregator(StaticBodyHttpHandler(MediaType.PLAIN_TEXT_UTF_8, "X: Response from second admin interface"))
    )
}
