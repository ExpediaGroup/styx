/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.common.content;

import io.netty.channel.EventLoop;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;

import static com.hotels.styx.common.content.FlowControllingHttpContentProducer.ProducerState.COMPLETED;
import static com.hotels.styx.common.content.FlowControllingHttpContentProducer.ProducerState.TERMINATED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Checks to see if a FlowControllingHttpContentProducer is active and tears it down if it is not.
 */
public class FlowControllerTimer {

    private static HashedWheelTimer timer = new HashedWheelTimer();
    private long inactivityTimeoutMs;
    private EventLoop eventLoop;
    private FlowControllingHttpContentProducer producer;

    public FlowControllerTimer(long inactivityTimeoutMs, EventLoop eventLoop, FlowControllingHttpContentProducer producer) {
        this.inactivityTimeoutMs = inactivityTimeoutMs;
        this.eventLoop = eventLoop;
        this.producer = producer;
        resetTimer(inactivityTimeoutMs);
    }

    public void checkActivity() {
        if (producer.state() != COMPLETED && producer.state() != TERMINATED) {
            resetTimerOrTearDownFlowController();
        }
    }

    private void resetTimerOrTearDownFlowController() {
        if (producer.isWaitingForSubscriber()) {
            long timeLeft = (producer.lastActive() + inactivityTimeoutMs) - System.currentTimeMillis();
            if (timeLeft > 0) {
                resetTimer(timeLeft);
            } else {
                eventLoop.submit(() -> producer.tearDownResources("Inactive subscriber"));
            }
        } else {
            resetTimer(inactivityTimeoutMs);
        }
    }

    private Timeout resetTimer(long delay) {
        return timer.newTimeout(timeout -> checkActivity(), delay, MILLISECONDS);
    }
}
