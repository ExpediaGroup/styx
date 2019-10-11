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
package com.hotels.styx.proxy

import java.util.concurrent.TimeUnit

import ch.qos.logback.classic.Level.ERROR
import com.google.common.base.Charsets.{US_ASCII, UTF_8}
import com.google.common.net.HostAndPort
import com.hotels.styx.StyxProxySpec
import com.hotels.styx.api.HttpHeaderNames.{CONTENT_TYPE, HOST}
import com.hotels.styx.client.StyxHeaderConfig.STYX_INFO_DEFAULT
import com.hotels.styx.support.TestClientSupport
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, ProxyConfig, StyxConfig}
import com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent
import com.hotels.styx.support.matchers.LoggingTestSupport
import com.hotels.styx.support.matchers.RegExMatcher.matchesRegex
import com.hotels.styx.utils.HttpTestClient
import io.netty.buffer.Unpooled
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.codec.http.HttpHeaderNames.CONNECTION
import io.netty.handler.codec.http.HttpMethod.GET
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http._
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.{hasItem, is}
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._
class BadRequestsSpec extends FunSpec
  with StyxProxySpec
  with TestClientSupport
  with Eventually {
  val normalBackend = FakeHttpServer.HttpStartupConfig().start()

  override val styxConfig = StyxConfig(ProxyConfig(requestTimeoutMillis = 300))

  var loggingSupport: LoggingTestSupport = new LoggingTestSupport(classOf[HttpErrorStatusCauseLogger])

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends(
      "/badrequest" -> HttpBackend("myapp", Origins(normalBackend))
    )
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()
    super.afterAll()
  }

  override protected def beforeEach() = {
    super.beforeEach()
    loggingSupport = new LoggingTestSupport(classOf[HttpErrorStatusCauseLogger])
  }

  override protected def afterEach(): Unit = {
    loggingSupport.stop()
    super.afterEach()
  }

  describe("Handling of bad requests") {
    describe("When styx receives a bad request") {

      it("Responds with 400 Bad Request when fails to decode an HTTP message.") {
        val request = Unpooled.copiedBuffer("GET TTTTTTTTTTTTTTTTY http://foo.com/badrequest/1 HTTP/1.1\r\n\r\n", US_ASCII)
        val client = craftedRequestHttpClient("localhost", styxServer.httpPort)

        withTestClient(client) {
          client.write(request)
          val response = client.waitForResponse(3, TimeUnit.SECONDS).asInstanceOf[FullHttpResponse]

          val content = response.content().toString(UTF_8)
          assert(response.status == BAD_REQUEST, s"\nExpecting 400 Bad Request in message: \n$response \n\n$content\n\n")
          assert(content == BAD_REQUEST.reasonPhrase())
          assertThat(response.headers().get(STYX_INFO_DEFAULT), matchesRegex("noJvmRouteSet;"))
          assertThat(response.headers().get(CONNECTION), is("close"))

          assertThat(loggingSupport.log(), hasItem(loggingEvent(ERROR, "Failure status=\"400 Bad Request\"", "io.netty.handler.codec.DecoderException", "com.hotels.styx.server.BadRequestException.*")))
          eventually(timeout(5 seconds)) {
            assertThat(client.isOpen, is(false))
          }
        }
      }

      it("Responds with 400 Bad Request for requests with unrecognised HTTP verbs") {
        val request = Unpooled.copiedBuffer("FAKEVERB http://foo.com/badrequest/2 HTTP/1.1\r\nhost: localhost:8080\r\n\r\n", US_ASCII)
        val client = craftedRequestHttpClient("localhost", styxServer.httpPort)

        withTestClient(client) {
          client.write(request)
          val response = client.waitForResponse(3, TimeUnit.SECONDS).asInstanceOf[FullHttpResponse]

          val content = response.content().toString(UTF_8)
          assert(response.status == BAD_REQUEST, s"\nExpecting 400 Bad Request in message: \n$response \n\n$content\n\n")
          assertThat(response.headers().get(STYX_INFO_DEFAULT), matchesRegex("noJvmRouteSet;"))
          assert(content == BAD_REQUEST.reasonPhrase())
          assertThat(response.headers().get(CONNECTION), is("close"))
        }
      }

      it("Responds with 400 Bad Request for multiple Host headers") {
        val requestMessage =
          """
            |GET /badrequest/4 HTTP/1.1
            |Host: no.hotels.com
            |Host: cz.hotels.com
            |
          """.stripMargin
        val request = Unpooled.copiedBuffer(requestMessage, US_ASCII)
        val client = craftedRequestHttpClient("localhost", styxServer.httpPort)

        withTestClient(client) {
          client.write(request)
          val response = client.waitForResponse(3, TimeUnit.SECONDS).asInstanceOf[FullHttpResponse]

          val content = response.content().toString(UTF_8)
          assert(response.status == BAD_REQUEST, s"\nExpecting 400 Bad Request in message: \n$response \n\n$content\n\n")
          assertThat(response.headers().get(STYX_INFO_DEFAULT), matchesRegex("noJvmRouteSet;"))
          assertThat(response.headers().get(CONNECTION), is("close"))

          assertThat(loggingSupport.log(), hasItem(loggingEvent(ERROR, "Failure status=\"400 Bad Request\"", "io.netty.handler.codec.DecoderException", "com.hotels.styx.server.BadRequestException: Bad Host header. .*")))
        }
      }

      it("Passes through invalid cookies and therefore responds with 200 OK") {
        val requestMessage =
          """
            |GET /badrequest/4 HTTP/1.1
            |Host: no.hotels.com
            |Cookie: $Path=foo
            |
          """.stripMargin
        val request = Unpooled.copiedBuffer(requestMessage, US_ASCII)
        val client = craftedRequestHttpClient("localhost", styxServer.httpPort)

        withTestClient(client) {
          client.write(request)
          val response = client.waitForResponse(3, TimeUnit.SECONDS).asInstanceOf[FullHttpResponse]

          val content = response.content().toString(UTF_8)
          assert(response.status == OK, s"\nExpecting 200 OK in message: \n$response \n\n$content\n\n")
          assertThat(response.headers().get(STYX_INFO_DEFAULT), matchesRegex("noJvmRouteSet;.*"))
        }
      }

      it("Responds with 408 Request Timeout when client does not send a full HTTP request within configurable time.") {
        val partial: DefaultHttpRequest = new DefaultHttpRequest(HTTP_1_1, GET, "/badrequest/5")
        partial.headers()
          .add(CONTENT_TYPE.toString, "text/html; charset=UTF-8")
          .add(HOST, styxServer.proxyHost)
        partial.headers().add("Content-Length", 500)

        val client = httpTestClient("localhost", styxServer.httpPort)
        withTestClient(client) {
          client.write(partial)
          val response = client.waitForResponse(((styxConfig.proxyConfig.requestTimeoutMillis * 5) millis).toSeconds, SECONDS).asInstanceOf[FullHttpResponse]

          val content = response.content().toString(UTF_8)
          assert(response.status == REQUEST_TIMEOUT, s"\nExpecting 408 Request Timeout in message: \n$response \n\n$content\n\n")
          assertThat(response.headers().get(STYX_INFO_DEFAULT), matchesRegex("noJvmRouteSet;[0-9a-f-]+"))
          assertThat(response.headers().get(CONNECTION), is("close"))
          assertThat(loggingSupport.log(), hasItem(loggingEvent(ERROR, "Failure status=\"408 Request Timeout\"", "com.hotels.styx.server.RequestTimeoutException", "message=DefaultHttpRequest.decodeResult: success.*")))
        }
      }
    }
  }

  def httpTestClient(hostname: String, port: Int): HttpTestClient = {
    new HttpTestClient(HostAndPort.fromParts(hostname, port),
      new ChannelInitializer[Channel] {
        override def initChannel(ch: Channel): Unit = {
          ch.pipeline()
            .addLast(new HttpClientCodec())
            .addLast(new HttpObjectAggregator(8192))
        }
      }).connect()
  }
}
