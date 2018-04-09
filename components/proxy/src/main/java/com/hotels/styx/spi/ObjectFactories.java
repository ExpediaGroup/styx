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

import com.hotels.styx.api.Resource;
import com.hotels.styx.api.io.FileResource;
import com.hotels.styx.api.io.FileResourceIndex;
import com.hotels.styx.spi.config.SpiExtensionFactory;
import org.slf4j.Logger;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
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
        Iterable<ClassLoader> classLoaders = pathOptional
                .map(path -> makeClassLoadersFor(readResourcesFrom(path)))
                .orElseGet(() -> singleton(ObjectFactories.class.getClassLoader()));

        List<T> instances = newArrayList();
        for (ClassLoader classLoader : classLoaders) {
            try {
                Class<?> aClass = Class.forName(className, false, classLoader);
                T instance = type.cast(aClass.newInstance());
                instances.add(instance);
            } catch (ClassNotFoundException e) {
                LOGGER.debug(e.getMessage(), e);
            } catch (InstantiationException | IllegalAccessException e) {
                LOGGER.error("error loading class={}", className);
                return Optional.empty();
            }
        }

        int size = instances.size();
        if (size == 0) {
            LOGGER.error("no class={} is found in the specified classpath={}", className, pathOptional.orElse(Paths.get("")));
            return Optional.empty();
        }
        if (size > 1) {
            LOGGER.warn("expecting only one implementation but found={}", size);
        }
        return Optional.of(instances.get(0));
    }

    private static Iterable<ClassLoader> makeClassLoadersFor(Iterable<Resource> resources) {
        return stream(resources.spliterator(), false)
                .map(ObjectFactories::classLoader)
                .collect(toList());
    }

    private static Iterable<Resource> readResourcesFrom(Path path) {
        return isJarResource(path) ? readJarResources(path) : readFileResources(path);
    }

    private static boolean isJarResource(Path path) {
        return path.toFile().getName().endsWith(".jar");
    }

    private static Iterable<Resource> readJarResources(Path path) {
        return singleton(new FileResource(path.toFile()));
    }

    private static Iterable<Resource> readFileResources(Path path) {
        FileResourceIndex resourceIndex = new FileResourceIndex();
        return resourceIndex.list(path.toString(), ".jar");
    }

    private static URLClassLoader classLoader(Resource resource) {
        return new URLClassLoader(new URL[]{resource.url()}, ObjectFactories.class.getClassLoader());
    }

}
