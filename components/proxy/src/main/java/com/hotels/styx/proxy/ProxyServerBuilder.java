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
package com.hotels.styx.proxy;

import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.netty.NettyServerBuilderSpec;

import java.util.function.Supplier;

import static com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder.ENCODE_UNWISECHARS;
import static java.util.Objects.requireNonNull;

/**
 * A builder for a ProxyServer.
 */
public final class ProxyServerBuilder {
    private final Environment environment;
    private final StyxInfoFormat styxInfoFormat;
    private final CharSequence styxInfoHeaderName;

    private Supplier<HttpHandler> handlerFactory;
    private Runnable onStartupAction = () -> {
    };

    public ProxyServerBuilder(Environment environment) {
        this.environment = requireNonNull(environment);
        this.styxInfoFormat = new StyxInfoFormat(environment);
        this.styxInfoHeaderName = environment.configuration().styxHeaderConfig().styxInfoHeaderName();
    }

    public HttpServer build() {
        ProxyServerConfig proxyConfig = environment.styxConfig().proxyServerConfig();
        String unwiseCharacters = environment.styxConfig().get(ENCODE_UNWISECHARS).orElse("");
        boolean requestTracking = environment.configuration().get("requestTracking", Boolean.class).orElse(false);

        return new NettyServerBuilderSpec("Proxy", environment.serverEnvironment(),
                new ProxyConnectorFactory(proxyConfig, environment.metricRegistry(), environment.errorListener(), unwiseCharacters, this::addInfoHeader, requestTracking))
                .toNettyServerBuilder(proxyConfig)
                .handlerFactory(handlerFactory)
                // register health check
                .doOnStartUp(onStartupAction)
                .build();
    }

    private LiveHttpResponse.Transformer addInfoHeader(LiveHttpResponse.Transformer responseBuilder, LiveHttpRequest request) {
        return responseBuilder.header(styxInfoHeaderName, styxInfoFormat.format(request));
    }

    public ProxyServerBuilder handlerFactory(Supplier<HttpHandler> handlerFactory) {
        this.handlerFactory = handlerFactory;
        return this;
    }

    public ProxyServerBuilder onStartup(Runnable startupAction) {
        this.onStartupAction = startupAction;
        return this;
    }
}
