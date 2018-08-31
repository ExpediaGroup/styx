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
package com.hotels.styx.servers;

import com.github.tomakehurst.wiremock.http.BasicResponseRenderer;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.HttpHeader;
import com.hotels.styx.api.FullHttpResponse;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.github.tomakehurst.wiremock.http.HttpHeader.httpHeader;
import static com.hotels.styx.api.HttpResponseStatus.CREATED;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.servers.WiremockResponseConverter.toStyxResponse;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class WiremockResponseConverterTest {

    @Test
    public void convertsCreatedResponse() {
        ResponseDefinition created = ResponseDefinition.created();
        Response render = new BasicResponseRenderer().render(created);

        FullHttpResponse styxResponse = toStyxResponse(render);

        assertThat(styxResponse.status(), is(CREATED));
        assertThat(styxResponse.bodyAs(UTF_8), is(""));
        assertThat(headerCount(styxResponse.headers()), is(0));
    }

    @Test
    public void convertsResponseWithBody() {
        ResponseDefinition response = new ResponseDefinition(HTTP_OK, "{ \"count\" : 0, \"requestJournalDisabled\" : false}");
        response.setHeaders(new HttpHeaders(
                httpHeader("Transfer-Encoding", "chunked"),
                httpHeader("Content-Type", "application/json")));

        FullHttpResponse styxResponse = toStyxResponse(new BasicResponseRenderer().render(response));

        assertThat(styxResponse.status(), is(OK));
        Map<String, String> actual = headersAsMap(styxResponse);

        assertThat(actual, is(ImmutableMap.of(
                "Transfer-Encoding", "chunked",
                "Content-Type", "application/json")));
        assertThat(styxResponse.bodyAs(UTF_8), is("{ \"count\" : 0, \"requestJournalDisabled\" : false}"));
        assertThat(headerCount(styxResponse.headers()), is(2));
    }

    private Map<String, String> headersAsMap(FullHttpResponse response) {
        Spliterator<HttpHeader> spliterator = response.headers().spliterator();

        return StreamSupport.stream(spliterator, false)
                .collect(Collectors.toMap(HttpHeader::name, HttpHeader::value));
    }

    private int headerCount(com.hotels.styx.api.HttpHeaders headers) {
        AtomicInteger count = new AtomicInteger();
        headers.forEach(header -> count.incrementAndGet());
        return count.get();
    }

}