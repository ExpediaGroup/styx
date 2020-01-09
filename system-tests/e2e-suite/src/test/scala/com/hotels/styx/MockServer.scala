/*
  Copyright (C) 2013-2020 Expedia Inc.

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
import com.hotels.styx.common.http.handler.{HttpAggregator, NotFoundHandler}
import com.hotels.styx.server.netty.{NettyServerBuilder, ServerConnector, WebServerConnectorFactory}
import com.hotels.styx.server.HttpServer
import com.hotels.styx.support.configuration.HttpConnectorConfig

class RequestRecordingHandler(val requestQueue: BlockingQueue[LiveHttpRequest], val delegate: HttpHandler) extends HttpHandler {
  override def handle(request: LiveHttpRequest, context: HttpInterceptor.Context): Eventual[LiveHttpResponse] = {
    requestQueue.add(request)
    delegate.handle(request, context)
  }
}

object MockServer {
  def responseSupplier(f: () => LiveHttpResponse) = new Supplier[LiveHttpResponse] {
    override def get(): LiveHttpResponse = f()
  }
}

class MockServer(id: String, val port: Int) extends AbstractIdleService with HttpServer with Logging {
  def this(port: Int) = this("origin-" + port.toString, port)

  val router = new HttpHandler {
    val routes = new ConcurrentHashMap[String, HttpHandler]()

    override def handle(request: LiveHttpRequest, context: HttpInterceptor.Context): Eventual[LiveHttpResponse] = {
      val handler: HttpHandler = routes.getOrDefault(request.path(), new HttpAggregator(new NotFoundHandler))
      handler.handle(request, context)
    }

    def addRoute(path: String, httpHandler: HttpHandler) = routes.put(path, httpHandler)

  }
  val requestQueue: BlockingQueue[LiveHttpRequest] = new LinkedBlockingQueue
  val server = NettyServerBuilder.newBuilder()
      .setProtocolConnector(new WebServerConnectorFactory().create(HttpConnectorConfig(port).asJava))
      .workerExecutor(NettyExecutor.create("MockServer", 1))
      .handler(router)
    .build()
  val guavaService = StyxServers.toGuavaService(server)

  def takeRequest(): LiveHttpRequest = {
    requestQueue.poll
  }

  def takeRequest(timeout: Long, unit: TimeUnit): LiveHttpRequest = {
    requestQueue.poll(timeout, unit)
  }

  def stub(path: String, responseSupplier: Supplier[LiveHttpResponse]): MockServer = {
    router.addRoute(path, requestRecordingHandler(requestQueue, (request, context) => Eventual.of(responseSupplier.get())))
    this
  }

  def requestRecordingHandler(requestQueue: BlockingQueue[LiveHttpRequest], handler: HttpHandler): HttpHandler = {
    new RequestRecordingHandler(requestQueue, handler)
  }

  override def startUp(): Unit = {
    guavaService.startAsync().awaitRunning()
    logger.info("mock server started on port " + server.inetAddress().getPort)
  }

  override def shutDown(): Unit = {
    logger.info(s"mock server running on port ${server.inetAddress().getPort} stopping")
    guavaService.stopAsync().awaitTerminated()
  }

  override def inetAddress(): InetSocketAddress = {
    server.inetAddress()
  }

  private def connectorOnFreePort: ServerConnector = {
    new WebServerConnectorFactory().create(HttpConnectorConfig(0).asJava)
  }

  def httpPort() = Option(server.inetAddress()).map(_.getPort).getOrElse(0)

  def origin = newOriginBuilder("localhost", httpPort()).id(id).build()

}
