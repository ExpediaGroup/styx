/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.startup;

import com.google.common.util.concurrent.Service.Listener;
import com.hotels.styx.server.HttpServer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.google.common.util.concurrent.Service.State.STOPPING;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.CREATED;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.RUNNING;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.STOPPED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ServerServiceTest {
    private Supplier<HttpServer> serverCreator;
    private volatile Listener serviceListener;

    @BeforeMethod
    public void setUp() {
        HttpServer server = mock(HttpServer.class);
        when(server.startAsync()).thenReturn(server);
        when(server.stopAsync()).thenReturn(server);

        doAnswer(invocation -> {
            serviceListener = (Listener) invocation.getArguments()[0];
            return null;
        }).when(server).addListener(any(Listener.class), any(Executor.class));

        serverCreator = mock(Supplier.class);
        when(serverCreator.get()).thenReturn(server);
    }

    @Test
    public void startsAndStops() throws InterruptedException, ExecutionException, TimeoutException {
        ServerService service = new ServerService("foo", serverCreator::get);

        assertThat(service.serviceName(), is("foo"));
        assertThat(service.status(), is(CREATED));

        // START
        CompletableFuture<Void> startFuture = service.start();
        serviceListener.running();
        startFuture.get(1, SECONDS);

        assertThat(service.status(), is(RUNNING));

        // STOP
        CompletableFuture<Void> stopFuture = service.stop();
        serviceListener.terminated(STOPPING);
        stopFuture.get(1, SECONDS);

        assertThat(service.status(), is(STOPPED));
    }

    @Test
    public void onlyUsesServerCreatorOnce() throws InterruptedException, ExecutionException, TimeoutException {
        ServerService service = new ServerService("foo", serverCreator::get);

        verifyZeroInteractions(serverCreator);

        // START
        CompletableFuture<Void> startFuture = service.start();
        serviceListener.running();
        startFuture.get(1, SECONDS);

        verify(serverCreator).get();

        // STOP
        CompletableFuture<Void> stopFuture = service.stop();
        serviceListener.terminated(STOPPING);
        stopFuture.get(1, SECONDS);

        verifyNoMoreInteractions(serverCreator);
    }
}