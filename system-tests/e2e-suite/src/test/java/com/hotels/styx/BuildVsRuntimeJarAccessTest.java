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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.ExamplePluginJarLocation.createTemporarySharedDirectoryForJars;
import static com.hotels.styx.ExamplePluginJarLocation.exampleDependencyJarLocation;
import static com.hotels.styx.ExamplePluginJarLocation.examplePluginJarLocation;
import static java.lang.ClassLoader.getSystemClassLoader;
import static java.nio.file.Files.list;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

// This test is primarily ensuring that our supporting classes and modules are fit-for-purpose
// i.e. that our example plugin and its dependency are only accessible by legitimately loading their
// jar files and not just included in the e2e-suite module
public class BuildVsRuntimeJarAccessTest {
    @Test(expectedExceptions = ClassNotFoundException.class)
    public void pluginClassesAreNotAvailableViaSystemClassLoader() throws ClassNotFoundException {
        getSystemClassLoader().loadClass("testgrp.TestPlugin");
    }

    @Test
    public void pluginClassesAreAvailableWhenReferencingJarExplicitly() throws IOException, ClassNotFoundException {
        Path jarLocation = examplePluginJarLocation();

        URL[] urls = {
                jarLocation.toUri().toURL()
        };

        try (URLClassLoader classLoader = URLClassLoader.newInstance(urls)) {
            Class<?> aClass = classLoader.loadClass("testgrp.TestPlugin");

            assertThat(aClass.getName(), is("testgrp.TestPlugin"));
        }
    }

    @Test
    public void dependencyClassesAreAvailableWhenReferencingJarExplicitly() throws IOException, ClassNotFoundException {
        Path jarLocation = exampleDependencyJarLocation();

        URL[] urls = {
                jarLocation.toUri().toURL()
        };

        try (URLClassLoader classLoader = URLClassLoader.newInstance(urls)) {
            Class<?> aClass = classLoader.loadClass("depend.ExampleDependency");

            assertThat(aClass.getName(), is("depend.ExampleDependency"));
        }
    }

    @Test
    public void pluginAndDependencyClassesAreAvailableWhenReferencingDirectory() throws IOException, ClassNotFoundException {
        Path jarLocation = createTemporarySharedDirectoryForJars();

        URL[] urls = list(jarLocation)
                .map(BuildVsRuntimeJarAccessTest::url)
                .toArray(URL[]::new);

        try (URLClassLoader classLoader = URLClassLoader.newInstance(urls)) {
            Class<?> pluginClass = classLoader.loadClass("testgrp.TestPlugin");
            assertThat(pluginClass.getName(), is("testgrp.TestPlugin"));

            Class<?> dependencyClass = classLoader.loadClass("depend.ExampleDependency");
            assertThat(dependencyClass.getName(), is("depend.ExampleDependency"));
        }
    }

    private static URL url(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw propagate(e);
        }
    }
}
