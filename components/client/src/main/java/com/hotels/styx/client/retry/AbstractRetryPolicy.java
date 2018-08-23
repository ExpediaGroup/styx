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
package com.hotels.styx.client.retry;


import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy;

/**
 * Skeleton implementation of {@link RetryPolicy} for setting the backoff delay and retry attempts.
 */
abstract class AbstractRetryPolicy implements RetryPolicy {
    private final int deltaBackoffMillis;
    private final int maxAttempts;

    /**
     * Creates an instance of the <code>RetryPolicy</code> class using the specified delta backoff and maximum retry
     * attempts.
     *
     * @param deltaBackoffMillis The backoff interval, in milliseconds, between retries.
     * @param maxAttempts        The maximum number of retry attempts.
     */
    public AbstractRetryPolicy(int deltaBackoffMillis, int maxAttempts) {
        this.deltaBackoffMillis = deltaBackoffMillis;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public abstract Outcome evaluate(Context context, LoadBalancer loadBalancingStrategy, LoadBalancer.Preferences lbContext);

    public int deltaBackoffMillis() {
        return deltaBackoffMillis;
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
