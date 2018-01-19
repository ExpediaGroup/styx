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
package com.hotels.styx.proxy.backends.file;

import com.hotels.styx.api.Resource;
import com.hotels.styx.api.service.spi.ServiceFailureException;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.FileBackedRegistry;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.infrastructure.Registry.ReloadResult;
import com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry.YAMLBackendServicesReader;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.io.ByteStreams.toByteArray;
import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.infrastructure.Registry.ReloadResult.reloaded;
import static com.hotels.styx.infrastructure.Registry.ReloadResult.unchanged;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileBackedBackendServicesRegistryTest {
    private FileBackedBackendServicesRegistry registry;

    @Test
    public void relaysReloadToRegistryDelegate() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);

        registry = new FileBackedBackendServicesRegistry(delegate);
        registry.reload();

        verify(delegate).reload();
    }

    @Test
    public void relaysAddListenerToRegisrtryDelegate() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        Registry.ChangeListener<BackendService> listener = mock(Registry.ChangeListener.class);

        registry = new FileBackedBackendServicesRegistry(delegate);
        registry.addListener(listener);

        verify(delegate).addListener(eq(listener));
    }

    @Test
    public void relaysRemoveListenerToRegisrtryDelegate() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        Registry.ChangeListener<BackendService> listener = mock(Registry.ChangeListener.class);

        registry = new FileBackedBackendServicesRegistry(delegate);
        registry.addListener(listener);
        registry.removeListener(listener);

        verify(delegate).removeListener(eq(listener));
    }

    @Test
    public void relaysGetToRgistryDelegate() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);

        registry = new FileBackedBackendServicesRegistry(delegate);
        registry.get();

        verify(delegate).get();
    }

    @Test
    public void reloadsDelegateRegistryOnStart() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.reload()).thenReturn(completedFuture(reloaded("Changes applied!")));

        registry = new FileBackedBackendServicesRegistry(delegate);
        await(registry.start());

        verify(delegate).reload();
    }

    @Test
    public void passesThroughUnchangedReloadResult() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.reload()).thenReturn(completedFuture(unchanged("file content did not change")));

        registry = new FileBackedBackendServicesRegistry(delegate);
        await(registry.start());

        verify(delegate).reload();
    }

    @Test
    public void propagatesStartupExceptions() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        RuntimeException cause = new RuntimeException("Failed to read file, etc.");
        when(delegate.reload()).thenReturn(failedFuture(cause));

        registry = new FileBackedBackendServicesRegistry(delegate);
        CompletableFuture<Void> future = registry.start();

        Optional<Throwable> result = await(future
                .thenApply(absent -> Optional.<Throwable>empty())
                .exceptionally(Optional::of));

        verify(delegate).reload();
        assertThat(result.get(), is(instanceOf(CompletionException.class)));
        assertThat(result.get().getCause(), is(instanceOf(ServiceFailureException.class)));
        assertThat(result.get().getCause().getCause(), is(instanceOf(RuntimeException.class)));
    }

    @Test
    public void yamlBackendReaderReadsBackendServicesFromByteStream() throws IOException {
        Resource resource = newResource("classpath:/backends/origins.yml");

        Iterable<BackendService> backendServices = new YAMLBackendServicesReader().read(toByteArray(resource.inputStream()));

        assertThat(newArrayList(backendServices).size(), is(3));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void yamlBackendReaderPropagatesExceptionWhenFailsToReadFromByteStream() throws IOException {
        Resource resource = newResource("classpath:/backends/origins-with-invalid-path.yml");

        new YAMLBackendServicesReader().read(toByteArray(resource.inputStream()));
    }

    private CompletableFuture<ReloadResult> failedFuture(Throwable cause) {
        CompletableFuture<ReloadResult> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

}