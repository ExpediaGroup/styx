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
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.exceptions.IsRetryableException;

import java.util.Optional;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.collect.Sets.newHashSet;

/**
 * A {@link RetryPolicy} that tries a configurable <code>maxAttempts</code>.
 */
public class RetryNTimes extends AbstractRetryPolicy {
    public RetryNTimes(int maxAttempts) {
        super(0, maxAttempts);
    }

    @Override
    public RetryPolicy.Outcome evaluate(Context context, LoadBalancer loadBalancingStrategy, LoadBalancer.Preferences lbContext) {
        return new RetryPolicy.Outcome() {
            @Override
            public long retryIntervalMillis() {
                return deltaBackoffMillis();
            }

            @Override
            public Optional<RemoteHost> nextOrigin() {
                return loadBalancingStrategy.choose(lbContext)
                        .filter(nextHost -> !newHashSet(context.previousOrigins()).contains(nextHost));
            }

            @Override
            public boolean shouldRetry() {
                boolean belowMaxRetryAttempts = context.currentRetryCount() < maxAttempts();
                Optional<Throwable> lastException = context.lastException();
                return belowMaxRetryAttempts && lastException.isPresent() && lastException.get() instanceof IsRetryableException;
            }

        };
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("delay", deltaBackoffMillis())
                .add("maxAttempts", maxAttempts())
                .toString();
    }
}
