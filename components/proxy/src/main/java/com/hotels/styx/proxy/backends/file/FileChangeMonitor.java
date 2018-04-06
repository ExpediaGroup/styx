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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.hotels.styx.common.Files.fileContentMd5;
import static java.lang.String.format;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.getLastModifiedTime;
import static java.nio.file.Files.isReadable;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Monitors a file system object and notifies the consumer of any changes.
 */
class FileChangeMonitor implements FileMonitor {
    private final Path monitoredFile;
    private final ScheduledExecutorService executor = newSingleThreadScheduledExecutor();
    private final long pollPeriod;
    private final TimeUnit timeUnit;

    private final AtomicReference<FileTime> lastChangedTime = new AtomicReference<>(FileTime.fromMillis(0));
    private final AtomicReference<HashCode> hashCode = new AtomicReference<>();

    private volatile boolean performHashCheck;
    private ScheduledFuture<?> monitoredTask;

    private static final Logger LOGGER = LoggerFactory.getLogger(FileChangeMonitor.class);

    @VisibleForTesting
    FileChangeMonitor(String monitoredFile, long pollPeriod, TimeUnit timeUnit) {
        requireExists(requireNonNull(monitoredFile));
        this.monitoredFile = Paths.get(monitoredFile);
        this.pollPeriod = pollPeriod;
        this.timeUnit = requireNonNull(timeUnit);
        this.hashCode.set(HashCode.fromLong(0));
    }

    public FileChangeMonitor(String monitoredFile) {
        this(monitoredFile, 1, SECONDS);
    }

    @Override
    public void start(Listener listener) {
        synchronized (this) {
            if (monitoredTask != null) {
                String message = format("File monitor for '%s' is already started", monitoredFile);
                throw new IllegalStateException(message);
            }

            monitoredTask = executor.scheduleAtFixedRate(detectFileChangesTask(listener), pollPeriod, pollPeriod, timeUnit);
        }
    }

    public void stop() {
        if (monitoredTask != null) {
            monitoredTask.cancel(true);
        }
    }

    private Runnable detectFileChangesTask(Listener listener) {
        return () -> {
            if (!exists(monitoredFile)) {
                LOGGER.debug("Monitored file does not exist. Path={}", monitoredFile);

            } else if (!isReadable(monitoredFile)) {
                LOGGER.debug("Monitored file is no longer readable. Path={}", monitoredFile);

            } else if (modificationTimeChanged(monitoredFile)) {
                hashCode.set(fileContentMd5(monitoredFile));
                performHashCheck = true;
                listener.fileChanged();

            } else if (performHashCheck && contentHashChanged(monitoredFile)) {
                listener.fileChanged();
            }
        };
    }

    private boolean modificationTimeChanged(Path monitoredFile) {
        try {
            FileTime current = getLastModifiedTime(monitoredFile);
            FileTime previous = lastChangedTime.getAndSet(current);
            boolean changed = !previous.equals(current);

            LOGGER.debug("modificationTimeChange probe. Changed={}", changed);
            return changed;
        } catch (IOException e) {
            String message = format("Cannot get modification time for Path=%s", monitoredFile);
            throw new RuntimeException(message, e);
        }
    }

    private boolean contentHashChanged(Path monitoredFile) {
        HashCode newHashCode = fileContentMd5(monitoredFile);
        boolean changed = !hashCode.getAndSet(newHashCode).equals(newHashCode);

        LOGGER.debug("contentHashChanged probe. Changed={}", changed);
        return changed;
    }

    private static void requireExists(String path) {
        if (!isReadable(Paths.get(path))) {
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

        boolean enabled() {
            return enabled;
        }
    }

}
