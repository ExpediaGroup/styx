/**
 * Copyright (C) 2013-2018 Expedia Inc.
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

import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.infrastructure.Registry.ReloadResult.reloaded;
import static com.hotels.styx.infrastructure.Registry.ReloadResult.unchanged;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;


public class FileBackedRegistryTest {
    private byte[] originalContent;
    private byte[] newContent;
    private BackendService backendService;
    private FileBackedRegistry<BackendService> registry;
    private Registry.ChangeListener<BackendService> listener;

    @BeforeMethod
    public void setUp() {
        originalContent = "... origins file ...".getBytes(UTF_8);
        newContent = "... new file ...".getBytes(UTF_8);
        backendService = new BackendService.Builder().build();
        listener = mock(Registry.ChangeListener.class, withSettings().verboseLogging());
    }

    @Test
    public void calculatesTheDifferenceBetweenCurrentAndNewResources() {
        Iterable<BackendService> newResources = singletonList(backendService("one", 9090));
        Iterable<BackendService> currentResources = singletonList(backendService("two", 9091));
        Registry.Changes<Identifiable> expected = new Registry.Changes.Builder<>()
                .added(backendService("one", 9090))
                .removed(backendService("two", 9091))
                .build();

        Registry.Changes<BackendService> changes = FileBackedRegistry.changes(newResources, currentResources);
        assertThat(changes.toString(), is(expected.toString()));
    }

    @Test
    public void announcesInitialStateWhenStarts() throws IOException {
        Resource configurationFile = mockResource("/styx/config", new ByteArrayInputStream(originalContent));

        registry = new FileBackedRegistry<>(configurationFile, bytes -> ImmutableList.of(backendService));
        registry.addListener(listener);

        await(registry.reload());

        verify(listener).onChange(eq(changeSet().added(backendService).build()));
    }

    @Test
    public void announcesNoMeaningfulChangesWhenFileDidNotChange() throws Exception {
        Resource configurationFile = mockResource("/styx/config",
                new ByteArrayInputStream(originalContent),
                new ByteArrayInputStream(originalContent)
        );

        registry = new FileBackedRegistry<>(configurationFile, bytes -> ImmutableList.of(backendService));
        registry.addListener(listener);
        await(registry.reload());
        verify(listener).onChange(eq(changeSet().added(backendService).build()));

        ReloadResult result = registry.reload().get();
        assertThat(result, is(unchanged("md5-hash=c346e70114eff08dceb13562f9abaa48, Identical file content.")));

        // Still only one invocation, because reload didn't introduce any changes to configuration
        verify(listener).onChange(eq(changeSet().added(backendService).build()));
    }

    @Test
    public void announcesNoMeaningfulChangesWhenNoSemanticChanges() throws Exception {
        Resource configurationFile = mockResource("/styx/config", new ByteArrayInputStream(originalContent));

        registry = new FileBackedRegistry<>(configurationFile, bytes -> ImmutableList.of(backendService));
        registry.addListener(listener);

        await(registry.reload());
        verify(listener).onChange(eq(changeSet().added(backendService).build()));

        when(configurationFile.inputStream()).thenReturn(new ByteArrayInputStream(newContent));
        ReloadResult result = registry.reload().get();
        assertThat(result, is(unchanged("md5-hash=24996b9d53b21a60c35dcb7ca3fb331a, No semantic changes.")));

        // Still only one invocation, because reload didn't introduce any changes to configuration
        verify(listener).onChange(eq(changeSet().added(backendService).build()));
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
        verify(listener).onChange(eq(changeSet().build()));

        await(registry.reload());
        verify(listener).onChange(eq(changeSet().added(backendService1).build()));

        ReloadResult result = registry.reload().get();
        assertThat(result, is(reloaded("md5-hash=24996b9d53b21a60c35dcb7ca3fb331a, File reloaded.")));
        assertThat(backendService1.equals(backendService2), is(false));

        verify(listener).onChange(eq(changeSet().updated(backendService2).build()));
    }

    private Registry.Changes.Builder<BackendService> changeSet() {
        return new Registry.Changes.Builder<>();
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
        await(registry.reload());

        verify(listener).onChange(eq(changeSet().added(backendService).build()));

        await(registry.reload());

        // Noting changed
        verify(listener).onChange(eq(changeSet().added(backendService).build()));
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