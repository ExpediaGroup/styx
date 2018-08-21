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
package com.hotels.styx.client.healthcheck.monitors;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.healthcheck.OriginHealthCheckFunction;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.client.healthcheck.Schedule;
import com.hotels.styx.support.DeterministicScheduler;
import org.testng.annotations.Test;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.common.HostAndPorts.localhost;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.HEALTHY;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.UNHEALTHY;
import static com.hotels.styx.common.StyxFutures.await;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ScheduledOriginHealthStatusMonitorTest {
    static final Origin LIVE_ORIGIN = newOriginBuilder(localhost(8080)).build();
    static final Origin DEAD_ORIGIN = newOriginBuilder(localhost(9090)).build();
    static final Origin DEAD_ORIGIN_2 = newOriginBuilder(localhost(9091)).build();

    final OriginHealthStatusMonitor.Listener listener = mock(OriginHealthStatusMonitor.Listener.class);
    final DeterministicScheduler scheduler = new DeterministicScheduler();

    @Test
    public void notifiesListenersWhenAnOriginIsUnreachable() {
        ScheduledOriginHealthStatusMonitor monitor = makeScheduledOriginHealthMonitor(new StubOriginStateOriginHealthCheckFunction(LIVE_ORIGIN));
        monitor.monitor(DEAD_ORIGIN);
        monitor.addOriginStatusListener(this.listener);
        await(monitor.start());

        scheduler.runNextPendingCommand();
        verifyOriginIsDead(DEAD_ORIGIN);
    }

    @Test
    public void notifiesListenerWhenMultipleOriginsAreUnreachable() {
        ScheduledOriginHealthStatusMonitor monitor = makeScheduledOriginHealthMonitor(new StubOriginStateOriginHealthCheckFunction(LIVE_ORIGIN));
        monitor.monitor(DEAD_ORIGIN, DEAD_ORIGIN_2);
        monitor.addOriginStatusListener(this.listener);

        await(monitor.start());
        scheduler.runNextPendingCommand();
        verify(this.listener, atLeast(2)).originUnhealthy(anyOrigin());
    }

    @Test
    public void notifiesListenersOfOriginStatesChange() throws InterruptedException {
        StubOriginStateOriginHealthCheckFunction healthChecker = new StubOriginStateOriginHealthCheckFunction(LIVE_ORIGIN);
        ScheduledOriginHealthStatusMonitor monitor = makeScheduledOriginHealthMonitor(healthChecker);
        monitor.monitor(DEAD_ORIGIN);
        monitor.addOriginStatusListener(this.listener);

        await(monitor.start());

        verifyOriginIsDead(DEAD_ORIGIN);

        healthChecker.raiseDeadOrigins();

        scheduler.runNextPendingCommand();
        verifyOriginIsLive(DEAD_ORIGIN);
    }

    private void verifyOriginIsLive(Origin origin) {
        verify(this.listener, atLeastOnce()).originHealthy(origin);
    }

    private void verifyOriginIsDead(Origin origin) {
        verify(this.listener, atLeastOnce()).originUnhealthy(origin);
    }

    private ScheduledOriginHealthStatusMonitor makeScheduledOriginHealthMonitor(OriginHealthCheckFunction healthChecker) {
        return new ScheduledOriginHealthStatusMonitor(this.scheduler, healthChecker, new Schedule(10, MILLISECONDS));
    }

    private static class StubOriginStateOriginHealthCheckFunction implements OriginHealthCheckFunction {

        private final Origin liveOrigin;
        private volatile boolean treatAllOriginsAsLive = false;

        StubOriginStateOriginHealthCheckFunction(Origin liveOrigin) {
            this.liveOrigin = liveOrigin;
        }

        @Override
        public void check(Origin origin, OriginHealthCheckFunction.Callback responseCallback) {
            responseCallback.originStateResponse(this.treatAllOriginsAsLive || origin.equals(this.liveOrigin) ? HEALTHY : UNHEALTHY);
        }

        void raiseDeadOrigins() {
            this.treatAllOriginsAsLive = true;
        }
    }

    private static Origin anyOrigin() {
        return any(Origin.class);
    }

}