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
package com.hotels.styx.support.api.matchers;

import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.HttpResponseStatus;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.Objects;

public class HttpResponseStatusMatcher extends TypeSafeMatcher<HttpResponse> {
    private final HttpResponseStatus status;

    @Factory
    public static Matcher<HttpResponse> hasStatus(HttpResponseStatus status) {
        return new HttpResponseStatusMatcher(status);
    }

    HttpResponseStatusMatcher(HttpResponseStatus status) {
        this.status = status;
    }

    @Override
    public boolean matchesSafely(HttpResponse request) {
        return Objects.equals(status, request.status());
    }

    @Override
    protected void describeMismatchSafely(HttpResponse response, Description mismatchDescription) {
        mismatchDescription.appendText("status with value of ").appendValue(response.status());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("status with value of ").appendValue(status.code());
    }
}
