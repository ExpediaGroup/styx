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

import com.hotels.styx.api.Id;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.api.FullHttpRequest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import com.hotels.styx.api.HttpRequest;

import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StyxHostHttpClientTest {

    private HttpRequest request;

    @BeforeMethod
    public void setUp() {
        request =  FullHttpRequest.get("/").build().toStreamingRequest();
    }

    @Test
    public void sendsRequestUsingTransport() {
        ConnectionPool pool = mock(ConnectionPool.class);
        Transport transport = mock(Transport.class);
        when(transport.send(any(HttpRequest.class), any(Optional.class), any(Id.class))).thenReturn(mock(HttpTransaction.class));

        new StyxHostHttpClient(Id.id("app-01"), pool, transport)
                .sendRequest(request);

        verify(transport).send(eq(request), eq(Optional.of(pool)), eq(Id.id("app-01")));
    }

    @Test
    public void closesTheConnectionPool() {
        ConnectionPool pool = mock(ConnectionPool.class);
        Transport transport = mock(Transport.class);
        when(transport.send(any(HttpRequest.class), any(Optional.class), any(Id.class))).thenReturn(mock(HttpTransaction.class));

        StyxHostHttpClient hostClient = new StyxHostHttpClient(Id.id("app-01"), pool, transport);

        hostClient.close();

        verify(pool).close();
    }

}
