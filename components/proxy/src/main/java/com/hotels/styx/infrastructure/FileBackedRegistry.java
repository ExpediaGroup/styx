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
package com.hotels.styx.infrastructure;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.Resource;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;
import static com.google.common.io.ByteStreams.toByteArray;
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
    private HashCode fileHash;

    public FileBackedRegistry(Resource configurationFile, Reader<T> reader) {
        this.configurationFile = checkNotNull(configurationFile);
        this.reader = checkNotNull(reader);
        this.fileHash = md5(configurationFile);
    }

    private HashCode md5(Resource resource) {
        try {
            return Files.hash(new File(resource.absolutePath()), Hashing.md5());
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    @Override
    protected void startUp() {
        LOG.info("starting {}", getClass().getSimpleName());

        Iterable<T> resources = reader.read(readFile());
        snapshot.set(resources);
        Changes<T> changes = new Changes.Builder<T>()
                .added(resources)
                .build();
        notifyListeners(changes);
    }

    @Override
    public synchronized void reload(ReloadListener listener) {
        if (!hasFileContentChanged()) {
            LOG.info("Not reloading {} as content did not change", configurationFile.absolutePath());
            listener.onNoMeaningfulChanges("file content did not change");
        } else {
            try {
                boolean changesPerformed = updateResources();

                if (!changesPerformed) {
                    LOG.info("Not firing change event for {} as content was not semantically different", configurationFile.absolutePath());
                    listener.onNoMeaningfulChanges("file content was not semantically different");
                } else {
                    LOG.debug("Changes applied!");
                    listener.onChangesApplied();
                }
            } catch (Exception e) {
                LOG.error("Not reloading {} as there was an error reading content", configurationFile.absolutePath(), e);
                notifyListenersOnError(e);
                listener.onErrorDuringReload(e);
            }
        }
    }

    private boolean updateResources() {
        Iterable<T> resources = reader.read(readFile());
        fileHash = md5(configurationFile);
        Changes<T> changes = changes(resources, snapshot.get());

        if (!changes.isEmpty()) {
            snapshot.set(resources);
            notifyListeners(changes);
        }

        return !changes.isEmpty();
    }

    private boolean hasFileContentChanged() {
        HashCode newFileHash = md5(configurationFile);

        return !newFileHash.equals(fileHash);
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
