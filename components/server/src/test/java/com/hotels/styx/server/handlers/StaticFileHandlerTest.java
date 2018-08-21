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
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static com.google.common.net.MediaType.CSS_UTF_8;
import static com.google.common.net.MediaType.GIF;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.JAVASCRIPT_UTF_8;
import static com.google.common.net.MediaType.JPEG;
import static com.google.common.net.MediaType.MICROSOFT_EXCEL;
import static com.google.common.net.MediaType.MPEG_AUDIO;
import static com.google.common.net.MediaType.MPEG_VIDEO;
import static com.google.common.net.MediaType.OCTET_STREAM;
import static com.google.common.net.MediaType.PDF;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.net.MediaType.PNG;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.server.handlers.MediaTypes.ICON;
import static com.hotels.styx.server.handlers.MediaTypes.MICROSOFT_ASF_VIDEO;
import static com.hotels.styx.server.handlers.MediaTypes.MICROSOFT_MS_VIDEO;
import static com.hotels.styx.server.handlers.MediaTypes.WAV_AUDIO;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.support.api.BlockingObservables.getFirst;
import static com.hotels.styx.support.api.matchers.HttpStatusMatcher.hasStatus;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class StaticFileHandlerTest {
    private File dir;
    private StaticFileHandler handler;

    @BeforeMethod
    public void createWorkingDir() {
        dir = Files.createTempDir();
        handler = new StaticFileHandler(dir);
    }

    /**
     * Clean up working dir at end of test.
     */
    @AfterMethod
    public void cleanUpWorkingDir() {
        dir.delete();
    }

    @Test
    public void should404ForMissingFiles() {
        assertThat(handle(get("/index.html").build(), HttpInterceptorContext.create()), hasStatus(NOT_FOUND));
        assertThat(handle(get("/notfound.html").build(), HttpInterceptorContext.create()), hasStatus(NOT_FOUND));
        assertThat(handle(get("/foo/bar").build(), HttpInterceptorContext.create()), hasStatus(NOT_FOUND));
    }

    @Test
    public void shouldServeExistingFiles() throws Exception {
        writeFile("index.html", "Hello world");
        writeFile("foo.js", "Blah");
        mkdir("/a/b");
        writeFile("a/b/good", "hi");
        assertThat("asking for /index.html", handle(get("/index.html").build(), HttpInterceptorContext.create()), hasStatus(OK));
        assertThat(handle(get("/foo.js").build(), HttpInterceptorContext.create()), hasStatus(OK));

        assertThat(handle(get("/notfound.html").build(), HttpInterceptorContext.create()), hasStatus(NOT_FOUND));
        assertThat(handle(get("/a/b/good").build(), HttpInterceptorContext.create()), hasStatus(OK));
        assertThat(handle(get("/a/b/bad").build(), HttpInterceptorContext.create()), hasStatus(NOT_FOUND));
    }

    @Test
    public void shouldIgnoreQueryParams() throws Exception {
        writeFile("index.html", "Hello world");
        writeFile("foo.js", "Blah");
        mkdir("/a/b");
        writeFile("a/b/good", "hi");

        assertThat(handle(get("/index.html?foo=x").build(), HttpInterceptorContext.create()), hasStatus(OK));
        assertThat(handle(get("/foo.js?sdfsd").build(), HttpInterceptorContext.create()), hasStatus(OK));
        assertThat(handle(get("/a/b/good?xx").build(), HttpInterceptorContext.create()), hasStatus(OK));
    }

    @Test
    public void shouldDisallowDirectoryTraversals() throws Exception {
        mkdir("/a/public");
        writeFile("a/public/index.html", "hi");

        mkdir("/a/private");
        writeFile("a/private/index.html", "hi");

        handler = new StaticFileHandler(new File(dir, "/a/public"));

        assertThat(handle(get("/index.html").build(), HttpInterceptorContext.create()), hasStatus(OK));
        assertThat(handle(get("/../private/index.html").build(), HttpInterceptorContext.create()), hasStatus(NOT_FOUND));
    }

    @Test
    public void shouldDisallowDirectoryTraversalsWithEncodedRequests() throws Exception {
        mkdir("/a/public");
        writeFile("a/public/index.html", "hi");

        mkdir("/a/private");
        writeFile("a/private/index.html", "hi");

        handler = new StaticFileHandler(new File(dir, "/a/public"));

        assertThat(handle(get("/index.html").build(), HttpInterceptorContext.create()), hasStatus(OK));
        assertThat(handle(get("/%2e%2e%2fprivate/index.html").build(), HttpInterceptorContext.create()), hasStatus(NOT_FOUND));
    }

    @Test(dataProvider = "fileTypesProvider")
    public void setsTheContentTypeBasedOnFileExtension(String path, MediaType mediaType) throws Exception {
        writeFile(path, mediaType.toString());

        handler = new StaticFileHandler(dir);

        HttpResponse response = handle(get("/" + path).build(), HttpInterceptorContext.create());
        assertThat(response, hasStatus(OK));
        assertThat(response.contentType(), isValue(mediaType.toString()));
    }

    @DataProvider(name = "fileTypesProvider")
    public Object[][] fileTypesProvider() {
        return new Object[][]{
                {"foo.html", HTML_UTF_8},
                {"foo.gif", GIF},
                {"foo.jpg", JPEG},
                {"foo.png", PNG},
                {"foo.css", CSS_UTF_8},
                {"foo.ico", ICON},
                {"foo.js", JAVASCRIPT_UTF_8},
                {"foo.xls", MICROSOFT_EXCEL},
                {"foo.txt", PLAIN_TEXT_UTF_8},
                {"foo.pgp", OCTET_STREAM},
                {"foo.pdf", PDF},

                {"foo.mp3", MPEG_AUDIO},
                {"foo.wav", WAV_AUDIO},

                {"foo.asf", MICROSOFT_ASF_VIDEO},
                {"foo.avi", MICROSOFT_MS_VIDEO},
                {"foo.mpg", MPEG_VIDEO}
        };
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

    private HttpResponse handle(HttpRequest request, HttpInterceptor.Context context) {
        return getFirst(handler.handle(request, context));
    }
}
