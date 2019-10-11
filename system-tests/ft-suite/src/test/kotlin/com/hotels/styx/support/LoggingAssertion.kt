/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.support

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent

fun List<ILoggingEvent>.shouldContain(level: Level, message: Regex) {
    for (event in this) {
        if (loggingEventMatches(event, level, message)) return
    }
    throw AssertionError("\nExpected log to contain event matching:\n"
        + "[" + level + "] " + message + "\n"
        + "But actual log:"
        + formatLogList(this));
}

private fun loggingEventMatches(event: ILoggingEvent, level: Level, message: Regex) : Boolean {
    return event.level == level && event.formattedMessage.matches(message);
}

private fun formatLogList(logList: List<ILoggingEvent>): String {
    return logList.fold("") {a,b -> a + "\n" + b}
}
