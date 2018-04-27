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
import static java.nio.file.Files.list;

/**
 * Use to find the JAR containing the TestPlugin.
 */
public final class ExamplePluginJarLocation {
    private ExamplePluginJarLocation() {
    }

    public static Path examplePluginJarLocation() throws IOException {
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
