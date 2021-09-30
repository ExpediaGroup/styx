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
package com.hotels.styx.client;

import com.hotels.styx.api.Id;
import com.hotels.styx.api.exceptions.StyxException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.exceptions.ExternalFault;

import java.util.Optional;

import static java.lang.String.format;

/**
 * An exception to throw when a bad HTTP response was received. For
 * example, when the decoding of the message failed.
 */
public class BadHttpResponseException extends RuntimeException implements StyxException, ExternalFault {
    private static final String MESSAGE_FORMAT = "Bad HTTP Response received from origin. origin=%s.";
    private final Origin origin;

    public BadHttpResponseException(Origin origin, Throwable cause) {
        super(format(MESSAGE_FORMAT, origin), cause);
        this.origin = origin;
    }

    @Override
    public Optional<Id> origin() {
        return Optional.of(origin.id());
    }

    @Override
    public Id application() {
        return origin.applicationId();
    }
}
