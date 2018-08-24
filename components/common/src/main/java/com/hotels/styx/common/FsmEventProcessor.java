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
package com.hotels.styx.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

import static java.util.Objects.requireNonNull;

/**
 * A state machine for driving Finite State Machines.
 *
 * @param <S> State type.
 */
public class FsmEventProcessor<S> implements EventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FsmEventProcessor.class);

    private final BiConsumer<Throwable, S> errorHandler;
    private final StateMachine<S> stateMachine;
    private final String loggingPrefix;

    public FsmEventProcessor(StateMachine<S> stateMachine, BiConsumer<Throwable, S> errorHandler, String loggingPrefix) {
        this.errorHandler = requireNonNull(errorHandler);
        this.stateMachine = requireNonNull(stateMachine);
        this.loggingPrefix = requireNonNull(loggingPrefix);
    }

    @Override
    public void submit(Object event) {
        requireNonNull(event);

        try {
            stateMachine.handle(event, loggingPrefix);
        } catch (Throwable cause) {
            String eventName = event.getClass().getSimpleName();
            LOGGER.error("{} {} -> ???: Exception occurred while processing event={}. Cause=\"{}\"",
                    new Object[]{loggingPrefix, stateMachine.currentState(), eventName, cause});

            errorHandler.accept(cause, stateMachine.currentState());
        }
    }
}
