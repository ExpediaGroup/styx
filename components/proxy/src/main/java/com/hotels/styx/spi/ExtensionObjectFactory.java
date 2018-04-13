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

import static java.util.Objects.requireNonNull;

/**
 * TODO javadoc.
 */
public class ExtensionObjectFactory {
    private final ExtensionClassLoader extensionClassLoader;

    @VisibleForTesting
    public ExtensionObjectFactory(ExtensionClassLoader extensionClassLoader) {
        this.extensionClassLoader = requireNonNull(extensionClassLoader);
    }

    public ExtensionObjectFactory() {
        this(new DefaultExtensionClassLoader());
    }

    public <T> T newInstance(SpiExtensionFactory extensionFactory, Class<T> type) {
        try {
            Class<?> extensionClass = extensionClassLoader.loadClass(extensionFactory);
            Object instance = extensionClass.newInstance();
            return type.cast(instance);
        } catch (Exception e) {
            throw new ClassLoadingException("error loading class=" + extensionFactory.factoryClass(), e);
        }
    }
}
