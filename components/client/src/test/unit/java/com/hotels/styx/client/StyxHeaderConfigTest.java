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
package com.hotels.styx.client;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.hotels.styx.client.StyxHeaderConfig.ORIGIN_ID_DEFAULT;
import static com.hotels.styx.client.StyxHeaderConfig.REQUEST_ID_DEFAULT;
import static com.hotels.styx.client.StyxHeaderConfig.STYX_INFO_DEFAULT;
import static com.hotels.styx.client.StyxHeaderConfig.STYX_INFO_FORMAT_DEFAULT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 *
 */
public class StyxHeaderConfigTest {
    private StyxHeaderConfig.StyxHeader styxInfoHeader;
    private StyxHeaderConfig.StyxHeader originIdHeader;
    private StyxHeaderConfig.StyxHeader requestIdHeader;

    @BeforeMethod
    public void setUp() {
        styxInfoHeader = new StyxHeaderConfig.StyxHeader("styxInfoFoo", "blah {INSTANCE} etc");
        originIdHeader = new StyxHeaderConfig.StyxHeader("originIdBar", null);
        requestIdHeader = new StyxHeaderConfig.StyxHeader("requestIdBaz", null);
    }

    @Test
    public void usesSetValues() {
        StyxHeaderConfig config = new StyxHeaderConfig(styxInfoHeader, originIdHeader, requestIdHeader);

        assertThat(config.styxInfoHeaderName().toString(), is("styxInfoFoo"));
        assertThat(config.originIdHeaderName().toString(), is("originIdBar"));
        assertThat(config.requestIdHeaderName().toString(), is("requestIdBaz"));
        assertThat(config.styxInfoHeaderFormat(), is("blah {INSTANCE} etc"));
    }

    @Test
    public void hasDefaultStyxInfoHeaderName() {
        styxInfoHeader = new StyxHeaderConfig.StyxHeader(null, "blah {INSTANCE} etc");

        StyxHeaderConfig config = new StyxHeaderConfig(styxInfoHeader, originIdHeader, requestIdHeader);

        assertThat(config.styxInfoHeaderName().toString(), is(STYX_INFO_DEFAULT));
    }

    @Test
    public void hasDefaultStyxInfoHeaderFormat() {
        styxInfoHeader = new StyxHeaderConfig.StyxHeader("styxInfoFoo", null);

        StyxHeaderConfig config = new StyxHeaderConfig(styxInfoHeader, originIdHeader, requestIdHeader);

        assertThat(config.styxInfoHeaderFormat(), is(STYX_INFO_FORMAT_DEFAULT));
    }

    @Test
    public void hasDefaultOriginIdHeaderName() {
        originIdHeader = new StyxHeaderConfig.StyxHeader(null, null);

        StyxHeaderConfig config = new StyxHeaderConfig(styxInfoHeader, originIdHeader, requestIdHeader);

        assertThat(config.originIdHeaderName().toString(), is(ORIGIN_ID_DEFAULT));
    }

    @Test
    public void hasDefaultRequestIdHeaderName() {
        requestIdHeader = new StyxHeaderConfig.StyxHeader(null, null);

        StyxHeaderConfig config = new StyxHeaderConfig(styxInfoHeader, originIdHeader, requestIdHeader);

        assertThat(config.requestIdHeaderName().toString(), is(REQUEST_ID_DEFAULT));
    }

}