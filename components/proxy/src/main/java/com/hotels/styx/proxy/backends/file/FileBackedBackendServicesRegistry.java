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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.infrastructure.FileBackedRegistry;
import com.hotels.styx.proxy.backends.file.FileChangeMonitor.FileMonitorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.common.io.ResourceFactory.newResource;
import static com.hotels.styx.api.extension.service.spi.Registry.Outcome.FAILED;
import static com.hotels.styx.applications.BackendServices.newBackendServices;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * File backed {@link BackendService} registry.
 */
public class FileBackedBackendServicesRegistry extends AbstractStyxService implements Registry<BackendService>, FileChangeMonitor.Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileBackedBackendServicesRegistry.class);
    private final FileBackedRegistry<BackendService> fileBackedRegistry;
    private final FileMonitor fileChangeMonitor;

    @VisibleForTesting
    FileBackedBackendServicesRegistry(FileBackedRegistry<BackendService> fileBackedRegistry, FileMonitor fileChangeMonitor) {
        super(format("FileBackedBackendServiceRegistry(%s)", fileBackedRegistry.fileName()));
        this.fileBackedRegistry = requireNonNull(fileBackedRegistry);
        this.fileChangeMonitor = requireNonNull(fileChangeMonitor);
    }

    @VisibleForTesting
    FileBackedBackendServicesRegistry(Resource originsFile, FileMonitor fileChangeMonitor) {
        this(new FileBackedRegistry<>(
                        originsFile,
                        new YAMLBackendServicesReader(),
                        new RejectDuplicatePaths()),
                fileChangeMonitor);
    }

    // Only used in OriginsHandlerTest, we need to refactor that test, since it should mock the registry instead
    public static FileBackedBackendServicesRegistry create(String originsFile) {
        return new FileBackedBackendServicesRegistry(newResource(originsFile), FileMonitor.DISABLED);
    }

    @Override
    public Registry<BackendService> addListener(ChangeListener<BackendService> changeListener) {
        return this.fileBackedRegistry.addListener(changeListener);
    }

    @Override
    public Registry<BackendService> removeListener(ChangeListener<BackendService> changeListener) {
        return this.fileBackedRegistry.removeListener(changeListener);
    }

    @Override
    public CompletableFuture<ReloadResult> reload() {
        return this.fileBackedRegistry.reload()
                .thenApply(outcome -> logReloadAttempt("Admin Interface", outcome));
    }

    @Override
    public Iterable<BackendService> get() {
        return this.fileBackedRegistry.get();
    }

    @Override
    protected CompletableFuture<Void> startService() {
        try {
            fileChangeMonitor.start(this);
        } catch (Exception e) {
            CompletableFuture<Void> x = new CompletableFuture<>();
            x.completeExceptionally(e);
            return x;
        }
        return this.fileBackedRegistry.reload()
                .thenApply(result -> logReloadAttempt("Initial load", result))
                .thenAccept(result -> {
                    if (result.outcome() == FAILED) {
                        throw new RuntimeException(result.cause().orElse(null));
                    }
                });
    }

    @Override
    public CompletableFuture<Void> stop() {
        return super.stop();
    }

    @VisibleForTesting
    FileMonitor monitor() {
        return fileChangeMonitor;
    }

    @Override
    public void fileChanged() {
        this.fileBackedRegistry.reload()
                .thenApply(outcome -> logReloadAttempt("File Monitor", outcome));
    }

    private ReloadResult logReloadAttempt(String reason, ReloadResult outcome) {
        String fileName = this.fileBackedRegistry.fileName();
        if (outcome.outcome() == Outcome.RELOADED || outcome.outcome() == Outcome.UNCHANGED) {
            LOGGER.info("Backend services reloaded. reason='{}', {}, file='{}'", new Object[]{reason, outcome.message(), fileName});
        } else if (outcome.outcome() == FAILED) {
            LOGGER.error("Backend services reload failed. reason='{}', {}, file='{}'",
                    new Object[]{reason, outcome.message(), fileName, outcome.cause().get()});
        }
        return outcome;
    }

    /**
     * Factory for creating a {@link FileBackedBackendServicesRegistry}.
     */
    public static class Factory implements Registry.Factory<BackendService> {

        @Override
        public Registry<BackendService> create(Environment environment, Configuration registryConfiguration) {
            String originsFile = registryConfiguration.get("originsFile", String.class)
                    .map(Factory::requireNonEmpty)
                    .orElseThrow(() -> new ConfigurationException(
                            "missing [services.registry.factory.config.originsFile] config value for factory class FileBackedBackendServicesRegistry.Factory"));

            FileMonitorSettings monitorSettings = registryConfiguration.get("monitor", FileMonitorSettings.class)
                    .orElse(new FileMonitorSettings());

            return registry(originsFile, monitorSettings);
        }

        private static Registry<BackendService> registry(String originsFile, FileMonitorSettings monitorSettings) {
            requireNonEmpty(originsFile);

            FileMonitor monitor = monitorSettings.enabled() ? new FileChangeMonitor(originsFile) : FileMonitor.DISABLED;
            Resource resource = newResource(originsFile);

            return new FileBackedBackendServicesRegistry(resource, monitor);
        }

        private static String requireNonEmpty(String originsFile) {
            if (originsFile.isEmpty()) {
                throw new ConfigurationException("empty [services.registry.factory.config.originsFile] config value for factory class FileBackedBackendServicesRegistry.Factory");
            } else {
                return originsFile;
            }
        }
    }

    @VisibleForTesting
    static class YAMLBackendServicesReader implements FileBackedRegistry.Reader<BackendService> {
        private static final ObjectMapper MAPPER = addStyxMixins(new ObjectMapper(new YAMLFactory()))
                .disable(FAIL_ON_UNKNOWN_PROPERTIES)
                .configure(AUTO_CLOSE_SOURCE, true);

        private static final TypeReference<List<BackendService>> TYPE = new TypeReference<List<BackendService>>() {
        };

        @Override
        public Iterable<BackendService> read(byte[] content) {
            try {
                JsonNode rootNode = MAPPER.readTree(content);

                List<BackendService> services = MAPPER.readValue(rootNode.traverse(), TYPE);

                return newBackendServices(services);
            } catch (Exception e) {
                throw propagate(e);
            }
        }
    }

    @VisibleForTesting
    static class RejectDuplicatePaths implements Predicate<Collection<BackendService>> {
        @Override
        public boolean test(Collection<BackendService> backendServices) {
            Map<String, List<BackendService>> pathToApp = backendServices.stream()
                    .collect(groupingBy(BackendService::path));

            List<List<BackendService>> duplicateApps = pathToApp.values()
                    .stream()
                    .filter(backends -> backends.size() > 1)
                    .collect(toList());

            if (duplicateApps.size() > 0) {
                LOGGER.error(errorMessage(duplicateApps));
            }

            return duplicateApps.size() == 0;
        }

        private static String errorMessage(List<List<BackendService>> backendServices) {
            return backendServices.stream()
                    .map(RejectDuplicatePaths::duplicatePathsErrorForPath)
                    .collect(joining(", "));
        }

        private static String duplicatePathsErrorForPath(List<BackendService> apps) {
            String path = apps.get(0).path();
            String appsList = apps.stream()
                    .map(app -> format("'%s'", app.id()))
                    .collect(joining(", "));

            return format("Duplicate path '%s' used for applications: %s", path, appsList);
        }
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("originsFileName", fileBackedRegistry.fileName())
                .toString();
    }
}
