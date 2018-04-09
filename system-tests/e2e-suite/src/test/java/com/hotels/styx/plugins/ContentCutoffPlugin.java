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

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import static com.hotels.styx.api.HttpMessageBody.utf8String;

public class ContentCutoffPlugin implements Plugin {
    private static Logger LOGGER = LoggerFactory.getLogger(ContentCutoffPlugin.class);

    @Override
    public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        return chain.proceed(request)
                .flatMap(response -> {
                    Observable<String> body = response.body()
                            .decode(utf8String(), 1024);
                    return body
                            .doOnNext(content -> LOGGER.info("Throw away response body={}", content))
                            .flatMap(content -> Observable.just(response));
                });

    }
}
