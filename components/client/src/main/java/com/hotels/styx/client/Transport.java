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
package com.hotels.styx.client;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.api.exceptions.NoAvailableHostsException;
import rx.Observable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Encapsulates a single connection to remote server which we can use to send the messages.
 */
class Transport {
    private final Id appId;
    private final CharSequence originIdHeaderName;

    public Transport(Id appId, CharSequence originIdHeaderName) {
        this.appId = requireNonNull(appId);
        this.originIdHeaderName = requireNonNull(originIdHeaderName);
    }

    public HttpTransaction send(HttpRequest request, Optional<ConnectionPool> origin, Id originId) {
        Observable<Connection> connection = connection(request, origin);

        AtomicReference<Connection> connectionRef = new AtomicReference<>(null);
        Observable<HttpResponse> observableResponse = connection.flatMap(tConnection -> {
            connectionRef.set(tConnection);
            return tConnection.write(request)
                    .map(response -> addOriginId(originId, response));
        });

        return new HttpTransaction() {
            private final AtomicBoolean cancelled = new AtomicBoolean(false);

            @Override
            public void cancel() {
                if (!cancelled.getAndSet(true)) {
                    closeIfConnected(origin, connectionRef);
                }
            }

            @Override
            public Observable<HttpResponse> response() {
                return observableResponse
                        .doOnError(throwable -> {
                            if (!cancelled.get()) {
                                closeIfConnected(origin, connectionRef);
                            }
                        })
                        .doOnCompleted(() -> {
                            if (!cancelled.get()) {
                                returnIfConnected(origin, connectionRef);
                            }
                        })
                        .doOnUnsubscribe(() -> {
                            if (!cancelled.get()) {
                                closeIfConnected(origin, connectionRef);
                            }
                        });
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            private synchronized void closeIfConnected(Optional<ConnectionPool> connectionPool, AtomicReference<Connection> connectionRef) {
                Connection connection = connectionRef.get();
                if (connection != null && connectionPool.isPresent()) {
                    connectionPool.get().closeConnection(connection);
                    connectionRef.set(null);
                }
            }

            private synchronized void returnIfConnected(Optional<ConnectionPool> connectionPool, AtomicReference<Connection> connectionRef) {
                Connection connection = connectionRef.get();
                if (connection != null && connectionPool.isPresent()) {
                    connectionPool.get().returnConnection(connection);
                    connectionRef.set(null);
                }
            }
        };
    }

    private Observable<Connection> connection(HttpRequest request, Optional<ConnectionPool> origin) {
        return origin
                .map(ConnectionPool::borrowConnection)
                .orElseGet(() -> {
                    request.releaseContentBuffers();
                    return Observable.error(new NoAvailableHostsException(appId));
                });
    }

    private HttpResponse addOriginId(Id originId, HttpResponse response) {
        return response.newBuilder()
                .header(originIdHeaderName, originId)
                .build();
    }
}
