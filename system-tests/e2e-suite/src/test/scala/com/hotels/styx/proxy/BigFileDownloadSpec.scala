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

import java.io.{File, IOException, RandomAccessFile}

import com.google.common.base.Charsets.UTF_8
import com.google.common.io.Files
import com.google.common.io.Files._
import com.hotels.styx.MockServer.responseSupplier
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.FullHttpResponse.response
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import com.hotels.styx.{DefaultStyxConfiguration, MockServer, StyxProxySpec}
import org.scalatest.FunSpec

import scala.concurrent.duration._

class BigFileDownloadSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration {
  val fileServer = new MockServer(0)

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
    fileServer.stub("/download", responseSupplier(() => response(OK).body(Files.toString(bigFile, UTF_8), UTF_8).build().toStreamingResponse))
  }

  override protected def afterAll(): Unit = {
    fileServer.stopAsync().awaitTerminated()
    println("BigFileDownloadSpec: Origin received requests: " + fileServer.requestQueue)
    println("Metrics after the test:", styxServer.metricsSnapshot)
    super.afterAll()
  }

  val ONE_HUNDRED_MB: Long = 100L * 1024L * 1024L

  ignore("Big file requests") {
    it("should proxy big file requests") {
      val req = get("/download")
        .addHeader(HOST, styxServer.proxyHost)
        .build()

      // Note: It is very important to consume the body. Otherwise
      // it won't get transmitted over the TCP connection:
      val resp = decodedRequest(req, maxSize = 2 * ONE_HUNDRED_MB.toInt, timeout = 60.seconds)

      assert(resp.status() == OK)
      val actualContentSize = resp.body.length

      println("Actual content size was: " + actualContentSize)
      actualContentSize should be(ONE_HUNDRED_MB)
    }
  }

  @throws(classOf[IOException])
  private def newBigFile(filename: String): File = {
    val tmpDir: File = createTempDir
    val tmpFile: String = tmpDir.toPath.resolve(filename).toString

    val file: RandomAccessFile = new RandomAccessFile(tmpFile, "rwd")
    try {
      println(s"Creating file $tmpFile of size $ONE_HUNDRED_MB bytes.")
      file.setLength(ONE_HUNDRED_MB)
    } finally {
      if (file != null) file.close()
    }

    tmpDir.deleteOnExit()
    new File(tmpFile)
  }
}
