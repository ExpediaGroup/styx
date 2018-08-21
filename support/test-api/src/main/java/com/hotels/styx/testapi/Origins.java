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
package com.hotels.styx.testapi;

import com.hotels.styx.api.extension.Origin;

import static java.util.UUID.randomUUID;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;

/**
 * Provides methods for easily creating {@link Origin}s for use with Styx Test API.
 */
public final class Origins {
    private Origins() {
    }

    /**
     * Creates a new {@link Origin} porting to localhost, with a specified port.
     *
     * @param port port
     * @return origin
     */
    public static Origin origin(int port) {
        return origin("localhost", port);
    }

    /**
     * Creates a new {@link Origin} porting to a specified host and port.
     *
     * @param host host
     * @param port port
     * @return origin
     */
    public static Origin origin(String host, int port) {
        return newOriginBuilder(host, port)
                .applicationId(randomUUID().toString())
                .id(randomUUID().toString())
                .build();
    }
}
