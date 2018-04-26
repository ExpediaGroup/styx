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
package com.hotels.styx.proxy.interceptors;

import com.hotels.styx.api.HttpInterceptor.Chain;
import com.hotels.styx.api.HttpRequest;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.proxy.interceptors.RequestRecordingChain.requestRecordingChain;
import static com.hotels.styx.proxy.interceptors.ReturnResponseChain.returnsResponse;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class UnexpectedRequestContentLengthRemoverTest {
    final Chain ANY_RESPONSE_HANDLER = returnsResponse(response().build());
    final UnexpectedRequestContentLengthRemover interceptor = new UnexpectedRequestContentLengthRemover();

    @Test
    public void removesContentLengthIfBothContentLengthAndChunkedHeaderExists() throws Exception {
        HttpRequest request = get("/foo")
                .header(CONTENT_LENGTH, "50")
                .header(TRANSFER_ENCODING, CHUNKED).build();

        HttpRequest interceptedRequest = interceptRequest(request);

        assertThat(interceptedRequest.contentLength(), isAbsent());
        assertThat(interceptedRequest.chunked(), is(true));
    }

    private HttpRequest interceptRequest(HttpRequest request) {
        RequestRecordingChain recording = requestRecordingChain(ANY_RESPONSE_HANDLER);
        interceptor.intercept(request, recording);
        return recording.recordedRequest();
    }
}