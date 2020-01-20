/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx;

import com.hotels.styx.api.extension.service.spi.StyxService;

import java.net.InetSocketAddress;

/**
 * A Styx Server is a StyxService with a server socket.
 */
public interface InetServer extends StyxService {

    /**
     * Return an associated server address.
     *
     * @return a server Inet address and port.
     */
    InetSocketAddress inetAddress();
}
