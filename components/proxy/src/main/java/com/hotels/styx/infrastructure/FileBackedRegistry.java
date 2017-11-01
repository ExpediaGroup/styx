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

import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.Resource;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.function.Function;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.io.ByteStreams.toByteArray;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * File backed registry for {@code T}.
 *
 * @param <T> type of the resource
 */
public class FileBackedRegistry<T extends Identifiable> extends AbstractRegistry<T> {
    private static final Logger LOG = getLogger(FileBackedRegistry.class);
    private final Parser<T> parser;
    private final FileMonitor fileMonitor;

    public FileBackedRegistry(Resource configurationFile, Parser<T> parser) {
        this(parser, configurationFile.absolutePath(), path -> {
            try {
                return toByteArray(configurationFile.inputStream());
            } catch (IOException e) {
                throw propagate(e);
            }
        });
    }

    FileBackedRegistry(Parser<T> parser, String absolutePath, Function<String, byte[]> byteLoader) {
        this.fileMonitor = new FileMonitor(byteLoader, absolutePath);
        this.parser = requireNonNull(parser);
    }

    @Override
    protected void startUp() {
        LOG.info("starting {}", getClass().getSimpleName());

        fileMonitor.load();

        Iterable<T> resources = parser.read(fileMonitor.bytes());
        snapshot.set(resources);
        Changes<T> changes = new Changes.Builder<T>()
                .added(resources)
                .build();
        notifyListeners(changes);
    }

    @Override
    public synchronized void reload(ReloadListener listener) {
        if (!fileMonitor.load()) {
            LOG.info("Not reloading {} as content did not change", fileMonitor.path());
            listener.onNoMeaningfulChanges("file content did not change");
        } else {
            try {
                boolean changesPerformed = updateResources();

                if (!changesPerformed) {
                    LOG.info("Not firing change event for {} as content was not semantically different", fileMonitor.path());
                    listener.onNoMeaningfulChanges("file content was not semantically different");
                } else {
                    LOG.debug("Changes applied!");
                    listener.onChangesApplied();
                }
            } catch (Exception e) {
                LOG.error("Not reloading {} as there was an error reading content", fileMonitor.path(), e);
                notifyListenersOnError(e);
                listener.onErrorDuringReload(e);
            }
        }
    }

    private boolean updateResources() {
        Iterable<T> resources = parser.read(fileMonitor.bytes());
        Changes<T> changes = changes(resources, snapshot.get());

        if (!changes.isEmpty()) {
            snapshot.set(resources);

            try {
                notifyListeners(changes);
            } catch (Exception e) {
                return throwReloadException(e);
            }
        }

        return !changes.isEmpty();
    }

    // return type can be anything because it never returns normally
    private <X> X throwReloadException(Exception e) {
        String message = format("Exception during reload: %s : previousFileContent=%s, newFileContent=%s",
                e.getMessage(),
                new String(fileMonitor.previousBytes()),
                new String(fileMonitor.bytes()));

        throw new ReloadException(message, e);
    }

    /**
     * Parses file contents into the element type.
     *
     * @param <T> element type
     */
    public interface Parser<T> {
        Iterable<T> read(byte[] content);
    }
}
