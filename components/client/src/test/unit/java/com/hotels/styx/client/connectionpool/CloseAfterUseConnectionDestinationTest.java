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

import com.hotels.styx.api.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.api.client.Origin;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static rx.Observable.error;
import static rx.Observable.just;

public class CloseAfterUseConnectionDestinationTest {
    private final Origin origin = newOriginBuilder("localhost", 9090).build();
    private final Connection.Settings connectionSettings = new ConnectionSettings(1000);
    private Connection.Factory connectionFactory;
    private Connection connection;

    @BeforeMethod
    public void setUp() {
        connectionFactory = mock(Connection.Factory.class);
        connection = mock(Connection.class);
        when(connectionFactory.createConnection(any(Origin.class), any(Connection.Settings.class))).thenReturn(just(connection));
    }

    @Test
    public void connectionsAreClosedAfterTaskCompletes() {
        CloseAfterUseConnectionDestination connectionDestination = new CloseAfterUseConnectionDestination(origin, connectionSettings, connectionFactory);

        Observable<String> result = connectionDestination.withConnection(connection -> just("Foo"));

        assertThat(result.toBlocking().single(), is("Foo"));
        verify(connection).close();
    }

    @Test
    public void connectionsAreClosedAfterTaskFails() {
        CloseAfterUseConnectionDestination connectionDestination = new CloseAfterUseConnectionDestination(origin, connectionSettings, connectionFactory);

        Observable<String> result = connectionDestination.withConnection(connection -> error(new RuntimeException("test")));

        TestSubscriber<String> subscriber = new TestSubscriber<>();
        result.subscribe(subscriber);
        subscriber.assertError(RuntimeException.class);

        verify(connection).close();
    }

    @Test
    public void connectionsAreClosedAfterTaskThrowsException() {
        CloseAfterUseConnectionDestination connectionDestination = new CloseAfterUseConnectionDestination(origin, connectionSettings, connectionFactory);

        Observable<String> result = connectionDestination.withConnection(connection -> {
            throw new RuntimeException("test");
        });

        TestSubscriber<String> subscriber = new TestSubscriber<>();
        result.subscribe(subscriber);
        subscriber.assertError(RuntimeException.class);

        verify(connection).close();
    }

    @Test
    public void addsSelfAsConnectionListenerToConnections() {
        CloseAfterUseConnectionDestination connectionDestination = new CloseAfterUseConnectionDestination(origin, connectionSettings, connectionFactory);

        Observable<String> result = connectionDestination.withConnection(connection -> just("Foo"));

        result.toBlocking().single();

        verify(connection).addConnectionListener(connectionDestination);
    }
}