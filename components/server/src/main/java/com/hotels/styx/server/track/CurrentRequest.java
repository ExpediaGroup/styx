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
package com.hotels.styx.server.track;

import static java.lang.System.currentTimeMillis;

import java.util.function.Supplier;
import com.hotels.styx.api.LiveHttpRequest;

/**
 * Bean that represent the current request.
 */
public class CurrentRequest {

    private final String request;
    private final long startingTimeMillies;
    private volatile Thread currentThread;
    private final Supplier<String> stateSupplier;
    private boolean requestSent;

    public CurrentRequest(LiveHttpRequest request, Supplier<String> stateSupplier) {
        this.startingTimeMillies = currentTimeMillis();
        this.currentThread = Thread.currentThread();
        this.request = request.toString();
        this.stateSupplier = stateSupplier;
    }

    public Thread getCurrentThread() {
        return currentThread;
    }

    public void setCurrentThread(Thread currentThread) {
        this.currentThread = currentThread;
    }

    public String getRequest() {
        return request;
    }

    public long getStartingTimeMillies() {
        return startingTimeMillies;
    }

    public String getState() {
        return stateSupplier.get();
    }

    public boolean isRequestSent() {
        return requestSent;
    }

    public void setRequestSent(boolean requestSent) {
        this.requestSent = requestSent;
    }
}
