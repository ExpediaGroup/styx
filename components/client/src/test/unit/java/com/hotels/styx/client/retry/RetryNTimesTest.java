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

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.exceptions.IsRetryableException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RetryNTimesTest {

    private RetryPolicy.Context retryPolicyContext;
    private RemoteHost remoteHost;
    private LoadBalancer loadBalancer;

    @BeforeMethod
    public void setupMocks() {
        this.retryPolicyContext = mock(RetryPolicy.Context.class);
        when(retryPolicyContext.currentRetryCount()).thenReturn(0);
        when(retryPolicyContext.lastException()).thenReturn(empty());

        this.remoteHost = RemoteHost.remoteHost(mock(Origin.class), mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class));
        this.loadBalancer = mock(LoadBalancer.class);

    }

    @Test
    public void shouldRetryWithIsRetryableException() {
        when(retryPolicyContext.lastException()).thenReturn(of(new RetryableTestException()));

        RetryPolicy.Outcome retryOutcome = new RetryNTimes(1).evaluate(
                retryPolicyContext, loadBalancer, null);

        assertThat(retryOutcome.shouldRetry(), equalTo(true));
    }

    @Test
    public void shouldNotRetryBasedOnMaxAttemptsReached() {
        when(retryPolicyContext.currentRetryCount()).thenReturn(1);
        when(retryPolicyContext.lastException()).thenReturn(of(new RetryableTestException()));

        RetryPolicy.Outcome retryOutcome = new RetryNTimes(1).evaluate(
                retryPolicyContext, loadBalancer, null);

        assertThat(retryOutcome.shouldRetry(), equalTo(false));
    }

    @Test
    public void shouldNotRetryBasedOnExceptionOtherThanIsRetryableException() {
        when(retryPolicyContext.lastException()).thenReturn(of(new RuntimeException()));

        RetryPolicy.Outcome retryOutcome = new RetryNTimes(1).evaluate(
                retryPolicyContext, loadBalancer, null);

        assertThat(retryOutcome.shouldRetry(), equalTo(false));
    }

    @Test
    public void returnsPreviouslyNonAttemptedOrigin() {
        when(retryPolicyContext.previousOrigins()).thenReturn(Collections.emptyList());
        when(loadBalancer.choose(any(LoadBalancer.Preferences.class))).thenReturn(of(remoteHost));

        RetryPolicy.Outcome retryOutcome = new RetryNTimes(1).evaluate(
                retryPolicyContext, loadBalancer, null);

        assertThat(retryOutcome.nextOrigin().get(), equalTo(remoteHost));
    }

    @Test
    public void filtersOutPreviouslyAttemptedOrigins() {
        when(retryPolicyContext.previousOrigins()).thenReturn(Collections.singleton(remoteHost));
        when(loadBalancer.choose(any(LoadBalancer.Preferences.class))).thenReturn(of(remoteHost));

        RetryPolicy.Outcome retryOutcome = new RetryNTimes(1).evaluate(
                retryPolicyContext, loadBalancer, null);

        assertThat(retryOutcome.nextOrigin().isPresent(), equalTo(false));
    }

    private final static class RetryableTestException extends RuntimeException implements IsRetryableException {

    }
}