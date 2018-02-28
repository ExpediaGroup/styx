/**
 * Copyright (C) 2013-2018 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.server.handlers;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.messages.FullHttpResponse;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.messages.HttpResponseStatus.*;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ClassPathResourceHandlerTest {
    ClassPathResourceHandler handler = new ClassPathResourceHandler("/admin/dashboard");

    @Test
    public void readsClassPathResources() throws IOException {
        String expectedMessage = "Foo"+System.lineSeparator()+"Bar"+System.lineSeparator();
        HttpRequest request = get("/admin/dashboard/expected.txt").build();
        FullHttpResponse response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(OK));
        assertThat(response.contentLength(), isValue(expectedMessage.length()));
        assertThat(response.bodyAs(UTF_8), is(expectedMessage));
    }

    @Test
    public void returns404IfResourceDoesNotExist() throws IOException {
        HttpRequest request = get("/admin/dashboard/unexpected.txt").build();
        FullHttpResponse response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(NOT_FOUND));
    }

    @DataProvider(name = "forbiddenPaths")
    private static Object[][] illegalPrefixes() {
        return new Object[][]{
                {"/admin/forbidden.txt"},
                {"/admin/dashboard/../forbidden.txt"},
                {"/admin/dashboard.txt"},
        };
    }


    @Test(dataProvider = "forbiddenPaths")
    public void returns403IfTryingToAccessResourcesOutsidePermittedRoot(String path) throws IOException {
        HttpRequest request = get(path).build();
        FullHttpResponse response = waitForResponse(handler.handle(request));

        assertThat(response.status(), is(FORBIDDEN));
    }
}