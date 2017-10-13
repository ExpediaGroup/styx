/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx

import com.google.common.base.Charsets._
import com.hotels.styx.support.api.BlockingObservables
import com.hotels.styx.support.api.BlockingObservables._
import com.hotels.styx.api.HttpRequest.Builder.get
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.client.StyxHttpClient._
import com.hotels.styx.support.NettyOrigins
import com.hotels.styx.support.configuration.{BackendService, ImplicitOriginConversions, Origins}
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled._
import io.netty.channel.ChannelFutureListener.CLOSE
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._
import org.scalatest._
import rx.functions.{Func1, Func2}
import rx.observers.TestSubscriber

import scala.concurrent.duration._

class HttpResponseSpec extends FunSuite
  with ImplicitOriginConversions
  with BeforeAndAfterAll
  with ShouldMatchers
  with BeforeAndAfter
  with Matchers
  with NettyOrigins {

  val (originOne, originOneServer) = originAndCustomResponseWebServer("app", "h1")

  var client: StyxHttpClient = _

  val responseTimeout = 1000.millis

  var testSubscriber: TestSubscriber[com.hotels.styx.api.HttpResponse] = _

  override protected def afterAll(): Unit = {
    originOneServer.stopAsync().awaitTerminated()
  }

  before {
    testSubscriber = new TestSubscriber[com.hotels.styx.api.HttpResponse]()

    client = newHttpClientBuilder(
      BackendService(
        origins = Origins(originOne),
        responseTimeout = responseTimeout).asJava)
      .build
  }

  test("Determines response content length from server closing the connection.") {
    originRespondingWith(response200OkFollowedFollowedByServerConnectionClose("Test message body."))

    val response = BlockingObservables.stringResponse(client.sendRequest(get("/foo/3").build()))
    val headers = response.responseBuilder().build()
    val body = response.body()

    assert(headers.status() == OK, s"\nDid not get response with 200 OK status.\n$response\n")
    assert(body == "Test message body.", s"\nIncorrect response body.")
  }

  def response200OkFollowedFollowedByServerConnectionClose(content: String): (ChannelHandlerContext, Any) => Any = {
    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      if (msg.isInstanceOf[LastHttpContent]) {
        val response = new DefaultFullHttpResponse(HTTP_1_1, OK, copiedBuffer(content, UTF_8))
        ctx.writeAndFlush(response).addListener(CLOSE)
      }
    }
  }

  def bodyFrom(response: api.HttpResponse): Option[String] =
    getFirst(response.body.content.map[String](new Func1[ByteBuf, String]() {
      override def call(t1: ByteBuf): String = t1.toString(UTF_8)
    }).reduce(None, new Func2[Option[String], String, Option[String]] {
      override def call(t1: Option[String], t2: String): Option[String] = t1 match {
        case Some(string) => Some(string + t2)
        case None => Some(t2)
      }
    }))
}