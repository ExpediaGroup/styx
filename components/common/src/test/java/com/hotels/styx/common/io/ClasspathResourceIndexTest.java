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
import org.testng.annotations.Test;

import static com.hotels.styx.common.io.ResourcePathMatcher.resourceWithPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class ClasspathResourceIndexTest {
    @Test
    public void listsByPathAndSuffix() {
        ClassLoader classLoader = ClasspathResourceIndexTest.class.getClassLoader();
        ClasspathResourceIndex index = new ClasspathResourceIndex(classLoader);

        Iterable<Resource> resources = index.list("com/hotels/styx/common/io", ".txt");

        assertThat(resources, containsInAnyOrder(
                resourceWithPath("resource.txt"),
                resourceWithPath("subdirectory/subdir-resource.txt")
        ));
    }
}