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
package com.hotels.styx.spi;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.io.FileResource;
import com.hotels.styx.api.io.FileResourceIndex;
import com.hotels.styx.spi.config.SpiExtensionFactory;
import org.slf4j.Logger;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Collections.singleton;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Loads a plugin from SpiExtensionFactory object.
 */
public final class ObjectFactories {
    private static final Logger LOGGER = getLogger(ObjectFactories.class);

    private ObjectFactories() {
    }

    public static <T> Optional<T> newInstance(SpiExtensionFactory classAndPath, Class<T> type) {
        return newInstance(type, classAndPath.factoryClass(), factoryClassPath(classAndPath.classPath()));
    }

    private static Optional<Path> factoryClassPath(String classPath) {
        return isNullOrEmpty(classPath) ? Optional.empty() : Optional.of(Paths.get(classPath));
    }

    private static <T> Optional<T> newInstance(Class<T> type, String className, Optional<Path> pathOptional) {
        ClassLoader classLoader = pathOptional
                .map(path -> classLoader(readResourcesFrom(path)))
                .map(ClassLoader.class::cast)
                .orElseGet(ObjectFactories.class::getClassLoader);

        try {
            Class<?> aClass = Class.forName(className, false, classLoader);
            T instance = type.cast(aClass.newInstance());
            return Optional.of(instance);
        } catch (ClassNotFoundException e) {
            LOGGER.error("no class={} is found in the specified classpath={}", className, pathOptional.orElse(Paths.get("")));
            return Optional.empty();
        } catch (InstantiationException | IllegalAccessException e) {
            LOGGER.error("error loading class={}", className);
            return Optional.empty();
        }
    }

    private static Collection<Resource> readResourcesFrom(Path path) {
        return isJarResource(path) ? readJarResources(path) : readFileResources(path);
    }

    private static boolean isJarResource(Path path) {
        return path.toFile().getName().endsWith(".jar");
    }

    private static Collection<Resource> readJarResources(Path path) {
        return singleton(new FileResource(path.toFile()));
    }

    private static Collection<Resource> readFileResources(Path path) {
        FileResourceIndex resourceIndex = new FileResourceIndex();
        return ImmutableList.copyOf(resourceIndex.list(path.toString(), ".jar"));
    }

    private static URLClassLoader classLoader(Collection<Resource> resource) {
        URL[] urls = resource.stream().map(Resource::url).toArray(URL[]::new);

        return new URLClassLoader(urls, ObjectFactories.class.getClassLoader());
    }
}
