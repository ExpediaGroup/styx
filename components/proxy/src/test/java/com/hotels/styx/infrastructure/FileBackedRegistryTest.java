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
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.io.FileResource;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

import static ch.qos.logback.classic.Level.ERROR;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.infrastructure.FileBackedRegistry.changes;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.write;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

public class FileBackedRegistryTest {
    private Path tempFile;
    private LoggingTestSupport log;

    @AfterMethod
    public void deleteTempFile() throws IOException {
        if (tempFile != null) {
            deleteIfExists(tempFile);
        }
    }

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
    public void logsFileChangeWhenChangeListenerThrowsAnExceptionDuringReload() throws IOException, TimeoutException {
        log = new LoggingTestSupport(FileBackedRegistry.class);
        tempFile = createTempFile("origins-", ".txt");

        write(tempFile, "original_test_content".getBytes());

        FileBackedRegistry<FakeData> registry = new FileBackedRegistry<>(new FileResource(tempFile.toFile()), content -> singletonList(new FakeData(content)));

        registry.startAsync().awaitRunning(1, SECONDS);

        Registry.ChangeListener<FakeData> listener = mock(Registry.ChangeListener.class);

        // First call is on "addListener" so do nothing. Next time is on "reload", so throw an exception
        doNothing().doThrow(new RuntimeException("Testing")).when(listener).onChange(any(Registry.Changes.class));

        registry.addListener(listener);

        write(tempFile, "modified_test_content".getBytes());
        registry.reload();

        assertThat(log.lastMessage(), is(loggingEvent(
                ERROR, "Not reloading .*\\.txt as there was an error reading content",
                ReloadException.class, "Exception during reload: Testing : previousFileContent=original_test_content, newFileContent=modified_test_content"
        )));
    }

    private static class FakeData implements Identifiable {
        private final String value;

        FakeData(byte[] value) {
            this.value = new String(value);
        }

        @Override
        public Id id() {
            return Id.id(value);
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