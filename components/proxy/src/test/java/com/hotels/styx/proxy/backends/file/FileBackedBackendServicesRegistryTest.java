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
package com.hotels.styx.proxy.backends.file;

import com.google.common.collect.Iterables;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Resource;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.infrastructure.Registry.ReloadListener;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static com.google.common.io.Files.copy;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistryTest.ChangeType.ADD;
import static com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistryTest.ChangeType.REMOVE;
import static com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistryTest.ChangeType.UPDATE;
import static java.lang.String.format;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class FileBackedBackendServicesRegistryTest {
    File fixturesPath = new File(newResource("classpath:/").absolutePath());
    File tempDirectory = new File(toFile("/backends"), "generated");

    Registry.ChangeListener<BackendService> changeListener;
    FileBackedBackendServicesRegistry registry;
    ReloadListener reloadListener;

    @BeforeMethod
    public void createTempDirectory() throws IOException {
        if (tempDirectory.exists()) {
            deleteFile(tempDirectory);
        }
        tempDirectory.mkdirs();

        changeListener = mock(Registry.ChangeListener.class);
        reloadListener = mock(ReloadListener.class);

        registry = new FileBackedBackendServicesRegistry(givenOrigins("/backends/origins.yml"));
        registry.addListener(changeListener);
        registry.startAsync().awaitRunning();
    }

    @Test
    public void firesChangeEventForInitialState() {
        verify(changeListener).onChange(anyChanges());
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Expected the service to be RUNNING, but the service has FAILED")
    public void discardsInvalidPaths() throws IOException {
        registry = new FileBackedBackendServicesRegistry(givenOrigins("/backends/origins-with-invalid-path.yml"));
        registry.addListener(changeListener);
        registry.startAsync().awaitRunning();
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Expected the service to be RUNNING, but the service has FAILED")
    public void discardsInvalidHealthCheckURIs() throws IOException {
        registry = new FileBackedBackendServicesRegistry(givenOrigins("/backends/origins-with-invalid-healthcheck-uri.yml"));
        registry.addListener(changeListener);
        registry.startAsync().awaitRunning();
    }

    @Test
    public void firesChangeEventWhenBackendIsUpdated() throws IOException {
        givenOrigins("/backends/origins-webapp-origin-removed.yml");
        reload();
        verify(reloadListener).onChangesApplied();
        verify(changeListener).onChange(updated("webapp"));
    }

    @Test
    public void firesChangeEventWhenBackendOriginIdsAreUpdated() throws IOException {
        givenOrigins("/backends/origins-id-changed.yml");
        reload();
        verify(reloadListener).onChangesApplied();
        verify(changeListener).onChange(updated("webapp"));
    }

    @Test
    public void firesChangeEventWhenBackendOriginHostsAreUpdated() throws IOException {
        givenOrigins("/backends/origins-host-changed.yml");
        reload();
        verify(reloadListener).onChangesApplied();
        verify(changeListener).onChange(updated("webapp"));
    }

    @Test
    public void doesNotFireChangeEventWhenFileIsNotChanged() {
        reload();
        verify(reloadListener).onNoMeaningfulChanges(any(String.class));
        verifyNoMoreInteractions(changeListener);
    }

    @Test
    public void doesNotFireChangeEventWhenFileChangesDoNotChangeConfiguration() throws IOException {
        givenOrigins("/backends/origins-comments-changed.yml");
        reload();
        verify(reloadListener).onNoMeaningfulChanges(any(String.class));
        verifyNoMoreInteractions(changeListener);
    }

    @Test
    public void firesChangeEventWhenBackendIsRemoved() throws IOException {
        givenOrigins("/backends/origins-landing-removed.yml");
        reload();
        verify(reloadListener).onChangesApplied();
        verify(changeListener).onChange(removed("landing"));
    }

    @Test
    public void firesChangeEventWhenBackendIsAdded() throws IOException {
        givenOrigins("/backends/origins-pd-app-added.yml");
        reload();
        verify(reloadListener).onChangesApplied();
        verify(changeListener).onChange(added("product-details"));
    }

    private void reload() {
        reset(changeListener);
        registry.reload(reloadListener);
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

    void deleteFile(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                deleteFile(f);
            }
        }

        file.delete();
    }
}