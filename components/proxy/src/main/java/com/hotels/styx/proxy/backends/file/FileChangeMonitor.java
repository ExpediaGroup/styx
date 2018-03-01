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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Monitors a file system object and notifies the consumer of any changes.
 */
public class FileChangeMonitor implements FileMonitor {
    private final Path monitoredFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final Logger LOGGER = LoggerFactory.getLogger(FileChangeMonitor.class);

    public FileChangeMonitor(String monitoredFile) {
        requireExists(requireNonNull(monitoredFile));
        this.monitoredFile = Paths.get(monitoredFile);
    }

    @Override
    public void start(Listener listener) {
        WatchService watcher = newWatchService();
        WatchKey watchKey = register(this.monitoredFile.getParent(), watcher);

        executor.submit(() -> {
            for (;;) {
                WatchKey key = watcherPoll(watcher, watchKey, 1000, MILLISECONDS);
                if (key == null) {
                    continue;
                }

                LOGGER.debug("key.pollEvents()");
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    LOGGER.debug("Event detected: {}", kind);

                    if (kind == OVERFLOW) {
                        continue;
                    }

                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    if (this.monitoredFile.getFileName().equals(filename)) {
                        LOGGER.info("Monitored file changed");
                        listener.fileChanged();
                    }
                }

                if (!key.reset()) {
                    return;
                }
            }
        });
    }

    private static WatchService newWatchService() {
        try {
            return FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private WatchKey register(Path target, WatchService watcher) {
        try {
            return target.register(watcher, OVERFLOW, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private WatchKey watcherPoll(WatchService watcher, WatchKey watchKey, int timeout, TimeUnit timeUnit) {
        try {
            LOGGER.debug("watcher.poll()");
            return watcher.poll(timeout, timeUnit);
        } catch (InterruptedException e) {
            watchKey.cancel();
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void requireExists(String path) {
        Paths.get(path);

        if (!Files.isReadable(Paths.get(path))) {
            throw new IllegalArgumentException(format("File '%s' does not exist or is not readable.", path));
        }
    }

    @VisibleForTesting
    static class FileMonitorSettings {
        private final boolean enabled;

        @JsonCreator
        FileMonitorSettings(@JsonProperty("enabled") boolean enabled) {
            this.enabled = enabled;
        }

        FileMonitorSettings() {
            this(false);
        }

        public boolean enabled() {
            return enabled;
        }
    }

}
