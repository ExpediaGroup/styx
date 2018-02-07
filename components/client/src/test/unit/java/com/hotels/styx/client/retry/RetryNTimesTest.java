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

import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.RemoteHost;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.netty.exceptions.IsRetryableException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static java.util.Collections.singleton;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RetryNTimesTest {

    private RetryNTimes retryNTimesPolicy;
    private RetryPolicy.Context retryPolicyContext;
    private LoadBalancingStrategy strategyMock;
    private LoadBalancingStrategy.Context strategyContextMock;
    private RemoteHost remoteHost;

    @BeforeMethod
    public void setupMocks() {
        this.retryNTimesPolicy = new RetryNTimes(1);

        this.retryPolicyContext = mock(RetryPolicy.Context.class);
        this.strategyMock = mock(LoadBalancingStrategy.class);
        this.strategyContextMock = mock(LoadBalancingStrategy.Context.class);
        this.remoteHost = RemoteHost.remoteHost(mock(Origin.class), mock(ConnectionPool.class), mock(HttpClient.class));

        when(retryPolicyContext.currentRetryCount()).thenReturn(0);
        when(retryPolicyContext.lastException()).thenReturn(empty());
    }

    @Test
    public void shouldRetryWithIsRetryableExceptionThrownAndMaxAttemptsNotReached() {
        when(retryPolicyContext.lastException()).thenReturn(of(new TestException()));
        RetryPolicy.Outcome retryOutcome = retryNTimesPolicy.evaluate(retryPolicyContext,
                strategyMock, strategyContextMock);

        assertThat(retryOutcome.shouldRetry(), equalTo(true));
    }

    @Test
    public void shouldNotRetryBasedOnMaxAttemptsReached() {
        when(retryPolicyContext.currentRetryCount()).thenReturn(1);
        when(retryPolicyContext.lastException()).thenReturn(of(new TestException()));
        RetryPolicy.Outcome retryOutcome = retryNTimesPolicy.evaluate(retryPolicyContext,
                strategyMock, strategyContextMock);

        assertThat(retryOutcome.shouldRetry(), equalTo(false));
    }

    @Test
    public void shouldNotRetryBasedOnExceptionOtherThanIsRetryableException() {
        when(retryPolicyContext.lastException()).thenReturn(of(new RuntimeException()));
        RetryPolicy.Outcome retryOutcome = retryNTimesPolicy.evaluate(retryPolicyContext,
                strategyMock, strategyContextMock);

        assertThat(retryOutcome.shouldRetry(), equalTo(false));
    }

    @Test
    public void shouldReturnUnfilteredOrigin() {
        when(retryPolicyContext.previousOrigins()).thenReturn(Collections.emptyList());
        when(strategyMock.vote(strategyContextMock)).thenReturn(singleton(remoteHost));

        RetryPolicy.Outcome retryOutcome = retryNTimesPolicy.evaluate(retryPolicyContext,
                strategyMock, strategyContextMock);

        assertThat(retryOutcome.nextOrigin().get(), equalTo(remoteHost));
    }

    @Test
    public void shouldReturnEmptyOriginList() {
        when(retryPolicyContext.previousOrigins()).thenReturn(Collections.singleton(remoteHost));
        when(strategyMock.vote(strategyContextMock)).thenReturn(singleton(remoteHost));

        RetryPolicy.Outcome retryOutcome = retryNTimesPolicy.evaluate(retryPolicyContext,
                strategyMock, strategyContextMock);

        assertThat(retryOutcome.nextOrigin().isPresent(), equalTo(false));
    }

    private final static class TestException extends RuntimeException implements IsRetryableException {

    }
}