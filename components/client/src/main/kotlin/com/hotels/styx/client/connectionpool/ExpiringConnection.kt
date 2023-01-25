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
package com.hotels.styx.client.connectionpool

import com.hotels.styx.api.Clock
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.client.Connection
import com.hotels.styx.javaconvenience.Stopwatch
import org.slf4j.LoggerFactory.getLogger
import reactor.core.publisher.Flux
import java.util.concurrent.TimeUnit.SECONDS


/**
 * Provides wrapper for connection, that tracks a connection expiration time. Also provides a method for verification of
 * a time pasted.
 */
class ExpiringConnection(
    private val nettyConnection: Connection,
    private val connectionExpirationSeconds: Long,
    clock: Clock
) : Connection {
    private val stopwatch = Stopwatch(clock)

    override fun isConnected() = if (isExpired) {
        LOGGER.warn(
            "Connection expired. Closing connection... origin=${nettyConnection.origin}, connection=$nettyConnection, " +
                    "connectionExpirationSeconds=$connectionExpirationSeconds"
        )
        close()
        false
    } else {
        nettyConnection.isConnected
    }

    override fun write(request: LiveHttpRequest): Flux<LiveHttpResponse> = nettyConnection.write(request)

    override fun getOrigin(): Origin = nettyConnection.origin

    override fun addConnectionListener(listener: Connection.Listener) = nettyConnection.addConnectionListener(listener)

    override fun close() = nettyConnection.close()

    private val isExpired: Boolean get() = stopwatch.timeElapsedSoFar(SECONDS) >= connectionExpirationSeconds

    companion object {
        private val LOGGER = getLogger(ExpiringConnection::class.java)
    }
}
