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
package com.hotels.styx.common.format;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static org.testng.Assert.assertTrue;

public class HttpMessageFormatterTest {

    private static final HttpRequest httpRequest = new HttpRequest.Builder()
            .version(HTTP_1_1)
            .header("HeaderName", "HeaderValue")
            .build();

    private static final HttpResponse httpResponse = new HttpResponse.Builder()
            .version(HTTP_1_1)
            .header("HeaderName", "HeaderValue")
            .build();

    private static final String HTTP_REQUEST_PATTERN = "\\{version=HTTP/1.1, method=GET, url=/, headers=\\[HeaderName:HeaderValue\\], id=[a-zA-Z0-9-]*}";
    private static final String HTTP_RESPONSE_PATTERN = "\\{version=HTTP/1.1, status=200 OK, headers=\\[HeaderName:HeaderValue\\]}";

    @Test
    public void shouldFormatHttpRequest() {
        String formattedRequest = HttpMessageFormatter.formatRequest(httpRequest);
        String expected = "HttpRequest" + HTTP_REQUEST_PATTERN;

        assertMatchesRegex(formattedRequest, expected);
    }

    @Test
    public void shouldFormatLiveHttpRequest() {
        String formattedRequest = HttpMessageFormatter.formatRequest(httpRequest.stream());
        String expected = "LiveHttpRequest" + HTTP_REQUEST_PATTERN;

        assertMatchesRegex(formattedRequest, expected);
    }

    @Test
    public void shouldFormatHttpResponse() {
        String formattedResponse = HttpMessageFormatter.formatResponse(httpResponse);
        String expected = "HttpResponse" + HTTP_RESPONSE_PATTERN;

        assertMatchesRegex(formattedResponse, expected);
    }

    @Test
    public void shouldFormatLiveHttpResponse() {
        String formattedResponse = HttpMessageFormatter.formatResponse(httpResponse.stream());
        String expected = "LiveHttpResponse" + HTTP_RESPONSE_PATTERN;

        assertMatchesRegex(formattedResponse, expected);
    }

    private void assertMatchesRegex(String actual, String expected) {
        assertTrue(actual.matches(expected),
                "\n\nPattern to match: " + expected + "\nActual result:    " + actual + "\n\n");
    }

}