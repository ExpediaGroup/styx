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
package com.hotels.styx.infrastructure;

import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static ch.qos.logback.classic.Level.ERROR;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.infrastructure.FileBackedRegistry.changes;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileBackedRegistryTest {
    private LoggingTestSupport log;

    @AfterMethod
    public void stopRecordingLogs() {
        if (log != null) {
            log.stop();
        }
    }

    @Test
    public void calculatesTheDifferenceBetweenCurrentAndNewResources() {
        Iterable<BackendService> newResources = singletonList(backendService("one", 9090));
        Iterable<BackendService> currentResources = singletonList(backendService("two", 9091));

        Registry.Changes<BackendService> changes = changes(newResources, currentResources);
        Registry.Changes<Identifiable> expected = new Registry.Changes.Builder<>()
                .added(backendService("one", 9090))
                .removed(backendService("two", 9091))
                .build();

        assertThat(changes.toString(), is(expected.toString()));
    }

    @Test
    public void loadsResources() throws TimeoutException {
        FileBackedRegistry<FakeData> registry = new FileBackedRegistry<>(
                content -> singletonList(new FakeData(content)),
                "/foo/origins-test.yml",
                resource -> "test_content".getBytes()
        );

        registry.startAsync().awaitRunning(1, SECONDS);

        assertThat(registry.snapshot.get(), contains(new FakeData("test_content")));
    }

    @Test
    public void reloadsResources() throws TimeoutException {
        Queue<byte[]> fileContents = new ArrayDeque<>(asList(
                "original_test_content".getBytes(),
                "modified_test_content".getBytes()
        ));

        FileBackedRegistry<FakeData> registry = new FileBackedRegistry<>(
                content -> singletonList(new FakeData(content)),
                "/foo/origins-test.yml",
                resource -> fileContents.poll()
        );

        registry.startAsync().awaitRunning(1, SECONDS);

        assertThat(registry.snapshot.get(), contains(new FakeData("original_test_content")));

        registry.reload();

        assertThat(registry.snapshot.get(), contains(new FakeData("modified_test_content")));
    }

    @Test
    public void logsFileChangeWhenChangeListenerThrowsAnExceptionDuringReload() throws IOException, TimeoutException {
        log = new LoggingTestSupport(FileBackedRegistry.class);

        Queue<byte[]> fileContents = new ArrayDeque<>(asList(
                "original_test_content".getBytes(),
                "modified_test_content".getBytes()
        ));

        Resource configFile = mock(Resource.class);
        when(configFile.absolutePath()).thenReturn("/foo/origins-test.yml");

        FileBackedRegistry<FakeData> registry = new FileBackedRegistry<>(
                content -> singletonList(new FakeData(content)),
                configFile.absolutePath(),
                resource -> fileContents.poll()
        );

        registry.startAsync().awaitRunning(1, SECONDS);

        Registry.ChangeListener<FakeData> listener = mock(Registry.ChangeListener.class);

        // First call is on "addListener" so do nothing. Next time is on "reload", so throw an exception
        doNothing().doThrow(new RuntimeException("Testing")).when(listener).onChange(any(Registry.Changes.class));

        registry.addListener(listener);

        registry.reload();

        assertThat(log.lastMessage(), is(loggingEvent(
                ERROR, "Not reloading /foo/origins-test.yml as there was an error reading content",
                ReloadException.class, "Exception during reload: Testing : previousFileContent=original_test_content, newFileContent=modified_test_content"
        )));
    }

    private static class FakeData implements Identifiable {
        private final String value;

        FakeData(byte[] value) {
            this.value = new String(value);
        }

        FakeData(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FakeData fakeData = (FakeData) o;
            return Objects.equals(value, fakeData.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public Id id() {
            return Id.id(value);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private static BackendService backendService(String id, int port) {
        return new BackendService.Builder()
                .id(id)
                .origins(newOrigin(port))
                .build();
    }

    private static Origin newOrigin(int port) {
        return newOriginBuilder("localhost", port).build();
    }
}