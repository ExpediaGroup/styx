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
package com.hotels.styx.proxy.backends.file;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.spi.Registry.ReloadResult;
import com.hotels.styx.api.extension.service.spi.ServiceFailureException;
import com.hotels.styx.infrastructure.FileBackedRegistry;
import com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry.RejectDuplicatePaths;
import com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry.YAMLBackendServicesReader;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.INFO;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.io.ByteStreams.toByteArray;
import static com.hotels.styx.common.io.ResourceFactory.newResource;
import static com.hotels.styx.api.extension.service.spi.Registry.Outcome.FAILED;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.failed;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.reloaded;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.unchanged;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileBackedBackendServicesRegistryTest {
    FileBackedBackendServicesRegistry registry;
    LoggingTestSupport log;
    LoggingTestSupport fileBackedLog;

    @BeforeMethod
    public void setUp() {
        log = new LoggingTestSupport(FileBackedBackendServicesRegistry.class);
        fileBackedLog = new LoggingTestSupport(FileBackedRegistry.class);
    }

    @AfterMethod
    public void tearDown() {
        log.stop();
        fileBackedLog.stop();
    }

    @Test
    public void relaysReloadToRegistryDelegate() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.reload()).thenReturn(CompletableFuture.completedFuture(ReloadResult.reloaded("relaod ok")));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.reload();

        verify(delegate).reload();
    }

    @Test
    public void relaysAddListenerToRegistryDelegate() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        Registry.ChangeListener<BackendService> listener = mock(Registry.ChangeListener.class);

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.addListener(listener);

        verify(delegate).addListener(eq(listener));
    }

    @Test
    public void relaysRemoveListenerToRegistryDelegate() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        Registry.ChangeListener<BackendService> listener = mock(Registry.ChangeListener.class);

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.addListener(listener);
        registry.removeListener(listener);

        verify(delegate).removeListener(eq(listener));
    }

    @Test
    public void relaysGetToRegistryDelegate() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.get();

        verify(delegate).get();
    }

    @Test
    public void reloadsDelegateRegistryOnStart() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.reload()).thenReturn(completedFuture(reloaded("Changes applied!")));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        await(registry.start());

        verify(delegate).reload();
    }

    @Test
    public void propagatesStartupExceptions() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        RuntimeException cause = new RuntimeException("Failed to read file, etc.");
        when(delegate.reload()).thenReturn(failedFuture(cause));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
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

    @Test
    public void monitorsFileChanges() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.reload()).thenReturn(completedFuture(reloaded("Changes applied!")));
        when(delegate.fileName()).thenReturn("/path/to/styx.yaml");

        FileChangeMonitor monitor = mock(FileChangeMonitor.class);
        registry = new FileBackedBackendServicesRegistry(delegate, monitor);

        CompletableFuture<Void> future = registry.start();
        await(future);

        verify(monitor).start(eq(registry));

        registry.fileChanged();
        verify(delegate, times(2)).reload();

        registry.fileChanged();
        verify(delegate, times(3)).reload();
    }

    @Test
    public void serviceStarts_logsInfoWhenReloadSucceeds() throws Exception {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.fileName()).thenReturn("/monitored/origins.yml");
        when(delegate.reload()).thenReturn(completedFuture(reloaded("md5-hash: 9034890345289043, Successfully reloaded")));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.startService().get();

        assertThat(log.log(), hasItem(
                loggingEvent(INFO, "Backend services reloaded. reason='Initial load', md5-hash: 9034890345289043, Successfully reloaded, file='/monitored/origins.yml'")
        ));
    }

    @Test(expectedExceptions = ExecutionException.class,
            expectedExceptionsMessageRegExp = "java.lang.RuntimeException: java.lang.RuntimeException: something went wrong")
    public void serviceStarts_failsToStartWhenReloadFails() throws Exception {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.fileName()).thenReturn("/monitored/origins.yml");
        when(delegate.reload()).thenReturn(
                completedFuture(failed(
                        "md5-hash: 9034890345289043, Failed to load file",
                        new RuntimeException("something went wrong"))));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.startService().get();
    }

    @Test
    public void serviceStarts_logsInfoWhenReloadFails() {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.fileName()).thenReturn("/monitored/origins.yml");
        when(delegate.reload()).thenReturn(
                completedFuture(failed(
                        "md5-hash: 9034890345289043, Failed to load file",
                        new RuntimeException("something went wrong"))));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);

        try {
            registry.startService().get();
        } catch (Throwable any) {
              // pass
        } finally {
            assertThat(log.lastMessage(), is(
                    loggingEvent(
                            ERROR,
                            "Backend services reload failed. reason='Initial load', md5-hash: 9034890345289043, Failed to load file, file='/monitored/origins.yml'",
                            RuntimeException.class,
                            "something went wrong")
            ));
        }
    }

    @Test
    public void reload_logsInfoWhenFileIsUnchanged() throws Exception {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.fileName()).thenReturn("/monitored/origins.yml");
        when(delegate.reload())
                .thenReturn(completedFuture(reloaded("md5-hash: 9034890345289043, Successfully reloaded")))
                .thenReturn(completedFuture(unchanged("md5-hash: 9034890345289043, No changes detected")));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.startService().get();

        registry.reload().get();
        assertThat(log.log(), hasItem(
                loggingEvent(INFO, "Backend services reloaded. reason='Admin Interface', md5-hash: 9034890345289043, No changes detected, file='/monitored/origins.yml'")
        ));
    }

    @Test
    public void reload_logsInfoWhenFailsToReadFile() throws Exception {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.fileName()).thenReturn("/monitored/origins.yml");
        when(delegate.reload())
                .thenReturn(completedFuture(reloaded("md5-hash: 9034890345289043, Successfully reloaded")))
                .thenReturn(completedFuture(failed("md5-hash: 9034890345289043, Failed to load file", new RuntimeException("error occurred"))));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.startService().get();

        registry.reload().get();
        assertThat(log.log(), hasItem(
                loggingEvent(ERROR, "Backend services reload failed. reason='Admin Interface', md5-hash: 9034890345289043, Failed to load file, file='/monitored/origins.yml'",
                        RuntimeException.class, "error occurred")
        ));
    }

    @Test
    public void reload_logsInfoOnSuccessfulReload() throws Exception {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.fileName()).thenReturn("/monitored/origins.yml");
        when(delegate.reload())
                .thenReturn(completedFuture(reloaded("md5-hash: 9034890345289043, Successfully reloaded")))
                .thenReturn(completedFuture(reloaded("md5-hash: 3428432789453897, Successfully reloaded")));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.startService().get();

        registry.reload().get();
        assertThat(log.log(), hasItem(
                loggingEvent(INFO, "Backend services reloaded. reason='Admin Interface', md5-hash: 3428432789453897, Successfully reloaded, file='/monitored/origins.yml'")
        ));
    }

    @Test
    public void fileChanged_logsInfoWhenFileIsUnchanged() throws Exception {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.fileName()).thenReturn("/monitored/origins.yml");
        when(delegate.reload())
                .thenReturn(completedFuture(reloaded("md5-hash: 9034890345289043, Successfully reloaded")))
                .thenReturn(completedFuture(unchanged("md5-hash: 9034890345289043, Unchanged")));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.startService().get();

        registry.fileChanged();
        assertThat(log.lastMessage(), is(
                loggingEvent(INFO, "Backend services reloaded. reason='File Monitor', md5-hash: 9034890345289043, Unchanged, file='/monitored/origins.yml'")
        ));
    }

    @Test
    public void fileChanged_logsInfoWhenFailsToReadFile() throws Exception {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.fileName()).thenReturn("/monitored/origins.yml");
        when(delegate.reload())
                .thenReturn(completedFuture(reloaded("md5-hash: 9034890345289043, Successfully reloaded")))
                .thenReturn(completedFuture(failed("md5-hash: 9034890345289043, Failed to reload", new RuntimeException("something went wrong"))));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.startService().get();

        registry.fileChanged();
        assertThat(log.lastMessage(), is(
                loggingEvent(ERROR, "Backend services reload failed. reason='File Monitor', md5-hash: 9034890345289043, Failed to reload, file='/monitored/origins.yml'",
                        RuntimeException.class, "something went wrong")
        ));
    }

    @Test
    public void fileChanged_logsInfoOnSuccessfulReload() throws Exception {
        FileBackedRegistry<BackendService> delegate = mock(FileBackedRegistry.class);
        when(delegate.fileName()).thenReturn("/monitored/origins.yml");
        when(delegate.reload())
                .thenReturn(completedFuture(reloaded("md5-hash: 9034890345289043, Successfully reloaded")))
                .thenReturn(completedFuture(reloaded("md5-hash: 3428432789453897, Successfully reloaded")));

        registry = new FileBackedBackendServicesRegistry(delegate, FileMonitor.DISABLED);
        registry.startService().get();

        registry.fileChanged();
        assertThat(log.lastMessage(), is(
                loggingEvent(INFO, "Backend services reloaded. reason='File Monitor', md5-hash: 3428432789453897, Successfully reloaded, file='/monitored/origins.yml'")
        ));
    }

    @Test
    public void duplicatePathPrefixesCausesReloadFailure() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        String configWithDupe = "" +
                "---\n" +
                "- id: \"first\"\n" +
                "  path: \"/testpath/\"\n" +
                "  origins:\n" +
                "  - id: \"l1\"\n" +
                "    host: \"localhost:60000\"\n" +
                "- id: \"second\"\n" +
                "  path: \"/testpath/\"\n" +
                "  origins:\n" +
                "  - id: \"l2\"\n" +
                "    host: \"localhost:60001\"";

        Resource stubResource = mock(Resource.class);
        when(stubResource.inputStream()).thenReturn(toInputStream(configWithDupe));

        FileBackedBackendServicesRegistry registry = new FileBackedBackendServicesRegistry(stubResource, FileMonitor.DISABLED);

        ReloadResult result = registry.reload().get(10, SECONDS);

        assertThat(result.outcome(), is(FAILED));
        assertThat(log.log(), hasItem(loggingEvent(ERROR, "Duplicate path '/testpath/' used for applications: 'first', 'second'")));
        assertThat(result.message(), is("timestamp=NA, md5-hash=3cf21842c8dee594b646ea903fc09490, Reload failure."));
    }

    @Test
    public void duplicatePathPrefixesCausesStartUpFailure() throws InterruptedException, TimeoutException, IOException {
        String configWithDupe = "" +
                "---\n" +
                "- id: \"first\"\n" +
                "  path: \"/testpath/\"\n" +
                "  origins:\n" +
                "  - id: \"l1\"\n" +
                "    host: \"localhost:60000\"\n" +
                "- id: \"second\"\n" +
                "  path: \"/testpath/\"\n" +
                "  origins:\n" +
                "  - id: \"l2\"\n" +
                "    host: \"localhost:60001\"";

        Resource stubResource = mock(Resource.class);
        when(stubResource.inputStream()).thenReturn(toInputStream(configWithDupe));

        FileBackedBackendServicesRegistry registry = new FileBackedBackendServicesRegistry(stubResource, FileMonitor.DISABLED);

        CompletableFuture<Void> status = registry.start();

        assertThat(failureCause(status), is(instanceOf(ServiceFailureException.class)));
    }

    @Test
    public void constraintAcceptsDistinctPaths() {
        Collection<BackendService> backendServices = ImmutableList.of(
                new BackendService.Builder()
                        .path("/foo")
                        .build(),
                new BackendService.Builder()
                        .path("/bar")
                        .build()
        );

        assertThat(new RejectDuplicatePaths().test(backendServices), is(true));
    }

    @Test
    public void constraintRejectsDuplicatePaths() {
        Collection<BackendService> backendServices = ImmutableList.of(
                new BackendService.Builder()
                        .id("app-a")
                        .path("/foo")
                        .build(),
                new BackendService.Builder()
                        .id("app-b")
                        .path("/foo")
                        .build(),
                new BackendService.Builder()
                        .id("app-c")
                        .path("/foo")
                        .build()
        );

        assertThat(new RejectDuplicatePaths().test(backendServices), is(false));
        assertThat(log.log(), hasItem(loggingEvent(ERROR, "Duplicate path '/foo' used for applications: 'app-a', 'app-b', 'app-c'")));
    }

    @Test
    public void constraintRejectsAndLogsMultipleDuplicatePaths() {
        Collection<BackendService> backendServices = ImmutableList.of(
                new BackendService.Builder()
                        .id("app-a")
                        .path("/foo")
                        .build(),
                new BackendService.Builder()
                        .id("app-b")
                        .path("/foo")
                        .build(),
                new BackendService.Builder()
                        .id("app-c")
                        .path("/foo")
                        .build(),
                new BackendService.Builder()
                        .id("app-x")
                        .path("/bar")
                        .build(),
                new BackendService.Builder()
                        .id("app-y")
                        .path("/bar")
                        .build()
        );

        assertThat(new RejectDuplicatePaths().test(backendServices), is(false));
        assertThat(log.log(), hasItem(loggingEvent(ERROR, "Duplicate path '/foo' used for applications: 'app-a', 'app-b', 'app-c', Duplicate path '/bar' used for applications: 'app-x', 'app-y'")));
    }

    private static Throwable failureCause(CompletableFuture<?> future) throws TimeoutException, InterruptedException {
        try {
            future.get(1, SECONDS);
            throw new IllegalStateException("Expected failure not present");
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    private static InputStream toInputStream(String string) {
        return new ByteArrayInputStream(string.getBytes(UTF_8));
    }

    private static CompletableFuture<ReloadResult> failedFuture(Throwable cause) {
        CompletableFuture<ReloadResult> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

}