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
package com.hotels.styx.server.routing;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.RequestCookie;
import com.hotels.styx.server.routing.antlr.AntlrConditionParser;

import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;

/**
 * A Route matcher based on ANTLR condition parser.
 */
public final class AntlrMatcher implements Matcher {
    static final Condition.Parser CONDITION_PARSER = new AntlrConditionParser.Builder()
            .registerFunction("method", request -> request.method().name())
            .registerFunction("path", HttpRequest::path)
            .registerFunction("userAgent", request -> request.header(USER_AGENT).orElse(""))
            .registerFunction("protocol", request -> request.isSecure() ? "https" : "http")
            .registerFunction("header", (request, input) -> request.header(input).orElse(""))
            .registerFunction("cookie", (request, input) -> request.cookie(input).map(RequestCookie::value).orElse(""))
            .build();
    private final Condition condition;

    public static AntlrMatcher antlrMatcher(String conditionString) {
        Condition condition = CONDITION_PARSER.parse(conditionString);
        return new AntlrMatcher(condition);
    }

    private AntlrMatcher(Condition condition) {
        this.condition = condition;
    }

    @Override
    public boolean apply(HttpRequest request) {
        return this.condition.evaluate(request);
    }
}
