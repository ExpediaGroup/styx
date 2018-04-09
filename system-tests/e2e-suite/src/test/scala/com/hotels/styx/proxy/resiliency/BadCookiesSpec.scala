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
package com.hotels.styx.proxy.resiliency

import java.util.concurrent.TimeUnit._

import com.google.common.base.Charsets._
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import com.hotels.styx.generators.HttpRequestGenerator
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import com.hotels.styx.support.generators.{CookieHeaderGenerator, CookieHeaderString}
import com.hotels.styx.support.{NettyOrigins, TestClientSupport}
import com.hotels.styx.utils.HttpTestClient
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpMethod._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT
import io.netty.handler.codec.http._
import org.scalacheck.Prop._
import org.scalacheck.Test
import org.scalatest.prop.{Checkers, Configuration}
import org.scalatest.{BeforeAndAfter, FunSpec}

import scala.util.Try

class BadCookiesSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with HttpRequestGenerator
  with Checkers
  with Configuration
  with TestClientSupport
  with NettyOrigins
  with BeforeAndAfter {

  val (originOne, originOneServer) = originAndCustomResponseWebServer("NettyOrigin")

  private var testClient: HttpTestClient = _

  override protected def beforeAll() = {
    super.beforeAll()
    styxServer.setBackends("/" -> HttpBackend("app-1", Origins(originOneServer)))
  }

  before {
    testClient = aggregatingTestClient("localhost", styxServer.httpPort)
  }

  after {
    testClient.disconnect()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()

    originOneServer.stopAsync().awaitTerminated()
  }

  def styxHost() = s"localhost:${styxServer.httpPort}"

  def sendRequest(request: HttpRequest): Try[FullHttpResponse] = {
    testClient.disconnect()
    testClient = aggregatingTestClient("localhost", styxServer.httpPort)
    tryToSend(Seq(request)).map(_ => waitForResponse().get)
  }

  describe("Styx resiliency") {

    it("Proxies request with bad cookie headers") {
      setRandomNumberGenerator("should accept requests with bad cookie headers")
      originRespondingWith(status200OkResponse)

      val badCookies = CookieHeaderGenerator().badCookieHeaders

      check(forAllNoShrink(badCookies) {
        (input: CookieHeaderString) => {
          val request: DefaultHttpRequest = requestWithCookie(input)

          val sent = tryToSend(List(request, EMPTY_LAST_CONTENT))

          val response = waitForResponse().get

          testClient.disconnect()
          testClient = aggregatingTestClient("localhost", styxServer.httpPort)

          response.getStatus == OK
        }
      })
    }
  }

  def requestWithCookie(input: CookieHeaderString, checkHeaders: Boolean = false): DefaultHttpRequest = {
    val request = new DefaultHttpRequest(HTTP_1_1, GET, "/foo", checkHeaders)
    request.headers().add(COOKIE, input.text)
    request.headers().add(HOST, styxHost())
    request
  }

  def tryToSend(chunks: Seq[HttpObject]): Try[Unit] = {
    Try[Unit] {
      sendRequestObjects(chunks)
    }
  }

  def setRandomNumberGenerator(description: String) {
    val seed = System.currentTimeMillis()
    info("Setting ScalaCheck RNG seed value: " + seed + " for: " + description)
    info(
      """In case of a test failure, use this seed to re-run the test.
        |It will generate an identical sequence of inputs for
        |debugging purposes.""".stripMargin)
    Test.Parameters.default.rng.setSeed(seed)
  }

  def sendRequestObjects(requestObjects: Seq[HttpObject]): Unit = {
    val request = requestObjects.head.asInstanceOf[HttpRequest]
    requestObjects.foreach {
      testClient.write(_)
    }
  }

  def waitForResponse(): Option[FullHttpResponse] = {
    val response = testClient.waitForResponse(5, SECONDS)
    response match {
      case null => None
      case _ => Some(response.asInstanceOf[FullHttpResponse])
    }
  }
}
