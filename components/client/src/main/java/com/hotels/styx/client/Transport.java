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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.netty.exceptions.NoAvailableHostsException;

import rx.Observable;

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
        AtomicBoolean outstandingRequest = new AtomicBoolean(true);

        return new HttpTransaction() {
            @Override
            public Observable<HttpResponse> response() {
                return connection.flatMap(tConnection ->
                    tConnection.write(request)
                        .map(response -> addOriginId(originId, response))
                        .doOnError(throwable -> {
                            closeIfConnected(origin, tConnection);
                        })
                        .doOnCompleted(() -> {
                            returnIfConnected(origin, tConnection);
                        })
                        .doOnUnsubscribe(() -> {
                            closeIfConnected(origin, tConnection);
                        }));
            }


            private synchronized void closeIfConnected(Optional<ConnectionPool> connectionPool, Connection connection) {
                if (connection == null) {
                    return;
                }
                if (outstandingRequest.getAndSet(false)) {
                    connectionPool.ifPresent(pool-> pool.closeConnection(connection));
                }
            }

            private synchronized void returnIfConnected(Optional<ConnectionPool> connectionPool, Connection connection) {
                if (connection == null) {
                    return;
                }
                if (outstandingRequest.getAndSet(false)) {
                    connectionPool.ifPresent(pool-> pool.returnConnection(connection));
                }
            }
        };
    }

    private Observable<Connection> connection(HttpRequest request, Optional<ConnectionPool> origin) {
        return origin
                .map(ConnectionPool::borrowConnection)
                .orElseGet(() -> {
                    request.body().releaseContentBuffers();
                    return Observable.error(new NoAvailableHostsException(appId));
                });
    }

    private HttpResponse addOriginId(Id originId, HttpResponse response) {
        return response.newBuilder()
                .header(originIdHeaderName, originId)
                .build();
    }
}
