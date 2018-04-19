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
package com.hotels.styx.api.v2;

import com.hotels.styx.api.HttpResponse;
import org.testng.annotations.Test;

import java.util.Optional;

import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ResponseStreamImplTest {

//    @Test
//    public void joinsResponseStreamsSynchronously() throws Exception {
//        StyxObservable<HttpResponse> s1 = StyxObservable.of(response(OK).build());
//
//        StyxObservable<HttpResponse> s2 = s1.map(response -> response.newBuilder().header("X-Test", "ok").build());
//
//        HttpResponse response = ((StyxCoreObservable<HttpResponse>) s2).delegate().toBlocking().first();
//        assertThat(response.header("X-Test"), is(Optional.of("ok")));
//    }
//
//    @Test
//    public void joinsResponseStreamsAsynchronously() throws Exception {
//        StyxObservable<HttpResponse> s1 = StyxObservable.of(response(OK).build());
//
//        StyxObservable<HttpResponse> s2 = s1.flatMap(response -> StyxObservable.of(response.newBuilder().header("X-Test", "ok").build()));
//
//        HttpResponse response = ((StyxCoreObservable<HttpResponse>) s2).delegate().toBlocking().first();
//        assertThat(response.header("X-Test"), is(Optional.of("ok")));
//    }
//
//    @Test
//    public void createsFromFuture() throws Exception {
//        StyxObservable<HttpResponse> stream = StyxObservable.of(response(OK).build());
//
//        HttpResponse response = ((StyxCoreObservable<HttpResponse>) stream).delegate().toBlocking().first();
//        assertThat(response.status(), is(OK));
//
//    }
}