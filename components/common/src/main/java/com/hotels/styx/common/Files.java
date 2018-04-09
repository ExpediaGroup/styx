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
package com.hotels.styx.common;

import com.google.common.hash.HashCode;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static com.google.common.hash.Hashing.md5;
import static com.google.common.io.ByteStreams.toByteArray;

public final class Files {
    private Files() {
    }

    public static HashCode fileContentMd5(Path path) {
        try (InputStream stream = new FileInputStream(path.toFile())) {
           return md5().hashBytes(toByteArray(stream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
