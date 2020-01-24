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
package com.hotels.styx.proxy

import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.GZIPInputStream

class CompressionSpec : FeatureSpec() {

    init {
        feature("Content-type of the response is compressible") {
            scenario("Compresses HTTP response if client requests gzip accept-encoding") {
                val request = get("/11")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .header("accept-encoding", "7z, gzip")
                        .build();

                client.send(request)
                        .wait()!!
                        .let {
                            it.status() shouldBe (OK)
                            it.header("content-encoding").get() shouldBe "gzip"
                            ungzip(it.body()) shouldBe ("Hello from http server!")
                        }
            }
            scenario("Does not compress HTTP response if client did not send accept-encoding") {
                val request = get("/11")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build();

                client.send(request)
                        .wait()!!
                        .let {
                            it.status() shouldBe (OK)
                            it.header("content-encoding").isPresent shouldBe false
                            it.bodyAs(UTF_8) shouldBe ("Hello from http server!")
                        }
            }

            scenario("Does not compress HTTP response if unsupported accept-encoding") {
                val request = get("/11")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .header("accept-encoding", "7z")
                        .build();

                client.send(request)
                        .wait()!!
                        .let {
                            it.status() shouldBe (OK)
                            it.header("content-encoding").isPresent shouldBe false
                            it.bodyAs(UTF_8) shouldBe ("Hello from http server!")
                        }
            }

            scenario("Does not compress response if already compressed (content-encoding is already set)") {
                val request = get("/compressed")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .header("accept-encoding", "gzip")
                        .build();

                client.send(request)
                        .wait()!!
                        .let {
                            it.status() shouldBe (OK)
                            it.header("content-encoding").get() shouldBe "gzip"
                            it.bodyAs(UTF_8) shouldBe ("Hello from http server!")
                        }
            }

        }
        feature("Content-type  of the response is not compressible") {
            scenario("Does not compress HTTP response even if accept-encoding is present") {
                val request = get("/image")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .header("accept-encoding", "gzip")
                        .build();

                client.send(request)
                        .wait()!!
                        .let {
                            it.status() shouldBe (OK)
                            it.header("content-encoding").isPresent shouldBe false
                            it.bodyAs(UTF_8) shouldBe ("Hello from http server!")
                        }
            }
        }

    }

    private fun ungzip(content: ByteArray): String = GZIPInputStream(content.inputStream()).bufferedReader(UTF_8).use { it.readText() }


    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    val styxServer = StyxServerProvider("""
        proxy:
          compressResponses: true
          connectors:
            http:
              port: 0


        admin:
          connectors:
            http:
              port: 0

        httpPipeline:
          type: InterceptorPipeline
          config:
            handler:
              type: ConditionRouter
              config:
                routes:
                  - condition: path() == "/image"
                    destination:
                      name: jpeg-image
                      type: StaticResponseHandler
                      config:
                        status: 200
                        content: "Hello from http server!"
                        headers:
                            - name: content-type
                              value: image/jpeg
                              
                  - condition: path() == "/compressed"
                    destination:
                      name: compressed-plain-text
                      type: StaticResponseHandler
                      config:
                        status: 200
                        content: "Hello from http server!"
                        headers:
                            - name: content-type
                              value: text/plain
                            - name: content-encoding
                              value: gzip
                  
                fallback:
                  type: StaticResponseHandler
                  config:
                    status: 200
                    content: "Hello from http server!"
                    headers:
                        - name: content-type
                          value: text/plain
      """.trimIndent())


    override fun beforeSpec(spec: Spec) {
        styxServer.restart()
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }
}
