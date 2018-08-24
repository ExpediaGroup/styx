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
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.nio.file.Paths;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class ResourcePathMatcher extends TypeSafeMatcher<Resource> {
    private final String path;

    private ResourcePathMatcher(String path) {
        this.path = requireNonNull(path);
    }

    public static ResourcePathMatcher resourceWithPath(String path) {
        return new ResourcePathMatcher(Paths.get(path).toString());
    }

    @Override
    protected boolean matchesSafely(Resource resource) {
        return Objects.equals(path, resource.path());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(path);
    }

    @Override
    protected void describeMismatchSafely(Resource item, Description mismatchDescription) {
        mismatchDescription.appendText(item.path());
    }
}
