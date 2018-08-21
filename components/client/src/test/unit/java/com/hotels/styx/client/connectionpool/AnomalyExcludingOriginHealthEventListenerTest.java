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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.healthcheck.AnomalyExcludingOriginHealthEventListener;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;

import org.testng.annotations.Test;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AnomalyExcludingOriginHealthEventListenerTest {
    static final Origin ORIGIN = newOriginBuilder("localhost", 9090).build();

    @Test
    public void convertsThreeSuccessiveUpEventsToOneUpEvent() {
        OriginHealthStatusMonitor.Listener listener = mock(OriginHealthStatusMonitor.Listener.class);
        AnomalyExcludingOriginHealthEventListener adapter = new AnomalyExcludingOriginHealthEventListener(listener, 3, 1);

        adapter.originHealthy(ORIGIN);
        adapter.originHealthy(ORIGIN);
        adapter.originHealthy(ORIGIN);

        verify(listener, times(1)).originHealthy(ORIGIN);
    }

    @Test
    public void willNotPropagateASingleUpEvent() {
        OriginHealthStatusMonitor.Listener listener = mock(OriginHealthStatusMonitor.Listener.class);
        AnomalyExcludingOriginHealthEventListener adapter = new AnomalyExcludingOriginHealthEventListener(listener, 2, 0);

        adapter.originHealthy(ORIGIN);

        verify(listener, never()).originHealthy(ORIGIN);
    }

    @Test
    public void convertsFiveSuccessiveDownEventsToOneDownEvent() {
        OriginHealthStatusMonitor.Listener listener = mock(OriginHealthStatusMonitor.Listener.class);
        int unhealthyThreshold = 5;
        AnomalyExcludingOriginHealthEventListener adapter = new AnomalyExcludingOriginHealthEventListener(listener, 1, unhealthyThreshold);

        for (int i = 0; i < unhealthyThreshold; i++) {
            adapter.originUnhealthy(ORIGIN);
        }

        verify(listener, times(1)).originUnhealthy(ORIGIN);
    }

    @Test
    public void alternatingUpsAndDownsDoNotCountTowardsConsecutiveEvents() {
        OriginHealthStatusMonitor.Listener listener = mock(OriginHealthStatusMonitor.Listener.class);
        int minimumCount = 5;
        AnomalyExcludingOriginHealthEventListener adapter = new AnomalyExcludingOriginHealthEventListener(listener, minimumCount, minimumCount);

        for (int i = 0; i < minimumCount; i++) {
            adapter.originUnhealthy(ORIGIN);
            adapter.originHealthy(ORIGIN);
        }

        verify(listener, times(0)).originUnhealthy(ORIGIN);
        verify(listener, times(0)).originHealthy(ORIGIN);
    }

    @Test
    public void willNotPropagateASingleDownEvent() {
        OriginHealthStatusMonitor.Listener listener = mock(OriginHealthStatusMonitor.Listener.class);
        AnomalyExcludingOriginHealthEventListener adapter = new AnomalyExcludingOriginHealthEventListener(listener, 2, 2);

        adapter.originUnhealthy(ORIGIN);

        verify(listener, never()).originUnhealthy(ORIGIN);
    }

    @Test
    public void resetsCountersAfterMonitoringStopped() throws Exception {
        OriginHealthStatusMonitor.Listener listener = mock(OriginHealthStatusMonitor.Listener.class);
        AnomalyExcludingOriginHealthEventListener adapter = new AnomalyExcludingOriginHealthEventListener(listener, 3, 3);

        adapter.originHealthy(ORIGIN);
        adapter.originHealthy(ORIGIN);

        adapter.monitoringEnded(ORIGIN);
        adapter.originHealthy(ORIGIN);
        adapter.originHealthy(ORIGIN);
        verify(listener, times(0)).originHealthy(ORIGIN);

        adapter.originHealthy(ORIGIN);
        verify(listener, times(1)).originHealthy(ORIGIN);
    }
}