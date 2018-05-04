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
package com.hotels.styx.metrics.reporting.graphite;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static com.hotels.styx.metrics.reporting.graphite.IoRetry.tryTimes;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;


public class IoRetryTest {

    private IOAction task;

    @BeforeMethod
    public void createMock() {
        task = mock(IOAction.class);
    }

    @Test
    public void runOnceIfNoErrors() throws IOException {
        tryTimes(2, task, null);
        verify(task).run();
    }

    @Test
    public void retryUpToMaxTimesForIoException() throws IOException {
        doThrow(new IOException("Could not connect")).when(task).run();
        try {
            tryTimes(3, task, (e) -> assertThat(e, is(instanceOf(IOException.class))));
            fail("An exception should have been thrown");
        } catch (UncheckedIOException e) {
            assertThat(e.getMessage(), is("Operation failed after 3 retries: Could not connect"));
        }
        verify(task, times(3)).run();
    }


    @Test
    public void retryOnlyOnceIfSingleError() throws IOException {
        doThrow(new IOException()).doNothing().when(task).run();
        tryTimes(3, task, (e) -> assertThat(e, is(instanceOf(IOException.class))));
        verify(task, times(2)).run();
    }

    @Test
    public void noRetriesForOtherExceptions() throws IOException {
        doThrow(new NullPointerException()).doNothing().when(task).run();
        try {
            tryTimes(3, task, null);
            fail("An exception should have been thrown");
        } catch (NullPointerException e) {
            verify(task, times(1)).run();
        }
    }


    @Test
    public void illegalArgumentExceptionAndNoAttemptsForInvalidArgument() throws IOException {
        doNothing().when(task).run();
        try {
            tryTimes(0, task, null);
            fail("An exception should have been thrown");
        } catch (IllegalArgumentException e) {
            verify(task, times(0)).run();
        }

    }

}