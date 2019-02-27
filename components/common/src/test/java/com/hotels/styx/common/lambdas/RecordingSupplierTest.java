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
package com.hotels.styx.common.lambdas;

import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

public class RecordingSupplierTest {
    @Test
    public void alwaysOutputsFirstResult() throws Exception {
        AtomicInteger increment = new AtomicInteger(1);
        SupplierWithCheckedException<Integer> supplier = increment::getAndIncrement;

        RecordingSupplier<Integer> recordingSupplier = new RecordingSupplier<>(supplier, false);

        assertThat(recordingSupplier.get(), is(1));
        assertThat(recordingSupplier.get(), is(1));
        assertThat(recordingSupplier.get(), is(1));
    }

    @Test
    public void willRecordExceptionsIfConfigured() throws Exception {
        SupplierWithCheckedException<Integer> supplier = mock(SupplierWithCheckedException.class);

        when(supplier.get()).thenThrow(new IllegalStateException("Test"))
                .thenReturn(1);

        RecordingSupplier<Integer> recordingSupplier = new RecordingSupplier<>(supplier, true);

        try {
            recordingSupplier.get();
            fail("Did not throw initial exception");
        } catch(IllegalStateException e) {
            try {
                recordingSupplier.get();
                fail("Did not record exception");
            } catch(IllegalStateException e2) {
                // Expected outcome
            }
        }
    }

    @Test
    public void willNotRecordExceptionsIfNotConfigured() throws Exception {
        SupplierWithCheckedException<Integer> supplier = mock(SupplierWithCheckedException.class);

        when(supplier.get()).thenThrow(new IllegalStateException("Test"))
                .thenReturn(1);

        RecordingSupplier<Integer> recordingSupplier = new RecordingSupplier<>(supplier, false);

        try {
            recordingSupplier.get();
            fail("Did not throw initial exception");
        } catch(IllegalStateException e) {
            assertThat(recordingSupplier.get(), is(1));
        }
    }
}