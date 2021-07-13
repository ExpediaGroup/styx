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

import static java.lang.String.format;

/**
 * A timeout due to a subscriber being inactive.
 */
public class InactiveSubscriberException extends TransportException {

    public InactiveSubscriberException(long bytesReceived, long chunksReceived, long bytesEmitted, long chunksEmitted) {
        super(message(bytesReceived, chunksReceived, bytesEmitted, chunksEmitted));
    }

    private static String message(long bytesReceived, long chunksReceived, long bytesEmitted, long chunksEmitted) {
        return "Timeout due to inactive subscriber. "
                + format("bytesReceived=%d, ", bytesReceived)
                + format("chunksReceived=%d, ", chunksReceived)
                + format("bytesEmitted=%d, ", bytesEmitted)
                + format("chunksEmitted=%d", chunksEmitted);
    }
}
