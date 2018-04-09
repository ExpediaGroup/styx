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
package com.hotels.styx.client;

import com.hotels.styx.api.HttpResponse;
import rx.Observable;

/**
 * An instance of a call to to a remote server.
 */
interface HttpTransaction {
    /**
     * An observable that returns a single event in the form of an HTTP response, or an error if the response could not
     * be obtained.
     *
     * @return response observable
     */
    Observable<HttpResponse> response();

    /**
     * Cancels the ongoing transaction. Default implementation does nothing.
     */
    default void cancel() {
        // do nothing
    }

    /**
     * Checks whether the current transaction is cancelled (non-consumable). Default implementation always returns false.
     *
     * @return true if the transaction was cancelled
     */
    default boolean isCancelled() {
        return false;
    }

    /**
     * Non cancellable txn.
     */
    class NonCancellableHttpTransaction implements HttpTransaction {
        private final Observable<HttpResponse> response;

        public NonCancellableHttpTransaction(Observable<HttpResponse> response) {
            this.response = response;
        }

        @Override
        public Observable<HttpResponse> response() {
            return response;
        }
    }
}
