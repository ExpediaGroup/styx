/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.client.retry;

import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.netty.exceptions.IsRetryableException;

import java.util.Optional;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.collect.Iterables.contains;
import static java.util.stream.StreamSupport.stream;

/**
 * A {@link RetryPolicy} that tries a configurable <code>maxAttempts</code>.
 */
public class RetryNTimes extends AbstractRetryPolicy {
    public RetryNTimes(int maxAttempts) {
        super(0, maxAttempts);
    }

    @Override
    public RetryPolicy.Outcome evaluate(Context context, LoadBalancingStrategy loadBalancingStrategy,
                                        LoadBalancingStrategy.Context lbContext) {
        return new RetryPolicy.Outcome() {
            @Override
            public long retryIntervalMillis() {
                return deltaBackoffMillis();
            }

            @Override
            public Optional<ConnectionPool> nextOrigin() {
                return stream(loadBalancingStrategy.vote(lbContext).spliterator(), false)
                        .filter(origin -> !contains(context.previousOrigins(), origin))
                        .findFirst();
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
