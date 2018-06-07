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

import ch.qos.logback.classic.Level._
import com.github.tomakehurst.wiremock.client.RequestPatternBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx._
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.matchers.LoggingEventMatcher._
import com.hotels.styx.support.matchers.LoggingTestSupport
import com.hotels.styx.support.server.UrlMatchingStrategies._
import org.hamcrest.MatcherAssert._
import org.hamcrest.Matchers.hasItem
import org.scalatest._

class UnwiseCharactersSpec extends FunSpec with StyxProxySpec {

  val recordingBackend = FakeHttpServer.HttpStartupConfig().start()

  val yamlText = "\n" +
    "url:\n" +
    "  encoding:\n" +
    "    unwiseCharactersToEncode: Q\n"

  override val styxConfig = StyxConfig(yamlText = yamlText)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends(
      "/" -> HttpBackend("app1", Origins(recordingBackend)))
  }

  override protected def afterAll(): Unit = {
    recordingBackend.stop()
    super.afterAll()
  }

  describe("Handling of unwise characters") {
    recordingBackend.stub(urlStartingWith("/url"), aResponse.withStatus(200))

    it("Should escape all unwise characters in URL as per styx configuration") {
      val logger = new LoggingTestSupport(classOf[ConfigurableUnwiseCharsEncoder])

      val req = get("/url/unwiseQQblah")
        .header(HOST, styxServer.proxyHost)
        .build()

      decodedRequest(req)

      recordingBackend.verify(receivedRewrittenUrl("/url/unwise%51%51blah"))
      assertThat(logger.log(), hasItem(loggingEvent(WARN, "Value contains unwise chars. you should fix this. raw=/url/unwiseQQblah, escaped=/url/unwise%51%51blah.*")))
    }
  }

  def receivedRewrittenUrl(newUrl: String): RequestPatternBuilder = getRequestedFor(urlEqualTo(newUrl))

}
