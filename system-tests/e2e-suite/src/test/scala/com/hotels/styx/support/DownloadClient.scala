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
package com.hotels.styx.support

import java.net.URL

import akka.actor.FSM.{Failure, Normal}
import akka.actor.{ActorRef, LoggingFSM}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.hotels.styx.api.HttpHeaderNames.{CONTENT_LENGTH, HOST, USER_AGENT}
import com.hotels.styx.support.DownloadClient._
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelOption.{AUTO_READ, CONNECT_TIMEOUT_MILLIS, TCP_NODELAY}
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpMethod.GET
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

sealed trait State

case object Idle extends State

case object Connecting extends State

case object Downloading extends State

case object SlowingDown extends State

case object CancellingConnection extends State

case object Terminated extends State

case object Finished extends State


sealed trait Data

case object Uninitialized extends Data

case class DownloadProgress(ctx: ChannelHandlerContext, bytesReceived: Long, startTime: Long) extends Data

case class CancelData(sender: ActorRef, currentState: State, progression: DownloadProgress) extends Data


object DownloadClient {

  case class Download(url: URL, bytesPerSecond: Double)

  case object CancelDownload

  case object CancelDownloadAck

  case class DownloadAck(bytesReceived: Long, elapsedTime: Long)

  case class DownloadNack(cause: Throwable)

  case class Connected()

  case class ConnectionFailed(cause: Throwable)

  case class ResponseHeadersReceived(status: Int)

  case object ContentFinished

  case class ContentChunk(chunk: ByteBuf)

  case class ChannelActive(ctx: ChannelHandlerContext)

  case object ChannelInactive

  case object ChannelReadComplete

  case class RequestFailed(cause: Throwable)

  case object ResumeDownload

  case object GetStatus

  case class GetStatusAck(clientName: String, state: String, bytesReceived: Long, startTime: Long)

}

class DownloadClient extends LoggingFSM[State, Data] {
  val LOGGER = LoggerFactory.getLogger(classOf[DownloadClient])

  val channelInitializer = new ChannelInitializer[Channel] {
    override def initChannel(ch: Channel): Unit = {
      ch.pipeline().addLast(new HttpClientCodec())
      ch.pipeline().addLast(new NettyAdapter(self))
    }
  }

  val eventLoopGroup = new NioEventLoopGroup(1, new ThreadFactoryBuilder().setNameFormat("Download-Client-%d").build())
  val bootstrap = new Bootstrap()
    .group(eventLoopGroup)
    .channel(classOf[NioSocketChannel])
    .handler(channelInitializer)
    .option(TCP_NODELAY.asInstanceOf[ChannelOption[Any]], true)
    .option(AUTO_READ.asInstanceOf[ChannelOption[Any]], false)
    .option(CONNECT_TIMEOUT_MILLIS.asInstanceOf[ChannelOption[Any]], 1000)

  var targetBytesPerSec: Double = 0.0
  var requestor: ActorRef = _
  var targetUrl: URL = _

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(Download(url, requestedBytesPerSec), Uninitialized) =>

      LOGGER.warn("{}: {}: Download request, bytesPerSec={}",  Array(self.path.name, stateName, requestedBytesPerSec).asInstanceOf[Array[Object]])

      targetBytesPerSec = requestedBytesPerSec
      targetUrl = url
      requestor = sender()

      Future {
        val connectOp = bootstrap.connect(url.getHost, url.getPort).await()
        if (connectOp.isSuccess) {
          self ! Connected()
        } else {
          self ! ConnectionFailed(connectOp.cause())
        }
      }

