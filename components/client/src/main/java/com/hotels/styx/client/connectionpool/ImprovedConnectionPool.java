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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

import static java.util.Objects.nonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A connection pool implementation.
 */
public class ImprovedConnectionPool implements Connection.Listener {
    private static final Logger LOG = getLogger(ImprovedConnectionPool.class);

    private final ConnectionSettings connectionSettings;
    private final Connection.Factory connectionFactory;
    private final Origin origin;

    private final ConcurrentLinkedDeque<MonoSink<Connection>> waitingSubscribers;
    private final Queue<Connection> activeConnections;


    public ImprovedConnectionPool(Origin origin, ConnectionSettings connectionSettings, Connection.Factory connectionFactory) {
        this.origin = origin;
        this.connectionSettings = connectionSettings;
        this.connectionFactory = connectionFactory;
        this.activeConnections = new ConcurrentLinkedDeque<>();
        this.waitingSubscribers = new ConcurrentLinkedDeque<>();
    }

    public Origin getOrigin() {
        return origin;
    }

    public Publisher<Connection> borrowConnection() {
        return Mono.create(sink -> {
            Connection connection = dequeue();
            if (connection != null) {
                sink.success(connection);
            }
            else {
                this.waitingSubscribers.add(
                        sink.onCancel(() -> waitingSubscribers.remove(sink))
                );
                this.connectionFactory.createConnection(this.origin, this.connectionSettings)
                        .subscribe(this::queueNewConnection);
            }
        });
    }

    private Connection dequeue() {
        Connection connection = activeConnections.poll();

        while (nonNull(connection) && !connection.isConnected()) {
            connection = activeConnections.poll();
        }

        return connection;
    }

    private void queueNewConnection(Connection connection) {
        MonoSink<Connection> subscriber = waitingSubscribers.poll();
        if (subscriber == null) {
            activeConnections.add(connection);
        } else {
            subscriber.success(connection);
        }
    }

    public boolean returnConnection(Connection connection) {
        if (connection.isConnected()) {
            queueNewConnection(connection);
        }
        return false;
    }

    public boolean closeConnection(Connection connection) {
        return true;
    }

    @Override
    public void connectionClosed(Connection connection) {

    }
}

