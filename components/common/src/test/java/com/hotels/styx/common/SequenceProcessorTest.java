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
package com.hotels.styx.common;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class SequenceProcessorTest {
    @Test
    public void countsSuccessesAndFailures() {
        BiConsumer<Integer, String> onSuccess = mock(BiConsumer.class);
        BiConsumer<Integer, Exception> onFailure = mock(BiConsumer.class);

        AtomicReference<Map<Integer, Exception>> collectedFailures = new AtomicReference<>();

        List<Integer> inputs = asList(1, 2, 3, 4, 5, 6, 7);

        List<String> outputs = SequenceProcessor.processSequence(inputs).map(number -> {
            // throw exception if even

            if (number % 2 == 0) {
                throw new IllegalStateException();
            }

            return String.valueOf(number);
        })
                .onEachSuccess(onSuccess)
                .onEachFailure(onFailure)
                .failuresPostProcessing(collectedFailures::set)
                .collect();

        assertThat(outputs, contains("1", "3", "5", "7"));

        verify(onSuccess).accept(1, "1");
        verify(onSuccess).accept(3, "3");
        verify(onSuccess).accept(5, "5");
        verify(onSuccess).accept(7, "7");
        verifyNoMoreInteractions(onSuccess);

        verify(onFailure).accept(eq(2), any(IllegalStateException.class));
        verify(onFailure).accept(eq(4), any(IllegalStateException.class));
        verify(onFailure).accept(eq(6), any(IllegalStateException.class));
        verifyNoMoreInteractions(onFailure);

        assertThat(collectedFailures.get().keySet(), containsInAnyOrder(2, 4, 6));
        assertThat(collectedFailures.get().values().stream().allMatch(err -> err instanceof IllegalStateException), is(true));
    }
}