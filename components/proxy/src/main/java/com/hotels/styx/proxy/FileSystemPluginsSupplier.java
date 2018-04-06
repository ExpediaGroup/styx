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
package com.hotels.styx.proxy;

import com.google.common.collect.Iterables;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.io.FileResourceIndex;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import org.slf4j.Logger;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.size;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A {@cod Plugin.Factory} supplier that loads the plugins from a file system.
 *
 */
public class FileSystemPluginsSupplier implements Supplier<Iterable<PluginFactory>> {
    private static final Logger LOG = getLogger(FileSystemPluginsSupplier.class);

    private final Iterable<Resource> jarFiles;

    public FileSystemPluginsSupplier(Iterable<Resource> jarFiles) {
        this.jarFiles = jarFiles;
    }

    public static FileSystemPluginsSupplier fromDirectoryListing(Path pluginsFolder) {
        FileResourceIndex resourceIndex = new FileResourceIndex();
        Iterable<Resource> jars = resourceIndex.list(pluginsFolder.toString(), ".jar");
        return new FileSystemPluginsSupplier(jars);
    }

    @Override
    public Iterable<PluginFactory> get() {
        LOG.info("Found {} plugins", Iterables.toString(jarFiles));
        return stream(jarFiles.spliterator(), false)
                .map(this::resourceClassLoader)
                .filter(this::isPluginJar)
                .map(this::loadPluginFactory)
                .collect(toList());
    }

    private URLClassLoader resourceClassLoader(Resource jar) {
        return new URLClassLoader(new URL[]{jar.url()});
    }

    private boolean isPluginJar(ClassLoader classLoader) {
        Iterable<PluginFactory> plugins = ServiceLoader.load(PluginFactory.class, classLoader);

        return size(plugins) == 1;
    }

    private PluginFactory loadPluginFactory(ClassLoader classLoader) {
        LOG.info("cls={}", classLoader);
        Iterable<PluginFactory> plugins = ServiceLoader.load(PluginFactory.class, classLoader);
        if (size(plugins) != 1) {
            throw new RuntimeException("Jars can only contain exactly one Plugin.Factory class. Current list is " + Iterables.toString(plugins));
        }
        return getOnlyElement(plugins);
    }
}
