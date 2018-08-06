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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;

import static com.hotels.styx.common.io.ResourceContentMatcher.contains;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ClasspathResourceTest {
    @Test(dataProvider = "validPaths")
    public void readsValidResourceFromClassLoader(String path) throws MalformedURLException {
        ClasspathResource resource = new ClasspathResource(path, ClasspathResourceTest.class.getClassLoader());

        assertThatResourceIsLoadedCorrectly(resource);
    }

    @Test(dataProvider = "validPaths")
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

    @DataProvider(name = "validPaths")
    private static Object[][] validPaths() {
        return new Object[][]{
                {"classpath:com/hotels/styx/common/io/resource.txt"},
                {"classpath:/com/hotels/styx/common/io/resource.txt"},
        };
    }

    private static String absolutePath(String path) {
        return ClasspathResourceTest.class.getResource(path).getPath();
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "java.io.FileNotFoundException: foobar")
    public void nonExistentResourceThrowsExceptionWhenTryingToGetURL() {
        ClasspathResource resource = new ClasspathResource("foobar", ClasspathResourceTest.class);

        resource.url();
    }

    @Test(expectedExceptions = FileNotFoundException.class, expectedExceptionsMessageRegExp = "classpath:foobar")
    public void nonExistentResourceThrowsExceptionWhenTryingToGetInputStream() throws FileNotFoundException {
        ClasspathResource resource = new ClasspathResource("foobar", ClasspathResourceTest.class);

        resource.inputStream();
    }
}