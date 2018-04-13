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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

import static com.hotels.styx.spi.ClassSource.fromClassLoader;
import static java.lang.ClassLoader.getSystemClassLoader;

/**
 * A class source that looks into resources for JAR files.
 */
class JarsClassSource implements ClassSource {
    private final ClassSource classSource;

    JarsClassSource(Collection<Resource> jars) {
        URL[] urls = jars.stream().map(Resource::url).toArray(URL[]::new);

        ClassLoader classLoader = new URLClassLoader(urls, getSystemClassLoader());

        this.classSource = fromClassLoader(classLoader);
    }

    @Override
    public Class<?> load(String name) throws ClassNotFoundException {
        return classSource.load(name);
    }
}
