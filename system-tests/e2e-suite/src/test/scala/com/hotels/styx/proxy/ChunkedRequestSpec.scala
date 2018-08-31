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

import java.io.{ByteArrayInputStream, IOException, InputStream}
import java.net.{HttpURLConnection, URL}

import com.github.tomakehurst.wiremock.client.{RequestPatternBuilder, UrlMatchingStrategy, ValueMatchingStrategy}
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.google.common.base.Charsets._
import com.google.common.base.Strings._
import com.google.common.base.{Joiner, Optional}
import com.google.common.io.ByteStreams
import com.google.common.io.ByteStreams._
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import com.hotels.styx.api.{FullHttpResponse, HttpResponse, HttpResponseStatus}
import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.support.TestClientSupport
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import org.scalatest.FunSpec

class ChunkedRequestSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with TestClientSupport {

  val normalBackend = FakeHttpServer.HttpStartupConfig().start()

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/chunked/1" -> HttpBackend("appOne", Origins(normalBackend))
    )
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()

    super.afterAll()
  }

  describe("Chunked HTTP content handling in Styx") {

    it("Should proxy chunked request content") {
      val chunks = Array[Byte]('a', 'b', 'c')
      val url: URL = new URL(styxServer.routerURL("/chunked/1"))

      val connection: HttpURLConnection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("POST")
      connection.setDoOutput(true)
      connection.setChunkedStreamingMode(chunks.length)
      ByteStreams.copy(new ByteArrayInputStream(chunks), connection.getOutputStream)
      readResponse(connection)

      normalBackend.verify(new RequestPatternBuilder(RequestMethod.POST, urlMatchingStrategy("/chunked/1"))
        .withRequestBody(valueMatchingStrategy("abc"))
      )

      connection.disconnect()
    }
  }

  def urlMatchingStrategy(path: String) = {
    val pathMatch = new UrlMatchingStrategy()
    pathMatch.setUrlPath("/chunked/1")
    pathMatch
  }

  def valueMatchingStrategy(matches: String) = {
    val matchingStrategy = new ValueMatchingStrategy()
    matchingStrategy.setMatches("abc")
    matchingStrategy
  }

  @throws(classOf[IOException])
  def readResponse(connection: HttpURLConnection): HttpResponse = {
    val status: Int = connection.getResponseCode
    var responseBuilder: FullHttpResponse.Builder = null

    val stream: InputStream = getInputStream(connection, status)
    try {
      responseBuilder = FullHttpResponse.response(HttpResponseStatus.statusWithCode(status)).body(toByteArray(stream), true)
      import scala.collection.JavaConversions._
      for (entry <- connection.getHeaderFields.entrySet) {
        if (!isNullOrEmpty(entry.getKey)) {
          responseBuilder.header(entry.getKey, Joiner.on(",").join(entry.getValue))
        }
      }
    } finally {
      if (stream != null) stream.close()
    }

    responseBuilder.build().toStreamingResponse
  }

  @throws(classOf[IOException])
  private def getInputStream(connection: HttpURLConnection, status: Int): InputStream = {
    if (status >= 400) {
      Optional.fromNullable(connection.getErrorStream).or(emptyStream)
    }
    else {
      connection.getInputStream
    }
  }

  private def emptyStream: ByteArrayInputStream = new ByteArrayInputStream("".getBytes(UTF_8))

}
