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
import com.hotels.styx.configstore.ConfigStore;

import static com.hotels.styx.api.HttpResponse.Builder.response;
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
        boolean started = configStore.get("server.started.proxy", Boolean.class)
                .orElse(false);

        return response()
                .body(Boolean.toString(started))
                .build();
    }
}
