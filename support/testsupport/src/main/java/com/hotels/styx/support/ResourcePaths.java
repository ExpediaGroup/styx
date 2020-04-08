/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.support;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ResourcePaths {

    private ResourcePaths() {
    }

    public static String fixturesHome() {
        try {
            return Paths.get(ResourcePaths.class.getResource("/").toURI()).toString() + File.separator;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


    public static Path fixturesHome(Class clazz, String path) {
        try {
            return Paths.get(clazz.getResource(path).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
