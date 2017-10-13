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
package com.hotels.styx.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An event processor base class for implementing custom event processors.
 *
 * @param <S>
 */
public abstract class AbstractEventProcessor<S> implements EventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEventProcessor.class);

    private final BiConsumer<Throwable, S> errorHandler;
    private final StateMachine<S> stateMachine;
    private final String loggingPrefix;

    public AbstractEventProcessor(StateMachine<S> stateMachine, BiConsumer<Throwable, S> errorHandler, String loggingPrefix) {
        this.errorHandler = errorHandler;
        this.stateMachine = stateMachine;
        this.loggingPrefix = loggingPrefix;
    }

    void processEvent(Object event) {
        try {
            stateMachine.handle(event, loggingPrefix);
        } catch (Throwable cause) {
            String eventName = event.getClass().getSimpleName();
            LOGGER.error("{} {} -> ???: Exception occurred while processing event={}. Cause=\"{}\"",
                    new Object[]{loggingPrefix, stateMachine.currentState(), eventName, cause});

            errorHandler.accept(cause, stateMachine.currentState());
        }
    }

    @Override
    public void submit(Object event) {
        eventSubmitted(checkNotNull(event));
    }

    abstract void eventSubmitted(Object event);
}
