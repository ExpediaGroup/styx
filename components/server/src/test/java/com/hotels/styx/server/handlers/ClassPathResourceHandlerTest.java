/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.server.HttpInterceptorContext;
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
import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ClassPathResourceHandlerTest {
    ClassPathResourceHandler handler = new ClassPathResourceHandler("/admin/dashboard");

    @Test
    public void readsClassPathResources() {
        HttpRequest request = get("/admin/dashboard/expected.txt").build();
        HttpResponse response = Mono.from(handler.handle(request, HttpInterceptorContext.create())).block();

        assertThat(response.status(), is(OK));
        assertThat(body(response), is("Foo\nBar\n"));
    }

    private static String body(HttpResponse response) {
        return response.bodyAs(UTF_8).replace(lineSeparator(), "\n");
    }

    @Test
    public void returns404IfResourceDoesNotExist() {
        HttpRequest request = get("/admin/dashboard/unexpected.txt").build();
        HttpResponse response = Mono.from(handler.handle(request, HttpInterceptorContext.create())).block();

        assertThat(response.status(), is(NOT_FOUND));
    }

    private static Stream<Arguments> forbiddenPaths() {
        return Stream.of(
                Arguments.of("/admin/forbidden.txt"),
                Arguments.of("/admin/dashboard/../forbidden.txt"),
                Arguments.of("/admin/dashboard.txt")
        );
    }


    @ParameterizedTest
    @MethodSource("forbiddenPaths")
    public void returns403IfTryingToAccessResourcesOutsidePermittedRoot(String path) {
        HttpRequest request = get(path).build();
        HttpResponse response = Mono.from(handler.handle(request, HttpInterceptorContext.create())).block();

        assertThat(response.status(), is(FORBIDDEN));
    }
}