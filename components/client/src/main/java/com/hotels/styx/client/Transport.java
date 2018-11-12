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

import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.ResponseEventListener;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import rx.Observable;

/**
 * Encapsulates a single connection to remote server which we can use to send the messages.
 */
class Transport {
    public HttpTransaction send(LiveHttpRequest request, ConnectionPool connectionPool) {
        Observable<Connection> connection = connectionPool.borrowConnection();

        return new HttpTransaction() {
            @Override
            public Observable<LiveHttpResponse> response() {
                return connection.flatMap(connection -> {
                    Observable<LiveHttpResponse> responseObservable = connection.write(request);

                    return ResponseEventListener.from(responseObservable)
                            .whenCancelled(() -> closeIfConnected(connectionPool, connection))
                            .whenResponseError(cause -> closeIfConnected(connectionPool, connection))
                            .whenContentError(cause -> closeIfConnected(connectionPool, connection))
                            .whenCompleted(() -> returnIfConnected(connectionPool, connection))
                            .apply();
                });
            }

            private synchronized void closeIfConnected(ConnectionPool connectionPool, Connection connection) {
                connectionPool.closeConnection(connection);
            }

            private synchronized void returnIfConnected(ConnectionPool connectionPool, Connection connection) {
                connectionPool.returnConnection(connection);
            }
        };
    }
}
