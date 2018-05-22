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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.http.handlers.BaseHttpHandler;
import com.hotels.styx.common.Result;
import com.hotels.styx.configstore.ConfigStore;

import java.util.Optional;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Handler that returns whether the proxy server has started.
 */
public class ProxyStatusHandler extends BaseHttpHandler {
    private final ConfigStore configStore;

    public ProxyStatusHandler(ConfigStore configStore) {
        this.configStore = requireNonNull(configStore);
    }

    @Override
    protected HttpResponse doHandle(HttpRequest request) {
        String resultDescription = configStore.get("server.started.proxy", Result.class)
                .map(result -> (Result<String>) result)
                .map(result -> result.mapSuccess(any -> "STARTED"))
                .map(result -> result.defaultOnFailure(any -> "FAILED"))
                .map(Result::successValue)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .orElse("INCOMPLETE");

        String json = format("{%n  \"status\":\"%s\"%n}%n", resultDescription);

        return response()
                .contentType(JSON_UTF_8)
                .body(json)
                .build();
    }
}
