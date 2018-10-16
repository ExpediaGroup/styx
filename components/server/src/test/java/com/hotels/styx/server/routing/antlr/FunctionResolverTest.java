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
package com.hotels.styx.server.routing.antlr;

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.RequestCookie;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.Test;

import java.util.Map;

import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FunctionResolverTest {
    private final Map<String, Function0> zeroArgumentFunctions = ImmutableMap.of(
            "path", (request, context) -> request.path(),
            "method", (request, context) -> request.method().name());

    private final Map<String, Function1> oneArgumentFunctions = ImmutableMap.of(
            "header", (request, context, name) -> request.header(name).orElse(""),
            "cookie", (request, context, name) -> request.cookie(name).map(RequestCookie::value).orElse(""));

    private final FunctionResolver functionResolver = new FunctionResolver(zeroArgumentFunctions, oneArgumentFunctions);

    private final HttpInterceptor.Context context = HttpInterceptorContext.create();

    @Test
    public void resolvesZeroArgumentFunctions() {
        LiveHttpRequest request = get("/foo").build();

        assertThat(functionResolver.resolveFunction("path", emptyList()).call(request, context), is("/foo"));
        assertThat(functionResolver.resolveFunction("method", emptyList()).call(request, context), is("GET"));
    }

    @Test(expectedExceptions = DslFunctionResolutionError.class,
            expectedExceptionsMessageRegExp = "No such function=\\[foobar\\], with n=\\[0\\] arguments=\\[\\]")
    public void throwsExceptionIfZeroArgumentFunctionDoesNotExist() {
        LiveHttpRequest request = get("/foo").build();

        functionResolver.resolveFunction("foobar", emptyList()).call(request, context);
    }

    @Test
    public void resolvesOneArgumentFunctions() {
        LiveHttpRequest request = get("/foo")
                .header("Host", "www.hotels.com")
                .cookies(requestCookie("lang", "en_US|en-us_hotels_com"))
                .build();

        assertThat(functionResolver.resolveFunction("header", singletonList("Host")).call(request, context), is("www.hotels.com"));
        assertThat(functionResolver.resolveFunction("cookie", singletonList("lang")).call(request, context), is("en_US|en-us_hotels_com"));
    }

    @Test(expectedExceptions = DslFunctionResolutionError.class,
            expectedExceptionsMessageRegExp = "No such function=\\[foobar\\], with n=\\[1\\] arguments=\\[barfoo\\]")
    public void throwsExceptionIfOneArgumentFunctionDoesNotExist() {
        LiveHttpRequest request = get("/foo")
                .header("Host", "www.hotels.com")
                .cookies(requestCookie("lang", "en_US|en-us_hotels_com"))
                .build();

        functionResolver.resolveFunction("foobar", singletonList("barfoo")).call(request, context);
    }
}