/*
  Copyright (C) 2013-2021 Expedia Inc.

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
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.stream.Stream;

import static com.hotels.styx.api.HttpHeaderValues.APPLICATION_JSON;
import static com.hotels.styx.api.HttpHeaderValues.HTML;
import static com.hotels.styx.api.HttpHeaderValues.PLAIN_TEXT;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.server.handlers.MediaTypes.ICON;
import static com.hotels.styx.server.handlers.MediaTypes.MICROSOFT_ASF_VIDEO;
import static com.hotels.styx.server.handlers.MediaTypes.MICROSOFT_MS_VIDEO;
import static com.hotels.styx.server.handlers.MediaTypes.OCTET_STREAM;
import static com.hotels.styx.server.handlers.MediaTypes.WAV_AUDIO;
import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.support.api.matchers.HttpStatusMatcher.hasStatus;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class StaticFileHandlerTest {
    private File dir;
    private StaticFileHandler handler;

    @BeforeEach
    public void createWorkingDir() {
        dir = Files.createTempDir();
        handler = new StaticFileHandler(dir);
    }

    /**
     * Clean up working dir at end of test.
     */
    @AfterEach
    public void cleanUpWorkingDir() {
        dir.delete();
    }

    @Test
    public void should404ForMissingFiles() {
        assertThat(handle(get("/index.html").build()), hasStatus(NOT_FOUND));
        assertThat(handle(get("/notfound.html").build()), hasStatus(NOT_FOUND));
        assertThat(handle(get("/foo/bar").build()), hasStatus(NOT_FOUND));
    }

    @Test
    public void shouldServeExistingFiles() throws Exception {
        writeFile("index.html", "Hello world");
        writeFile("foo.js", "Blah");
        mkdir("/a/b");
        writeFile("a/b/good", "hi");
        assertThat("asking for /index.html", handle(get("/index.html").build()), hasStatus(OK));
        assertThat(handle(get("/foo.js").build()), hasStatus(OK));

        assertThat(handle(get("/notfound.html").build()), hasStatus(NOT_FOUND));
        assertThat(handle(get("/a/b/good").build()), hasStatus(OK));
        assertThat(handle(get("/a/b/bad").build()), hasStatus(NOT_FOUND));
    }

    @Test
    public void shouldIgnoreQueryParams() throws Exception {
        writeFile("index.html", "Hello world");
        writeFile("foo.js", "Blah");
        mkdir("/a/b");
        writeFile("a/b/good", "hi");

        assertThat(handle(get("/index.html?foo=x").build()), hasStatus(OK));
        assertThat(handle(get("/foo.js?sdfsd").build()), hasStatus(OK));
        assertThat(handle(get("/a/b/good?xx").build()), hasStatus(OK));
    }

    @Test
    public void shouldDisallowDirectoryTraversals() throws Exception {
        mkdir("/a/public");
        writeFile("a/public/index.html", "hi");

        mkdir("/a/private");
        writeFile("a/private/index.html", "hi");

        handler = new StaticFileHandler(new File(dir, "/a/public"));

        assertThat(handle(get("/index.html").build()), hasStatus(OK));
        assertThat(handle(get("/../private/index.html").build()), hasStatus(NOT_FOUND));
    }

    @Test
    public void shouldDisallowDirectoryTraversalsWithEncodedRequests() throws Exception {
        mkdir("/a/public");
        writeFile("a/public/index.html", "hi");

        mkdir("/a/private");
        writeFile("a/private/index.html", "hi");

        handler = new StaticFileHandler(new File(dir, "/a/public"));

        assertThat(handle(get("/index.html").build()), hasStatus(OK));
        assertThat(handle(get("/%2e%2e%2fprivate/index.html").build()), hasStatus(NOT_FOUND));
    }

    @ParameterizedTest
    @MethodSource("fileTypesProvider")
    public void setsTheContentTypeBasedOnFileExtension(String path, CharSequence mediaType) throws Exception {
        writeFile(path, mediaType.toString());

        handler = new StaticFileHandler(dir);

        LiveHttpResponse response = handle(get("/" + path).build());
        assertThat(response, hasStatus(OK));
        assertThat(response.contentType(), isValue(mediaType.toString()));
    }

    private static Stream<Arguments> fileTypesProvider() {
        return Stream.of(
                Arguments.of("foo.html", HTML),
                Arguments.of("foo.gif", "image/gif"),
                Arguments.of("foo.jpg", "image/jpeg"),
                Arguments.of("foo.png", "image/png"),
                Arguments.of("foo.css", "text/css;charset=UTF-8"),
                Arguments.of("foo.ico", ICON),
                Arguments.of("foo.js", APPLICATION_JSON),
                Arguments.of("foo.xls", "application/vnd.ms-excel"),
                Arguments.of("foo.txt", PLAIN_TEXT),
                Arguments.of("foo.pgp", OCTET_STREAM),
                Arguments.of("foo.pdf", "application/pdf"),

                Arguments.of("foo.mp3", "audio/mpeg"),
                Arguments.of("foo.wav", WAV_AUDIO),

                Arguments.of("foo.asf", MICROSOFT_ASF_VIDEO),
                Arguments.of("foo.avi", MICROSOFT_MS_VIDEO),
                Arguments.of("foo.mpg", "video/mpeg")
        );
    }

    private void mkdir(String path) {
        new File(dir, path).mkdirs();
    }

    /**
     * Write text file to FileSystem.
     */
    private void writeFile(String path, String contents) throws IOException {
        try (FileWriter writer = new FileWriter(new File(dir, path))) {
            writer.write(contents);
        }
    }

    private LiveHttpResponse handle(LiveHttpRequest request) {
        return Mono.from(handler.handle(request, requestContext())).block();
    }
}
