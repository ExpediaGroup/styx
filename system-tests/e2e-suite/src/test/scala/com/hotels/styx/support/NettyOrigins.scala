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
package com.hotels.styx.support

import java.util.concurrent.atomic.AtomicReference

import com.google.common.net.HostAndPort
import com.google.common.net.HostAndPort._
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.api.Id._
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.Origin._
import com.hotels.styx.common.HostAndPorts._
import com.hotels.styx.server.HttpServer
import com.hotels.styx.server.netty.{NettyServerBuilder, ServerConnector}
import io.netty.buffer.Unpooled._
import io.netty.channel._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._

class NettyHttpServerConnector(serverPort: Int, handler: ChannelInboundHandlerAdapter) extends ServerConnector {
  override def `type`(): String = "http"

  override def configure(channel: Channel, httpController: HttpHandler): Unit = {
    val pipeline: ChannelPipeline = channel.pipeline()
    pipeline.addLast("encoder", new HttpResponseEncoder)
    pipeline.addLast("decoder", new HttpRequestDecoder())
    pipeline.addLast("origin-handler", handler)
  }

  override def port(): Int = serverPort
}

trait NettyOrigins {

  case class HttpHeader(name: CharSequence, value: String)

  val customResponseHandler = new CustomResponseHandler()

  def customResponseWebServer(port: Int, responseHandler: CustomResponseHandler): HttpServer = {
    val server: HttpServer = new NettyServerBuilder()
      .name("Netty test origin")
      .setHttpConnector(new NettyHttpServerConnector(port, responseHandler))
      .build()
    server

  }


  def responseWithHeaders(headers: HttpHeader*) = {
    val response = new DefaultFullHttpResponse(HTTP_1_1, OK, buffer(0), false)

    for (header <- headers) {
      response.headers().add(header.name, header.value)
    }

    (ctx: ChannelHandlerContext, msg: scala.Any) => {
      if (msg.isInstanceOf[LastHttpContent]) {
        ctx.writeAndFlush(response)
      }
    }
  }

  type HttpResponderFunc = (ChannelHandlerContext, scala.Any) => Any

  def originRespondingWith(readHandler: HttpResponderFunc): Unit = {
    customResponseHandler.setBehaviour(readHandler)
  }


  @ChannelHandler.Sharable
  class CustomResponseHandler extends ChannelInboundHandlerAdapter {
    var latestRequest: HttpRequest = null

    val behaviourRef = new AtomicReference[HttpResponderFunc]()
    setBehaviour(status200OkResponse)

    def setBehaviour(f: (ChannelHandlerContext, scala.Any) => Any) = {
      behaviourRef.set(f)
    }

    override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
      if (msg.isInstanceOf[HttpRequest]) {
        latestRequest = msg.asInstanceOf[HttpRequest]
      }
      behaviourRef.get()(ctx, msg)
    }
  }

  val status200OkResponse = (ctx: ChannelHandlerContext, msg: scala.Any) => {
    if (msg.isInstanceOf[LastHttpContent]) {
      val response = new DefaultFullHttpResponse(HTTP_1_1, OK)
      response.headers().set(CONTENT_LENGTH, "0")
      ctx.writeAndFlush(response)
    }
  }

  def originAndCustomResponseWebServer(originId: String): (Origin, HttpServer) = {
    lazy val serverPort = freePort()
    val server = customResponseWebServer(serverPort, customResponseHandler)

    val origin: Origin = newOriginBuilder(fromParts("localhost", serverPort)).id("NettyOrigin").build
    server.startAsync().awaitRunning()

    origin -> server
  }

  def originAndCustomResponseWebServer(appId: String, originId: String, responseHandler: CustomResponseHandler = customResponseHandler) = {
    lazy val serverPort = freePort()
    val server = customResponseWebServer(serverPort, responseHandler)

    val origin: Origin = newOriginBuilder(HostAndPort.fromParts("localhost", serverPort)).applicationId(id(appId)).id(originId).build
    server.startAsync().awaitRunning()

    origin -> server
  }
}
