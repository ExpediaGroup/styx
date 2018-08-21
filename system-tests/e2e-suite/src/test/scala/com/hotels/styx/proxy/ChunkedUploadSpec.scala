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

import java.util.concurrent.TimeUnit.SECONDS

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.google.common.base.Charsets
import com.google.common.base.Charsets._
import com.google.common.net.HostAndPort
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.common.HostAndPorts._
import com.hotels.styx.server.HttpServers.createHttpServer
import com.hotels.styx.support.TestClientSupport
import com.hotels.styx.support.configuration.{HttpBackend, Origins, ProxyConfig, StyxConfig}
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.utils.HttpTestClient
import com.hotels.styx.utils.handlers.ContentDigestHandler
import com.hotels.styx.{StyxClientSupplier, StyxProxySpec}
import io.netty.buffer.Unpooled
import io.netty.buffer.Unpooled._
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.codec.http.HttpHeaders.Names.{HOST, TRANSFER_ENCODING}
import io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED
import io.netty.handler.codec.http.HttpMethod.POST
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT
import io.netty.handler.codec.http._
import io.netty.handler.stream.ChunkedWriteHandler
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually
import org.slf4j.Logger
import org.slf4j.LoggerFactory._
import com.hotels.styx.utils.HttpTestClient

import scala.concurrent.duration._

class ChunkedUploadSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with Eventually
  with TestClientSupport {

  private val LOGGER: Logger = getLogger(classOf[ChunkedUploadSpec])

  val CRLF = "\r\n"
  val lastChunk = "0" + CRLF * 2
  val recordingBackend = FakeHttpServer.HttpStartupConfig().start()

  override val styxConfig = StyxConfig(ProxyConfig(requestTimeoutMillis = 1000))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends(
      "/upload" -> HttpBackend("upload", Origins(recordingBackend), responseTimeout = 2.seconds)
    )
  }

  override protected def beforeEach() = {
    recordingBackend.reset()
    recordingBackend.stub(WireMock.post(urlEqualTo("/upload")), aResponse
      .withStatus(200)
      .withBody(""))
  }

  override protected def afterAll(): Unit = {
    recordingBackend.stop()
    super.afterAll()
  }

  describe("Chunked upload") {

    it("should proxy a request with chunked HTTP content.") {
      val content = "Hello, World 0123456789 0123456789 0123456789 0123456789\n"

      val client = chunkingHttpClient("localhost", styxServer.httpPort)
      client.connect()

      val response = transactionWithTestClient[FullHttpResponse](client) {
        client.write(postRequest("/upload"))
        client.write(new DefaultHttpContent(copiedBuffer(content * 1, UTF_8)))
        client.write(new DefaultHttpContent(copiedBuffer(content * 2, UTF_8)))
        client.write(new DefaultLastHttpContent(copiedBuffer(content * 3, UTF_8)))

        client.waitForResponse(5, SECONDS)
      }

      val requestedBody: String = List.fill(6)(content).mkString("")
      recordingBackend.verify(
        postRequestedFor(urlPathEqualTo("/upload"))
          .withRequestBody(equalTo(requestedBody))
      )

    }

    it("Styx should not trigger a timeout when proxying a long lasting chunked HTTP upload.") {
      val requestBody = "Foo bar 0123456789012345678901234567890123456789\\n" * 100
      val digest = requestBody.hashCode()

      val client = chunkingHttpClient("localhost", styxServer.httpPort)
      client.connect()


      val response = transactionWithTestClient[FullHttpResponse](client) {
        client.write(postRequest("/upload"))
        sendContentInChunks(client, requestBody, 250 millis)
        client.write(EMPTY_LAST_CONTENT)
        client.waitForResponse(5, SECONDS)
      }

      recordingBackend.verify(
        postRequestedFor(urlPathEqualTo("/upload"))
          .withRequestBody(equalTo(requestBody))
      )

    }

    it("Styx accepts a request with chunk size zero.") {
      val request =
        "POST /upload HTTP/1.1" + CRLF +
          s"Host: ${styxServer.proxyHost}" + CRLF +
          "Transfer-Encoding: chunked" + CRLF +
          "Content-Type: text/plain" + CRLF +
          CRLF +
          lastChunk

      val response = httpTransaction(request)

      assert(response.nonEmpty, "\nStyx did not respond.")
      val content = response.get.content().toString(UTF_8)
      assert(response.get.getStatus == OK, s"\nExpecting 200 OK, but got: \n$response \n\n$content\n\n")
    }

    it("should respond with bad request if chunked request is not actually followed with chunks in the body") {
      val request =
        "POST /upload HTTP/1.1" + CRLF +
          s"Host: ${styxServer.proxyHost}" + CRLF +
          "Transfer-Encoding: chunked" + CRLF +
          "Content-Type: text/plain" + CRLF +
          CRLF +
          CRLF + CRLF * 2

      val response = httpTransaction(request)

      assert(response.nonEmpty, "\nStyx did not respond.")
      val content = response.get.content().toString(UTF_8)
      assert(response.get.getStatus == BAD_REQUEST, s"\nExpecting 400 Bad Request, but got: \n$response \n\n$content\n\n")
    }

    it("should accept a chunk header padded with 7 leading zeroes.") {
      val request =
        "POST /upload HTTP/1.1" + CRLF +
          s"Host: localhost:${styxServer.httpPort}" + CRLF +
          "Transfer-Encoding: chunked" + CRLF +
          "Content-Type: text/plain" + CRLF +
          CRLF +
          s"0000000C${CRLF}xxxxxxxxxxxx$CRLF" +
          lastChunk

      val response = httpTransaction(request)

      assert(response.nonEmpty, "\nStyx did not respond.")
      val content = response.get.content().toString(UTF_8)
      assert(response.get.getStatus == OK, s"\nExpecting 200 OK, but got: \n$response \n\n$content\n\n")
    }

    it("Accepts a chunk header padded with 8 leading zeroes.") {
      val request =
        "POST /upload HTTP/1.1" + CRLF +
          s"Host: localhost:${styxServer.httpPort}" + CRLF +
          "Transfer-Encoding: chunked" + CRLF +
          "Content-Type: text/plain" + CRLF +
          CRLF +
          s"00000000C${CRLF}xxxxxxxxxxxx$CRLF" +
          lastChunk

      val response = httpTransaction(request)

      assert(response.nonEmpty, "\nStyx did not respond.")
      val content = response.get.content().toString(UTF_8)
      assert(response.get.getStatus == OK, s"\nExpecting 200 OK, but got: \n$response \n\n$content\n\n")
    }

    it("Responds with 408 Request Timeout when it doesn't receive chunk-length amount of octets.") {
      val request =
        "POST /upload HTTP/1.1" + CRLF +
          s"Host: localhost:${styxServer.httpPort}" + CRLF +
          "Transfer-Encoding: chunked" + CRLF +
          "Content-Type: text/plain" + CRLF +
          CRLF +
          s"7fFFffFF${CRLF}xxxxxxxxxxxx$CRLF" +
          lastChunk

      val response = httpTransaction(request)

      assert(response.nonEmpty, "\nStyx did not respond.")
      val content = response.get.content().toString(UTF_8)
      assert(response.get.getStatus == REQUEST_TIMEOUT, s"\nExpecting 408 Request Timeout, but got: \n$response \n\n$content\n\n")
    }

    it("Responds with 400 Bad Request when chunk size 80000000 causes an integer overflow.") {
      val request =
        "POST /upload HTTP/1.1" + CRLF +
          s"Host: localhost:${styxServer.httpPort}" + CRLF +
          "Transfer-Encoding: chunked" + CRLF +
          "Content-Type: text/plain" + CRLF +
          CRLF +
          s"80000000${CRLF}xxxxxxxxxxxx$CRLF" +
          lastChunk

      val response = httpTransaction(request)

      assert(response.nonEmpty, "\nStyx did not respond.")
      val content = response.get.content().toString(UTF_8)
      assert(response.get.getStatus == BAD_REQUEST, s"\nExpecting 400 Bad Request, but got: \n$response \n\n$content\n\n")
    }

    it("Responds with 400 Bad Request when chunk size FFFFFFFF causes an integer overflow.") {
      val request =
        "POST /upload HTTP/1.1" + CRLF +
          s"Host: localhost:${styxServer.httpPort}" + CRLF +
          "Transfer-Encoding: chunked" + CRLF +
          "Content-Type: text/plain" + CRLF +
          CRLF +
          s"ffffffff${CRLF}xxxxxxxxxxxx$CRLF" +
          lastChunk

      val response = httpTransaction(request)

      assert(response.nonEmpty, "\nStyx did not respond.")
      val content = response.get.content().toString(UTF_8)
      assert(response.get.getStatus == BAD_REQUEST, s"\nExpecting 400 Bad Request, but got: \n$response \n\n$content\n\n")
    }

    it("Accepts a chunk size 25 octets.") {
      val request =
        "POST /upload HTTP/1.1" + CRLF +
          s"Host: localhost:${styxServer.httpPort}" + CRLF +
          "Transfer-Encoding: chunked" + CRLF +
          "Content-Type: text/plain" + CRLF +
          CRLF +
          s"19${CRLF}Hello World!Hello World!!$CRLF" +
          lastChunk

      val response = httpTransaction(request)

      assert(response.nonEmpty, "\nStyx did not respond.")
      val content = response.get.content().toString(UTF_8)
      assert(response.get.getStatus == OK, s"\nExpecting 200 OK, but got: \n$response \n\n$content\n\n")
    }

    it("Rejects a chunk size over 8 characters.") {
      val request =
        "POST /upload HTTP/1.1" + CRLF +
          s"Host: localhost:${styxServer.httpPort}" + CRLF +
          "Transfer-Encoding: chunked" + CRLF +
          "Content-Type: text/plain" + CRLF +
          CRLF +
          s"1ABABABAB${CRLF}Hello World!Hello World!!$CRLF" +
          lastChunk

      val response = httpTransaction(request)

      assert(response.nonEmpty, "\nStyx did not respond.")
      val content = response.get.content().toString(UTF_8)
      assert(response.get.getStatus == BAD_REQUEST, s"\nExpecting 400 Bad Request, but got: \n$response \n\n$content\n\n")
    }
  }

  def httpTransaction(request: String) = {
    val client = craftedRequestHttpClient("localhost", styxServer.httpPort)
    val response = transactionWithTestClient[FullHttpResponse](client) {
      client.write(bufferFromRequestText(request))
      client.waitForResponse(10, SECONDS)
    }
    response
  }

  def bufferFromRequestText(request: String) = Unpooled.copiedBuffer(request, US_ASCII)

  def postRequest(path: String = "/foo/bar"): HttpRequest = {
    val request = new DefaultHttpRequest(HTTP_1_1, POST, path)
    request.headers().set(TRANSFER_ENCODING, CHUNKED)
    request.headers().set(HOST, styxServer.proxyHost)
    request
  }

  def contentOf(response: FullHttpResponse) = response.content().toString(Charsets.UTF_8)

  def sendContentInChunks(client: HttpTestClient, data: String, delay: Duration): Unit = {
    val chunkData = data.take(100)
    if (chunkData.length > 0) {
      client.write(new DefaultHttpContent(Unpooled.copiedBuffer(chunkData, Charsets.UTF_8)))
      Thread.sleep(delay.toMillis)
      sendContentInChunks(client, data.drop(100), delay)
    }
  }

  def chunkingHttpClient(hostname: String, port: Int): HttpTestClient = {
    new HttpTestClient(HostAndPort.fromParts(hostname, port),
      new ChannelInitializer[Channel] {
        override def initChannel(ch: Channel): Unit = {
          ch.pipeline().addLast(new HttpClientCodec())
          ch.pipeline().addLast(new HttpObjectAggregator(8192))
          ch.pipeline().addLast(new ChunkedWriteHandler())
        }
      })
  }

  def originAndWebServer(appId: String, originId: String) = {
    val serverPort = freePort()
    val origin = newOriginBuilder("localhost", serverPort).applicationId("app").id("app1").build()
    val server = createHttpServer(serverPort, new ContentDigestHandler(origin))
    server.startAsync().awaitRunning()

    origin -> server
  }
}
