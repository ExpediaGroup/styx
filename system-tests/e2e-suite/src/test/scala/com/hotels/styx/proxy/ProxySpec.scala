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
package com.hotels.styx.proxy

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.hotels.styx.MockServer.responseSupplier
import com.hotels.styx.api.HttpHeaderNames._
import com.hotels.styx.api.HttpMethod.CONNECT
import com.hotels.styx.api.HttpRequest.{get, head}
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.api.HttpVersion._
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.client.StyxHeaderConfig.STYX_INFO_DEFAULT
import com.hotels.styx.client.{HttpClient, StyxHttpClient}
import com.hotels.styx.common.FreePorts._
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origin, Origins}
import com.hotels.styx.support.matchers.IsOptional.{isValue, matches}
import com.hotels.styx.support.matchers.RegExMatcher.matchesRegex
import com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith
import com.hotels.styx.{DefaultStyxConfiguration, MockServer, StyxProxySpec}
import org.hamcrest.MatcherAssert.assertThat
import org.scalatest.BeforeAndAfter
import org.scalatest.funspec.AnyFunSpec
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Optional
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.Await
import scala.concurrent.duration._

class ProxySpec extends AnyFunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with BeforeAndAfter {
  val mockServer = new MockServer(0)

  private val LOGGER = LoggerFactory.getLogger(getClass)
  val recordingBackend = FakeHttpServer.HttpStartupConfig().start()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    mockServer.startAsync().awaitRunning()
  }

  override protected def afterAll(): Unit = {
    mockServer.stopAsync().awaitTerminated()
    recordingBackend.stop()
    super.afterAll()
  }

  before {
    styxServer.setBackends(
      "/" -> HttpBackend("default-app", Origins(recordingBackend)))
  }

  describe("Standard behaviour") {
    it("should respond with response from origin + expected enhancements") {
      styxServer.setBackends(
        "/" -> HttpBackend("app-1", Origins(recordingBackend))
      )

      recordingBackend
        .stub(urlPathEqualTo("/"), aResponse
          .withStatus(200)
          .withHeader("headerName", "headerValue")
          .withBody("bodyContent")
        )

      val req = get("/")
        .addHeader(HOST, styxServer.proxyHost)
        .build()

      val resp = decodedRequest(req)

      recordingBackend.verify(getRequestedFor(urlPathEqualTo("/")))
      assert(resp.status() == OK)
      assertThat(resp.header("headerName"), isValue("headerValue"))
      assert(resp.bodyAs(UTF_8) == "bodyContent")
      assertThat(resp.header(STYX_INFO_DEFAULT), matches(matchesRegex("noJvmRouteSet;[0-9a-f-]+")))
    }
  }

  describe("Advertising http 1.1") {
    it("should advertise HTTP/1.1 when proxies HTTP/1.0 request.") {
      styxServer.setBackends(
        "/" -> HttpBackend("app-1", Origins(recordingBackend)),
        "/http10" -> HttpBackend("http10", Origins(mockServer))
      )

      mockServer.stub("/http10", responseSupplier(() => response().build()))


      val req = get("/http10")
        .addHeader(HOST, styxServer.proxyHost)
        .build()

      val resp = decodedRequest(req)


      val recordedReq = mockServer.takeRequest()
      assert(recordedReq.version() == HTTP_1_1)
    }
  }

  describe("Handling responses without body") {
    recordingBackend
      .stub(urlPathEqualTo("/bodiless"), aResponse.withStatus(200))
      .stub(WireMock.head(urlPathEqualTo("/bodiless")), aResponse.withStatus(200))

    it("should respond to HEAD with bodiless response") {

      val client: HttpClient = new StyxHttpClient.Builder()
        .connectTimeout(1000, MILLISECONDS)
        .maxHeaderSize(2 * 8192)
        .build()

      val req = head("/bodiless")
        .addHeader(HOST, styxServer.proxyHost)
        .build()

      val resp = Await.result(client.send(req).toScala, 5.seconds)

      recordingBackend.verify(headRequestedFor(urlPathEqualTo("/bodiless")))

      assert(resp.status() == OK)
      assertThatResponseIsBodiless(resp)
    }

    it("should remove body from the 204 No Content responses") {
      recordingBackend
        .stub(urlPathEqualTo("/204"), aResponse.withStatus(204).withBody("I should not be here"))

      val request = get("/204")
        .addHeader(HOST, styxServer.proxyHost)
        .build()

      val response = decodedRequest(request)
      recordingBackend.verify(getRequestedFor(urlPathEqualTo(request.path())))
      assert(response.status() == NO_CONTENT)
      assertThatResponseIsBodiless(response)
    }

    ignore("should remove body from the 304 Not Modified responses") {
      recordingBackend
        .stub(urlPathEqualTo("/304"), aResponse.withStatus(304).withBody("I should not be here"))

      val request = get("/304")
        .addHeader(HOST, styxServer.proxyHost)
        .build()

      val response = decodedRequest(request)
      recordingBackend.verify(getRequestedFor(urlPathEqualTo(request.path())))
      assert(response.status() == NOT_MODIFIED)
      assertThatResponseIsBodiless(response)
    }

    describe("backend services unavailable") {
      // The specific behaviour depends on the pool implementation.
      // The improved connection pool attempts to recreate failed connections in the background.
      // The connection failures are no longer visible for the pool consumers. Instead they just
      // stay in the pending subscriber queue and time out.

      it("should return a 503 Service Unavailable if the connection to the backend is refused") {
        styxServer.setBackends(
          "/" -> HttpBackend("app-1", Origins(recordingBackend)),
          "/unavailable" -> HttpBackend("http10", Origins(Origin("localhost", freePort(), "app-1-01")))
        )

        val req = get("/unavailable")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)
        assert(resp.status() == SERVICE_UNAVAILABLE)
        assert(resp.header(CONNECTION) == Optional.of("close"))
      }
    }

    describe("no configured backend service") {
      it("should return a 502 BAD_GATEWAY if a backend service is not configured") {
        styxServer.setBackends()

        val req = get("/nonexistant-service")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        val resp = decodedRequest(req)

        LOGGER.info("resp: " + resp)
        LOGGER.info("body: " + resp.bodyAs(UTF_8))

        assert(resp.status() == BAD_GATEWAY)
        assert(resp.header(CONNECTION) == Optional.of("close"))
      }
    }

    describe("request url handling") {
      recordingBackend
        .stub(urlStartingWith("/url"), aResponse.withStatus(200))

      it("should substitute URL path according to configuration") {
        val req = get("/url/search.do?resolved-location=CITY%3A549499%3APROVIDED%3APROVIDED&destination-id=549499&q-destination=London%2C%20England%2C%20United%20Kingdom&q-rooms=1&q-room-0-adults=2&q-room-0-children=0")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        decodedRequest(req)
        recordingBackend.verify(receivedRewrittenUrl("/url/search.do?resolved-location=CITY%3A549499%3APROVIDED%3APROVIDED&destination-id=549499&q-destination=London%2C%20England%2C%20United%20Kingdom&q-rooms=1&q-room-0-adults=2&q-room-0-children=0"))
      }

      it("should encode the path") {
        val req = get("/url/lp/de408991/%D7%9E%D7%9C%D7%95%D7%A0%D7%95%D7%AA-%D7%A7%D7%95%D7%A4%D7%A0%D7%94%D7%92%D7%9F-%D7%93%D7%A0%D7%9E%D7%A8%D7%A7/")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        decodedRequest(req)
        recordingBackend.verify(receivedRewrittenUrl("/url/lp/de408991/%D7%9E%D7%9C%D7%95%D7%A0%D7%95%D7%AA-%D7%A7%D7%95%D7%A4%D7%A0%D7%94%D7%92%D7%9F-%D7%93%D7%A0%D7%9E%D7%A8%D7%A7/"))
      }

      it("should pass url with allowed chars in the path without encoding") {
        val req = get("/url/newsletter/subscribe.html;sessid=lUmrydZOJM85gj5BliH_qVcl-V.noJvmRouteSet")
          .addHeader(HOST, styxServer.proxyHost)
          .build()

        decodedRequest(req)
        recordingBackend.verify(receivedRewrittenUrl("/url/newsletter/subscribe.html;sessid=lUmrydZOJM85gj5BliH_qVcl-V.noJvmRouteSet"))
      }

      def receivedRewrittenUrl(newUrl: String): RequestPatternBuilder = getRequestedFor(urlEqualTo(newUrl))
    }


    def assertThatResponseIsBodiless(response: HttpResponse) {
      val headers = response.headers()
      assert(response.contentLength().orElse(0) == 0, s"\nexpected headers with no Content-Length header but found $headers")
      assert(!response.chunked(), s"\nexpected headers with no Transfer-Encoding header but found $headers")
      assert(response.bodyAs(UTF_8).isEmpty, s"\nexpected response with no body but found ${response.bodyAs(UTF_8)}")
    }
  }

  describe("Handling CONNECT method") {
    it("Should reject CONNECT requests") {
      val request = get("/connectTest")
        .addHeader(HOST, styxServer.proxyHost)
        .method(CONNECT)
        .build();

      assert(decodedRequest(request).status() == METHOD_NOT_ALLOWED)
    }

  }

}
