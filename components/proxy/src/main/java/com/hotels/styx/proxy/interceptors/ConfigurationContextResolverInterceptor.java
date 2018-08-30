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
package com.hotels.styx.proxy.interceptors;

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationContextResolver;

import com.hotels.styx.api.HttpRequest;

import static java.util.Objects.requireNonNull;

/**
 * Interceptor that populates the chain with configuration context.
 */
public class ConfigurationContextResolverInterceptor implements HttpInterceptor {
    private final ConfigurationContextResolver configurationContextResolver;

    public ConfigurationContextResolverInterceptor(ConfigurationContextResolver configurationContextResolver) {
        this.configurationContextResolver = requireNonNull(configurationContextResolver);
    }

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        Configuration.Context context = configurationContextResolver.resolve(request);
        chain.context().add("config.context", context);
        return chain.proceed(request);
    }
}
