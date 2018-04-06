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
package com.hotels.styx.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.lang.Thread.currentThread;

/**
 * An utility class to make your life easier with CompletableFutures.
 */
public final class StyxFutures {
    private StyxFutures() {
    }

    /**
     * Blocks on the CompletableFuture. Wraps the InterruptedException and/or ExecutionException
     * into a RuntimeException, and retains the interrupted() status of the thread.
     *
     * @param future Future object.
     * @param <T>    Return type of the future.
     * @return       Returns the future value once it completes.
     */
    public static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
