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

import java.io.File;
import java.nio.file.Path;

import static com.hotels.styx.common.io.ResourcePathMatcher.resourceWithPath;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class FileResourceIndexTest {
    private Path PLUGINS_FIXTURE_PATH = fixturesHome(FileResourceIndexTest.class, "/plugins");
    private FileResourceIndex resourceIndex = new FileResourceIndex();

    @Test
    public void listsResourcesFromFileSystemDirectory() {
        Iterable<Resource> jars = resourceIndex.list(PLUGINS_FIXTURE_PATH.toString(), ".jar");

        assertThat(jars, contains(resourceWithPath("oneplugin/url-rewrite-1.0-SNAPSHOT.jar")));
    }

    @Test
    public void listsResourcesFromFileSystemFile() {
        File file = new File(PLUGINS_FIXTURE_PATH.toFile(), "groovy/UrlRewrite.groovy");
        Iterable<Resource> files = resourceIndex.list(file.getPath(), ".anything");

        assertThat(files, contains(resourceWithPath(
                PLUGINS_FIXTURE_PATH.resolve("groovy/UrlRewrite.groovy").toString())));
    }
}
