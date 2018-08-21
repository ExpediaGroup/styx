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
package com.hotels.styx.common;

import com.google.common.net.HostAndPort;

import java.io.IOException;
import java.net.ServerSocket;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.net.HostAndPort.fromParts;

/**
 * Utility class to create a {@link com.google.common.net.HostAndPort}.
 */
public final class HostAndPorts {
    private HostAndPorts() {
    }

    /**
     * Gets localhost and a free port.
     *
     * @return localhost and a free port
     */
    public static HostAndPort localHostAndFreePort() {
        return HostAndPort.fromParts("localhost", freePort());
    }

    /**
     * Creates a local {@link com.google.common.net.HostAndPort} from the specified {@code port}.
     *
     * @param port a port number from [0..65535]
     * @return a localhost with the specified port
     */
    public static HostAndPort localhost(int port) {
        return fromParts("localhost", port);
    }

    /**
     * Returns an available free socket port in the system.
     *
     * @return an available free socket port in the system
     */
    public static int freePort() {
        try {
            ServerSocket serverSocket = new ServerSocket(0);
            int localPort = serverSocket.getLocalPort();
            serverSocket.close();
            return localPort;
        } catch (IOException e) {
            throw propagate(e);
        }
    }

}
