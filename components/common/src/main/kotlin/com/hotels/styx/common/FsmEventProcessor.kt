/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.common

import org.slf4j.LoggerFactory.getLogger
import java.util.function.BiConsumer

/**
 * A state machine for driving Finite State Machines.
 *
 * @param <S> State type.
 */
class FsmEventProcessor<S>(
    private val stateMachine: StateMachine<S>,
    private val errorHandler: BiConsumer<Throwable, S>,
    private val loggingPrefix: String
) : EventProcessor {
    override fun submit(event: Any) = try {
        stateMachine.handle(event)
    } catch (cause: Throwable) {
        val eventName = event.javaClass.simpleName
        LOGGER.error("$loggingPrefix ${stateMachine.currentState} -> ???: Exception occurred while processing event=$eventName.", cause)
        errorHandler.accept(cause, stateMachine.currentState)
    }

    companion object {
        private val LOGGER = getLogger(FsmEventProcessor::class.java)
    }
}
