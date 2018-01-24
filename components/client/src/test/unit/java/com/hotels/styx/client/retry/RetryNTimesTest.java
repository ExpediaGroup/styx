/**
 * Copyright (C) 2013-2018 Expedia Inc.
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
import org.testng.annotations.Test;

import java.util.Collections;

import static java.util.Collections.*;
import static java.util.Optional.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RetryNTimesTest {

    @Test
    public void shouldRetry() {
        RetryNTimes retryNTimes = new RetryNTimes(1);

        RetryPolicy.Context context = mock(RetryPolicy.Context.class);
        when(context.currentRetryCount()).thenReturn(0);
        when(context.lastException()).thenReturn(of(new TestException()));
        RetryPolicy.Outcome retryOutcome = retryNTimes.evaluate(context,
                mock(LoadBalancingStrategy.class), mock(LoadBalancingStrategy.Context.class));

        assertThat(retryOutcome.shouldRetry(), equalTo(true));
    }

    @Test
    public void shouldNotRetryBasedOnMaxAttemptsReached() {
        RetryNTimes retryNTimes = new RetryNTimes(1);

        RetryPolicy.Context context = mock(RetryPolicy.Context.class);
        when(context.currentRetryCount()).thenReturn(1);
        when(context.lastException()).thenReturn(of(new TestException()));
        RetryPolicy.Outcome retryOutcome = retryNTimes.evaluate(context,
                mock(LoadBalancingStrategy.class), mock(LoadBalancingStrategy.Context.class));

        assertThat(retryOutcome.shouldRetry(), equalTo(false));
    }

    @Test
    public void shouldNotRetryBasedOnWrongException() {
        RetryNTimes retryNTimes = new RetryNTimes(1);

        RetryPolicy.Context context = mock(RetryPolicy.Context.class);
        when(context.currentRetryCount()).thenReturn(0);
        when(context.lastException()).thenReturn(of(new RuntimeException()));
        RetryPolicy.Outcome retryOutcome = retryNTimes.evaluate(context,
                mock(LoadBalancingStrategy.class), mock(LoadBalancingStrategy.Context.class));

        assertThat(retryOutcome.shouldRetry(), equalTo(false));
    }

    @Test
    public void shouldReturnUnfilteredOrigin() {
        RetryNTimes retryNTimes = new RetryNTimes(1);

        RetryPolicy.Context context = mock(RetryPolicy.Context.class);
        ConnectionPool connectionPool = mock(ConnectionPool.class);
        LoadBalancingStrategy.Context lbContext = mock(LoadBalancingStrategy.Context.class);
        LoadBalancingStrategy loadBalancingStrategy = mock(LoadBalancingStrategy.class);

        when(context.currentRetryCount()).thenReturn(0);
        when(context.lastException()).thenReturn(empty());
        when(context.previousOrigins()).thenReturn(Collections.emptyList());

        when(loadBalancingStrategy.vote(lbContext)).thenReturn(singleton(connectionPool));
        RetryPolicy.Outcome retryOutcome = retryNTimes.evaluate(context,
                loadBalancingStrategy, lbContext);

        assertThat(retryOutcome.nextOrigin().get(), equalTo(connectionPool));
    }

    @Test
    public void shouldReturnEmptyOriginList() {
        RetryNTimes retryNTimes = new RetryNTimes(1);

        RetryPolicy.Context context = mock(RetryPolicy.Context.class);
        ConnectionPool connectionPool = mock(ConnectionPool.class);
        LoadBalancingStrategy.Context lbContext = mock(LoadBalancingStrategy.Context.class);
        LoadBalancingStrategy loadBalancingStrategy = mock(LoadBalancingStrategy.class);

        when(context.currentRetryCount()).thenReturn(0);
        when(context.lastException()).thenReturn(empty());
        when(context.previousOrigins()).thenReturn(Collections.singleton(connectionPool));

        when(loadBalancingStrategy.vote(lbContext)).thenReturn(singleton(connectionPool));
        RetryPolicy.Outcome retryOutcome = retryNTimes.evaluate(context,
                loadBalancingStrategy, lbContext);

        assertThat(retryOutcome.nextOrigin().isPresent(), equalTo(false));
    }

    private final static class TestException extends RuntimeException implements IsRetryableException {

    }
}