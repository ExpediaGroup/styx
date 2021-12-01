/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.api.extension.service.spi;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpHeaderValues.APPLICATION_JSON;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.CREATED;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.FAILED;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.RUNNING;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.STARTING;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.STOPPED;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.STOPPING;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * A helper class for implementing StyxService interface.
 * <p>
 * AbstractStyxService provides service state management facilities
 * for implementing a StyxSerive interface.
 */
public abstract class AbstractStyxService implements StyxService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStyxService.class);

    private final String name;
    private final AtomicReference<StyxServiceStatus> status = new AtomicReference<>(CREATED);

    public AbstractStyxService(String name) {
        this.name = name;
        LOGGER.debug("Created {}", name);
    }

    public StyxServiceStatus status() {
        return status.get();
    }

    protected CompletableFuture<Void> startService() {
        return completedFuture(null);
    }

    protected CompletableFuture<Void> stopService() {
        return completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> start() {
        boolean changed = status.compareAndSet(CREATED, STARTING);
        LOGGER.debug("Starting {}", name);

        if (changed) {
            return startService()
                    .exceptionally(failWithMessage("Service failed to start."))
                    .thenAccept(na -> {
                        status.compareAndSet(STARTING, RUNNING);
                        LOGGER.debug("Started {}", name);
                    });
        } else {
            throw new IllegalStateException(format("Start '%s' called in %s state", name, status.get()));
        }
    }

    @Override
    public CompletableFuture<Void> stop() {
        boolean changed = status.compareAndSet(RUNNING, STOPPING);

        LOGGER.debug("Stopping {}", name);
        if (changed) {
            return stopService()
                    .exceptionally(failWithMessage("Service failed to stop."))
                    .thenAccept(na -> {
                        status.compareAndSet(STOPPING, STOPPED);
                        LOGGER.debug("Stopped {}", name);
                    });
        } else {
            throw new IllegalStateException(format("Service '%s' stopped in %s state", name, status.get()));
        }
    }

    private Function<Throwable, Void> failWithMessage(String message) {
        return cause -> {
            LOGGER.error(name + ": " + message, cause);
            status.set(FAILED);
            throw new ServiceFailureException(message, cause);
        };
    }

    @Override
    public Map<String, HttpHandler> adminInterfaceHandlers() {
        return Map.of("status", (request, context) -> Eventual.of(
                response(OK)
                        .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .body(format("{ name: \"%s\" status: \"%s\" }", name, status), UTF_8)
                        .build()
                        .stream()));
    }

    public String serviceName() {
        return name;
    }
}
