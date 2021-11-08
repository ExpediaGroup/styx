/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.service.RewriteConfig;
import com.hotels.styx.api.extension.service.RewriteRule;
import com.hotels.styx.client.RewriteRuleset;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.routing.config.HttpInterceptorFactory;
import com.hotels.styx.routing.config.StyxObjectDefinition;

import java.util.ArrayList;
import java.util.List;

import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.list;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.string;

/**
 * A built-in interceptor for URL rewrite.
 */
public class RewriteInterceptor implements HttpInterceptor {
    public static final Schema.FieldType SCHEMA = list(
            object(
                    field("urlPattern", string()),
                    field("replacement", string())
            ));

    private final RewriteRuleset rewriteRuleset;

    private RewriteInterceptor(RewriteRuleset rewriteRuleset) {
        this.rewriteRuleset = rewriteRuleset;
    }

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        return chain.proceed(this.rewriteRuleset.rewrite(request));
    }

    /**
     * A factory for built-in interceptors.
     */
    public static class Factory implements HttpInterceptorFactory {
        @Override
        public HttpInterceptor build(StyxObjectDefinition configBlock) {
            List<RewriteRule> rules = new ArrayList<>();
            configBlock.config().iterator().forEachRemaining(
                    node -> {
                        RewriteConfig rewriteConfig = new JsonNodeConfig(node).as(RewriteConfig.class);
                        rules.add(rewriteConfig);
                    }
            );

            return new RewriteInterceptor(new RewriteRuleset(List.copyOf(rules)));
        }

    }
}
