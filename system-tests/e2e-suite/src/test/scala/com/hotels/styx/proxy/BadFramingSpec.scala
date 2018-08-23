/*
  Copyright (C) 2013-2018 Expedia Inc.

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

import java.util.concurrent.TimeUnit.SECONDS

import com.github.tomakehurst.wiremock.client.WireMock._
import com.google.common.base.Charsets.UTF_8
import com.google.common.net.HostAndPort._
import com.hotels.styx._
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.{BAD_REQUEST, OK}
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import com.hotels.styx.support.{NettyOrigins, TestClientSupport}
import com.hotels.styx.utils.HttpTestClient
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO
import io.netty.buffer.Unpooled.copiedBuffer
import io.netty.channel._
import io.netty.handler.codec.http.HttpHeaders.Names.{CONTENT_LENGTH, HOST, TRANSFER_ENCODING}
import io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED
import io.netty.handler.codec.http.HttpMethod._
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http._
import org.scalatest.FunSpec

class BadFramingSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with NettyOrigins
  with TestClientSupport {


  val (originOne, originOneServer) = originAndCustomResponseWebServer("NettyOrigin")

  val originTwoServer = FakeHttpServer.HttpStartupConfig()
    .start()
    .stub(post(urlMatching("/badFramingSpec/two/.")), aResponse.withStatus(200).withHeader(STUB_ORIGIN_INFO.toString, "application"))


  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/badFramingSpec/one/" -> HttpBackend("app-1", Origins(originOneServer)),
      "/badFramingSpec/two/" -> HttpBackend("app-2", Origins(originTwoServer))
    )
  }

  override protected def afterAll(): Unit = {
    originTwoServer.stop()
    originOneServer.stopAsync().awaitTerminated()

    super.afterAll()
  }

  describe("Detects bad HTTP message framing.") {

    it("Returns BAD_REQUEST Bad Request if client request contains illegal url.") {
      originRespondingWith(status200OkResponse)

      val client = aggregatingTestClient("localhost", styxServer.httpPort)
      val request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/no5_such3_file7.pl?\"><script>alert(73541);</script>56519<script>alert(1)</script>0e134")
      request.headers().add(HOST, styxServer.proxyHost)

      withTestClient(client) {
        client.write(request)
        val response = client.waitForResponse().asInstanceOf[FullHttpResponse]
        assert(response.status == HttpResponseStatus.BAD_REQUEST, clueMessage("Must receive 400 Bad Request, but received: ", response))
      }
    }

    it("Returns BAD_REQUEST Bad Request if client request contains multiple differing content-length headers.") {
      originRespondingWith(status200OkResponse)
      val request = get(styxServer.routerURL("/badFramingSpec/one/foo/e"))
        .disableValidation()
        .addHeader(CONTENT_LENGTH, "50")
        .addHeader(CONTENT_LENGTH, "60")
        .build()

      val response = decodedRequest(request)
      response.status() should be(BAD_REQUEST)
    }


    it("Returns BAD_REQUEST Bad Request if client request contains multiple differing content-length values.") {
      originRespondingWith(status200OkResponse)
      val request = get(styxServer.routerURL("/badFramingSpec/one/foo/f"))
        .disableValidation()
        .header(CONTENT_LENGTH, "50, 60")
        .build()

      val response = decodedRequest(request)
      response.status() should be(BAD_REQUEST)
    }


    it("Returns BAD_REQUEST Bad Request if client request contains multiple identical content-length values.") {
      originRespondingWith(status200OkResponse)
      val request = get(styxServer.routerURL("/badFramingSpec/one/foo/g"))
        .disableValidation()
        .header(CONTENT_LENGTH, "50, 50")
        .build()

      val response = decodedRequest(request)
      response.status() should be(BAD_REQUEST)
    }


    it("Assumes chunked encoding when response from origins contains both Content-Length and chunked encoding") {
      originRespondingWith((ctx: ChannelHandlerContext, msg: scala.Any) => {
        if (msg.isInstanceOf[LastHttpContent]) {
          val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK)
          response.headers().set(CONTENT_LENGTH, "60")
          response.headers().set(TRANSFER_ENCODING, CHUNKED)
          ctx.writeAndFlush(response)
          ctx.writeAndFlush(new DefaultHttpContent(copiedBuffer("a" * 10, UTF_8)))
          ctx.writeAndFlush(new DefaultHttpContent(copiedBuffer("b" * 20, UTF_8)))
          ctx.writeAndFlush(new DefaultLastHttpContent(copiedBuffer("c" * 30, UTF_8)))
        }
      })
      val request = get(styxServer.routerURL("/badFramingSpec/one/foo/i"))
        .build()
      val response = decodedRequest(request)
      response.status() should be(OK)
      response.header(CONTENT_LENGTH).isPresent should be(false)

      assert(response.bodyAs(UTF_8) == "a" * 10 + "b" * 20 + "c" * 30, s"\nReceived incorrect body: ${response.bodyAs(UTF_8)}")
    }


    it("Assumes inbound request is chunked when it contains both Content-Length and chunked transfer encoding.") {
      val request = new DefaultHttpRequest(HTTP_1_1, POST, "/badFramingSpec/two/j", false)
      request.headers().set(HOST, styxServer.proxyHost)
      request.headers().set(CONTENT_LENGTH, "90")
      request.headers().set(TRANSFER_ENCODING, CHUNKED)

      val client = chunkedTransferHttpClient("localhost", styxServer.httpPort)
      withTestClient(client) {
        client.write(request)
        client.write(new DefaultHttpContent(copiedBuffer("a" * 30, UTF_8)))
        client.write(new DefaultHttpContent(copiedBuffer("b" * 20, UTF_8)))
        client.write(new DefaultLastHttpContent(copiedBuffer("c" * 40, UTF_8)))

        val response = client.waitForResponse(3, SECONDS).asInstanceOf[HttpResponse]
        response.status should be(HttpResponseStatus.OK)
      }

      originTwoServer.verify(
        postRequestedFor(urlEqualTo("/badFramingSpec/two/j"))
          .withHeader(TRANSFER_ENCODING, equalTo(CHUNKED))
          .withoutHeader(CONTENT_LENGTH))
    }
  }

  def clueMessage(message: String, response: FullHttpResponse) = {
    "\n" + message + s"\nReceived response: \n$response\n\n" + response.content().toString(UTF_8) + "\n\n"
  }

  def chunkedTransferHttpClient(hostname: String, port: Int): HttpTestClient = {
    val client = new HttpTestClient(fromParts(hostname, port),
      new ChannelInitializer[Channel] {
        override def initChannel(ch: Channel): Unit = {
          ch.pipeline().addLast(new HttpClientCodec())
        }
      })
    client.connect()
    client
  }
}
