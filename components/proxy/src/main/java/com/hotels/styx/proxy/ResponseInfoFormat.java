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
package com.hotels.styx.proxy;

import com.hotels.styx.Environment;

import static com.hotels.styx.StyxConfig.NO_JVM_ROUTE_SET;
import com.hotels.styx.api.HttpRequest;

/**
 * Formats response info into a string.
 */
public class ResponseInfoFormat {
    private final String format;

    ResponseInfoFormat(Environment environment) {
        String releaseTag = environment.buildInfo().releaseTag();
        String jvmRoute = environment.configuration().get("jvmRouteName").orElse(NO_JVM_ROUTE_SET);

        String rawFormat = environment.styxConfig().styxHeaderConfig().styxInfoHeaderFormat();

        this.format = rawFormat
                .replace("{INSTANCE}", jvmRoute)
                .replace("{VERSION}", releaseTag)
                .replace("{REQUEST_ID}", "%s");
    }

    public String format(HttpRequest request) {
        return String.format(format, request == null ? "" : request.id());
    }
}
