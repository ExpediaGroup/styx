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

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationContextResolver;
import com.hotels.styx.api.StyxObservable;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.api.matchers.HttpStatusMatcher.hasStatus;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.hotels.styx.api.HttpRequest;

public class ConfigurationContextResolverInterceptorTest {
    @Test
    public void resolvesConfigurationContext() {
        HttpRequest request = get("/").build();
        Configuration.Context context = context(ImmutableMap.of("key1", "value1", "key2", "value2"));

        ConfigurationContextResolver configurationContextResolver = configurationContextResolver(request, context);

        ConfigurationContextResolverInterceptor interceptor = new ConfigurationContextResolverInterceptor(configurationContextResolver);

        TestChain chain = new TestChain();

        StyxObservable<HttpResponse> responseObservable = interceptor.intercept(request, chain);

        assertThat(await(responseObservable.asCompletableFuture()), hasStatus(OK));
        assertThat(chain.proceedWasCalled, is(true));
        assertThat(chain.context.get("config.context", Configuration.Context.class), is(context));
    }

    private ConfigurationContextResolver configurationContextResolver(HttpRequest request, Configuration.Context context) {
        ConfigurationContextResolver configurationContextResolver = mock(ConfigurationContextResolver.class);
        when(configurationContextResolver.resolve(request)).thenReturn(context);
        return configurationContextResolver;
    }

    private Configuration.Context context(ImmutableMap<String, String> map) {
        Configuration.Context context = mock(Configuration.Context.class);
        when(context.asMap()).thenReturn(map);
        return context;
    }

    private static class TestChain implements HttpInterceptor.Chain {
        private TestChainContext context = new TestChainContext();
        private boolean proceedWasCalled;

        @Override
        public HttpInterceptor.Context context() {
            return context;
        }

        @Override
        public StyxObservable<HttpResponse> proceed(HttpRequest request) {
            proceedWasCalled = true;

            return StyxObservable.of(response(OK).build());
        }

        private class TestChainContext implements HttpInterceptor.Context {
            private Map<String, Object> map = new HashMap<>();

            @Override
            public void add(String key, Object value) {
                map.put(key, value);
            }

            @Override
            public <T> T get(String key, Class<T> type) {
                return type.cast(map.get(key));
            }
        }
    }
}