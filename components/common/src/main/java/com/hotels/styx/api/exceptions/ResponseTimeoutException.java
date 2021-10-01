/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.Origin;

import java.util.Optional;

import static java.lang.String.format;

/**
 * An exception due to a response timeout.
 */
public class ResponseTimeoutException extends TransportException implements StyxException, ExternalFault {
    private final Origin origin;

    public ResponseTimeoutException(Origin origin, String reason, long bytesReceived, long chunksReceived, long bytesEmitted, long chunksEmitted) {
        super(message(origin, reason, bytesReceived, chunksReceived, bytesEmitted, chunksEmitted));
        this.origin = origin;
    }

    /**
     * Origin that a response was expected from.
     *
     * @return origin
     */
    @Override
    public Optional<Id> origin() {
        return Optional.of(origin.id());
    }

    @Override
    public Id application() {
        return origin.applicationId();
    }

    private static String message(Origin origin, String reason, long bytesReceived, long chunksReceived, long bytesEmitted, long chunksEmitted) {
        return format("No response from origin. origin=%s, ", origin)
                + format("reason=%s, ", reason)
                + format("bytesReceived=%d, ", bytesReceived)
                + format("chunksReceived=%d, ", chunksReceived)
                + format("bytesEmitted=%d, ", bytesEmitted)
                + format("chunksEmitted=%d", chunksEmitted);
    }
}
