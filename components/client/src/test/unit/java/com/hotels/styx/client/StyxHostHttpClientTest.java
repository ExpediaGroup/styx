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
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;

import static com.hotels.styx.api.LiveHttpResponse.response;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StyxHostHttpClientTest {

    private LiveHttpRequest request;

    @BeforeMethod
    public void setUp() {
        request =  HttpRequest.get("/").build().stream();
    }

    @Test
    public void sendsRequestUsingTransport() {
        ConnectionPool pool = mock(ConnectionPool.class);
        Transport transport = mock(Transport.class);
        HttpTransaction transaction = mock(HttpTransaction.class);

        when(transport.send(any(LiveHttpRequest.class), any(ConnectionPool.class))).thenReturn(transaction);
        when(transaction.response()).thenReturn(Observable.just(response().build()));

        new StyxHostHttpClient(pool, transport)
                .sendRequest(request);

        verify(transport).send(eq(request), eq(pool));
    }

    @Test
    public void closesTheConnectionPool() {
        ConnectionPool pool = mock(ConnectionPool.class);
        Transport transport = mock(Transport.class);
        when(transport.send(any(LiveHttpRequest.class), any(ConnectionPool.class))).thenReturn(mock(HttpTransaction.class));

        StyxHostHttpClient hostClient = new StyxHostHttpClient(pool, transport);

        hostClient.close();

        verify(pool).close();
    }

}
