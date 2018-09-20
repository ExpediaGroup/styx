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
package com.hotels.styx.threads;

import com.hotels.styx.ItemsDump;

import java.lang.management.ThreadInfo;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.management.ManagementFactory.getThreadMXBean;
import static java.util.stream.Collectors.toList;

/**
 * Helper methods for looking at threads.
 */
public final class Threads {
    private Threads() {
    }

    /**
     * Get all threads as an {@link ItemsDump}, allowing for quick + easy logging.
     *
     * @return all threads
     */
    public static ItemsDump allThreadsDump() {
        return ItemsDump.dump(allThreads());
    }

    /**
     * Get all threads as a list.
     *
     * @return all threads
     */
    public static List<String> allThreads() {
        return Stream.of(getThreadMXBean().dumpAllThreads(false, false))
                .map(ThreadInfo::getThreadName)
                .sorted()
                .collect(toList());
    }

    /**
     * Get the total number of threads.
     *
     * @return total number of threads
     */
    public static int totalThreadCount() {
        return getThreadMXBean().getThreadCount();
    }

}
