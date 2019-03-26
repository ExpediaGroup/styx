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
package com.hotels.styx.api;

import com.hotels.styx.api.extension.service.spi.StyxService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Provides utility methods for services.
 */
public final class Services {
    private Services() {
    }

    /**
     * Derives a new service interface with added side-effects for errors.
     * This could be used for logging, metrics, etc.
     *
     * @param service service
     * @param consumer error consumer
     * @return a new service interface
     */
    public static StyxService doOnStartFailure(StyxService service, Consumer<Throwable> consumer) {
        return new StyxService() {
            @Override
            public CompletableFuture<Void> start() {
                return service.start().exceptionally(throwable -> {
                    consumer.accept(throwable);

                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }

                    if (throwable instanceof Error) {
                        throw (Error) throwable;
                    }

                    throw new RuntimeException(throwable);
                });
            }

            @Override
            public CompletableFuture<Void> stop() {
                return service.stop();
            }

            @Override
            public Map<String, HttpHandler> adminInterfaceHandlers() {
                return service.adminInterfaceHandlers();
            }

            @Override
            public String toString() {
                return service.toString();
            }
        };
    }
}
