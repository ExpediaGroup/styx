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

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.spi.config.SpiExtensionFactory;

import java.nio.file.Paths;
import java.util.function.Function;

import static com.hotels.styx.spi.ClassSource.fromClassLoader;
import static com.hotels.styx.spi.ClassSourceLocator.JARS;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Instantiates objects defined in SPI extensions.
 */
public class ExtensionObjectFactory {
    public static final ExtensionObjectFactory EXTENSION_OBJECT_FACTORY = new ExtensionObjectFactory();

    private final Function<SpiExtensionFactory, ClassSource> extensionLocator;

    private ExtensionObjectFactory() {
        this(ExtensionObjectFactory::locateExtension);
    }

    @VisibleForTesting
    ExtensionObjectFactory(Function<SpiExtensionFactory, ClassSource> extensionLocator) {
        this.extensionLocator = requireNonNull(extensionLocator);
    }

    /**
     * Instantiates an object as defined by the extension factory, and casts it to the desired type.
     *
     * @param extensionFactory extension factory
     * @param type             type to cast to
     * @param <T>              type to cast to
     * @return instantiated object
     * @throws ExtensionLoadingException if it is not possible to instantiate the extension object
     */
    public <T> T newInstance(SpiExtensionFactory extensionFactory, Class<T> type) throws ExtensionLoadingException {
        try {
            Class<?> extensionClass = loadClass(extensionFactory);
            Object instance = extensionClass.newInstance();
            return type.cast(instance);
        } catch (ExtensionLoadingException e) {
            throw e;
        } catch (Exception e) {
            throw new ExtensionLoadingException("error instantiating class=" + extensionFactory.factoryClass(), e);
        }
    }

    private Class<?> loadClass(SpiExtensionFactory extensionFactory) throws ExtensionLoadingException {
        ClassSource classSource = extensionLocator.apply(extensionFactory);

        try {
            return classSource.load(extensionFactory.factoryClass());
        } catch (ClassNotFoundException e) {
            throw new ExtensionLoadingException(classNotFoundMessage(extensionFactory), e);
        }
    }

    private static ClassSource locateExtension(SpiExtensionFactory extensionFactory) {
        if (nonEmpty(extensionFactory.classPath())) {
            return JARS.classSource(Paths.get(extensionFactory.classPath()));
        }

        return fromClassLoader(ExtensionObjectFactory.class.getClassLoader());
    }

    private static String classNotFoundMessage(SpiExtensionFactory extensionFactory) {
        String exceptionFormat = nonEmpty(extensionFactory.classPath())
                ? "no class=%s is found in the specified classpath=%s"
                : "no class=%s is found (no classpath specified)";

        return format(exceptionFormat, extensionFactory.factoryClass(), extensionFactory.classPath());
    }

    private static boolean nonEmpty(String classPath) {
        return classPath != null && !classPath.trim().isEmpty();
    }
}
