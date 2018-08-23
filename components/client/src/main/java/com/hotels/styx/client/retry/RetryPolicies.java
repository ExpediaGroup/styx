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

import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy;

import java.util.Optional;

/**
 * Static utility methods pertaining to {@link RetryPolicy} instances.
 */
public final class RetryPolicies {
    private static final RetryPolicy.Outcome NEGATIVE_OUTCOME = new RetryPolicyOutcome(0, Optional.empty(), false);
    private static final RetryPolicy.Outcome POSITIVE_OUTCOME = new RetryPolicyOutcome(0, Optional.empty(), true);

    private static final RetryPolicy DO_NOT_RETRY = (context, loadBalancingStrategy, lbContext) -> NEGATIVE_OUTCOME;
    private static final RetryPolicy RETRY_ALWAYS = (context, loadBalancingStrategy, lbContext) -> POSITIVE_OUTCOME;

    /**
     * Returns a retry policy that performs no retries.
     *
     * @return a retry policy that performs no retries
     */
    public static RetryPolicy doNotRetry() {
        return DO_NOT_RETRY;
    }

    /**
     * Returns a retry policy that performs a retry no matter what.
     *
     * @return a retry policy that performs a retry no matter what
     */
    public static RetryPolicy retryAlways() {
        return RETRY_ALWAYS;
    }


    private static final class RetryPolicyOutcome implements RetryPolicy.Outcome {
        private final long retryIntervalMillis;
        private final Optional<RemoteHost> origin;
        private final boolean shouldRetry;

        private RetryPolicyOutcome(long retryIntervalMillis, Optional<RemoteHost> remoteHost, boolean shouldRetry) {
            this.retryIntervalMillis = retryIntervalMillis;
            this.origin = remoteHost;
            this.shouldRetry = shouldRetry;
        }

        @Override
        public long retryIntervalMillis() {
            return retryIntervalMillis;
        }

        @Override
        public Optional<RemoteHost> nextOrigin() {
            return origin;
        }

        @Override
        public boolean shouldRetry() {
            return shouldRetry;
        }
    }
}
