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
package com.hotels.styx.spi;

import org.testng.annotations.Test;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hotels.styx.spi.ClassSourceLocator.JARS;
import static com.hotels.styx.spi.ClassSourceLocator.cached;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertSame;

public class ClassSourceLocatorTest {
    @Test
    public void canCacheClassSources() {
        Path path = Paths.get("/this_is_a_test");

        ClassSource[] classSources = {
                mock(ClassSource.class),
                mock(ClassSource.class),
                mock(ClassSource.class)
        };

        AtomicInteger incrementingIndex = new AtomicInteger();

        ClassSourceLocator locator = p -> classSources[incrementingIndex.getAndIncrement()];

        ClassSourceLocator cached = cached(locator);

        assertThat(cached.classSource(path), is(classSources[0]));
        assertThat(cached.classSource(path), is(classSources[0]));
        assertThat(cached.classSource(path), is(classSources[0]));
        assertThat(cached.classSource(Paths.get("/this_is_a_test_2")), is(classSources[1]));
    }

    @Test
    public void jarBasedClassSourceIsCached() {
        String pluginPath = "/plugins/oneplugin/testPluginA-1.0-SNAPSHOT.jar";

        URL resource = ClassSourceLocatorTest.class.getResource(pluginPath);

        assertThat(resource, is(notNullValue()));

        Path path = Paths.get(resource.getPath());

        ClassSource firstTime = JARS.classSource(path);
        ClassSource secondTime = JARS.classSource(path);
        ClassSource thirdTime = JARS.classSource(path);

        // Checking that the same object reference is returned each time
        assertSame(firstTime, secondTime);
        assertSame(secondTime, thirdTime);
    }

}