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
package com.hotels.styx;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;

import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;

/**
 * This plugin serves as an example of how you can create a Styx plugin.
 *
 * You can change it in whatever way you like (provided it still implements the Plugin interface),
 * and should rename it to something relevant to your project.
 */
public class ExamplePlugin implements Plugin {
    private final ExamplePluginConfig config;

    /**
     * The plugin will be constructed by your plugin factory, so the constructor can take whatever form you like.
     *
     * @param config
     */
    public ExamplePlugin(ExamplePluginConfig config) {
        this.config = requireNonNull(config);
    }

    /**
     * When a request is processed, the response is not returned immediately - instead we return an Eventual.
     * This is similar to a Future in that it is not immediately available, instead it informs us that a response
     * will be available at some point.
     *
     * @param request request to intercept
     * @param chain chain linking to the next plugins
     * @return eventual response
     */
    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        /* the intercept method is where you can modify the request, modify the response and
         handle side-effects.

         Resource-intensive actions, such as external calls should be done in separate threads,
         otherwise you will block worker threads. Worker threads are shared by multiple channels, meaning
         that blocking a thread will also impact other customers.
        * */

        // Here is a simple example of modifying an incoming request.
        LiveHttpRequest newRequest = request.newBuilder()
                .header("myRequestHeader", config.requestHeaderValue())
                .build();

        return chain.proceed(newRequest).map(response ->
                // Here is a simple example of modifying an outgoing response.
                response.newBuilder()
                        .header("myResponseHeader", config.responseHeaderValue())
                        .build()
        );
    }

    /*
    * The method below has default implementations in the interface, so you do not need to include it if you
    * don't want to add anything. See the Plugin interface documentation for more information.
    * */
    @Override
    public Map<String, HttpHandler> adminInterfaceHandlers() {
        return emptyMap();
    }
}