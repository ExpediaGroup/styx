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
package com.hotels.styx.common.io;

import java.io.File;

/**
 * A holder for java class package name.
 */
public final class PackageName {
    public static final String CLASSPATH_SCHEME = "classpath:";

    private final String rawValue;
    private final String value;

    public static PackageName packageName(String value) {
        return new PackageName(value, packageNameFrom(value));
    }

    private PackageName(String rawValue, String value) {
        this.rawValue = rawValue;
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String asClasspath() {
        return CLASSPATH_SCHEME + value.replace('.', '/').replace(File.separatorChar, '/');
    }

    private static String packageNameFrom(String value) {
        return isClasspathPath(value)
                ? stripClasspathPrefix(value)
                : value.replace('/', '.').replace('\\', '.');
    }

    private static boolean isClasspathPath(String path) {
        return path.startsWith(CLASSPATH_SCHEME);
    }

    private static String stripClasspathPrefix(String path) {
        return path.substring(CLASSPATH_SCHEME.length());
    }
}
