/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.stream.Stream;

import static com.google.common.io.Files.createTempDir;
import static com.google.common.io.Files.write;
import static com.hotels.styx.common.io.ResourceContentMatcher.contains;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.io.File.createTempFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class FileResourceTest {
    static final String TEMP_FILE_CONTENT = "Some test content";
    static final String NAMED_TEMP_FILE_CONTENT = "Some different test content";
    static File tempFile;
    File tempDir;
    File namedTempFile;

    @BeforeAll
    public void setUp() throws IOException {
        tempDir = createTempDir();
        tempFile = createTempFile("foo", "bar");
        namedTempFile = new File(tempDir, "test.txt");
        write(TEMP_FILE_CONTENT, tempFile, UTF_8);
        write(NAMED_TEMP_FILE_CONTENT, namedTempFile, UTF_8);
    }

    @AfterAll
    public void tearDown() {
        tempFile.delete();
        namedTempFile.delete();
        tempDir.delete();
    }

    @ParameterizedTest
    @MethodSource("validPaths")
    public void readsValidResourceFromPath(String path) throws MalformedURLException {
        FileResource resource = new FileResource(path);

        String absolutePath = tempFile.getAbsolutePath();

        assertThat(resource.path(), is(absolutePath));
        assertThat(resource.absolutePath(), is(absolutePath));
        assertThat(resource.url(), is(tempFile.toURI().toURL()));
        assertThat(resource, contains(TEMP_FILE_CONTENT));
    }

    private static Stream<Arguments> validPaths() {
        String path = tempFile.getAbsolutePath();

        return Stream.of(
                Arguments.of("file:" + path),
                Arguments.of(path)
        );
    }

    @Test
    public void readsValidResourceFromFile() throws MalformedURLException {
        FileResource resource = new FileResource(tempFile);

        String absolutePath = tempFile.getAbsolutePath();

        assertThat(resource.path(), is(absolutePath));
        assertThat(resource.absolutePath(), is(absolutePath));
        assertThat(resource.url(), is(tempFile.toURI().toURL()));
        assertThat(resource, contains(TEMP_FILE_CONTENT));
    }

    @Test
    public void readsValidResourceFromDirectoryAndFile() throws MalformedURLException {
        FileResource resource = new FileResource(tempDir, namedTempFile);
        File expectedFile = new File(tempDir,"test.txt");
        assertThat(resource.path(), is("test.txt"));

        assertThat(resource.absolutePath(), is(expectedFile.getPath()));
        assertThat(resource.url(), is(expectedFile.toURI().toURL()));
        assertThat(resource, contains(NAMED_TEMP_FILE_CONTENT));
    }

    @Test
    public void nonExistentResourceThrowsExceptionWhenTryingToGetInputStream() throws IOException {
        FileResource resource = new FileResource("foobar");

        Exception e = assertThrows(FileNotFoundException.class,
                () -> resource.inputStream());
        assertThat(e.getMessage(), matchesPattern("foobar.*"));
    }
}