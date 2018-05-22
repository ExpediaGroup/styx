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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.events.EventNexus;
import com.hotels.styx.events.Event;
import com.hotels.styx.support.api.HttpMessageBodies;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.subjects.PublishSubject;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProxyStatusHandlerTest {
    private HttpRequest request;
    private ProxyStatusHandler handler;
    private PublishSubject<Event> publishSubject;

    @BeforeMethod
    public void setUp() {
        request = get("/").build();
        publishSubject = PublishSubject.create();
        EventNexus eventNexus = mock(EventNexus.class);
        when(eventNexus.events(anyString())).thenReturn(publishSubject);
        handler = new ProxyStatusHandler(eventNexus);
    }

    @Test
    public void initiallyResponseIsFalse() {
        String response = handler.handle(request)
                .map(HttpMessageBodies::bodyAsString)
                .toBlocking()
                .single();

        assertThat(response, is("false"));
    }

    @Test
    public void afterEventIsFiredResponseIsTrue() {
        publishSubject.onNext(new Event("server.started.proxy", true));
        publishSubject.onCompleted();

        String response = handler.handle(request)
                .map(HttpMessageBodies::bodyAsString)
                .toBlocking()
                .single();

        assertThat(response, is("true"));
    }
}