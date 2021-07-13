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
package com.hotels.styx.common.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.stream.Stream;

import static com.hotels.styx.common.io.ResourceContentMatcher.contains;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClasspathResourceTest {
    @ParameterizedTest
    @MethodSource("validPaths")
    public void readsValidResourceFromClassLoader(String path) throws MalformedURLException {
        ClasspathResource resource = new ClasspathResource(path, ClasspathResourceTest.class.getClassLoader());

        assertThatResourceIsLoadedCorrectly(resource);
    }

    @ParameterizedTest
    @MethodSource("validPaths")
    public void readsValidResourceFromClass(String path) throws MalformedURLException {
        ClasspathResource resource = new ClasspathResource(path, ClasspathResourceTest.class);

        assertThatResourceIsLoadedCorrectly(resource);
    }

    private static void assertThatResourceIsLoadedCorrectly(ClasspathResource resource) throws MalformedURLException {
        assertThat(resource.path(), is("com/hotels/styx/common/io/resource.txt"));
        assertThat(resource.absolutePath(), is(absolutePath("/com/hotels/styx/common/io/resource.txt")));
        assertThat(resource.url(), is(new URL("file:" + absolutePath("/com/hotels/styx/common/io/resource.txt"))));
        assertThat(resource, contains("This is an example resource.\nIt has content to use in automated tests."));
    }

    private static Stream<Arguments> validPaths() {
        return Stream.of(
                Arguments.of("classpath:com/hotels/styx/common/io/resource.txt"),
                Arguments.of("classpath:/com/hotels/styx/common/io/resource.txt")
        );
    }

    private static String absolutePath(String path) {
        return ClasspathResourceTest.class.getResource(path).getPath();
    }

    @Test
    public void nonExistentResourceThrowsExceptionWhenTryingToGetURL() {
        ClasspathResource resource = new ClasspathResource("foobar", ClasspathResourceTest.class);

        Exception e = assertThrows(RuntimeException.class,
                () -> resource.url());
        assertEquals("java.io.FileNotFoundException: foobar", e.getMessage());
    }

    @Test
    public void nonExistentResourceThrowsExceptionWhenTryingToGetInputStream() {
        ClasspathResource resource = new ClasspathResource("foobar", ClasspathResourceTest.class);

        Exception e = assertThrows(FileNotFoundException.class,
                () -> resource.inputStream());
        assertEquals("classpath:foobar", e.getMessage());
    }
}