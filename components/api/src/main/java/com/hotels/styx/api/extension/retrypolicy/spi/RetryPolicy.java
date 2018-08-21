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
package com.hotels.styx.api.extension.retrypolicy.spi;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;

import java.util.Optional;

/**
 * Interface for a retry mechanism for unreliable actions and transient conditions.
 */
public interface RetryPolicy {
    /**
     * Represents the context for a retry of a request made against the backend services.
     */
    interface Context {
        /**
         * Returns the ID of the application being tried.
         *
         * @return an app ID
         */
        Id appId();

        /**
         * The number of retries for the given operation.
         *
         * @return number of retries
         */
        int currentRetryCount();

        /**
         * The current request.
         *
         * @return current request
         */
        HttpRequest currentRequest();

        /**
         * The exception that occurred while processing the request, if one occurred.
         *
         * @return any throwable or absent
         */
        Optional<Throwable> lastException();

        /**
         * Previously tried origins.
         *
         * @return connection pools for the previously tried origins
         */
        Iterable<RemoteHost> previousOrigins();
    }

    /**
     * Specifies the outcome of applying the policy to the current context.
     */
    interface Outcome {
        /**
         * The interval in milliseconds until the next retry.
         *
         * @return A <code>long</code> which represents the retry interval (in milliseconds)
         */
        long retryIntervalMillis();

        /**
         * Returns the origin that the next retry should target.
         *
         * @return A {@link RemoteHost} that the next retry should target
         */
        Optional<RemoteHost> nextOrigin();

        /**
         * Specifies if the current outcome is to retry or not.
         *
         * @return true if the operation should be retried
         */
        boolean shouldRetry();
    }

    /**
     * Determines whether the request should be retried and specifies the delay before the next retry.
     *
     * @param context               A {@link Context} object that indicates the number of retries,
     *                              last requests results, etc
     * @param loadBalancer
     * @return A {@link com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy.Outcome}
     * whether the request should be retried and specifies the delay before the next retry
     */
    Outcome evaluate(Context context, LoadBalancer loadBalancer, LoadBalancer.Preferences lbContext);
}

