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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.ClassLoader.getSystemClassLoader;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.list;

/**
 * Use to find the JAR containing the TestPlugin.
 */
public final class ExamplePluginJarLocation {
    private ExamplePluginJarLocation() {
    }

    public static Path createTemporarySharedDirectoryForJars() throws IOException {
        Path tempDirectory = createTempDirectory("styx-plugin-test-jars-");
        tempDirectory.toFile().deleteOnExit();

        Path plugin = examplePluginJarLocation();
        Path dependency = exampleDependencyJarLocation();

        copy(plugin, tempDirectory.resolve(plugin.toFile().getName()));
        copy(dependency, tempDirectory.resolve(dependency.toFile().getName()));

        return tempDirectory;
    }

    static Path examplePluginJarLocation() throws IOException {
        return jarLocation("example-styx-plugin");
    }

    static Path exampleDependencyJarLocation() throws IOException {
        return jarLocation("example-styx-plugin-dependencies");
    }

    // module must be adjacent to e2e-suite
    private static Path jarLocation(String module) throws IOException {
        Path parent = modulesDirectory()
                .resolve(module)
                .resolve("target");

        return list(parent)
                .filter(file -> file.toString().endsWith(".jar"))
                .filter(file -> !file.toString().contains("-sources"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot find any JAR at the specified location"));
    }

    private static Path modulesDirectory() {
        return classPathRoot().getParent().getParent().getParent();
    }

    private static Path classPathRoot() {
        return Paths.get(getSystemClassLoader().getResource("").getFile());
    }
}
