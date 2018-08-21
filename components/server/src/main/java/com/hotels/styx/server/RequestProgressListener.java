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
package com.hotels.styx.server;

/**
 * A listener interface for progression of HTTP requests.
 */
public interface RequestProgressListener {
    RequestProgressListener IGNORE_REQUEST_PROGRESS = new RequestProgressListener() {
        @Override
        public void onRequest(Object requestId) {

        }

        @Override
        public void onComplete(Object requestId, int responseStatus) {

        }

        @Override
        public void onTerminate(Object requestId) {

        }
    };

    /**
     * To be called when a request begins.
     *
     * @param requestId a unique ID to identify the request
     */
    void onRequest(Object requestId);

    /**
     * To be called when a request completes successfully.
     * If onComplete or onTerminate has already been called. This method should do nothing.
     *
     * @param requestId a unique ID to identify the request
     * @param responseStatus the status code of the response
     */
    void onComplete(Object requestId, int responseStatus);

    /**
     * To be called when a request is terminated after being unable to complete successfully.
     * If onComplete or onTerminate has already been called. This method should do nothing.
     *
     * @param requestId a unique ID to identify the request
     */
    void onTerminate(Object requestId);
}
