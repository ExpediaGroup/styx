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
package com.hotels.styx.proxy.resiliency

import java.io.{File, IOException, RandomAccessFile}
import java.net.URL

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.google.common.base.Charsets._
import com.google.common.io.Files
import com.hotels.styx.MockServer.responseSupplier
import com.hotels.styx._
import com.hotels.styx.api.FullHttpResponse
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.infrastructure.{MemoryBackedRegistry, RegistryServiceAdapter}
import com.hotels.styx.proxy.resiliency.DirectBufferMetrics.directBufferMetrics
import com.hotels.styx.server.HttpServer
import com.hotels.styx.support.DownloadClient._
import com.hotels.styx.support.configuration.{HttpBackend, ImplicitOriginConversions, Origins, StyxConfig}
import com.hotels.styx.support.{DownloadClient, NettyOrigins, TestClientSupport}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, SequentialNestedSuiteExecution, ShouldMatchers}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class LongDownloadsSpec extends org.scalatest.fixture.WordSpec
  with SequentialNestedSuiteExecution
  with BeforeAndAfterAll
  with TestClientSupport
  with ShouldMatchers
  with NettyOrigins
  with Eventually
{

  val LOGGER = LoggerFactory.getLogger(classOf[LongDownloadsSpec])
  val actorSystem = ActorSystem("LongDownloadSpec")
  val (styxServer, styxConfig) = SharedStyx.styx

  case class FixtureParam(styxServer: StyxServer, config: StyxConfig) {
    def serverPort = styxServer.proxyHttpAddress().getPort
    def adminPort = styxServer.adminHttpAddress().getPort
  }

  def withFixture(test: OneArgTest) = {
    withFixture(test.toNoArgTest(FixtureParam(styxServer, styxConfig)))
  }

  override protected def afterAll(): Unit = {
    styxServer.stopAsync().awaitTerminated()
  }

  "Styx" should {

    "deal with cancelled downloads" ignore {
      (fixtures) =>
        val downloadUrl: URL = new URL("http", "localhost", fixtures.serverPort, "/download")
        val NUMBER_OF_CLIENTS = 25
        val clients = for (i <- 1 to NUMBER_OF_CLIENTS) yield actorSystem.actorOf(Props[DownloadClient], s"cancelClient_$i")

        clients.foreach { client => client ! Download(downloadUrl, 5000 * 1024) }

        Thread.sleep(5000)
        val directBuffersBefore = directBufferMetrics(fixtures.adminPort)

        try {
          implicit val timeout = Timeout(15.seconds)
          val cancelOps = clients.map { client => client ? CancelDownload }
          val results = Await.result(Future.sequence(cancelOps), Timeout(15.seconds).duration)
          results.size should be(NUMBER_OF_CLIENTS)
        }
        finally {
          logClientStates("test1", clients)

          eventually(timeout(15.seconds), interval(2.seconds)) {
            val directBuffersAfter = directBufferMetrics(fixtures.adminPort)
            LOGGER.info("Direct buffers before: " + directBuffersBefore)
            LOGGER.info("Direct buffers after:  " + directBuffersAfter)

            directBuffersAfter.get.capacity should be <= (0.2 * directBuffersBefore.get.capacity).toInt
          }
        }
    }

    "proxy multiple requests" ignore {
      (fixtures) =>
        implicit val timeout = Timeout(35.seconds)
        val NUMBER_OF_CLIENTS = 25

        val clients = for (i <- 1 to NUMBER_OF_CLIENTS) yield actorSystem.actorOf(Props[DownloadClient], s"downloadClient_$i")

        val downloadOps = clients.map {
          client => client ? Download(new URL("http", "localhost", fixtures.serverPort, "/download"), 5000 * 1024)
        }

        try {
          val results: Seq[DownloadAck] = Await.result(Future.sequence(downloadOps), timeout.duration)
            .filter(any => any match {
              case d: DownloadAck => true
              case _ => false
            })
            .map(any => any.asInstanceOf[DownloadAck])

          logResults(results)
          results.size should be(NUMBER_OF_CLIENTS)
        }
        finally {
          logClientStates("test 2", clients)
          val directBuffersAfter = directBufferMetrics(fixtures.adminPort)
          LOGGER.info("Direct buffers after:  " + directBuffersAfter)
        }
    }
  }

  def logClientStates(prefix: String, clients: Seq[ActorRef]): Unit = {
    implicit val timeout = Timeout(35.seconds)
    LOGGER.info(s"$prefix Client states: ")
    clients.foreach {
      client =>
        val queryOp = client ? GetStatus
        val GetStatusAck(clientName, status, bytesReceived, _) = Await.result(queryOp, 2.seconds)
        LOGGER.info("Client: %s: %s - %d".format(clientName, status, bytesReceived))
    }
  }

  def logResults(results: Seq[DownloadAck]): Unit = {
    LOGGER.info("Results size: %d".format(results.size))
    for (
      (result, i) <- results.zipWithIndex
    ) {
      LOGGER.info("client %d: bytes received: %d".format(i + 1, result.bytesReceived))
    }

  }
}


object SharedOrigins extends NettyOrigins {
  val FIFTY_MB: Long = 50L * 1024L * 1024L
  val bigFile: File = newBigFile("big_file.dat")
  val bigFileContent: String = Files.toString(bigFile, UTF_8)

  @throws(classOf[IOException])
  private def newBigFile(filename: String): File = {
    val tmpDir: File = Files.createTempDir
    val tmpFile: String = tmpDir.toPath.resolve(filename).toString

    val file: RandomAccessFile = new RandomAccessFile(tmpFile, "rwd")
    try {
      println(s"Creating file $tmpFile of size $FIFTY_MB bytes.")
      file.setLength(FIFTY_MB)
    } finally {
      if (file != null) file.close()
    }

    tmpDir.deleteOnExit()
    new File(tmpFile)
  }

  private val fileServerStartOp = Future[HttpServer] {
    val fileServer = new MockServer(0)
    fileServer.startAsync().awaitRunning()

    fileServer.stub("/download",
      responseSupplier(() => FullHttpResponse.response(OK)
        .header("X-File-Server", "true")
        .body(bigFileContent, UTF_8)
        .build()
        .toStreamingResponse
      ))

    fileServer
  }

  def fileServer = Await.result(fileServerStartOp, 5.seconds)
}

object SharedStyx extends BackendServicesRegistrySupplier with ImplicitOriginConversions {
  System.setProperty("io.netty.leakDetectionLevel", "ADVANCED")

  private val styxStartOp = Future {
    val backendsRegistry = new MemoryBackedRegistry[BackendService]
    val styxServer = StyxConfig().startServer(new RegistryServiceAdapter(backendsRegistry))

    setBackends(backendsRegistry, "/download" -> HttpBackend("myapp", Origins(SharedOrigins.fileServer), responseTimeout = 25.seconds))

    (styxServer, StyxConfig())
  }

  def styx: (StyxServer, StyxConfig) = Await.result(styxStartOp, 10.seconds)

}

