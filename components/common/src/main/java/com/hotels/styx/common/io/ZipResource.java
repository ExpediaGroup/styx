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

import com.hotels.styx.api.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.Objects.requireNonNull;

/**
 * A resource inside a JAR.
 */
public class ZipResource implements Resource {
    private final ZipFile jarFile;
    private final ZipEntry jarEntry;

    public ZipResource(ZipFile jarFile, ZipEntry jarEntry) {
        this.jarFile = requireNonNull(jarFile);
        this.jarEntry = requireNonNull(jarEntry);
    }

    @Override
    public String path() {
        return jarEntry.getName();
    }

    @Override
    public URL url() {
        return null;
    }

    @Override
    public String absolutePath() {
        return jarFile.getName() + "!/" + path();
    }

    @Override
    public InputStream inputStream() throws IOException {
        return jarFile.getInputStream(jarEntry);
    }

    @Override
    public ClassLoader classLoader() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return absolutePath();
    }
}
