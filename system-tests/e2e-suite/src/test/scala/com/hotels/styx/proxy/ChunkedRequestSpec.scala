/*
  Copyright (C) 2013-2024 Expedia Inc.

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

import com.github.tomakehurst.wiremock.client.WireMock.{matching, urlMatching}
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.hotels.styx.api.{HttpResponse, HttpResponseStatus, LiveHttpResponse}
import com.hotels.styx.javaconvenience.UtilKt
import com.hotels.styx.support.TestClientSupport
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import org.scalatest.FunSpec

import java.io.{ByteArrayInputStream, IOException, InputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets._
import java.util.Optional

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
      UtilKt.copy(new ByteArrayInputStream(chunks), connection.getOutputStream)
      readResponse(connection)

      normalBackend.verify(new RequestPatternBuilder(RequestMethod.POST, urlMatching("/chunked/1"))
        .withRequestBody(matching("abc"))
      )

      connection.disconnect()
    }
  }

  @throws(classOf[IOException])
  def readResponse(connection: HttpURLConnection): LiveHttpResponse = {
    val status: Int = connection.getResponseCode
    var responseBuilder: HttpResponse.Builder = null

    val stream: InputStream = getInputStream(connection, status)
    try {
      responseBuilder = HttpResponse.response(HttpResponseStatus.statusWithCode(status)).body(UtilKt.bytes(stream), true)
      import scala.collection.JavaConversions._
      for (entry <- connection.getHeaderFields.entrySet) {
        if (entry.getKey != null && entry.getKey.nonEmpty) {
          responseBuilder.header(entry.getKey, String.join(",", entry.getValue))
        }
      }
    } finally {
      if (stream != null) stream.close()
    }

    responseBuilder.build().stream
  }

  @throws(classOf[IOException])
  private def getInputStream(connection: HttpURLConnection, status: Int): InputStream = {
    if (status >= 400) {
      Optional.ofNullable(connection.getErrorStream).orElse(emptyStream)
    }
    else {
      connection.getInputStream
    }
  }

  private def emptyStream: ByteArrayInputStream = new ByteArrayInputStream("".getBytes(UTF_8))

}
