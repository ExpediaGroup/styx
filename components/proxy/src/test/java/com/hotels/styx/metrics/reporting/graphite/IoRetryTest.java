package com.hotels.styx.metrics.reporting.graphite;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static com.hotels.styx.metrics.reporting.graphite.IoRetry.IoRunnable;
import static com.hotels.styx.metrics.reporting.graphite.IoRetry.tryTimes;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;


public class IoRetryTest {

    private IoRunnable task;

    @BeforeMethod
    public void initialize() {
        task = mock(IoRunnable.class);
    }

    @Test
    public void runOnceIfNoErrors() throws IOException {
        tryTimes(2, task, null);
        verify(task).run();
    }

    @Test
    public void retryUpToMaxTimesForIoException() throws IOException {
        doThrow(new IOException()).when(task).run();
        try {
            tryTimes(3, task, (e) -> assertThat(e, is(instanceOf(IOException.class))));
            fail("An exception should have been thrown");
        } catch (UncheckedIOException e) {
            assertThat(e.getMessage(), containsString("Operation failed after"));
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
        doThrow(new IllegalArgumentException()).doNothing().when(task).run();
        try {
            tryTimes(3, task, null);
            fail("An exception should have been thrown");
        } catch (IllegalArgumentException e) {
        }
        verify(task, times(1)).run();
    }


}