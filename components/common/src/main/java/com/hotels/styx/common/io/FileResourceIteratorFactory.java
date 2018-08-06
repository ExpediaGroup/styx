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
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Iterator;

/**
 * Factory which creates {@link FileResourceIterator}s.
 * <p/>
 * <p>{@link FileResourceIterator}s should be created for any cases where a
 * URL's protocol isn't otherwise handled. Thus, {@link #isFactoryFor(URL)}
 * will always return <code>true</code>. Because of this behavior, the
 * <code>FileResourceIteratorFactory</code> should never be registered as a
 * service implementation for {@link ResourceIteratorFactory} as it could
 * easily hide other service implementations.</p>
 */
public class FileResourceIteratorFactory implements ResourceIteratorFactory {

    /**
     * Initializes a new instance of the FileResourceIteratorFactory class.
     */
    public FileResourceIteratorFactory() {
        // intentionally empty
    }

    @Override
    public boolean isFactoryFor(URL url) {
        return true;
    }

    @Override
    public Iterator<Resource> createIterator(URL url, String path, String suffix) {
        File file = new File(getPath(url));
        File rootDir = new File(file.getAbsolutePath().substring(0, file.getAbsolutePath().length() - path.length()));
        return new FileResourceIterator(rootDir, file, suffix);
    }

    static String getPath(URL url) {
        try {
            return URLDecoder.decode(url.getPath(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Encoding problem", e);
        }
    }
}
