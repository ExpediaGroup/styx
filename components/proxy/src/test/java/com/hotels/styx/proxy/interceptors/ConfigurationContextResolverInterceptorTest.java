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
package com.hotels.styx.proxy.interceptors;

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationContextResolver;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.support.api.matchers.HttpStatusMatcher.hasStatus;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigurationContextResolverInterceptorTest {
    @Test
    public void resolvesConfigurationContext() {
        LiveHttpRequest request = get("/").build();
        Configuration.Context context = context(ImmutableMap.of("key1", "value1", "key2", "value2"));

        ConfigurationContextResolver configurationContextResolver = configurationContextResolver(request, context);

        ConfigurationContextResolverInterceptor interceptor = new ConfigurationContextResolverInterceptor(configurationContextResolver);

        TestChain chain = new TestChain();

        Eventual<LiveHttpResponse> responseObservable = interceptor.intercept(request, chain);

        assertThat(Mono.from(responseObservable).block(), hasStatus(OK));
        assertThat(chain.proceedWasCalled, is(true));
        assertThat(chain.context().get("config.context", Configuration.Context.class), is(context));
    }

    private ConfigurationContextResolver configurationContextResolver(LiveHttpRequest request, Configuration.Context context) {
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
        private final HttpInterceptor.Context context = requestContext();
        private boolean proceedWasCalled;

        @Override
        public HttpInterceptor.Context context() {
            return context;
        }

        @Override
        public Eventual<LiveHttpResponse> proceed(LiveHttpRequest request) {
            proceedWasCalled = true;

            return Eventual.of(response(OK).build());
        }
    }
}