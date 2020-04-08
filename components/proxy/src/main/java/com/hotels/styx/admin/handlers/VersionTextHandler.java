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
package com.hotels.styx.admin.handlers;

import com.google.common.io.CharStreams;
import com.hotels.styx.api.Resource;
import com.hotels.styx.common.http.handler.StaticBodyHttpHandler;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provides version text from one or more files provided in configuration.
 */
public class VersionTextHandler extends StaticBodyHttpHandler {
    private static final Logger LOG = getLogger(VersionTextHandler.class);

    /**
     * Constructs an instance with a list of resources to get content from.
     *
     * @param files content resources
     */
    public VersionTextHandler(Iterable<Resource> files) {
        super(PLAIN_TEXT_UTF_8, body(files));
    }

    private static String body(Iterable<Resource> files) {
        checkArgument(!isEmpty(files));

        return versionFileContent(files).orElse("Unknown version\n");
    }

    private static Optional<String> versionFileContent(Iterable<Resource> files) {
        StringBuilder builder = new StringBuilder();

        files.forEach(versionFile ->
                readString(versionFile).ifPresent(content ->
                        builder.append(content).append('\n')));

        return builder.length() > 0
                ? Optional.of(builder.toString())
                : Optional.empty();
    }

    private static Optional<String> readString(Resource resource) {
        try (BufferedReader reader = bufferedReader(resource)) {
            return Optional.of(CharStreams.toString(reader));
        } catch (IOException e) {
            LOG.warn("Could not load file={}", resource.path());
            return Optional.empty();
        }
    }

    private static BufferedReader bufferedReader(Resource resource) throws IOException {
        return new BufferedReader(new InputStreamReader(resource.inputStream()));
    }
}
