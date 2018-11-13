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

import com.hotels.styx.api.LiveHttpRequest;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Manger class to manage the current requests.
 */
public class CurrentRequestTracker implements RequestTracker {
    public static final CurrentRequestTracker INSTANCE = new CurrentRequestTracker();

    private final ConcurrentHashMap<Object, CurrentRequest> currentRequests = new ConcurrentHashMap<>();

    public void trackRequest(LiveHttpRequest request, Supplier<String> state) {
        if (currentRequests.containsKey(request.id())) {
            currentRequests.get(request.id()).setCurrentThread(Thread.currentThread());
        } else {
            currentRequests.put(request.id(), new CurrentRequest(request, state));
        }
    }

    public void trackRequest(LiveHttpRequest request) {
        if (currentRequests.containsKey(request.id())) {
            currentRequests.get(request.id()).setCurrentThread(Thread.currentThread());
        } else {
            trackRequest(request, () -> "Status NOT Available.");
        }
    }

    public void markRequestAsSent(LiveHttpRequest request) {
        if (currentRequests.containsKey(request.id())) {
            currentRequests.get(request.id()).requestSent();
        }
    }

    public void endTrack(LiveHttpRequest request) {
        currentRequests.remove(request.id());
    }

    public Collection<CurrentRequest> currentRequests() {
        return currentRequests.values();
    }
}
