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
package com.hotels.styx.api;

import org.testng.annotations.Test;

import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static rx.Observable.empty;
import static rx.Observable.just;

public class StyxCoreObservableTest {
    @Test
    public void convertsSingleToFuture() throws InterruptedException, ExecutionException, TimeoutException {
        String result = new StyxCoreObservable<>(just("foo"))
                .asCompletableFuture()
                .get(1, SECONDS);

        assertThat(result, is("foo"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Sequence contains too many elements")
    public void throwsExceptionWhenTryingToConvertMultipleToFuture() throws Throwable {
        try {
            new StyxCoreObservable<>(just("foo", "bar"))
                    .asCompletableFuture()
                    .get(1, SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test(expectedExceptions = NoSuchElementException.class, expectedExceptionsMessageRegExp = "Sequence contains no elements")
    public void throwsExceptionWhenTryingToConvertEmptyToFuture() throws Throwable {
        try {
            new StyxCoreObservable<String>(empty())
                    .asCompletableFuture()
                    .get(1, SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void mapsValues() throws InterruptedException, ExecutionException, TimeoutException {
        String result = new StyxCoreObservable<>(just("foo"))
                .map(value -> value + "bar")
                .asCompletableFuture()
                .get(1, SECONDS);

        assertThat(result, is("foobar"));
    }

    @Test
    public void flatMapsValues() throws InterruptedException, ExecutionException, TimeoutException {
        String result = new StyxCoreObservable<>(just("foo"))
                .flatMap(value -> StyxObservable.of(value + "bar"))
                .asCompletableFuture()
                .get(1, SECONDS);

        assertThat(result, is("foobar"));
    }

    @Test
    public void reducesValues() throws InterruptedException, ExecutionException, TimeoutException {
        String result = new StyxCoreObservable<>(just("foo", "bar", "baz"))
                .reduce((a, b) -> b + a, "")
                .asCompletableFuture()
                .get(1, SECONDS);

        assertThat(result, is("foobarbaz"));
    }

    @Test
    public void flatMapsWorksWithCustomObservable() throws InterruptedException, ExecutionException, TimeoutException {
        StyxObservable<String> custom = mock(StyxObservable.class);
        when(custom.asCompletableFuture()).thenReturn(completedFuture("bar"));

        String result = new StyxCoreObservable<>(just("foo"))
                .flatMap(value -> custom)
                .asCompletableFuture()
                .get(1, SECONDS);

        assertThat(result, is("bar"));
    }
}