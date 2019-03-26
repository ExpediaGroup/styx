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

import com.google.common.util.concurrent.Service;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import com.hotels.styx.common.lambdas.SupplierWithCheckedException;
import com.hotels.styx.server.HttpServer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * A service that creates AND starts an HTTP server when started.
 */
public class ServerService extends AbstractStyxService {
    private final SupplierWithCheckedException<HttpServer> serverSupplier;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Construct a service. The supplier will be called once, when the service starts up.
     * The server instance returned by that call will continue to be used thereafter.
     *
     * @param name           service name
     * @param serverSupplier server supplier
     */
    public ServerService(String name, SupplierWithCheckedException<HttpServer> serverSupplier) {
        super(name);
        this.serverSupplier = serverSupplier.recordFirstOutput(true);
    }

    @Override
    protected CompletableFuture<Void> startService() {
        return server().thenCompose(server -> {
            CompletableFuture<Void> future = new CompletableFuture<>();

            server.addListener(new Service.Listener() {
                @Override
                public void running() {
                    future.complete(null);
                }

                @Override
                public void failed(Service.State from, Throwable failure) {
                    future.completeExceptionally(failure);
                }
            }, executor);

            server.startAsync();

            return future;
        });
    }

    @Override
    protected CompletableFuture<Void> stopService() {
        return server().thenCompose(server -> {
            CompletableFuture<Void> future = new CompletableFuture<>();

            server.addListener(new Service.Listener() {
                @Override
                public void terminated(Service.State from) {
                    future.complete(null);
                }

                @Override
                public void failed(Service.State from, Throwable failure) {
                    future.completeExceptionally(failure);
                }
            }, executor);

            server.stopAsync();

            return future;
        });
    }

    private CompletableFuture<HttpServer> server() {
        try {
            HttpServer server = serverSupplier.get();
            return completedFuture(server);
        } catch (Exception e) {
            CompletableFuture<HttpServer> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
