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
package com.hotels.styx.routing.interceptors;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.extension.service.RewriteConfig;
import com.hotels.styx.api.extension.service.RewriteRule;
import com.hotels.styx.client.RewriteRuleset;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.routing.config.HttpInterceptorFactory;
import com.hotels.styx.routing.config.RouteHandlerDefinition;
import com.hotels.styx.api.HttpRequest;

/**
 * A built-in interceptor for URL rewrite.
 */
public class RewriteInterceptor implements HttpInterceptor {
    private final RewriteRuleset rewriteRuleset;

    private RewriteInterceptor(RewriteRuleset rewriteRuleset) {
        this.rewriteRuleset = rewriteRuleset;
    }

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        return chain.proceed(this.rewriteRuleset.rewrite(request));
    }

    /**
     * A factory for built-in interceptors.
     */
    public static class ConfigFactory implements HttpInterceptorFactory {
        @Override
        public HttpInterceptor build(RouteHandlerDefinition configBlock) {
            ImmutableList.Builder<RewriteRule> rules = ImmutableList.builder();
            configBlock.config().iterator().forEachRemaining(
                    node -> {
                        RewriteConfig rewriteConfig = new JsonNodeConfig(node).as(RewriteConfig.class);
                        rules.add(rewriteConfig);
                    }
            );

            return new RewriteInterceptor(new RewriteRuleset(rules.build()));
        }

    }
}
