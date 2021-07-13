/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.hotels.styx.client.healthcheck.AnomalyExcludingOriginHealthEventListener;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AnomalyExcludingOriginHealthStatusMonitorTest {

    @ParameterizedTest
    @MethodSource("invalidThresholdValues")
    public void forHealthyThresholdAcceptsOnlyValuesGreaterThanZero(int healthyThreshold) {
        ScheduledOriginHealthStatusMonitor healthStatusMonitory = mock(ScheduledOriginHealthStatusMonitor.class);
        assertThrows(IllegalArgumentException.class,
                () -> new AnomalyExcludingOriginHealthStatusMonitor(healthStatusMonitory, healthyThreshold, 1));
    }

    @ParameterizedTest
    @MethodSource("invalidThresholdValues")
    public void forUnhealthyThresholdAcceptsOnlyValuesGreaterThanZero(int unHealthyThreshold) {
        ScheduledOriginHealthStatusMonitor healthStatusMonitory = mock(ScheduledOriginHealthStatusMonitor.class);
        assertThrows(IllegalArgumentException.class,
                () -> new AnomalyExcludingOriginHealthStatusMonitor(healthStatusMonitory, 1, unHealthyThreshold));
    }

    private static Stream<Arguments> invalidThresholdValues() {
        return Stream.of(
                Arguments.of(-1),
                Arguments.of(0),
                Arguments.of(-2)
        );
    }

    @Test
    public void makesListenersThresholdAware() {
        ScheduledOriginHealthStatusMonitor healthStatusMonitory = mock(ScheduledOriginHealthStatusMonitor.class);
        AnomalyExcludingOriginHealthStatusMonitor countingMonitor = new AnomalyExcludingOriginHealthStatusMonitor(healthStatusMonitory, 2, 3);

        countingMonitor.addOriginStatusListener(mock(OriginHealthStatusMonitor.Listener.class));
        verify(healthStatusMonitory).addOriginStatusListener(any(AnomalyExcludingOriginHealthEventListener.class));
    }
}