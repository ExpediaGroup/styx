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
package com.hotels.styx.plugins;


import com.hotels.styx.api.FormData;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpRequest.DecodedRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.http.handlers.HttpMethodFilteringHandler;
import com.hotels.styx.api.plugins.spi.Plugin;
import rx.Observable;

import java.util.List;
import java.util.Map;

import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.lang.String.format;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class FormUrlEncodedDataTesterPlugin implements Plugin {

    @Override
    public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        return chain.proceed(request);
    }

    @Override
    public Map<String, HttpHandler> adminInterfaceHandlers() {
        return singletonMap("foo", new HttpMethodFilteringHandler(POST, this::buildResponse));
    }

    private Observable<HttpResponse> buildResponse(HttpRequest req) {
        return req.decodePostParams(100).map(request ->
                        response(OK)
                                .body(paramsFrom(request)
                                        .stream()
                                        .collect(joining("\n")))
                                .build()
        );
    }

    private List<String> paramsFrom(DecodedRequest<FormData> dataRequest) {
        return dataRequest.body()
                .parameters()
                .entrySet()
                .stream()
                .map(entry -> format("%s: %s", entry.getKey(), entry.getValue()))
                .collect(toList());
    }
}
