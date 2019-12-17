/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import java.io.{File, IOException, RandomAccessFile}
import java.util.concurrent.TimeUnit.MILLISECONDS

import com.google.common.base.Charsets.UTF_8
import com.google.common.io.Files
import com.google.common.io.Files._
import com.hotels.styx.MockServer.responseSupplier
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import com.hotels.styx.{DefaultStyxConfiguration, MockServer, StyxProxySpec}
import org.scalatest.FunSpec
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

import scala.concurrent.duration._

class BigFileDownloadSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration {
  val fileServer = new MockServer(0)

  private val LOGGER = LoggerFactory.getLogger(classOf[BigFileDownloadSpec])

  val myClient: StyxHttpClient = new StyxHttpClient.Builder()
    .connectTimeout(1000, MILLISECONDS)
    .maxHeaderSize(2 * 8192)
    .build()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    fileServer.startAsync().awaitRunning()

    styxServer.setBackends(
      "/download" -> HttpBackend(
        "download-server",
        origins = Origins(fileServer),
        responseTimeout = 9.seconds
      )
    )

    val bigFile: File = newBigFile("big_file.dat")
    fileServer.stub("/download", responseSupplier(() => response(OK).body(Files.toString(bigFile, UTF_8), UTF_8).build().stream))
  }

  override protected def afterAll(): Unit = {
    fileServer.stopAsync().awaitTerminated()
    LOGGER.info("BigFileDownloadSpec: Origin received requests: " + fileServer.requestQueue)
    LOGGER.info("Metrics after the test:", styxServer.metricsSnapshot)
    super.afterAll()
  }

  val ONE_HUNDRED_MB: Long = 100L * 1024L * 1024L

  describe("Big file requests") {
    it("should proxy big file requests") {
      val req = get("/download")
        .addHeader(HOST, styxServer.proxyHost)
        .build()

      var bodyLength = 0

      // Note: It is very important to consume the body. Otherwise
      // it won't get transmitted over the TCP connection:
      val response = myClient.streaming().send(req).get()

      Mono.from(response.newBuilder()
          .body(body => body
                      .map(buf => { bodyLength = bodyLength + buf.size() ; buf })
                      .drop()
                      .doOnEnd( x => LOGGER.info("body consumed!")))
          .build()
          .aggregate(100)).block()

      assert(response.status() == OK)
      val actualContentSize = bodyLength

      LOGGER.info("Actual content size was: " + actualContentSize)
      actualContentSize should be(ONE_HUNDRED_MB)
    }
  }

  @throws(classOf[IOException])
  private def newBigFile(filename: String): File = {
    val tmpDir: File = createTempDir
    val tmpFile: String = tmpDir.toPath.resolve(filename).toString

    val file: RandomAccessFile = new RandomAccessFile(tmpFile, "rwd")
    try {
      LOGGER.info(s"Creating file $tmpFile of size $ONE_HUNDRED_MB bytes.")
      file.setLength(ONE_HUNDRED_MB)
    } finally {
      if (file != null) file.close()
    }

    tmpDir.deleteOnExit()
    new File(tmpFile)
  }
}
