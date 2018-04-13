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

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.ClassLoader.getSystemClassLoader;
import static java.lang.String.format;
import static java.util.Collections.singleton;

/**
 * Loads a plugin from SpiExtensionFactory object.
 */
public final class ObjectFactories {
    private ObjectFactories() {
    }

    public static <T> T newInstance(SpiExtensionFactory classAndPath, Class<T> type) {
        return newInstance(type, classAndPath.factoryClass(), factoryClassPath(classAndPath.classPath()));
    }

    private static Optional<Path> factoryClassPath(String classPath) {
        return isNullOrEmpty(classPath) ? Optional.empty() : Optional.of(Paths.get(classPath));
    }

    private static <T> T newInstance(Class<T> type, String className, Optional<Path> pathOptional) {
        ClassLoader classLoader = classLoader(pathOptional);

        try {
            Class<?> aClass = Class.forName(className, false, classLoader);

            return type.cast(aClass.newInstance());
        } catch (ClassNotFoundException e) {
            String exceptionMessage = pathOptional
                    .map(classPath ->
                            format("no class=%s is found in the specified classpath=%s", className, classPath))
                    .orElse(format("no class=%s is found (no classpath specified)", className));

            throw new ClassLoadingException(exceptionMessage, e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ClassLoadingException("error loading class=" + className, e);
        }
    }

    private static ClassLoader classLoader(Optional<Path> pathOptional) {
        return pathOptional
                .map(path -> classLoader(readResourcesFrom(path)))
                .orElseGet(ObjectFactories.class::getClassLoader);
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

    private static ClassLoader classLoader(Collection<Resource> resource) {
        URL[] urls = resource.stream().map(Resource::url).toArray(URL[]::new);

        return new URLClassLoader(urls, getSystemClassLoader());
    }
}
