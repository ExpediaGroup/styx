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

import com.hotels.styx._
import com.hotels.styx.generators.HttpRequestGenerator
import com.hotels.styx.support._
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import com.hotels.styx.support.generators.{HttpObjects, NettyHttpMessageGenerator}
import com.hotels.styx.utils.HttpTestClient
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpMethod.GET
import io.netty.handler.codec.http.HttpResponseStatus.{INTERNAL_SERVER_ERROR, OK}
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http.{FullHttpResponse, HttpObject, HttpResponseStatus, LastHttpContent, _}
import org.scalacheck.Prop._
import org.scalacheck.Test
import org.scalatest.prop.{Checkers, Configuration}
import org.scalatest.{BeforeAndAfter, FunSpec}

import scala.util.Try

class OriginResponseResiliencySpec extends FunSpec
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

  describe("Styx origin response resiliency") {

    it("should pass through valid responses and transforms invalid responses to responses with relevant status codes") {
      setRandomNumberGenerator("origin response handling")

      val httpResponses = NettyHttpMessageGenerator(styxServer.proxyHost).nettyResponse
      check(forAll(httpResponses) {
        (httpResponseObjects: HttpObjects) =>
          originRespondingWith(responseFrom(httpResponseObjects.objects))

          val request = new DefaultFullHttpRequest(HTTP_1_1, GET, styxServer.routerURL("/proxyPropertySpec/random-responses"))
          request.headers().add(HOST, styxServer.proxyHost)

          val sent = tryToSend(List(request))
          val response = waitForResponse().get

          reconnectClientAfterHttpError(response.getStatus)

          response.getStatus != INTERNAL_SERVER_ERROR
      })
    }
  }

  def sendRequest(request: HttpRequest): HttpResponse = {
    val sent = tryToSend(Seq(request))
    waitForResponse().get
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

  def reconnectClientAfterHttpError(status: HttpResponseStatus): Unit = {
    if (status != OK) {
      testClient.disconnect()
      testClient = aggregatingTestClient("localhost", styxServer.httpPort)
    }
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

  def responseFrom(httpObjects: List[HttpObject]): HttpResponderFunc = (ctx: ChannelHandlerContext, msg: Any) => {
    if (msg.isInstanceOf[LastHttpContent]) {
      httpObjects.foreach {
        ctx.writeAndFlush(_)
      }
    }
  }
}