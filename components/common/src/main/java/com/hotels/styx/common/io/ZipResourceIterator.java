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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Iterates over resources in a JAR.
 */
public class ZipResourceIterator implements Iterator<Resource> {
    private final String path;
    private final String suffix;
    private final ZipFile jarFile;
    private final Enumeration<? extends ZipEntry> entries;
    private Resource next;

    public ZipResourceIterator(String zipPath, String path, String suffix) throws IOException {
        this.path = path;
        this.suffix = suffix;
        this.jarFile = new ZipFile(zipPath);
        this.entries = jarFile.entries();

        moveToNext();
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public Resource next() {
        try {
            if (next == null) {
                throw new NoSuchElementException();
            }
            return next;
        } finally {
            moveToNext();
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    private void moveToNext() {
        next = null;
        while (entries.hasMoreElements()) {
            ZipEntry jarEntry = entries.nextElement();
            String entryName = jarEntry.getName();
            if (entryName.startsWith(path) && hasSuffix(suffix, entryName)) {
                next = new ZipResource(jarFile, jarEntry);
                break;
            }
        }
    }

    private static boolean hasSuffix(String suffix, String name) {
        return suffix == null || name.endsWith(suffix);
    }

}
