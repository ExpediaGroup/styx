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

import _root_.io.netty.handler.codec.http.HttpHeaders.Names.{UPGRADE, _}
import _root_.io.netty.handler.codec.http.HttpHeaders.Values._
import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpHeaderNames.X_FORWARDED_FOR
import com.hotels.styx.api.HttpHeaderValues
import com.hotels.styx.api.RequestCookie.requestCookie
import com.hotels.styx.api.ResponseCookie.responseCookie
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.support.NettyOrigins
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import org.scalatest.FunSpec

import scala.collection.JavaConversions._


class HeadersSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with NettyOrigins {
  val (originOne, originOneServer) = originAndCustomResponseWebServer("NettyOrigin")

  val recordingBackend = FakeHttpServer.HttpStartupConfig().start()

  override protected def beforeEach() = {
    recordingBackend.reset()
    recordingBackend.stub(urlPathEqualTo("/headers"), aResponse.withStatus(200))
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/headers" -> HttpBackend("app-2", Origins(recordingBackend)),
      "/badheaders" -> HttpBackend("app-1", Origins(originOne))
    )
  }

  override protected def afterAll(): Unit = {
    recordingBackend.stop()
    originOneServer.stopAsync().awaitTerminated()

    super.afterAll()
  }

  describe("Headers handling") {

    it("should pass through most http headers to the backend") {
      val req = get("/headers")
        .addHeader(HOST, styxServer.proxyHost)
        .addHeader("Foo", "bar")
        .addHeader("User-Agent", "Styx/1.0")
        .build()

      val resp = decodedRequest(req)

      assert(resp.status() == OK)

      recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers"))
        .withHeader("Foo", equalTo("bar"))
        .withHeader("User-Agent", equalTo("Styx/1.0"))
        .withHeader("Host", equalTo(styxServer.proxyHost)))
    }

    it("should not add a default User-Agent if there isn't one in the request") {
      val req = get("/headers")
        .addHeader(HOST, styxServer.proxyHost)
        .build()

      val resp = decodedRequest(req)

      assert(resp.status() == OK)

      recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers"))
        .withoutHeader("User-Agent"))
    }

    describe("setting host header") {

      it("it should set the host if missing") {
        val req = get(s"http://localhost:${recordingBackend.port()}/headers")
          .build()

        val resp = decodedRequest(req)

        assert(resp.status() == OK)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers"))
          .withHeader(HOST, equalTo(s"localhost:${recordingBackend.port()}")))
      }

      it("should use host and port from an absolute URI to override the Host header") {
        val req = get(s"http://localhost:${recordingBackend.port()}/headers")
          .addHeader(HOST, "www.example.com")
          .build()

        val resp = decodedRequest(req)

        assert(resp.status() == OK)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers"))
          .withHeader(HOST, equalTo(s"localhost:${recordingBackend.port()}")))
      }

    }

    describe("setting to X-Forwarded-For header") {

      it("should add the client IP to X-Forwarded-For") {
        val req = get("/headers")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        assert(resp.status() == OK)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers"))
          .withHeader(X_FORWARDED_FOR.toString, equalTo("127.0.0.1")))

      }

      it("should append the client IP to X-Forwarded-For") {
        val req = get("/headers")
          .addHeader(HOST, styxServer.proxyHost)
          .addHeader(X_FORWARDED_FOR, "10.9.8.7")
          .build()

        val resp = decodedRequest(req)

        assert(resp.status() == OK)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers"))
          .withHeader(X_FORWARDED_FOR.toString, equalTo("10.9.8.7, 127.0.0.1")))

      }
    }

    describe("setting VIA header") {

      it("should add itself to the Via request header for an HTTP/1.1 request") {
        val req = get("/headers")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        assert(resp.status() == OK)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers"))
          .withHeader(VIA, equalTo("1.1 styx")))
      }

      it("should append itself to the Via request header for an HTTP/1.1 request") {
        val req = get("/headers")
          .addHeader(HOST, styxServer.proxyHost)
          .addHeader(VIA, "1.1 apache")
          .build()

        val resp = decodedRequest(req)

        assert(resp.status() == OK)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers"))
          .withHeader(VIA, equalTo("1.1 apache, 1.1 styx")))
      }

      it("should add itself to the Via response heaver") {
        val req = get("/headers")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)
        resp.headers(VIA).toSeq should equal(Iterable("1.1 styx"))
      }

      it("should append itself to the Via response header") {
        recordingBackend.stub(urlPathEqualTo("/headers"), aResponse.withStatus(200)
          .withHeader(VIA, "1.1 apache"))

        val req = get("/headers")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers")))

        assert(resp.header(VIA).get() == "1.1 apache, 1.1 styx")
      }
    }

    describe("hop by hop headers") {

      it("should remove hop by hop headers, apart from Transfer-Encoding, from request") {
        val req = get("/headers")
          .header(HOST, styxServer.proxyHost)
          .header("Keep-Alive", "true")
          .header(PROXY_AUTHENTICATE, "true")
          .header(PROXY_AUTHORIZATION, "foo")
          .header(TE, "bar")
          .header(TRAILER, "true")
          .header(TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
          .header(UPGRADE, "true")
          .addHeader("Test-Case", "1")
          .build()

        val resp = decodedRequest(req)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers")))
      }

      it("should removes hop by hop headers from response.") {
        recordingBackend.stub(urlPathEqualTo("/headers"), aResponse
          .withStatus(OK.code())
          .withHeader("Keep-Alive", "foo")
          .withHeader(PROXY_AUTHENTICATE, "foo")
          .withHeader(PROXY_AUTHORIZATION, "foo")
          .withHeader(TE, "foo")
          .withHeader(TRAILER, "foo")
          .withHeader(TRANSFER_ENCODING, CHUNKED)
          .withHeader(UPGRADE, "foo")
        )


        val req = get("/headers")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers")))

        resp.status() should be(OK)
        resp.header("Keep-Alive").isPresent should be(false)
        resp.header(PROXY_AUTHENTICATE).isPresent should be(false)
        resp.header(PROXY_AUTHORIZATION).isPresent should be(false)
        resp.header(TE).isPresent should be(false)
        resp.header(TRAILER).isPresent should be(false)
        resp.header(UPGRADE).isPresent should be(false)
      }


      it("should remove all fields from request indicated by Connection header value") {
        val req = get("/headers")
          .header(HOST, styxServer.proxyHost)
          .addHeader(CONNECTION, "Foo, Bar, Baz")
          .addHeader("Foo", "abc")
          .addHeader("Foo", "def")
          .header("Foo", "last")
          .addHeader("Bar", "one, two, three")
          .addHeader("Test-Case", "2")
          .build()

        val resp = decodedRequest(req)

        recordingBackend.verify(getRequestedFor(urlEqualTo("/headers"))
          .withoutHeader(CONNECTION)
          .withoutHeader("Foo")
          .withoutHeader("Bar")
          .withoutHeader("Baz")
          .withHeader("Test-Case", equalTo("2"))
        )
      }


      it("should remove all fields from response indicated by Connection header value") {
        recordingBackend.stub(urlPathEqualTo("/headers"), aResponse()
          .withStatus(OK.code())
          .withHeader("Keep-Alive", "foo")
          .withHeader(PROXY_AUTHENTICATE, "foo")
          .withHeader(PROXY_AUTHORIZATION, "foo")
          .withHeader(TE, "foo")
          .withHeader(TRAILER, "foo")
          .withHeader(TRANSFER_ENCODING, CHUNKED)
          .withHeader(UPGRADE, "foo")
        )

        val req = get("/headers")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        resp.status() should be(OK)
        resp.header(CONNECTION).isPresent should be(false)
        resp.header("Foo").isPresent should be(false)
        resp.header("Bar").isPresent should be(false)
        resp.header("Baz").isPresent should be(false)

      }
    }

    describe("bad headers content") {

      it("should return HTTP BAD_GATEWAY Bad Gateway if origin returns multiple differing content-length headers.") {
        recordingBackend.stub(urlPathEqualTo("/headers"), aResponse.withStatus(200)
          .withHeader(CONTENT_LENGTH, "50")
          .withHeader(CONTENT_LENGTH, "60")
        )

        val req = get("/headers")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/headers")))

        assert(resp.status() == BAD_GATEWAY)
      }

      it("should return HTTP BAD_GATEWAY Bad Gateway if origin returns multiple differing content-length values.") {
        originRespondingWith(
          responseWithHeaders(
            HttpHeader(CONTENT_LENGTH, "50, 60")))

        val req = get("/badheaders")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        assert(resp.status() == BAD_GATEWAY)
      }

      it("Returns HTTP BAD_GATEWAY Bad Gateway if origin returns multiple identical content-length values.") {
        originRespondingWith(
          responseWithHeaders(
            HttpHeader(CONTENT_LENGTH, "50, 50")))

        val req = get("/badheaders")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        assert(resp.status() == BAD_GATEWAY)
      }

      it("should remove Content-Length from response when both Content-Length and Transfer-Encoding: chunked headers are returned by the origin") {
        originRespondingWith(
          responseWithHeaders(
            HttpHeader(CONTENT_LENGTH, "50"),
            HttpHeader(TRANSFER_ENCODING, CHUNKED)))

        val req = get("/badheaders")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        assert(resp.status() == OK)
        assert(!resp.contentLength.isPresent, "content length header should be empty")
        assert(resp.header(TRANSFER_ENCODING).get() == CHUNKED)
      }
    }

    describe("handling cookies") {

      it("should leave request and response quoted cookie values") {
        recordingBackend.stub(urlPathEqualTo("/quotedCookies"), aResponse.withStatus(200)
          .withHeader("Set-Cookie", "test-cookie=\"hu_hotels_com,HCOM_HU,hu_HU,\"; Version=1; Domain=.example.com; Path=/"))

        styxServer.setBackends(
          "/quotedCookies" -> HttpBackend("app-2", Origins(recordingBackend)),
          "/badheaders" -> HttpBackend("app-1", Origins(originOne))
        )

        val req = get("/quotedCookies")
          .addHeader(HOST, styxServer.proxyHost)
          .cookies(requestCookie("test-cookie", "\"hu_hotels_com,HCOM_HU,hu_HU,\""))
          .build()

        val resp = decodedRequest(req)

        recordingBackend.verify(getRequestedFor(urlPathEqualTo("/quotedCookies"))
          .withHeader("Cookie", equalTo("test-cookie=\"hu_hotels_com,HCOM_HU,hu_HU,\""))
          .withHeader("Host", equalTo(styxServer.proxyHost)))

        assert(resp.cookie("test-cookie").get == responseCookie("test-cookie", "\"hu_hotels_com,HCOM_HU,hu_HU,\"").domain(".example.com").path("/").build())
      }

      it("should handle http only cookies") {
        recordingBackend.stub(urlPathEqualTo("/cookies"), aResponse.withStatus(200)
          .withHeader("Set-Cookie", "SESSID=sessid; Domain=.example.com; Path=/; HttpOnly")
          .withHeader("Set-Cookie", "abc=1; Domain=.example.com; Path=/"))

        styxServer.setBackends(
          "/cookies" -> HttpBackend("app-2", Origins(recordingBackend)),
          "/badheaders" -> HttpBackend("app-1", Origins(originOne))
        )

        val req = get("/cookies")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)
        assert(resp.cookies().size() == 2)
        assert(resp.cookie("abc").get == responseCookie("abc", "1").domain(".example.com").path("/").build())
        assert(resp.cookie("SESSID").get == responseCookie("SESSID", "sessid").domain(".example.com").path("/").httpOnly(true).build())

      }
    }
  }
}