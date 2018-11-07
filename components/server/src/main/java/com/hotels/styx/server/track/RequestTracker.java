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

import java.util.function.Supplier;

/**
 * An interface for tracking requests as they pass through Styx.
 */
public interface RequestTracker {
    RequestTracker NO_OP = new RequestTracker() {
        @Override
        public void trackRequest(LiveHttpRequest request, Supplier<String> state) {
        }

        @Override
        public void trackRequest(LiveHttpRequest request) {
        }

        @Override
        public void markRequestAsSent(LiveHttpRequest request) {
        }

        @Override
        public void endTrack(LiveHttpRequest request) {

        }
    };

    void trackRequest(LiveHttpRequest request, Supplier<String> state);
    void trackRequest(LiveHttpRequest request);
    void markRequestAsSent(LiveHttpRequest request);
    void endTrack(LiveHttpRequest request);
}
