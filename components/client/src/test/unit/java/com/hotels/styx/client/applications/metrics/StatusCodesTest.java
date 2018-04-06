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
package com.hotels.styx.client.applications.metrics;

import org.testng.annotations.Test;

import static com.hotels.styx.client.applications.metrics.StatusCodes.statusCodeName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StatusCodesTest {
    @Test
    public void createsNameFromStatusCode() {
        assertThat(statusCodeName(200), is("status.200"));
    }

    @Test
    public void defaultsToMinusOneIfStatusCodeIsLessThan100() {
        assertThat(statusCodeName(99), is("status.-1"));
        assertThat(statusCodeName(100), is("status.100"));
    }

    @Test
    public void defaultsToMinusOneIfStatusCodeIsGreaterThanOrEqualTo600() {
        assertThat(statusCodeName(600), is("status.-1"));
        assertThat(statusCodeName(599), is("status.599"));
    }
}