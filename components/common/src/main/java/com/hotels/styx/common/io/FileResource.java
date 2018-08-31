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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Resource implementation for {@link File} handles.
 */
public class FileResource implements Resource {
    public static final String FILE_SCHEME = "file:";

    private final File root;
    private final File file;

    public FileResource(File file) {
        this(file, file);
    }

    public FileResource(File root, File file) {
        this.root = root;
        this.file = file;
        if (!file.getAbsolutePath().startsWith(root.getAbsolutePath())) {
            throw new IllegalArgumentException(file.getAbsolutePath() + " is not a parent of " + root.getAbsolutePath());
        }
    }

    public FileResource(String path) {
        this(new File(path.replace(FILE_SCHEME, "")));
    }

    @Override
    public String path() {
        if (file.equals(root)) {
            return file.getPath();
        }
        return file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
    }

    @Override
    public URL url() {
        try {
            return getFile().toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String absolutePath() {
        return file.getAbsolutePath();
    }

    @Override
    public InputStream inputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public ClassLoader classLoader() {
        throw new UnsupportedOperationException();
    }

    public File getFile() {
        return file;
    }

    @Override
    public String toString() {
        return absolutePath();
    }
}
