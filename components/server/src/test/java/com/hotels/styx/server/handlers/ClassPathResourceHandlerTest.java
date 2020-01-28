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
package com.hotels.styx.server.handlers;

import com.hotels.styx.api.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;

import java.util.stream.Stream;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.FORBIDDEN;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.support.Support.requestContext;
import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ClassPathResourceHandlerTest {

    @Test
    void servesResourcesFromClassPath() {
        HttpResponse response = Mono.from(
                new ClassPathResourceHandler("/admin/dashboard")
                        .handle(get("/admin/dashboard/expected.txt").build(), requestContext()))
                .block();

        assertThat(response.status(), is(OK));
        assertThat(body(response), is("Foo\nBar\n"));
    }

    @Test
    void servesResourcesForCorrectlyPrefixedRequests() {
        HttpResponse response = Mono.from(
                new ClassPathResourceHandler("/a/prefix/", "/admin/dashboard")
                        .handle(get("/a/prefix/expected.txt").build(), requestContext()))
                .block();

        assertThat(response.status(), is(OK));
        assertThat(body(response), is("Foo\nBar\n"));
    }

    @Test
    void removesDuplicatePathSeparators() {
        HttpResponse response = Mono.from(
                new ClassPathResourceHandler("/a/prefix/", "/admin/dashboard")
                        .handle(get("/a/prefix/expected.txt").build(), requestContext()))
                .block();

        assertThat(response.status(), is(OK));
        assertThat(body(response), is("Foo\nBar\n"));
    }
    @Test
    void returns404IfResourceDoesNotExist() {
        HttpResponse response = Mono.from(
                new ClassPathResourceHandler("/admin/dashboard")
                        .handle(get("/admin/dashboard/unexpected.txt").build(), requestContext()))
                .block();

        assertThat(response.status(), is(NOT_FOUND));
    }

    @ParameterizedTest
    @MethodSource("forbiddenPaths")
    void returns403IfTryingToAccessResourcesOutsidePermittedRoot(String path) {
        HttpResponse response = Mono.from(new ClassPathResourceHandler("/admin/dashboard")
                .handle(get(path).build(), requestContext())).block();

        assertThat(response.status(), is(FORBIDDEN));
    }

    @ParameterizedTest
    @MethodSource("forbiddenPaths")
    void returnsForbiddenIfPrefixedRequestAttemptsToAccessResourcesOutsidePermittedRoot(String path) {
        HttpResponse response = Mono.from(
                new ClassPathResourceHandler("/admin/dashboard", "/admin/dashboard")
                        .handle(get(path).build(), requestContext()))
                .block();

        assertThat(response.status(), is(FORBIDDEN));
    }

    private static String body(HttpResponse response) {
        return response.bodyAs(UTF_8).replace(lineSeparator(), "\n");
    }

    private static Stream<Arguments> forbiddenPaths() {
        return Stream.of(
                Arguments.of("/admin/forbidden.txt"),
                Arguments.of("/admin/dashboard/../forbidden.txt"),
                Arguments.of("/admin/dashboard.txt")
        );
    }

}