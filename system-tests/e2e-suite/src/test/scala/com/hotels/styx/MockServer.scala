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
package com.hotels.styx

import java.net.InetSocketAddress
import java.util.concurrent.{BlockingQueue, ConcurrentHashMap, LinkedBlockingQueue, TimeUnit}
import java.util.function.Supplier

import com.google.common.util.concurrent.AbstractIdleService
import com.hotels.styx.api._
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.common.HostAndPorts._
import com.hotels.styx.common.http.handler.NotFoundHandler
import com.hotels.styx.server.handlers.ReturnResponseHandler.returnsResponse
import com.hotels.styx.server.netty.{NettyServerBuilder, ServerConnector, WebServerConnectorFactory}
import com.hotels.styx.server.{HttpConnectorConfig, HttpServer}

class RequestRecordingHandler(val requestQueue: BlockingQueue[HttpRequest], val delegate: HttpHandler) extends HttpHandler {
  override def handle(request: HttpRequest, context: HttpInterceptor.Context): StyxObservable[HttpResponse] = {
    requestQueue.add(request)
    delegate.handle(request, context)
  }
}

object MockServer {
  def responseSupplier(f: () => HttpResponse) = new Supplier[HttpResponse] {
    override def get(): HttpResponse = f()
  }
}

class MockServer(id: String, val port: Int) extends AbstractIdleService with HttpServer with Logging {
  def this(port: Int) = this("origin-" + port.toString, port)

  val router = new HttpHandler {
    val routes = new ConcurrentHashMap[String, HttpHandler]()

    override def handle(request: HttpRequest, context: HttpInterceptor.Context): StyxObservable[HttpResponse] = {
      val handler: HttpHandler = routes.getOrDefault(request.path(), new NotFoundHandler)
      handler.handle(request, context)
    }

    def addRoute(path: String, httpHandler: HttpHandler) = routes.put(path, httpHandler)

  }
  val requestQueue: BlockingQueue[HttpRequest] = new LinkedBlockingQueue
  val server = NettyServerBuilder.newBuilder()
      .name("MockServer")
      .setHttpConnector(new WebServerConnectorFactory().create(new HttpConnectorConfig(port)))
      .httpHandler(router)
    .build()

  def takeRequest(): HttpRequest = {
    requestQueue.poll
  }

  def takeRequest(timeout: Long, unit: TimeUnit): HttpRequest = {
    requestQueue.poll(timeout, unit)
  }

  def stub(path: String, responseSupplier: Supplier[HttpResponse]): MockServer = {
    router.addRoute(path, requestRecordingHandler(requestQueue, returnsResponse(responseSupplier)))
    this
  }

  def requestRecordingHandler(requestQueue: BlockingQueue[HttpRequest], handler: HttpHandler): HttpHandler = {
    new RequestRecordingHandler(requestQueue, handler)
  }

  override def startUp(): Unit = {
    server.startAsync().awaitRunning()
    logger.info("mock server started on port " + server.httpAddress().getPort)
  }

  override def shutDown(): Unit = {
    server.stopAsync().awaitTerminated()
    logger.info(s"mock server running on port ${server.httpAddress().getPort} stopped")
  }

  override def httpAddress(): InetSocketAddress = {
    server.httpAddress()
  }

  override def httpsAddress(): InetSocketAddress = {
    server.httpsAddress()
  }

  private def connectorOnFreePort: ServerConnector = {
    new WebServerConnectorFactory().create(new HttpConnectorConfig(freePort))
  }

  def httpPort() = Option(server.httpAddress()).map(_.getPort).getOrElse(0)

  def origin = newOriginBuilder("localhost", httpPort()).id(id).build()

}
