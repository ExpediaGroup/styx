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
package com.hotels.styx.api.exceptions

import com.hotels.styx.api.Id
import com.hotels.styx.api.extension.Origin
import io.netty.channel.Channel
import io.netty.util.AttributeKey
import java.net.SocketAddress
import java.util.Optional
import kotlin.Any
import kotlin.String


/**
 * Exception thrown when the connection between styx and origin is lost.
 */
class TransportLostException : TransportException, StyxException {
    private val address: SocketAddress?
    private val origin: Origin

    constructor(channel: Channel, origin: Origin) : super(
        "Connection to origin lost. origin=$origin, connection=$channel, closedByStyx=${
            if (channel.hasAttr<Any>(CLOSED_BY_STYX)) {
                channel.attr<Any>(CLOSED_BY_STYX).get()
            } else {
                "false"
            }
        }"
    ) {
        address = channel.remoteAddress()
        this.origin = origin
    }

    /**
     * Construct an exception.
     *
     * @param address address of socket used for connection
     * @param origin  origin connected to
     */
    constructor(address: SocketAddress?, origin: Origin) : super(String.format(MESSAGE_FORMAT, origin, address)) {
        this.address = address
        this.origin = origin
    }

    /**
     * Address of socket used for connection.
     *
     * @return remote address
     */
    fun remoteAddress(): SocketAddress? = address

    /**
     * Origin connected to.
     *
     * @return origin
     */
    override fun origin(): Optional<Id> = Optional.of(origin.id())

    override fun application(): Id = origin.applicationId()

    companion object {
        @JvmField
        val CLOSED_BY_STYX: AttributeKey<Any> = AttributeKey.newInstance("CLOSED_BY_STYX")
        private const val MESSAGE_FORMAT = "Connection to origin lost. origin=\"%s\", remoteAddress=\"%s\"."
    }
}
