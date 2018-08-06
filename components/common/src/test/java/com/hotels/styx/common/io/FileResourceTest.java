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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import static com.google.common.io.Files.createTempDir;
import static com.google.common.io.Files.write;
import static com.hotels.styx.common.io.ResourceContentMatcher.contains;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.io.File.createTempFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FileResourceTest {
    static final String TEMP_FILE_CONTENT = "Some test content";
    static final String NAMED_TEMP_FILE_CONTENT = "Some different test content";
    File tempFile;
    File tempDir;
    File namedTempFile;

    @BeforeClass
    public void setUp() throws IOException {
        tempDir = createTempDir();
        tempFile = createTempFile("foo", "bar");
        namedTempFile = new File(tempDir, "test.txt");
        write(TEMP_FILE_CONTENT, tempFile, UTF_8);
        write(NAMED_TEMP_FILE_CONTENT, namedTempFile, UTF_8);
    }

    @AfterClass
    public void tearDown() {
        tempFile.delete();
        namedTempFile.delete();
        tempDir.delete();
    }

    @Test(dataProvider = "validPaths")
    public void readsValidResourceFromPath(String path) throws MalformedURLException {
        FileResource resource = new FileResource(path);

        String absolutePath = tempFile.getAbsolutePath();

        assertThat(resource.path(), is(absolutePath));
        assertThat(resource.absolutePath(), is(absolutePath));
        assertThat(resource.url(), is(tempFile.toURI().toURL()));
        assertThat(resource, contains(TEMP_FILE_CONTENT));
    }

    @DataProvider(name = "validPaths")
    private Object[][] validPaths() {
        String path = tempFile.getAbsolutePath();

        return new Object[][]{
                {"file:" + path},
                {path},
        };
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

    @Test(expectedExceptions = FileNotFoundException.class, expectedExceptionsMessageRegExp = "foobar.*")
    public void nonExistentResourceThrowsExceptionWhenTryingToGetInputStream() throws IOException {
        FileResource resource = new FileResource("foobar");

        resource.inputStream();
    }
}