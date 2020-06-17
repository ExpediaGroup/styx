/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Provides an uptime via admin interface.
 */
public class UptimeHandler implements WebServiceHandler {
    private final MeterRegistry registry;

    public UptimeHandler(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        Gauge gauge = registry.find("jvm.uptime").gauge();
        String uptime = gauge == null ? "The uptime metric is missing!" : formatTime((long) gauge.value());

        return Eventual.of(HttpResponse.response(OK)
                .disableCaching()
                .addHeader(CONTENT_TYPE, "application/json")
                .body(format("\"%s\"", uptime), UTF_8)
                .build());
    }

    private String formatTime(long timeInMilliseconds) {
        Duration duration = Duration.ofMillis(timeInMilliseconds);

        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusHours(duration.toHours()).toMinutes();

        return format("%dd %dh %dm", days, hours, minutes);
    }
}
