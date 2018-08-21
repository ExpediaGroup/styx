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
package com.hotels.styx.server;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CompositeHttpErrorStatusListenerTest {
    CompositeHttpErrorStatusListener listener;
    HttpErrorStatusListener delegate1;
    HttpErrorStatusListener delegate2;
    HttpErrorStatusListener delegate3;
    HttpRequest request = HttpRequest.get("/foo").build();
    HttpResponse response = HttpResponse.response(OK).build();

    @BeforeMethod
    public void setUp() {
        delegate1 = mock(HttpErrorStatusListener.class);
        delegate2 = mock(HttpErrorStatusListener.class);
        delegate3 = mock(HttpErrorStatusListener.class);
        listener = new CompositeHttpErrorStatusListener(asList(delegate1, delegate2, delegate3));
    }

    @Test
    public void propagatesProxyErrorsWithCause() {
        IOException cause = new IOException();
        listener.proxyErrorOccurred(cause);

        verify(delegate1).proxyErrorOccurred(cause);
        verify(delegate2).proxyErrorOccurred(cause);
        verify(delegate3).proxyErrorOccurred(cause);
    }

    @Test
    public void propagatesProxyErrorsWithStatusAndCause() {
        IOException cause = new IOException();
        listener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, cause);

        verify(delegate1).proxyErrorOccurred(INTERNAL_SERVER_ERROR, cause);
        verify(delegate2).proxyErrorOccurred(INTERNAL_SERVER_ERROR, cause);
        verify(delegate3).proxyErrorOccurred(INTERNAL_SERVER_ERROR, cause);
    }

    @Test
    public void propagatesProxyWriteFailures() {
        IOException cause = new IOException();
        listener.proxyWriteFailure(request, response, cause);

        verify(delegate1).proxyWriteFailure(request, response, cause);
        verify(delegate2).proxyWriteFailure(request, response, cause);
        verify(delegate3).proxyWriteFailure(request, response, cause);
    }

    @Test
    public void propagatesProxyingFailures() {
        IOException cause = new IOException();
        listener.proxyingFailure(request, response, cause);

        verify(delegate1).proxyingFailure(request, response, cause);
        verify(delegate2).proxyingFailure(request, response, cause);
        verify(delegate3).proxyingFailure(request, response, cause);
    }

    @Test
    public void propagatesProxyErrorsWithRequests() {
        HttpRequest request = HttpRequest.get("/foo").build();
        IOException cause = new IOException();
        listener.proxyErrorOccurred(request, INTERNAL_SERVER_ERROR, cause);

        verify(delegate1).proxyErrorOccurred(request, INTERNAL_SERVER_ERROR, cause);
        verify(delegate2).proxyErrorOccurred(request, INTERNAL_SERVER_ERROR, cause);
        verify(delegate3).proxyErrorOccurred(request, INTERNAL_SERVER_ERROR, cause);
    }
}