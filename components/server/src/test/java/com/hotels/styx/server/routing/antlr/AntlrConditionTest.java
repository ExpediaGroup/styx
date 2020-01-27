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
package com.hotels.styx.server.routing.antlr;

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpMethod;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.RequestCookie;
import com.hotels.styx.server.routing.Condition;
import org.junit.jupiter.api.Test;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpRequest.post;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.support.Support.requestContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AntlrConditionTest {
    final Condition.Parser parser = new AntlrConditionParser.Builder()
            .registerFunction("method", (request, context) -> request.method().name())
            .registerFunction("path", (request, context) -> request.path())
            .registerFunction("userAgent", (request, context) -> request.header(USER_AGENT).orElse(""))
            .registerFunction("header", (request, context, input) -> request.header(input).orElse(""))
            .registerFunction("cookie", (request, context, input) ->
                    request.cookie(input).map(RequestCookie::value).orElse(""))
            .build();

    private static LiveHttpRequest.Builder newRequest(String uri) {
        return new LiveHttpRequest.Builder(HttpMethod.GET, uri);
    }

    private static LiveHttpRequest.Builder newRequest() {
        return get("/blah");
    }

    private Condition condition(String condition) {
        return parser.parse(condition);
    }

    private final HttpInterceptor.Context context = requestContext();

    @Test
    public void matchesRequestPath() {
        Condition condition = condition("path() == '/some-request'");
        assertThat(condition.evaluate(newRequest("/some-request").build(), context), is(true));
    }

    @Test
    public void notMatchingRequestPath() {
        Condition condition = condition("path() == '/some-request'");
        assertThat(condition.evaluate(newRequest("/random-request").build(), context), is(false));
    }

    @Test
    public void matchesHttpHeader() {
        Condition condition = condition("header('Host') == 'bbc.co.uk'");
        LiveHttpRequest request = newRequest("/path")
                .header(HOST, "bbc.co.uk")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        LiveHttpRequest request2 = newRequest("/path")
                .header(HOST, "hotels.com")
                .build();
        assertThat(condition.evaluate(request2, context), is(false));

        LiveHttpRequest request3 = newRequest("/path")
                .build();
        assertThat(condition.evaluate(request3, context), is(false));
    }

    @Test
    public void acceptsDoubleQuotesAsRegexp() throws Exception {
        assertThat(
                condition("path() =~ \"/shop/.*\"").evaluate(newRequest("/shop/x").build(), context),
                is(true));
    }

    @Test
    public void acceptsSingleQuotesAsRegexp() throws Exception {
        assertThat(
                condition("path() =~ '/shop/.*'").evaluate(newRequest("/shop/x").build(), context),
                is(true));
    }

    @Test
    public void acceptsSingleQuotesAsFunctionArgument() throws Exception {
        Condition condition = condition("header('Host') =~ '.*\\.co\\.uk'");

        LiveHttpRequest request = newRequest()
                .header(HOST, "bbc.co.uk")
                .build();
        assertThat(condition.evaluate(request, context), is(true));
    }

    @Test
    public void acceptsDoubleQuotesAsFunctionArgument() throws Exception {
        Condition condition = condition("header(\"Host\") =~ '.*\\.co\\.uk'");

        LiveHttpRequest request = newRequest()
                .header(HOST, "bbc.co.uk")
                .build();
        assertThat(condition.evaluate(request, context), is(true));
    }

    @Test
    public void acceptsSingleQuotesAsRightHandSide() throws Exception {
        assertThat(
                condition("path() =~ '/shop/.*'").evaluate(newRequest("/shop/x").build(), context),
                is(true));
    }

    @Test
    public void acceptsDoubleQuotesAsRightHandSide() throws Exception {
        assertThat(
                condition("path() =~ \"^/shop/.*\"").evaluate(newRequest("/shop/x").build(), context),
                is(true));
    }


    @Test
    public void matchesHeaderPresence() {
        Condition condition = condition("header('Host')");

        LiveHttpRequest request = newRequest("/foo")
                .header(HOST, "bbc.co.uk")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        LiveHttpRequest request2 = newRequest("/foo")
                .build();

        assertThat(condition.evaluate(request2, context), is(false));
    }

    @Test
    public void regexpMatchesHttpHeader() {
        Condition condition = condition("header('Host') =~ '.*\\.co\\.uk'");

        LiveHttpRequest request = newRequest()
                .header(HOST, "bbc.co.uk")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        LiveHttpRequest request2 = request.newBuilder()
                .header(HOST, "hotels.com")
                .build();
        assertThat(condition.evaluate(request2, context), is(false));

        LiveHttpRequest request3 = request.newBuilder()
                .header(HOST, "hotels.co.uk")
                .build();
        assertThat(condition.evaluate(request3, context), is(true));

        LiveHttpRequest request4 = newRequest()
                .build();
        assertThat(condition.evaluate(request4, context), is(false));
    }

    @Test
    public void pathPrefixReges() {
        Condition condition = condition("path() =~ '.foo.*'");

        assertThat(
                condition.evaluate(get("/foo/bar").build(), context),
                is(true));
    }

    @Test
    public void matchesAndExpressions() {
        Condition condition = condition("header('Host') == 'bbc.co.uk' AND header('Content-Length') == '7'");

        LiveHttpRequest request = newRequest()
                .header(HOST, "bbc.co.uk")
                .header(CONTENT_LENGTH, "7")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        LiveHttpRequest request2 = newRequest()
                .header(HOST, "bbc.co.uk")
                .header(CONTENT_LENGTH, "1")
                .build();
        assertThat(condition.evaluate(request2, context), is(false));

        LiveHttpRequest request3 = newRequest()
                .header(HOST, "hotels.com")
                .header(CONTENT_LENGTH, "7")
                .build();
        assertThat(condition.evaluate(request3, context), is(false));

        LiveHttpRequest request4 = newRequest()
                .header(CONTENT_LENGTH, "7")
                .build();
        assertThat(condition.evaluate(request4, context), is(false));

        LiveHttpRequest request5 = newRequest()
                .header(HOST, "bbc.co.uk")
                .build();
        assertThat(condition.evaluate(request5, context), is(false));
    }

    @Test
    public void combinesStringEqualsAndStringRegexpMatchOperationsWithAndOperator() {
        Condition condition = condition("header('Host') == 'bbc.co.uk' AND header('Content-Length') =~ '[123][0-9]'");
        LiveHttpRequest request = newRequest()
                .header(HOST, "bbc.co.uk")
                .header(CONTENT_LENGTH, "20")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        LiveHttpRequest request2 = newRequest()
                .header(HOST, "bbc.co.uk")
                .header(CONTENT_LENGTH, "70")
                .build();
        assertThat(condition.evaluate(request2, context), is(false));
    }

    @Test
    public void matchesCompositionOfExpressionsWithEnd() {
        Condition condition = condition(
                "header('Host') == 'bbc.co.uk' AND header('Content-Length') == '7' AND header('App-Name')=='app1'");

        LiveHttpRequest request = newRequest()
                .header(HOST, "bbc.co.uk")
                .header(CONTENT_LENGTH, "7")
                .header("App-Name", "app1")
                .build();
        assertThat(condition.evaluate(request, context), is(true));
    }


    @Test
    public void matchesOrExpressions() {
        Condition condition = condition("header('Host') == 'bbc.co.uk' OR header('Content-Length') == '7'");

        LiveHttpRequest request = newRequest()
                .header(HOST, "bbc.co.uk")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        LiveHttpRequest request2 = newRequest()
                .header(HOST, "hotels.com")
                .header(CONTENT_LENGTH, "7")
                .build();
        assertThat(condition.evaluate(request2, context), is(true));

        LiveHttpRequest request3 = newRequest()
                .header(HOST, "hotels.com")
                .header(CONTENT_LENGTH, "8")
                .build();
        assertThat(condition.evaluate(request3, context), is(false));
    }

    @Test
    public void andExpressionsHasHigherPrecedenceThanOrExpression() {
        Condition condition = condition("header('Host') == 'bbc.co.uk' " +
                "AND header('Content-Length') == '7' " +
                "OR header('App-Name') =~ 'app[0-9]'");

        LiveHttpRequest request = newRequest()
                .header("App-Name", "app5")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        request = newRequest()
                .header(HOST, "bbc.co.uk")
                .header(CONTENT_LENGTH, "7")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        request = newRequest()
                .header(HOST, "bbc.co.uk")
                .header(CONTENT_LENGTH, "8")
                .build();
        assertThat(condition.evaluate(request, context), is(false));

        request = newRequest()
                .header(HOST, "hotels.com")
                .header(CONTENT_LENGTH, "7")
                .header("App-Name", "appX")
                .build();
        assertThat(condition.evaluate(request, context), is(false));
    }

    @Test
    public void andExpressionsHasHigherPrecedenceThanOrExpressionOrBeforeAnd() {
        Condition condition = condition("header('App-Name') =~ 'app[0-9]' " +
                "OR header('Host') == 'bbc.co.uk' " +
                "AND header('Content-Length') == '7'");

        LiveHttpRequest request = newRequest()
                .header("App-Name", "app5")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        request = newRequest()
                .header(HOST, "bbc.co.uk")
                .header(CONTENT_LENGTH, "7")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        request = newRequest()
                .header(HOST, "bbc.co.uk")
                .header(CONTENT_LENGTH, "8")
                .build();
        assertThat(condition.evaluate(request, context), is(false));

        request = newRequest()
                .header(HOST, "hotels.com")
                .header(CONTENT_LENGTH, "7")
                .header("App-Name", "appX")
                .build();
        assertThat(condition.evaluate(request, context), is(false));
    }

    @Test
    public void notExpressionNegatesTheExpressionResult() {
        Condition condition = condition("NOT header('Host')");
        LiveHttpRequest request1 = newRequest()
                .header(HOST, "bbc.co.uk")
                .build();
        assertThat(condition.evaluate(request1, context), is(false));

        LiveHttpRequest request2 = newRequest()
                .header(CONTENT_LENGTH, 7)
                .build();
        assertThat(condition.evaluate(request2, context), is(true));
    }

    @Test
    public void parenthesisChangesOperatorPrecedence() {
        Condition condition = condition("header('Host') " +
                "AND (header('App-Name') =~ 'app[0-9]' OR header('App-Name') =~ 'shop[0-9]')");

        LiveHttpRequest request1 = newRequest()
                .header(HOST, "bbc.co.uk")
                .header("App-Name", "app1")
                .build();
        assertThat(condition.evaluate(request1, context), is(true));

        LiveHttpRequest request2 = newRequest()
                .header(HOST, "bbc.co.uk")
                .header("App-Name", "shop2")
                .build();
        assertThat(condition.evaluate(request2, context), is(true));

        LiveHttpRequest request3 = newRequest()
                .header(HOST, "bbc.co.uk")
                .header("App-Name", "landing3")
                .build();
        assertThat(condition.evaluate(request3, context), is(false));
    }

    @Test
    public void notExpressionHasHigherPrecedenceThanAndExpression() {
        Condition condition = condition("header('Host') " +
                "AND NOT header('App-Name') =~ 'app[0-9]' OR header('App-Name') =~ 'shop[0-9]'");

        LiveHttpRequest request1 = newRequest()
                .header(HOST, "bbc.co.uk")
                .header("App-Name", "landing1")
                .build();
        assertThat(condition.evaluate(request1, context), is(true));

        LiveHttpRequest request2 = newRequest()
                .header(HOST, "bbc.co.uk")
                .header("App-Name", "app2")
                .build();
        assertThat(condition.evaluate(request2, context), is(false));

        LiveHttpRequest request3 = newRequest()
                .header(HOST, "bbc.co.uk")
                .header("App-Name", "shop1")
                .build();
        assertThat(condition.evaluate(request3, context), is(true));

    }

    @Test
    public void cookieValueIsPresent() {
        Condition condition = condition("cookie('TheCookie')");
        LiveHttpRequest request = newRequest()
                .cookies(requestCookie("TheCookie", "foobar-foobar-baz"))
                .header("App-Name", "app3")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        request = newRequest()
                .cookies(requestCookie("AnotherCookie", "foobar-foobar-baz"))
                .header("App-Name", "app3")
                .build();
        assertThat(condition.evaluate(request, context), is(false));

        request = newRequest()
                .header("App-Name", "app3")
                .build();
        assertThat(condition.evaluate(request, context), is(false));
    }


    @Test
    public void cookieValueMatchesWithString() {
        Condition condition = condition("cookie('TheCookie') == 'foobar-foobar-baz'");
        LiveHttpRequest request = newRequest()
                .cookies(requestCookie("TheCookie", "foobar-foobar-baz"))
                .header("App-Name", "app3")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        request = newRequest()
                .cookies(requestCookie("AnotherCookie", "foobar-baz"))
                .header("App-Name", "app3")
                .build();
        assertThat(condition.evaluate(request, context), is(false));

        request = newRequest()
                .header("App-Name", "app3")
                .build();
        assertThat(condition.evaluate(request, context), is(false));
    }

    @Test
    public void cookieValueMatchesWithRegexp() {
        Condition condition = condition("cookie('TheCookie') =~ 'foobar-.*-baz'");

        LiveHttpRequest request = newRequest()
                .cookies(requestCookie("TheCookie", "foobar-foobar-baz"))
                .header("App-Name", "app3")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        request = newRequest()
                .cookies(requestCookie("AnotherCookie", "foobar-x-baz"))
                .header("App-Name", "app3")
                .build();
        assertThat(condition.evaluate(request, context), is(false));

        request = newRequest()
                .header("App-Name", "app3")
                .build();
        assertThat(condition.evaluate(request, context), is(false));
    }

    @Test
    public void methodMatchesString() {
        Condition condition = condition("method() == 'GET'");
        LiveHttpRequest request = get("/blah")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        request = post("/blah")
                .build();
        assertThat(condition.evaluate(request, context), is(false));
    }

    @Test
    public void userAgentMatchesUserAgent() {
        Condition condition = condition("userAgent() == 'Mozilla Firefox 1.1.2' OR userAgent() =~ 'Safari.*'");

        LiveHttpRequest request = get("/blah")
                .header(USER_AGENT, "Mozilla Firefox 1.1.2")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        request = get("/blah")
                .header(USER_AGENT, "Mozilla Firefox 1.1.25")
                .build();
        assertThat(condition.evaluate(request, context), is(false));

        request = get("/blah")
                .header(USER_AGENT, "Foxzilla x.y.z")
                .build();
        assertThat(condition.evaluate(request, context), is(false));

        request = get("/blah")
                .header(USER_AGENT, "Safari-XYZ")
                .build();
        assertThat(condition.evaluate(request, context), is(true));

        request = post("/blah")
                .build();
        assertThat(condition.evaluate(request, context), is(false));
    }

    @Test
    public void throwsSyntaxErrorForIncompleteCondition() {
        assertThrows(DslSyntaxError.class, () -> condition("queryString"));
    }

    @Test
    public void throwsSyntaxErrorWhenQuoteIsNotClosed() {
        assertThrows(DslSyntaxError.class, () -> condition("queryString('foobar)"));
    }

    @Test
    public void throwsErrorWhenCloseParenthesisIsMissing() {
        assertThrows(DslSyntaxError.class, () -> condition("queryString('foobar' AND userAgent()"));
    }

    @Test
    public void throwsIllegalArgumentExceptionWhenTooManyArgumentsAreProvided() {
        assertThrows(IllegalArgumentException.class, () -> condition("queryString('foobar', 'blah')"));
    }
}