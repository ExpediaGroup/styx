/*
  Copyright (C) 2013-2022 Expedia Inc.

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

import com.hotels.styx.api.Clock;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.Connection;
import com.hotels.styx.javaconvenience.Stopwatch;
import reactor.core.publisher.Flux;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Provides wrapper for connection, that tracks a connection expiration time. Also provides a method for verification of
 * a time pasted.
 */
class ExpiringConnection implements Connection {
    private final Connection nettyConnection;
    private final long connectionExpirationSeconds;
    private final Stopwatch stopwatch;

    ExpiringConnection(Connection nettyConnection, long connectionExpirationSeconds, Clock clock) {
        this.connectionExpirationSeconds = connectionExpirationSeconds;
        this.nettyConnection = requireNonNull(nettyConnection);
        this.stopwatch = new Stopwatch(clock);
    }

    @Override
    public boolean isConnected() {
        if (isExpired()) {
            close();
            return false;
        }
        return nettyConnection.isConnected();
    }

    @Override
    public Flux<LiveHttpResponse> write(LiveHttpRequest request) {
        return nettyConnection.write(request);
    }

    @Override
    public Origin getOrigin() {
        return nettyConnection.getOrigin();
    }

    @Override
    public void addConnectionListener(Listener listener) {
        nettyConnection.addConnectionListener(listener);
    }

    @Override
    public void close() {
        nettyConnection.close();
    }

    private boolean isExpired() {
        return stopwatch.timeElapsedSoFar(SECONDS) >= connectionExpirationSeconds;
    }
}
