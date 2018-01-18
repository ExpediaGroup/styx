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

import com.google.common.collect.Iterables;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.service.spi.ServiceFailureException;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.common.StyxFutures;
import com.hotels.styx.infrastructure.FileBackedRegistry;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.infrastructure.Registry.ReloadResult;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.mockito.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static com.google.common.io.Files.copy;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.infrastructure.Registry.Outcome.RELOADED;
import static com.hotels.styx.infrastructure.Registry.Outcome.UNCHANGED;
import static com.hotels.styx.infrastructure.Registry.ReloadResult.reloaded;
import static com.hotels.styx.infrastructure.Registry.ReloadResult.unchanged;
import static com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistryTest.ChangeType.ADD;
import static com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistryTest.ChangeType.REMOVE;
import static com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistryTest.ChangeType.UPDATE;
import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class FileBackedBackendServicesRegistryTest {
    File fixturesPath = new File(newResource("classpath:/").absolutePath());
    File tempDirectory = new File(toFile("/backends"), "generated");

    Registry.ChangeListener<BackendService> changeListener;
    FileBackedBackendServicesRegistry registry;

    @BeforeMethod
    public void createTempDirectory() throws IOException {
        if (tempDirectory.exists()) {
            deleteFile(tempDirectory);
        }
        tempDirectory.mkdirs();

        changeListener = mock(Registry.ChangeListener.class);

//        registry = new FileBackedBackendServicesRegistry(givenOrigins("/backends/origins.yml"));
//        registry.addListener(changeListener);
//        StyxFutures.await(registry.start());
    }

//    @Test
//    public void firesChangeEventForInitialState() {
//        verify(changeListener).onChange(anyChanges());
//    }

// TODO: MIKKO: FIND A BETTER PLACE FOR THESE:
//    @Test(expectedExceptions = ServiceFailureException.class,
//            expectedExceptionsMessageRegExp = "Service failed to start.")
//    public void discardsInvalidPaths() throws Throwable {
//        registry = new FileBackedBackendServicesRegistry(givenOrigins("/backends/origins-with-invalid-path.yml"));
//        registry.addListener(changeListener);
//        unwrapException(() -> registry.start().join());
//    }
//
//    @Test(expectedExceptions = ServiceFailureException.class,
//            expectedExceptionsMessageRegExp = "Service failed to start.")
//    public void discardsInvalidHealthCheckURIs() throws Throwable {
//        registry = new FileBackedBackendServicesRegistry(givenOrigins("/backends/origins-with-invalid-healthcheck-uri.yml"));
//        registry.addListener(changeListener);
//        unwrapException(() -> registry.start().join());
//    }
//
//    @Test
//    public void discardsInvalidHealthCheckURIs2() throws Throwable {
//        registry = new FileBackedBackendServicesRegistry(givenOrigins("/backends/origins-with-invalid-healthcheck-uri.yml"));
//        registry.addListener(changeListener);
//        unwrapException(() -> registry.start());
//    }


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

    /*
        protected CompletableFuture<Void> startService() {
        return CompletableFuture.runAsync(() -> {
            LOG.info("starting {}", getClass().getSimpleName());

            byte[] content = readFile();
            fileHash = md5().hashBytes(content);
            Iterable<T> resources = reader.read(content);

            set(resources);
        });
    }

    ----

    @Override
    public CompletableFuture<ReloadResult> reload() {
        return supplyAsync(() -> {
            byte[] content = readFile();
            HashCode hashCode = md5().hashBytes(content);

            if (fileHash.equals(hashCode)) {
                LOG.info("Not reloading {} as content did not change", configurationFile.absolutePath());
                return unchanged("file content did not change");
            } else {
                try {
                    boolean changesPerformed = updateResources(content, hashCode);

                    if (!changesPerformed) {
                        LOG.info("Not firing change event for {} as content was not semantically different", configurationFile.absolutePath());
                        return unchanged("file content was not semantically different");
                    } else {
                        LOG.debug("Changes applied!");
                        return reloaded("Changes applied!");
                    }
                } catch (Exception e) {
                    LOG.error("Not reloading {} as there was an error reading content", configurationFile.absolutePath(), e);
                    notifyListenersOnError(e);
                    throw e;
                }
            }
        }, newSingleThreadExecutor());
    }

     */

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

    // TODO: There is no way to de-register all listeners.
    // TODO: How to inform the listeners that the service is shutting?
    // TODO: Do we need to do this anyway?

//    @Test
//    public void stopsService() {
//        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
//        registry = new FileBackedBackendServicesRegistry(delegate);
//        when(delegate.reload()).thenReturn(completedFuture(reloaded("Changes applied!")));
//
//        await(registry.start());
//
//        await(registry.stop());
//
//        verify(delegate).
//    }

    private CompletableFuture<ReloadResult> failedFuture(Throwable cause) {
        CompletableFuture<ReloadResult> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

//    @Test
//    public void firesChangeEventWhenBackendIsUpdated() throws IOException, ExecutionException, InterruptedException {
//        givenOrigins("/backends/origins-webapp-origin-removed.yml");
//        ReloadResult result = reload().get();
//        assertThat(result.outcome(), is(RELOADED));
//        verify(changeListener).onChange(updated("webapp"));
//    }

//    @Test
//    public void firesChangeEventWhenBackendOriginIdsAreUpdated() throws IOException, ExecutionException, InterruptedException {
//        givenOrigins("/backends/origins-id-changed.yml");
//        ReloadResult result = reload().get();
//        assertThat(result.outcome(), is(RELOADED));
//        verify(changeListener).onChange(updated("webapp"));
//    }

//    @Test
//    public void firesChangeEventWhenBackendOriginHostsAreUpdated() throws IOException, ExecutionException, InterruptedException {
//        givenOrigins("/backends/origins-host-changed.yml");
//        ReloadResult result = reload().get();
//        assertThat(result.outcome(), is(RELOADED));
//        verify(changeListener).onChange(updated("webapp"));
//    }

//    @Test
//    public void doesNotFireChangeEventWhenFileIsNotChanged() throws ExecutionException, InterruptedException {
//        ReloadResult result = reload().get();
//        assertThat(result.outcome(), is(UNCHANGED));
//        verifyNoMoreInteractions(changeListener);
//    }
//
//    @Test
//    public void doesNotFireChangeEventWhenFileChangesDoNotChangeConfiguration() throws IOException, ExecutionException, InterruptedException {
//        givenOrigins("/backends/origins-comments-changed.yml");
//        ReloadResult result = reload().get();
//        assertThat(result.outcome(), is(UNCHANGED));
//        verifyNoMoreInteractions(changeListener);
//    }

//    @Test
//    public void firesChangeEventWhenBackendIsRemoved() throws IOException, ExecutionException, InterruptedException {
//        givenOrigins("/backends/origins-landing-removed.yml");
//        ReloadResult result = reload().get();
//        assertThat(result.outcome(), is(RELOADED));
//        verify(changeListener).onChange(removed("landing"));
//    }

//    @Test
//    public void firesChangeEventWhenBackendIsAdded() throws IOException, ExecutionException, InterruptedException {
//        givenOrigins("/backends/origins-pd-app-added.yml");
//        ReloadResult result = reload().get();
//        assertThat(result.outcome(), is(RELOADED));
//        verify(changeListener).onChange(added("product-details"));
//    }

    private CompletableFuture<ReloadResult> reload() {
        reset(changeListener);
        return registry.reload();
    }

    private Registry.Changes<BackendService> anyChanges() {
        return any(Registry.Changes.class);
    }

    private Resource givenOrigins(String path) throws IOException {
        File from = toFile(path);
        File to = toFile("/backends/generated/origins.yml");
        copy(from, to);
        return newResource("classpath:/backends/generated/origins.yml");
    }

    private File toFile(String path) {
        return fixturesPath.toPath().resolve(path.replaceFirst("/", "")).toFile();
    }

    private Registry.Changes<BackendService> updated(String id) {
        return argThat(new ChangesMatcher(UPDATE, id(id)));
    }

    private Registry.Changes<BackendService> added(String id) {
        return argThat(new ChangesMatcher(ADD, id(id)));
    }

    private Registry.Changes<BackendService> removed(String id) {
        return argThat(new ChangesMatcher(REMOVE, id(id)));
    }

    private static class ChangesMatcher extends TypeSafeMatcher<Registry.Changes<BackendService>> {
        private final ChangeType changeType;
        private final Id backendService;

        public ChangesMatcher(ChangeType changeType, Id backendService) {
            this.changeType = changeType;
            this.backendService = backendService;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(format("change: type = %s; backend = %s", changeType, backendService));
        }

        @Override
        protected boolean matchesSafely(Registry.Changes<BackendService> changes) {
            return Iterables.any(changes(changes), bs -> bs.id().equals(backendService));
        }

        private Iterable<BackendService> changes(Registry.Changes<BackendService> changes) {
            switch (changeType) {
                case UPDATE:
                    return changes.updated();
                case REMOVE:
                    return changes.removed();
                case ADD:
                    return changes.added();
                default:
                    throw new IllegalStateException(changeType.name());
            }
        }
    }

    enum ChangeType {
        ADD, REMOVE, UPDATE
    }

    private static void deleteFile(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                deleteFile(f);
            }
        }

        file.delete();
    }

    private static void unwrapException(Runnable x) throws Throwable {
        try {
            x.run();
        } catch (CompletionException e) {
            throw e.getCause();
        }
    }

}