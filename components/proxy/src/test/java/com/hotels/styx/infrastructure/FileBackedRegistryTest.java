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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.Registry.ReloadResult;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.infrastructure.FileBackedRegistry.changes;
import static com.hotels.styx.infrastructure.Registry.ReloadResult.reloaded;
import static com.hotels.styx.infrastructure.Registry.ReloadResult.unchanged;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FileBackedRegistryTest {

    private RegistryChangeListener listener;
    private byte[] originalContent;
    private byte[] newContent;
    private BackendService backendService;
    private FileBackedRegistry<BackendService> registry;

    @BeforeMethod
    public void setUp() throws Exception {
        listener = new RegistryChangeListener();
        originalContent = "... origins file ...".getBytes(UTF_8);
        newContent = "... new file ...".getBytes(UTF_8);
        backendService = new BackendService.Builder().build();
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
    public void announcesInitialStateWhenStarts() throws IOException, ExecutionException, InterruptedException {
        Resource configurationFile = mockResource("/styx/config", new ByteArrayInputStream(originalContent));

        registry = new FileBackedRegistry<>(configurationFile, bytes -> ImmutableList.of(backendService));
        registry.addListener(listener);

        await(registry.startService());

        assertThat(listener.changeLog().get(0).added(), contains(backendService));
        assertThat(listener.changeLog().get(0).removed(), emptyIterable());
        assertThat(listener.changeLog().get(0).updated(), emptyIterable());
        assertThat(listener.errorLog().size(), is(0));
    }


    @Test
    public void announcesNoMeaningfulChangesWhenFileDidNotChange() throws Exception {
        Resource configurationFile = mockResource("/styx/config",
                new ByteArrayInputStream(originalContent),
                new ByteArrayInputStream(originalContent)
        );

        registry = new FileBackedRegistry<>(configurationFile, bytes -> ImmutableList.of(backendService));
        registry.addListener(listener);
        await(registry.startService());

        ReloadResult result = registry.reload().get();
        assertThat(result, is(unchanged("file content did not change")));

        assertThat(listener.changeLog().get(0).added(), contains(backendService));
        assertThat(listener.changeLog().get(0).removed(), emptyIterable());
        assertThat(listener.changeLog().get(0).updated(), emptyIterable());
        assertThat(listener.errorLog().size(), is(0));
    }

    @Test
    public void announcesNoMeaningfulChangesWhenNoSemanticChanges() throws Exception {
        Resource configurationFile = mockResource("/styx/config", new ByteArrayInputStream(originalContent));

        registry = new FileBackedRegistry<>(configurationFile, bytes -> ImmutableList.of(backendService));
        registry.addListener(listener);

        await(registry.startService());

        when(configurationFile.inputStream()).thenReturn(new ByteArrayInputStream(newContent));
        ReloadResult result = registry.reload().get();
        assertThat(result, is(unchanged("file content was not semantically different")));

        assertThat(listener.changeLog().get(0).added(), contains(backendService));
        assertThat(listener.changeLog().get(0).removed(), emptyIterable());
        assertThat(listener.changeLog().get(0).updated(), emptyIterable());
        assertThat(listener.errorLog().size(), is(0));
    }

    @Test
    public void announcesChanges() throws Exception {
        BackendService backendService1 = new BackendService.Builder().id("x").path("/x").build();
        BackendService backendService2 = new BackendService.Builder().id("x").path("/y").build();

        Resource configurationFile = mockResource("/styx/config",
                new ByteArrayInputStream(originalContent),
                new ByteArrayInputStream(newContent));

        registry = new FileBackedRegistry<>(
                configurationFile,
                bytes -> {
                    if (new String(bytes).equals(new String(originalContent))) {
                        return ImmutableList.of(backendService1);
                    } else {
                        return ImmutableList.of(backendService2);
                    }
                });

        registry.addListener(listener);

        await(registry.startService());

        ReloadResult result = registry.reload().get();
        assertThat(result, is(reloaded("Changes applied!")));

        assertThat(listener.changeLog().get(1).added(), emptyIterable());
        assertThat(listener.changeLog().get(1).removed(), emptyIterable());
        assertThat(listener.changeLog().get(1).updated(), contains(backendService2));
        assertThat(listener.errorLog().size(), is(0));
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "java.util.concurrent.ExecutionException: java.lang.RuntimeException: Something went wrong...")
    public void completesWithExceptionWhenErrorsDuringReload() throws Exception {
        Resource configurationFile = mockResource("/styx/config",
                new ByteArrayInputStream(originalContent),
                new ByteArrayInputStream(newContent));

        registry = new FileBackedRegistry<>(
                configurationFile,
                bytes -> {
                    if (new String(bytes).equals(new String(originalContent))) {
                        return ImmutableList.of(backendService);
                    } else {
                        throw new RuntimeException("Something went wrong...");
                    }
                });
        registry.addListener(listener);
        await(registry.startService());
        assertThat(listener.changeLog().get(0).added(), contains(backendService));

        await(registry.reload());
    }

    private Resource mockResource(String path, ByteArrayInputStream content) throws IOException {
        Resource configuration = mock(Resource.class);
        when(configuration.absolutePath()).thenReturn(path);
        when(configuration.inputStream()).thenReturn(content);
        return configuration;
    }

    private Resource mockResource(String path, ByteArrayInputStream content1, ByteArrayInputStream... contents) throws IOException {
        Resource configuration = mock(Resource.class);
        when(configuration.absolutePath()).thenReturn(path);
        when(configuration.inputStream()).thenReturn(content1, contents);

        return configuration;
    }

    static class RegistryChangeListener implements Registry.ChangeListener<BackendService> {
        private List<Registry.Changes<BackendService>> changeLog = new ArrayList<>();
        private List<Throwable> errorLog = new ArrayList<>();

        @Override
        public void onChange(Registry.Changes<BackendService> changes) {
            System.out.println("onChange: " + changes);
            changeLog.add(changes);
        }

        @Override
        public void onError(Throwable ex) {
            errorLog.add(ex);
        }

        List<Registry.Changes<BackendService>> changeLog() {
            return changeLog;
        }

        List<Throwable> errorLog() {
            return errorLog;
        }
    }

    private BackendService backendService(String id, int port) {
        return new BackendService.Builder()
                .id(id)
                .origins(newOrigin(port))
                .build();
    }

    private Origin newOrigin(int port) {
        return newOriginBuilder("localhost", port).build();
    }
}