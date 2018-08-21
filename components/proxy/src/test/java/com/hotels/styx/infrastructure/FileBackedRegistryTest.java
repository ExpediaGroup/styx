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
package com.hotels.styx.infrastructure;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry.ReloadResult;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.util.function.Supplier;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.reloaded;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.unchanged;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
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
    private Supplier<FileTime> timeSupplier;

    @BeforeMethod
    public void setUp() {
        originalContent = "... origins file ...".getBytes(UTF_8);
        newContent = "... new file ...".getBytes(UTF_8);
        backendService = new BackendService.Builder().build();
        listener = mock(Registry.ChangeListener.class, withSettings().verboseLogging());
        timeSupplier = () -> FileTime.fromMillis(1520857740000L);
    }

    @Test
    public void announcesInitialStateWhenStarts() throws IOException {
        Resource configurationFile = mockResource("/styx/config", new ByteArrayInputStream(originalContent));

        registry = new FileBackedRegistry<>(configurationFile, bytes -> ImmutableList.of(backendService), timeSupplier);
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

        registry = new FileBackedRegistry<>(configurationFile, bytes -> ImmutableList.of(backendService), timeSupplier);
        registry.addListener(listener);
        await(registry.reload());
        verify(listener).onChange(eq(changeSet().added(backendService).build()));

        ReloadResult result = registry.reload().get();
        assertThat(result, is(unchanged("timestamp=2018-03-12T12:29:00Z, md5-hash=c346e70114eff08dceb13562f9abaa48, Identical file content.")));

        // Still only one invocation, because reload didn't introduce any changes to configuration
        verify(listener).onChange(eq(changeSet().added(backendService).build()));
    }

    @Test
    public void announcesNoMeaningfulChangesWhenNoSemanticChanges() throws Exception {
        Resource configurationFile = mockResource("/styx/config", new ByteArrayInputStream(originalContent));

        registry = new FileBackedRegistry<>(configurationFile, bytes -> ImmutableList.of(backendService), timeSupplier);
        registry.addListener(listener);

        await(registry.reload());
        verify(listener).onChange(eq(changeSet().added(backendService).build()));

        when(configurationFile.inputStream()).thenReturn(new ByteArrayInputStream(newContent));
        ReloadResult result = registry.reload().get();
        assertThat(result, is(unchanged("timestamp=2018-03-12T12:29:00Z, md5-hash=24996b9d53b21a60c35dcb7ca3fb331a, No semantic changes.")));

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
                },
                timeSupplier);

        registry.addListener(listener);
        verify(listener).onChange(eq(changeSet().build()));

        await(registry.reload());
        verify(listener).onChange(eq(changeSet().added(backendService1).build()));

        ReloadResult result = registry.reload().get();
        assertThat(result, is(reloaded("timestamp=2018-03-12T12:29:00Z, md5-hash=24996b9d53b21a60c35dcb7ca3fb331a, File reloaded.")));
        assertThat(backendService1.equals(backendService2), is(false));

        verify(listener).onChange(eq(changeSet().updated(backendService2).build()));
    }


    @Test
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
                },
                timeSupplier);
        registry.addListener(listener);
        ReloadResult outcome = await(registry.reload());
        assertThat(outcome, is(reloaded("timestamp=2018-03-12T12:29:00Z, md5-hash=c346e70114eff08dceb13562f9abaa48, File reloaded.")));

        verify(listener).onChange(eq(changeSet().added(backendService).build()));
        outcome = await(registry.reload());
        assertThat(outcome.outcome(), is(Registry.Outcome.FAILED));
        assertThat(outcome.message(), is("timestamp=2018-03-12T12:29:00Z, md5-hash=24996b9d53b21a60c35dcb7ca3fb331a, Reload failure."));
        assertThat(outcome.cause().get(), instanceOf(RuntimeException.class));

        // Noting changed
        verify(listener).onChange(eq(changeSet().added(backendService).build()));
    }

    @Test
    public void modifyTimeProviderHandlesExceptions() throws Exception {
        registry = new FileBackedRegistry<>(
                mockResource("/styx/config", new ByteArrayInputStream(originalContent)),
                bytes -> ImmutableList.of(new BackendService.Builder().id("x").path("/x").build()),
                () -> {
                    throw new RuntimeException("something went wrong");
                }
        );

        registry.addListener(listener);
        verify(listener).onChange(eq(changeSet().build()));

        ReloadResult result = registry.reload().get();
        assertThat(result, is(reloaded("timestamp=NA, md5-hash=c346e70114eff08dceb13562f9abaa48, File reloaded.")));
    }

    private Registry.Changes.Builder<BackendService> changeSet() {
        return new Registry.Changes.Builder<>();
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

}