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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.Url;
import com.hotels.styx.api.extension.service.RewriteRule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A list of rules that defines how to rewrite the URL in HTTP requests. The rules are tested in order, until a matching
 * rule is found. When the match is found, a rewrite is performed. If no match is found, the original URL is retained.
 */
public class RewriteRuleset {
    private final List<RewriteRule> rewriteRules;

    /**
     * Constructs an instance with a list of rewrite rules.
     *
     * @param rewriteRules rewrite rules
     */
    public RewriteRuleset(List<RewriteRule> rewriteRules) {
        this.rewriteRules = ImmutableList.copyOf(rewriteRules);
    }

    /**
     * Rewrites the URL of the request according to the rewrite rules.
     *
     * @param request a request
     * @return a rewritten request
     */
    public HttpRequest rewrite(HttpRequest request) {
        String path = request.path();
        String newPath = newPath(path);

        if (!Objects.equals(newPath, path)) {
            Url newUrl = request.url().newBuilder().path(newPath).build();
            return request.newBuilder()
                    .url(newUrl)
                    .build();
        }

        return request;
    }

    private String newPath(String requestUri) {
        return rewriteRules.stream()
                .map(rewriteRule -> rewriteRule.rewrite(requestUri))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(requestUri);
    }
}
