/**
 * Copyright (C) 2013-2017 Expedia Inc.
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
package com.hotels.styx.servers;

import com.github.tomakehurst.wiremock.http.BasicResponseRenderer;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.HttpHeader;
import com.hotels.styx.api.messages.FullHttpResponse;
import io.netty.util.AsciiString;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.github.tomakehurst.wiremock.http.HttpHeader.httpHeader;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class WiremockResponseConverterTest {

    @Test
    public void convertsCreatedResponse() {
        FullHttpResponse<String> styxResponse = WiremockResponseConverter.toStyxResponse(new BasicResponseRenderer().render(ResponseDefinition.created()));

        assertThat(styxResponse.status(), is(CREATED));
        assertThat(styxResponse.header("Content-Length"), is(Optional.empty()));
        assertThat(styxResponse.body(), is(""));
    }

    @Test
    public void convertsResponseWithBody() {
        ResponseDefinition response = new ResponseDefinition(HTTP_OK, "{ \"count\" : 0, \"requestJournalDisabled\" : false}");
        response.setHeaders(new HttpHeaders(
                httpHeader("Content-Length", "48"),
                httpHeader("Content-Type", "application/json")));

        FullHttpResponse<String> styxResponse = WiremockResponseConverter.toStyxResponse(new BasicResponseRenderer().render(response));

        assertThat(styxResponse.status(), is(OK));
        Map<String, String> actual = headersAsMap(styxResponse);
        Map<String, String> of = ImmutableMap.of("Content-Length", "48", "Content-Type", "application/json");

        assertThat(actual, is(of));
        assertThat(styxResponse.body(), is("{ \"count\" : 0, \"requestJournalDisabled\" : false}"));
    }

    private Map<String, String> headersAsMap(FullHttpResponse<?> response) {
        Spliterator<HttpHeader> spliterator = response.headers().spliterator();

        return StreamSupport.stream(spliterator, false)
                .collect(Collectors.toMap(HttpHeader::name, HttpHeader::value));
    }


}