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

import static java.lang.String.format;

/**
 * Exception for when a host is down.
 */
public class OriginUnreachableException extends TransportException implements IsRetryableException {
    private static final String MESSAGE_FORMAT = "Origin server is unreachable. Could not connect to origin=%s";
    private final Origin origin;

    /**
     * Construct an exception.
     *
     * @param origin origin that is down.
     * @param cause exception that caused this exception
     */
    public OriginUnreachableException(Origin origin, Throwable cause) {
        super(format(MESSAGE_FORMAT, origin), cause);
        this.origin = origin;
    }

    /**
     * Origin that is down.
     *
     * @return origin
     */
    public Origin origin() {
        return origin;
    }
}
