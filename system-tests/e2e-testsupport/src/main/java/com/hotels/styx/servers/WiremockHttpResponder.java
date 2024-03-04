/*
  Copyright (C) 2013-2024 Expedia Inc.

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

import com.github.tomakehurst.wiremock.http.HttpResponder;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;

import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class WiremockHttpResponder implements HttpResponder {
    private Response response;

    @Override
    public void respond(Request request, Response response, Map<String, Object> map) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        delayIfRequired(response.getInitialDelay());
        this.response = response;
    }

    public Response getResponse() {
        return response;
    }

    private void delayIfRequired(long delayMillis) {
        try {
            MILLISECONDS.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
