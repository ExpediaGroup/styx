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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@TestInstance(PER_CLASS)
public class SanitisedHttpMessageFormatterTest {

    private static final HttpRequest httpRequest = get("/")
            .version(HTTP_1_1)
            .header("HeaderName", "HeaderValue")
            .build();

    private static final HttpResponse httpResponse = new HttpResponse.Builder()
            .version(HTTP_1_1)
            .header("HeaderName", "HeaderValue")
            .build();

    private static final String FORMATTED_HEADERS = "headers";
    private static final String HTTP_REQUEST_PATTERN = "\\{version=HTTP/1.1, method=GET, uri=/, headers=\\[" + FORMATTED_HEADERS + "\\], id=[a-zA-Z0-9-]*}";
    private static final String HTTP_RESPONSE_PATTERN = "\\{version=HTTP/1.1, status=200 OK, headers=\\[" + FORMATTED_HEADERS + "\\]}";

    @Mock
    private SanitisedHttpHeaderFormatter sanitisedHttpHeaderFormatter;

    private SanitisedHttpMessageFormatter sanitisedHttpMessageFormatter;

    @BeforeAll
    public void setup() {
        MockitoAnnotations.initMocks(this);
        sanitisedHttpMessageFormatter = new SanitisedHttpMessageFormatter(sanitisedHttpHeaderFormatter);
        when(sanitisedHttpHeaderFormatter.format(any())).thenReturn(FORMATTED_HEADERS);
    }

    @Test
    public void shouldFormatHttpRequest() {
        String formattedRequest = sanitisedHttpMessageFormatter.formatRequest(httpRequest);
        assertMatchesRegex(formattedRequest, HTTP_REQUEST_PATTERN);
    }

    @Test
    public void shouldFormatLiveHttpRequest() {
        String formattedRequest = sanitisedHttpMessageFormatter.formatRequest(httpRequest.stream());
        assertMatchesRegex(formattedRequest, HTTP_REQUEST_PATTERN);
    }

    @Test
    public void shouldFormatHttpResponse() {
        String formattedResponse = sanitisedHttpMessageFormatter.formatResponse(httpResponse);
        assertMatchesRegex(formattedResponse, HTTP_RESPONSE_PATTERN);
    }

    @Test
    public void shouldFormatLiveHttpResponse() {
        String formattedResponse = sanitisedHttpMessageFormatter.formatResponse(httpResponse.stream());
        assertMatchesRegex(formattedResponse, HTTP_RESPONSE_PATTERN);
    }

    private void assertMatchesRegex(String actual, String expected) {
        assertTrue(actual.matches(expected),
                "\n\nPattern to match: " + expected + "\nActual result:    " + actual + "\n\n");
    }

}