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
package com.hotels.styx.server.handlers;

import com.google.common.io.Files;
import com.google.common.net.MediaType;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import org.slf4j.Logger;
import rx.Observable;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.MediaTypes.mediaTypeOf;
import static com.hotels.styx.api.http.handlers.NotFoundHandler.NOT_FOUND_HANDLER;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.just;

/**
 * HTTP handler that provides a static file.
 */
public class StaticFileHandler implements HttpHandler {
    private static final Logger LOG = getLogger(StaticFileHandler.class);
    private final File dir;

    /**
     * Constructor.
     *
     * @param dir directory to find files in
     */
    public StaticFileHandler(File dir) {
        this.dir = dir;
    }

    @Override
    public Observable<HttpResponse> handle(HttpRequest request) {
        try {
            return resolveFile(request.path())
                    .map(ResolvedFile::new)
                    .map(resolvedFile -> response()
                            .addHeader(CONTENT_TYPE, resolvedFile.mediaType)
                            .body(resolvedFile.content)
                            .build())
                    .map(Observable::just)
                    .orElseGet(() -> NOT_FOUND_HANDLER.handle(request));
        } catch (IOException e) {
            return just(response(INTERNAL_SERVER_ERROR).build());
        }
    }

    private static class ResolvedFile {
        private final String content;
        private final MediaType mediaType;

        private ResolvedFile(File file) {
            this.content = readLines(file);
            this.mediaType = mediaTypeOf(file.getName());
        }
    }

    private static String readLines(File file) {
        try {
            return Files.toString(file, UTF_8);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    /**
     * Resolve path to actual file.
     *
     * @param path file path
     * @return file if it exists
     * @throws IOException I/O exception
     */
    private Optional<File> resolveFile(String path) throws IOException {
        File result = new File(dir, path).getCanonicalFile();
        LOG.debug("Resolved path={}", result);

        if (!result.exists()) {
            return Optional.empty();
        }

        // For security, check file really does exist under root.
        String fullPath = result.getPath();
        if (!fullPath.startsWith(dir.getCanonicalPath() + File.separator) && !fullPath.equals(dir.getCanonicalPath())) {
            // Prevent paths like http://foo/../../etc/passwd
            result = null;
        }

        return Optional.ofNullable(result);
    }
}
