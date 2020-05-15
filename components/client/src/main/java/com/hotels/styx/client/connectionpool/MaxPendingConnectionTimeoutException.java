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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.Id;
import com.hotels.styx.api.exceptions.StyxException;
import com.hotels.styx.api.extension.Origin;

import java.util.Optional;

import static java.lang.String.format;

/**
 * The consumer has been pending for connection for too long time.
 */
public class MaxPendingConnectionTimeoutException extends ResourceExhaustedException implements StyxException {
    private final Origin origin;
    private final int pendingConnectionTimeoutMillis;

    public MaxPendingConnectionTimeoutException(Origin origin, int pendingConnectionTimeoutMillis) {
        super(format("Maximum wait time exceeded for origin=%s. pendingConnectionTimeoutMillis=%d", origin, pendingConnectionTimeoutMillis));
        this.origin = origin;
        this.pendingConnectionTimeoutMillis = pendingConnectionTimeoutMillis;
    }

    public int pendingConnectionTimeoutMillis() {
        return pendingConnectionTimeoutMillis;
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
