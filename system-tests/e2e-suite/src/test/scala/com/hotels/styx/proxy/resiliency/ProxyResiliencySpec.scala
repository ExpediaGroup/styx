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
import com.hotels.styx.StyxProxySpec
import com.hotels.styx.generators.HttpRequestGenerator
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.{NettyOrigins, TestClientSupport}
import com.hotels.styx.utils.HttpTestClient
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http._
import org.scalacheck.Test
import org.scalatest.prop.{Checkers, Configuration, PropertyChecks}
import org.scalatest.{BeforeAndAfter, FunSpec}

import scala.util.{Failure, Success, Try}

class ProxyResiliencySpec extends FunSpec
  with PropertyChecks
  with StyxProxySpec
  with HttpRequestGenerator
  with Checkers
  with Configuration
  with TestClientSupport
  with NettyOrigins
  with BeforeAndAfter {

  val (originOne, originOneServer) = originAndCustomResponseWebServer("NettyOrigin")

  val logback = fixturesHome(this.getClass, "/conf/logback/logback-suppress-errors.xml")
  override val styxConfig = StyxConfig(
    logbackXmlLocation = logback
  )

  private var testClient: HttpTestClient = _

  override protected def beforeAll() = {
    super.beforeAll()

    styxServer.setBackends(
      "/" -> HttpBackend("app", Origins(originOne))
    )
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

    it("should reject requests with bad URIs") {
      setRandomNumberGenerator("client request handling")
      forAll(badRequests(styxHost())) { (request: HttpRequest) =>
        sendRequest(request) match {
          case Success(response) => assertResponseIsBadRequest(response)
          case Failure(exception) => println(exception)
        }
      }
    }

    it("should reject requests with bad header names") {
      setRandomNumberGenerator("client request handling")
      forAll(requestsWithBadHeaderNames(styxHost())) { (request: HttpRequest) =>
        originRespondingWith(status200OkResponse)

        sendRequest(request) match {
          case Success(response) =>
            println("response=" + response)
            assertResponseIsBadRequest(response)
          case Failure(exception) => println(exception)
        }
      }
    }
  }

  def assertResponseIsBadClientOrOK(response: HttpResponse): Boolean = {
    Seq(response.getStatus.code() % 100) contains oneOf(2, 4)
  }

  def assertResponseIsBadRequest(response: HttpResponse) = {
    assert(response.getStatus == BAD_REQUEST, s"received response $response")
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

  def ensureClientConnectedIfOk(status: HttpResponseStatus): Unit = {
    if (status == OK && !testClient.isOpen) {
      fail("Client disconnected after successful response.")
    }
  }

  def ensureErrorCodeReceivedWhenPartiallySent(sent: Try[Unit], status: HttpResponseStatus): Unit = {
    if (sent.isFailure && status != BAD_REQUEST) {
      fail("Failed to send request, expected 400 BAD REQUEST but styx responded with status: [%s]".format(status))
    }
  }

  def reconnectClientAfterHttpError(status: HttpResponseStatus): Unit = {
    if (status != OK) {
      testClient.disconnect()
      testClient = aggregatingTestClient("localhost", styxServer.httpPort)
      println("Client reconnected after HTTP status=%d.".format(status.code()))
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

  def errorMessage(message: String, response: FullHttpResponse) = s"\n$message\n$response\n\n${
    response.content().toString(UTF_8)
  }\n"
}
