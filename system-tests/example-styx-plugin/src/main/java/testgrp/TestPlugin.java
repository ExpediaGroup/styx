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
package testgrp;

import com.google.common.base.Charsets;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StreamingHttpMessage;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import io.netty.buffer.ByteBuf;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public class TestPlugin implements Plugin {
    private static final String X_HCOM_PLUGINS_HEADER = "X-Hcom-Plugins";
    private static final String X_HCOM_PLUGINS_CONFIGURATION_PATH = "X-Hcom-Plugin-Configuration-Path";
    private static final String X_HCOM_PLUGINS_LIST = "X-Hcom-Plugins-List";
    private final PluginFactory.Environment environment;
    private boolean styxStarted = false;
    private boolean styxStopped = false;

    public TestPlugin(PluginFactory.Environment environment) {
        this.environment = environment;
    }


    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        String header = xHcomPluginsHeader(request);

        final String configPath = environment.pluginConfig(String.class);
        String pluginsList = environment.configuration().get("plugins.active").get();

        HttpRequest newRequest = request.newBuilder()
                .header(X_HCOM_PLUGINS_HEADER, header)
                .header(X_HCOM_PLUGINS_CONFIGURATION_PATH, configPath)
                .header(X_HCOM_PLUGINS_LIST, pluginsList)
                .header("X-Hcom-Styx-Started", styxStarted)
                .header("X-Hcom-Styx-Stopped", styxStopped)
                .build();

        Function<ByteBuf, String> byteBufStringFunction = byteBuf -> byteBuf.toString(Charsets.UTF_8);

        return chain.proceed(newRequest)
                .flatMap(response -> response.toFullResponse(1 * 1024 * 1024))
                .map(response ->
                        response.newBuilder()
                                .header(X_HCOM_PLUGINS_HEADER, header)
                                .header(X_HCOM_PLUGINS_CONFIGURATION_PATH, configPath)
                                .header(X_HCOM_PLUGINS_LIST, pluginsList)
                                .header("X-Hcom-Styx-Started", styxStarted)
                                .header("X-Hcom-Styx-Stopped", styxStopped)
                                .build())
                .map(FullHttpResponse::toStreamingResponse);
    }

    private String xHcomPluginsHeader(StreamingHttpMessage message) {
        return message.headers().get(X_HCOM_PLUGINS_HEADER).orElse("")
                .concat(" test-plugin-a")
                .trim();
    }

    @Override
    public Map<String, HttpHandler> adminInterfaceHandlers() {
        return Collections.emptyMap();
    }

    @Override
    public void styxStarting() {

    }

    @Override
    public void styxStopping() {

    }
}
