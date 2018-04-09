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
package com.hotels.styx.client.applications;

/**
 * An object that receives origin statistics.
 */
public interface OriginStats {
    String REQUEST_SUCCESS = "requests.success-rate";
    String REQUEST_FAILURE = "requests.error-rate";

    /**
     * To be called when a request is successful.
     */
    void requestSuccess();

    /**
     * To be called when a request encounters an error.
     */
    void requestError();

    /**
     * Starts request latency timer.
     */
    AggregateTimer requestLatencyTimer();

    /**
     * records a response with a status code.
     *
     * @param statusCode status code
     */
    void responseWithStatusCode(int statusCode);

    /**
     * One minute rate for 5xx error messages.
     */
    double oneMinuteErrorRate();

    /**
     * Called when request is cancelled.
     */
    void requestCancelled();
}
