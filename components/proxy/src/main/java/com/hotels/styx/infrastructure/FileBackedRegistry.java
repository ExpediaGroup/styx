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

import com.google.common.hash.HashCode;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.Resource;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;
import static com.google.common.hash.HashCode.fromLong;
import static com.google.common.hash.Hashing.md5;
import static com.google.common.io.ByteStreams.toByteArray;
import static com.hotels.styx.infrastructure.Registry.ReloadResult.reloaded;
import static com.hotels.styx.infrastructure.Registry.ReloadResult.unchanged;
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
    private HashCode fileHash = fromLong(0);

    public FileBackedRegistry(Resource configurationFile, Reader<T> reader) {
        this.configurationFile = requireNonNull(configurationFile);
        this.reader = checkNotNull(reader);
    }

    public String fileName() {
        return configurationFile.absolutePath();
    };

    @Override
    public CompletableFuture<ReloadResult> reload() {
        return supplyAsync(() -> {
            byte[] content = readFile();
            HashCode hashCode = md5().hashBytes(content);

            if (hashCode.equals(fileHash)) {
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
        try {
            return toByteArray(configurationFile.inputStream());
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
