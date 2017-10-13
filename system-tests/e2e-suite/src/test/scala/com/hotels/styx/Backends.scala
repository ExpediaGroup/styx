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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client._
import com.hotels.styx.api.client.Origin
import com.hotels.styx.client.applications.BackendService
import com.hotels.styx.support.server.FakeHttpServer

object BackendHttpServer {


}

class BackendHttpServer(val backendService: BackendService, val webServer: FakeHttpServer) {

  def port(): Int = {
    webServer.port()
  }

  def reset(): BackendHttpServer = {
    webServer.reset()
    this
  }

  def addResponseDelay(delay: Long): BackendHttpServer = {
    webServer.setDelay(delay.toInt)
    this
  }

  def stub(urlMatchingStrategy: UrlMatchingStrategy, response: ResponseDefinitionBuilder): BackendHttpServer = {
    require(webServer.isRunning(), "start server before configuring stubs")
    configureFor("localhost", port)
    stubFor(WireMock.get(urlMatchingStrategy).willReturn(response))
    this
  }

  def stub(mappingBuilder: MappingBuilder, response: ResponseDefinitionBuilder): BackendHttpServer = {
    require(webServer.isRunning(), "start server before configuring stubs")
    configureFor("localhost", port)
    stubFor(mappingBuilder.willReturn(response))
    this
  }

  def verify(builder: RequestPatternBuilder) {
    configureFor("localhost", port)
    WireMock.verify(builder)
  }

  def start(): BackendHttpServer = {
    webServer.start()
    this
  }

  def stop(): BackendHttpServer = {
    webServer.stop()
    this
  }

}
