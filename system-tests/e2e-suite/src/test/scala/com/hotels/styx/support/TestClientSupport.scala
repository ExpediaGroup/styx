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

import com.google.common.net.HostAndPort._
import com.hotels.styx.utils.HttpTestClient
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.codec.http.{HttpClientCodec, HttpObjectAggregator, HttpResponseDecoder}
import io.netty.handler.logging.{LogLevel, LoggingHandler}

trait TestClientSupport {
  def connectedTestClient(hostname: String, port: Int): HttpTestClient = {
    val client = new HttpTestClient(fromParts(hostname, port),
      new ChannelInitializer[Channel] {
        override def initChannel(ch: Channel): Unit = {
          ch.pipeline().addLast(new HttpClientCodec(4096, 2 * 8192, 8192))
        }
      })
    client.connect()
    client
  }

  def aggregatingTestClient(hostname: String, port: Int, log: Boolean = false): HttpTestClient = {
    val client = new HttpTestClient(fromParts(hostname, port),
      new ChannelInitializer[Channel] {
        override def initChannel(ch: Channel): Unit = {
          if (log) {
            ch.pipeline().addLast(new LoggingHandler(LogLevel.WARN))
          }
          ch.pipeline().addLast(new HttpClientCodec(4096, 2 * 8192, 8192))
          ch.pipeline().addLast(new HttpObjectAggregator(2 * 8192))
        }
      })
    client.connect()
    client
  }

  def craftedRequestHttpClient(hostname: String, port: Int): HttpTestClient = {
    val client = new HttpTestClient(fromParts(hostname, port),
      new ChannelInitializer[Channel] {
        override def initChannel(ch: Channel): Unit = {
          ch.pipeline()
            .addLast(new HttpResponseDecoder())
            .addLast(new HttpObjectAggregator(8192))
        }
      })
    client.connect()
    client
  }

  def withTestClient(testClient: HttpTestClient)(codeBlock: => Unit): Unit = {
    try {
      codeBlock
    } finally {
      testClient.disconnect()
    }
  }

  def transactionWithTestClient[A](testClient: HttpTestClient)(clientOperation: => Object): Option[A] = {
    try {
      val result = clientOperation
      result match {
        case null => None
        case _ => Some(result.asInstanceOf[A])
      }
    } finally {
      testClient.disconnect()
    }
  }

  def withConnectedTestClient[A](testClient: HttpTestClient)(codeBlock: => A): A = {
    try {
      testClient.connect()
      codeBlock
    } finally {
      testClient.disconnect()
    }
  }

}
