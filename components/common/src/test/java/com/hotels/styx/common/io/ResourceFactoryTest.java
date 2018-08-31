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

import static com.hotels.styx.common.io.ResourceContentMatcher.contains;
import static org.hamcrest.MatcherAssert.assertThat;

public class ResourceFactoryTest {
    @Test
    public void canAcquireClasspathResources() {
        Resource resource = ResourceFactory.newResource("classpath:com/hotels/styx/common/io/resource.txt");
        
        assertThat(resource, contains("This is an example resource.\nIt has content to use in automated tests."));
    }

    @Test
    public void canAcquireFileResources() {
        String filePath = ResourceFactoryTest.class.getResource("/com/hotels/styx/common/io/resource.txt").getPath();
        
        Resource resource = ResourceFactory.newResource(filePath);

        assertThat(resource, contains("This is an example resource.\nIt has content to use in automated tests."));
    }
}