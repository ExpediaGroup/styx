/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.client.netty.connectionpool

import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.exceptions.TransportLostException.Companion.CLOSED_BY_STYX
import com.hotels.styx.api.extension.Announcer
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.client.Connection
import com.hotels.styx.client.HttpConfig
import com.hotels.styx.client.HttpRequestOperationFactory
import io.netty.channel.Channel
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContentDecompressor
import io.netty.handler.ssl.SslContext
import reactor.core.publisher.Flux
import java.util.Optional


/**
 * A connection using a netty channel.
 *
 * @param origin                  the origin connected to
 * @param channel                 the netty channel associated with this connection
 * @param requestOperationFactory used to create operation objects that send http requests via this connection
 * @param httpConfig              configuration settings for the **origin**
 * @param sslContext              TLS context in case of secure connections
 * @param sendSni                 include the servername extension (server name indicator) in the TLS handshake
 * @param sniHost                 hostname override for the server name indicator
 */
class NettyConnection(
    private val origin: Origin,
    @get:JvmName("channel")
    val channel: Channel,
    private val requestOperationFactory: HttpRequestOperationFactory,
    httpConfig: HttpConfig,
    sslContext: SslContext?,
    sendSni: Boolean,
    sniHost: Optional<String?>
) : Connection {
    private val listeners = Announcer.to(Connection.Listener::class.java)

    init {
        channel.closeFuture().addListener {
            listeners.announce().connectionClosed(this)
        }
        addChannelHandlers(channel, httpConfig, sslContext, sendSni, sniHost.orElse(origin.host()))
    }

    override fun write(request: LiveHttpRequest): Flux<LiveHttpResponse> =
        requestOperationFactory.newHttpRequestOperation(request).execute(this)

    override fun isConnected() = channel.isActive

    override fun getOrigin() = origin

    override fun addConnectionListener(listener: Connection.Listener) = listeners.addListener(listener)

    override fun close() {
        if (channel.isOpen) {
            channel.attr(CLOSED_BY_STYX).set(true)
            channel.close()
        }
    }

    override fun toString() = buildString(256) {
        append(javaClass.simpleName)
        append("{host=")
        append(origin.hostAndPortString())
        append(", channel=")
        append(toString(channel))
        append('}')
    }

    companion object {
        private const val IGNORED_PORT_NUMBER = -1
        private fun addChannelHandlers(channel: Channel, httpConfig: HttpConfig, sslContext: SslContext?, sendSni: Boolean, targetHost: String?) {
            val pipeline = channel.pipeline()
            if (sslContext != null) {
                val sslHandler = if (sendSni)
                    sslContext.newHandler(channel.alloc(), targetHost, IGNORED_PORT_NUMBER)
                else
                    sslContext.newHandler(channel.alloc())

                pipeline.addLast("ssl", sslHandler)
            }
            pipeline.addLast("http-codec", HttpClientCodec(httpConfig.maxInitialLength(), httpConfig.maxHeadersSize(), httpConfig.maxChunkSize()))
            if (httpConfig.compress()) {
                pipeline.addLast("decompressor", HttpContentDecompressor())
            }
        }

        private fun toString(channel: Channel) = buildString(224) {
            append(channel.javaClass.simpleName)
            append("{active=")
            append(channel.isActive)
            append(", open=")
            append(channel.isOpen)
            append(", registered=")
            append(channel.isRegistered)
            append(", writable=")
            append(channel.isWritable)
            append(", localAddress=")
            append(channel.localAddress())
            append(", clientAddress=")
            append(channel.remoteAddress())
            append(", hashCode=")
            append(channel.hashCode())
            append('}')
        }
    }
}
