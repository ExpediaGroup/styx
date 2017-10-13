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
package com.hotels.styx.infrastructure.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.io.FileResource;
import com.hotels.styx.api.io.FileResourceIndex;
import org.slf4j.Logger;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Factory for objects of a given class.
 */
public class ObjectFactory {
    private static final Logger LOG = getLogger(ObjectFactory.class);

    private final String factoryClass;
    private final String classPath;

    public ObjectFactory(@JsonProperty("class") String factoryClass,
                         @JsonProperty("classPath") String classPath) {
        this.factoryClass = checkNotNull(factoryClass);
        this.classPath = classPath;
    }

    public <T> Optional<T> newInstance(Class<T> type) {
        return newInstance(type, factoryClass, factoryClassPath(classPath));
    }

    private static Optional<Path> factoryClassPath(String classPath) {
        return isNullOrEmpty(classPath) ? Optional.empty() : Optional.of(Paths.get(classPath));
    }

    private static <T> Optional<T> newInstance(Class<T> type, String className, Optional<Path> pathOptional) {
        Iterable<ClassLoader> classLoaders = pathOptional
                .map(path -> makeClassLoadersFor(readResourcesFrom(path)))
                .orElseGet(() -> singleton(ObjectFactory.class.getClassLoader()));

        List<T> instances = newArrayList();
        for (ClassLoader classLoader : classLoaders) {
            try {
                Class<?> aClass = Class.forName(className, false, classLoader);
                T instance = type.cast(aClass.newInstance());
                instances.add(instance);
            } catch (ClassNotFoundException e) {
                LOG.debug("", e);
            } catch (InstantiationException | IllegalAccessException e) {
                LOG.error("error loading class={}", className);
                return Optional.empty();
            }
        }

        int size = instances.size();
        if (size == 0) {
            LOG.error("no class={} is found in the specified classpath={}", className, pathOptional.orElse(Paths.get("")));
            return Optional.empty();
        }
        if (size > 1) {
            LOG.warn("expecting only one implementation but found={}", size);
        }
        return Optional.of(instances.get(0));
    }

    private static Iterable<ClassLoader> makeClassLoadersFor(Iterable<Resource> resources) {
        return stream(resources.spliterator(), false)
                .map(ObjectFactory::classLoader)
                .collect(toList());
    }

    private static Iterable<Resource> readResourcesFrom(Path path) {
        return isJarResource(path) ? readJarResources(path) : readFileResources(path);
    }

    private static boolean isJarResource(Path path) {
        return path.toFile().getName().endsWith(".jar");
    }

    private static Iterable<Resource> readJarResources(Path path) {
        return singleton((Resource) new FileResource(path.toFile()));
    }

    private static Iterable<Resource> readFileResources(Path path) {
        FileResourceIndex resourceIndex = new FileResourceIndex();
        return resourceIndex.list(path.toString(), ".jar");
    }

    private static URLClassLoader classLoader(Resource resource) {
        return new URLClassLoader(new URL[]{resource.url()}, ObjectFactory.class.getClassLoader());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(factoryClass, classPath);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ObjectFactory other = (ObjectFactory) obj;
        return Objects.equal(this.factoryClass, other.factoryClass)
                && Objects.equal(this.classPath, other.classPath);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("factoryClass", factoryClass)
                .add("classPath", classPath)
                .toString();
    }
}