      goto(Connecting) using Uninitialized
  }

  when(Connecting) {
    case Event(ConnectionFailed(cause), Uninitialized) =>
      LOGGER.warn("{}: {}: ConnectionFailed", Array(self.path.name, stateName).asInstanceOf[Array[Object]])

      requestor ! DownloadNack(cause)
      goto(Terminated) using Uninitialized

    case Event(ChannelActive(ctx), Uninitialized) =>
      LOGGER.warn("{}: {}: Received: ChannelActive, {} -> {}", Array(self.path.name, stateName, ctx.channel().localAddress(), ctx.channel().remoteAddress()).asInstanceOf[Array[Object]])

      val request = new DefaultFullHttpRequest(HTTP_1_1, GET, targetUrl.getPath)
      request.headers().add(HOST, "localhost")
      request.headers().add(CONTENT_LENGTH, "0")
      request.headers().add(USER_AGENT, "Download Client %s".format(self.path.name))

      Future {
        val requestOp = ctx.writeAndFlush(request).await()
        if (!requestOp.isSuccess) {
          self ! RequestFailed(requestOp.cause())
        }
      }

      ctx.read()

      goto(Downloading) using DownloadProgress(ctx, 0L, System.currentTimeMillis())

    case Event(CancelDownload, Uninitialized) =>
      goto(CancellingConnection) using CancelData(sender(), Connecting, DownloadProgress(null, 0L, 0L))
  }

  when(CancellingConnection) {
    case Event(ChannelActive(ctx), CancelData(canceller, _, _)) =>
      ctx.close()
      canceller ! CancelDownloadAck
      goto(Finished)
  }

  when(Downloading) {
    case Event(ResponseHeadersReceived(ctx), progress: DownloadProgress) =>
      LOGGER.warn("{}: {}: Received: ResponseHeadersReceived", Array(self.path.name, stateName).asInstanceOf[Array[Object]])
      stay()

    case Event(ContentChunk(chunk), progress: DownloadProgress) =>
      val newProgress: DownloadProgress = progress.copy(bytesReceived = progress.bytesReceived + chunk.readableBytes())
      chunk.release()
      stay() using newProgress

    case Event(ChannelReadComplete, progress: DownloadProgress) =>
      val kbReceived = progress.bytesReceived.toDouble / 1024
      val elapsedTimeSecs = (System.currentTimeMillis() - progress.startTime) / 1000
      val kbPerSec = kbReceived / elapsedTimeSecs

      val td = timeDelta(progress)
      if (td <= 0) {
        progress.ctx.read()
        stay()
      } else {
        LOGGER.warn("{}: {}: Slowing Down for {} milliseconds", Array(self.path.name, stateName, td).asInstanceOf[Array[Object]])
        context.system.scheduler.scheduleOnce(td.millis, self, ResumeDownload)
        goto(SlowingDown) using progress
      }

    case Event(ContentFinished, progress: DownloadProgress) =>
      LOGGER.warn("{}: {}: ContentFinished, bytes={}", Array(self.path.name, stateName, progress.bytesReceived).asInstanceOf[Array[Object]])

      val elaspedTime = System.currentTimeMillis() - progress.startTime
      progress.ctx.close()
      requestor ! DownloadAck(progress.bytesReceived, elaspedTime)
      goto(Finished) using Uninitialized

    case Event(CancelDownload, progress: DownloadProgress) =>
      LOGGER.warn("{}: {}: CancelDownload, bytes={}", Array(self.path.name, stateName, progress.bytesReceived).asInstanceOf[Array[Object]])
      progress.ctx.close()
      sender() ! CancelDownloadAck
      goto(Finished) using CancelData(null, Downloading, progress)
  }

  when(SlowingDown) {
    case Event(ResumeDownload, progress: DownloadProgress) =>
      LOGGER.warn("{}: {}: Received: ResumeDownload", Array(self.path.name, stateName).asInstanceOf[Array[Object]])

      val td = timeDelta(progress)
      if (td <= 0) {
        progress.ctx.read()
        goto(Downloading) using progress
      } else {
        LOGGER.warn("{}: {}: Slowing Down for {} milliseconds", Array(self.path.name, stateName, td).asInstanceOf[Array[Object]])
        context.system.scheduler.scheduleOnce(td.millis, self, ResumeDownload)
        stay
      }

    case Event(CancelDownload, progress: DownloadProgress) =>
      progress.ctx.close()
      sender() ! CancelDownloadAck
      goto(Finished) using CancelData(null, SlowingDown, progress)
  }

  when(Terminated) {
    case (Event(i: Int, _)) => stay
  }

  when(Finished) {
    case (Event(i: Int, _)) => stay
  }

  whenUnhandled {
    case Event(GetStatus, cancelData: CancelData) =>
      sender ! GetStatusAck(self.path.name, "finished/" + cancelData.currentState.toString, cancelData.progression.bytesReceived, cancelData.progression.startTime)
      stay
    case Event(GetStatus, progress: DownloadProgress) =>
      sender() ! GetStatusAck(self.path.name, stateName.toString, progress.bytesReceived, progress.startTime)
      stay
    case Event(GetStatus, _) =>
      sender() ! GetStatusAck(self.path.name, stateName.toString, -1, -1)
      stay
    case Event(CancelDownload, _) =>
      sender() ! CancelDownloadAck
      stay
  }

  private def timeDelta(progress: DownloadProgress): Double = {
    val elapsedTimeMillis = System.currentTimeMillis() - progress.startTime
    val bytesPerMillis = progress.bytesReceived / elapsedTimeMillis.toDouble
    val targetBytesPerMillis = targetBytesPerSec / 1000.toDouble

    (progress.bytesReceived.toDouble / targetBytesPerMillis) - elapsedTimeMillis.toDouble
  }

  class NettyAdapter(parent: ActorRef) extends ChannelInboundHandlerAdapter {

    override def channelActive(ctx: ChannelHandlerContext): Unit = {
      //      LOGGER.warn("Netty channelActive")
      parent ! ChannelActive(ctx)
    }

    override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
      //      LOGGER.warn("Netty channelRead")

      msg match {
        case response: HttpResponse =>
          parent ! ResponseHeadersReceived(response.getStatus.code())
        case lastChunk: LastHttpContent =>
          parent ! ContentChunk(lastChunk.content().retain())
          parent ! ContentFinished
          lastChunk.release()
        case chunk: HttpContent =>
          parent ! ContentChunk(chunk.content().retain())
          chunk.release()
      }
    }

    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
      parent ! ChannelReadComplete
    }
  }

}
