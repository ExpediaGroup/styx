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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashCode;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.extension.service.spi.AbstractRegistry;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.hash.HashCode.fromLong;
import static com.google.common.hash.Hashing.md5;
import static com.google.common.io.ByteStreams.toByteArray;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.failed;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.reloaded;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.unchanged;
import static java.lang.String.format;
import static java.nio.file.Files.getLastModifiedTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * File backed registry for {@code T}.
 *
 * @param <T> type of the resource
 */
public class FileBackedRegistry<T extends Identifiable> extends AbstractRegistry<T> {
    private static final Logger LOG = getLogger(FileBackedRegistry.class);
    private final Resource configurationFile;
    private final Reader<T> reader;
    private final Supplier<FileTime> modifyTimeSupplier;
    private HashCode fileHash = fromLong(0);

    private FileBackedRegistry(Resource configurationFile, Reader<T> reader, Supplier<FileTime> modifyTimeSupplier, Predicate<Collection<T>> resourceConstraint) {
        super(resourceConstraint);
        this.configurationFile = requireNonNull(configurationFile);
        this.reader = requireNonNull(reader);
        this.modifyTimeSupplier = modifyTimeSupplier;
    }

    @VisibleForTesting
    FileBackedRegistry(Resource configurationFile, Reader<T> reader, Supplier<FileTime> modifyTimeSupplier) {
        this(configurationFile, reader, modifyTimeSupplier, any -> true);
    }

    public FileBackedRegistry(Resource configurationFile, Reader<T> reader, Predicate<Collection<T>> resourceConstraint) {
        this(configurationFile, reader, fileModificationTimeProvider(configurationFile), resourceConstraint);
    }

    public String fileName() {
        return configurationFile.absolutePath();
    }

    @Override
    public CompletableFuture<ReloadResult> reload() {
        return supplyAsync(() -> {
            byte[] content = readFile();
            HashCode hashCode = md5().hashBytes(content);
            String modifyTime = fileModificationTime().map(FileTime::toString).orElse("NA");

            String logPrefix = format("timestamp=%s, md5-hash=%s", modifyTime, hashCode);

            if (hashCode.equals(fileHash)) {
                LOG.info("Not reloading {} as content did not change", configurationFile.absolutePath());
                return unchanged(format("%s, Identical file content.", logPrefix));
            } else {
                try {
                    boolean changesPerformed = updateResources(content, hashCode);

                    if (!changesPerformed) {
                        LOG.info("Not firing change event for {} as content was not semantically different", configurationFile.absolutePath());
                        return unchanged(format("%s, No semantic changes.", logPrefix));
                    } else {
                        LOG.debug("Changes applied!");
                        return reloaded(format("%s, File reloaded.", logPrefix));
                    }
                } catch (Exception e) {
                    // Eliminate unhelpful error message when resource constraint exception fails.
                    // This is a candidate for further refactoring.
                    if (!"Resource constraint failure".equals(e.getMessage())) {
                        LOG.error("Not reloading {} as there was an error reading content", configurationFile.absolutePath(), e);
                    }
                    return failed(format("%s, Reload failure.", logPrefix), e);
                }
            }
        }, newSingleThreadExecutor());
    }

    private static Supplier<FileTime> fileModificationTimeProvider(Resource path) {
        return () -> {
            try {
                return getLastModifiedTime(Paths.get(path.path()));
            } catch (Throwable cause) {
                throw new RuntimeException(cause);
            }
        };
    }

    private Optional<FileTime> fileModificationTime() {
        try {
            return Optional.of(modifyTimeSupplier.get());
        } catch (Throwable cause) {
            return Optional.empty();
        }
    }

    private boolean updateResources(byte[] content, HashCode hashCode) {
        Iterable<T> resources = reader.read(content);
        Changes<T> changes = changes(resources, get());

        if (!changes.isEmpty()) {
            set(resources);
        }

        fileHash = hashCode;
        return !changes.isEmpty();
    }

    private byte[] readFile() {
        try (InputStream configurationContent = configurationFile.inputStream()) {
            return toByteArray(configurationContent);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    /**
     * Reader.
     *
     * @param <T>
     */
    public interface Reader<T> {
        Iterable<T> read(byte[] content);
    }
}
