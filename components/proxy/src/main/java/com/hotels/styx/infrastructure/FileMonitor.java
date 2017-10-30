/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.infrastructure;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Monitors a file to load the content, check for changes and keep a record of the current and previous state.
 */
public class FileMonitor {
    private final Function<String, byte[]> byteLoader;
    private final String absolutePath;

    private HashCode fileHash;
    private byte[] previousBytes;
    private byte[] currentBytes;

    FileMonitor(Function<String, byte[]> byteLoader, String absolutePath) {
        this.byteLoader = requireNonNull(byteLoader);
        this.absolutePath = requireNonNull(absolutePath);
    }

    public boolean load() {
        previousBytes = currentBytes;
        currentBytes = byteLoader.apply(absolutePath);

        HashCode newFileHash = Hashing.md5().hashBytes(currentBytes);
        boolean contentHasChanged = !newFileHash.equals(fileHash);
        fileHash = newFileHash;
        return contentHasChanged;
    }

    byte[] bytes() {
        return currentBytes;
    }

    byte[] previousBytes() {
        return previousBytes;
    }

    public String path() {
        return absolutePath;
    }
}
