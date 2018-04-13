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

import com.hotels.styx.spi.config.SpiExtensionFactory;
import com.hotels.styx.spi.newstuff.ClassSource;
import com.hotels.styx.spi.newstuff.ClassSourceLocator;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.String.format;

/**
 * TODO javadoc.
 */
public class DefaultExtensionClassLoader implements ExtensionClassLoader {
    @Override
    public Class<?> loadClass(SpiExtensionFactory extensionFactory) throws ClassLoadingException {
        ClassSource classSource;

        if (nonEmpty(extensionFactory.classPath())) {
            classSource = classSource(Paths.get(extensionFactory.classPath()));
        } else {
            classSource = ClassSource.fromClassLoader(DefaultExtensionClassLoader.class.getClassLoader());
        }

        try {
            return classSource.load(extensionFactory.factoryClass());
        } catch (ClassNotFoundException e) {
            throw new ClassLoadingException(classNotFoundMessage(extensionFactory), e);
        }
    }

    private static String classNotFoundMessage(SpiExtensionFactory extensionFactory) {
        String exceptionFormat = nonEmpty(extensionFactory.classPath())
                ? "no class=%s is found in the specified classpath=%s"
                : "no class=%s is found (no classpath specified)";

        return format(exceptionFormat, extensionFactory.factoryClass(), extensionFactory.classPath());
    }

    private static ClassSource classSource(Path classPath) {
        // replace this with mockable
        return ClassSourceLocator.JARS.classSource(classPath);
    }

    private static boolean nonEmpty(String classPath) {
        return classPath != null && !classPath.trim().isEmpty();
    }
}
