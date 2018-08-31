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
package com.hotels.styx.api.exceptions;

import com.hotels.styx.api.extension.Origin;
import io.netty.channel.Channel;

import java.net.SocketAddress;

import static java.lang.String.format;

/**
 * Exception thrown when the connection between styx and origin is lost.
 */
public class TransportLostException extends TransportException {
    private static final String MESSAGE_FORMAT = "Connection to origin lost. origin=\"%s\", remoteAddress=\"%s\".";
    private final SocketAddress address;
    private final Origin origin;

    public TransportLostException(Channel channel, Origin origin) {
        super(format("Connection to origin lost. origin=%s, connection=%s", origin, channel.toString()));
        this.address = channel.remoteAddress();
        this.origin = origin;
    }

    /**
     * Construct an exception.
     *
     * @param address address of socket used for connection
     * @param origin  origin connected to
     */
    public TransportLostException(SocketAddress address, Origin origin) {
        super(format(MESSAGE_FORMAT, origin, address));
        this.address = address;
        this.origin = origin;
    }

    /**
     * Address of socket used for connection.
     *
     * @return remote address
     */
    public SocketAddress remoteAddress() {
        return address;
    }

    /**
     * Origin connected to.
     *
     * @return origin
     */
    public Origin origin() {
        return origin;
    }
}
