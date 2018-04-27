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
package com.hotels.styx;

import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.ClassLoader.getSystemClassLoader;
import static java.nio.file.Files.list;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

// TODO fix name
// The goal here is to ensure that plugin classes cannot be found in the system class loader
// So that we know the plugin loading actually works.
public class PluginsNotCheatingTest {
    @Test(expectedExceptions = ClassNotFoundException.class)
    public void pluginClassesAreNotAvailableViaSystemClassLoader() throws ClassNotFoundException {
        getSystemClassLoader().loadClass("testgrp.TestPlugin");
    }

    @Test
    public void pluginClassesAreAvailableWhenReferencingJarExplicitly() throws IOException, ClassNotFoundException {
        Path jarLocation = getJarLocation();

        URL[] urls = {
                jarLocation.toUri().toURL()
        };

        try (URLClassLoader classLoader = URLClassLoader.newInstance(urls)) {
            Class<?> aClass = classLoader.loadClass("testgrp.TestPlugin");

            assertThat(aClass.getName(), is("testgrp.TestPlugin"));
        }
    }

    private static Path getJarLocation() throws IOException {
        Path systemRoot = Paths.get("/");
        Path classPathRoot = Paths.get(getSystemClassLoader().getResource("").getFile());

        Path parent = systemRoot
                .resolve(classPathRoot.subpath(0, classPathRoot.getNameCount() - 3))
                .resolve("example-styx-plugin/target/");

        return list(parent)
                .filter(file -> file.toString().endsWith(".jar"))
                .filter(file -> !file.toString().contains("-sources"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot find any JAR at the specified location"));
    }
}
