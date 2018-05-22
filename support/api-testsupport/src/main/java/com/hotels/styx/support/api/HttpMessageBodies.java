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
package com.hotels.styx.support.api;

import com.hotels.styx.api.HttpResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import com.hotels.styx.api.HttpRequest;


/**
 * Provides a support method for dealing with streaming HTTP request bodies.
 */
public final class HttpMessageBodies {
    /**
     * Return the body of {@code message} as string. Note that this will buffer all the message body in memory.
     *
     * @param message the message to read the body from
     * @return the body of the message as string
     */
    public static String bodyAsString(HttpRequest message) {
        return await(message.toFullRequest(0x100000)
                .asCompletableFuture())
                .bodyAs(UTF_8);
    }

    public static String bodyAsString(HttpResponse message) {
        return await(message.toFullResponse(0x100000)
                .asCompletableFuture())
                .bodyAs(UTF_8);
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpMessageBodies() {
    }
}
