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
package com.hotels.styx.logging

import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.ERROR
import ch.qos.logback.classic.Level.DEBUG
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST
import com.hotels.styx.client.BadHttpResponseException
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.proxy.HttpErrorStatusCauseLogger
import com.hotels.styx.support.*
import com.hotels.styx.support.matchers.LoggingTestSupport
import io.kotlintest.Spec
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldNotContain
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import kotlin.test.assertFailsWith

class HttpMessageLoggingSpec : FeatureSpec() {

    init {
        feature("Styx request/response logging") {
            styxServer.restart(loggingConfig = null)

            scenario("Logger should hide cookies and headers") {

                client.send(HttpRequest.get("/a/path")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .header("header1", "h1")
                        .header("header2", "h2")
                        .header("cookie", "cookie1=c1;cookie2=c2")
                        .build())
                        .wait()

                val expectedRequest = Regex("requestId=[-a-z0-9]+, secure=false, origin=null, "
                        + "request=\\{version=HTTP/1.1, method=GET, uri=/a/path, headers=\\[Host=localhost:[0-9]+, header1=\\*\\*\\*\\*, header2=h2, cookie=cookie1=\\*\\*\\*\\*;cookie2=c2\\], id=[-a-z0-9]+\\}")

                val expectedResponse = Regex("requestId=[-a-z0-9]+, secure=false, "
                        + "response=\\{version=HTTP/1.1, status=200 OK, headers=\\[header1=\\*\\*\\*\\*, header2=h2, cookie=cookie1=\\*\\*\\*\\*;cookie2=c2, Via=1.1 styx\\]\\}")

                logger.log().shouldContain(INFO, expectedRequest)
                logger.log().shouldContain(INFO, expectedResponse)
            }
        }

        // These tests have been disabled as our API does not allow us to create invalid cookies any longer
        feature("!Logging invalid requests/responses hides sensitive information") {
            scenario("Requests with badly-formed headers should hide sensitive cookies and headers when logged") {

                httpErrorLogger.logger.level = ERROR
                httpErrorLogger.appender.list.clear()

                val response = client.send(HttpRequest.get("/a/path")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .header("header1", "h1")
                        .header("header2", "h2")
                        .header("cookie", "cookie1=c1;cookie2=c2")
                        .header("badheader", "with\u0000nullchar")
                        .build())
                        .wait()!!

                response.status() shouldBe BAD_REQUEST

                val event = httpErrorLogger.log().first()
                event.level shouldBe ERROR

                var t = event.throwableProxy
                while (t != null) {
                    anySensitiveHeadersAreHidden(t.message)
                    t = t.cause
                }
            }

            scenario("Requests with badly-formed cookies should hide sensitive cookies and headers when logged") {

                httpErrorLogger.logger.level = ERROR
                httpErrorLogger.appender.list.clear()

                val response = client.send(HttpRequest.get("/a/path")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .header("header1", "h1")
                        .header("header2", "h2")
                        .header("cookie", "cookie1=c1;cookie2=c2;badcookie=bad\u0000bad")
                        .build())
                        .wait()!!

                response.status() shouldBe BAD_REQUEST

                val event = httpErrorLogger.log().first()
                event.level shouldBe ERROR

                var t = event.throwableProxy
                while (t != null) {
                    anySensitiveHeadersAreHidden(t.message)
                    t = t.cause
                }
            }

            scenario("Responses with badly-formed headers should hide sensitive cookies and headers when logged") {
                // In this scenario, the response generated for this request should include an invalid cookie.
                rootLogger.appender.list.clear()
                rootLogger.logger.level = DEBUG

                var exception: Throwable? = assertFailsWith<BadHttpResponseException> {
                    client.send(HttpRequest.get("/bad/path")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .header("header1", "h1")
                            .header("header2", "h2")
                            .header("cookie", "cookie1=c1;cookie2=c2")
                            .build())
                            .wait()
                }

                while (exception != null) {
                    anySensitiveHeadersAreHidden(exception.message ?: "")
                    exception = exception.cause
                }

                rootLogger.log().forEach {
                    anySensitiveHeadersAreHidden(it.message)
                    var t = it.throwableProxy
                    while (t != null) {
                        anySensitiveHeadersAreHidden(t.message)
                        t = t.cause
                    }
                }
            }
        }
    }

    private fun anySensitiveHeadersAreHidden(msg: String) {
        msg shouldNotContain "header1=h1"
        msg shouldNotContain "cookie1=c1"
        if (msg.contains("header2=h2")) {
            msg shouldContain "header1=****"
        }
        if (msg.contains("cookie2=c2")) {
            msg shouldContain "cookie1=****"
        }
    }

    private val logger = LoggingTestSupport("com.hotels.styx.http-messages.inbound")
    private val httpErrorLogger = LoggingTestSupport(HttpErrorStatusCauseLogger::class.java)
    private val rootLogger = LoggingTestSupport("ROOT")


    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    val styxServer = StyxServerProvider("""
        proxy:
          connectors:
            http:
              port: 0

            https:
              port: 0
              sslProvider: JDK
              sessionTimeoutMillis: 300000
              sessionCacheSize: 20000
              
        request-logging:
          inbound:
            enabled: true
            longFormat: true
          hideCookies:
            - cookie1
          hideHeaders:
            - header1

        admin:
          connectors:
            http:
              port: 0

        routingObjects:
          root:
            type: PathPrefixRouter
            config:
              routes:
                - prefix: /
                  destination: default
                  
          default:
            type: StaticResponseHandler
            config:
              status: 200
              content: ""
              headers:
                - name: "header1"
                  value: "h1"
                - name: "header2"
                  value: "h2" 
                - name: "cookie"
                  value: "cookie1=c1;cookie2=c2"


        httpPipeline: root
      """.trimIndent())

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }

}