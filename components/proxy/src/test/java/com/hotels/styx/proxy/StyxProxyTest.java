/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.common.http.handler.HttpAggregator;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.StandardHttpRouter;
import com.hotels.styx.server.netty.NettyServerBuilder;
import com.hotels.styx.server.netty.ServerConnector;
import com.hotels.styx.server.netty.WebServerConnectorFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

public class StyxProxyTest extends SSLSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger(StyxProxyTest.class);
    final HttpClient client = new StyxHttpClient.Builder()
            .build();

    @Test
    public void startsAndStopsAServer() {
        HttpServer server = new NettyServerBuilder()
                .setProtocolConnector(connector(0))
                .bossExecutor(NettyExecutor.create("Test-Server-Boss", 1))
                .workerExecutor(NettyExecutor.create("Test-Server-Worker", 0))
                .build();

        server.startAsync().awaitRunning();
        assertThat("Server should be running", server.isRunning());

        server.stopAsync().awaitTerminated();
        assertThat("Server should not be running", !server.isRunning());
    }

    @Test
    public void startsServerWithHttpConnector() {
        HttpInterceptor echoInterceptor = (request, chain) -> textResponse("Response from http connector");
        StandardHttpRouter handler = new StandardHttpRouter();

        HttpServer server = NettyServerBuilder.newBuilder()
                .setProtocolConnector(connector(0))
                .bossExecutor(NettyExecutor.create("Test-Server-Boss", 1))
                .workerExecutor(NettyExecutor.create("Test-Server-Worker", 0))
                .handler(new HttpInterceptorPipeline(
                        ImmutableList.of(echoInterceptor),
                        (request, context) -> new HttpAggregator(new StandardHttpRouter()).handle(request, context),
                        false))
                .build();
        server.startAsync().awaitRunning();
        assertThat("Server should be running", server.isRunning());

        HttpResponse secureResponse = get("http://localhost:" + server.inetAddress().getPort());
        assertThat(secureResponse.bodyAs(UTF_8), containsString("Response from http connector"));

        server.stopAsync().awaitTerminated();
        assertThat("Server should not be running", !server.isRunning());
    }

    private Eventual<LiveHttpResponse> textResponse(String body) {
        return Eventual.of(HttpResponse.response(OK)
                .body("Response from http connector", UTF_8)
                .build()
                .stream());
    }

    private ServerConnector connector(int port) {
        return connector(new HttpConnectorConfig(port));
    }

    private ServerConnector connector(HttpConnectorConfig config) {
        return new WebServerConnectorFactory().create(config);
    }

    @Disabled
    @Test
    public void startsServerWithBothHttpAndHttpsConnectors() throws IOException {
        HttpServer server = NettyServerBuilder.newBuilder()
                .setProtocolConnector(connector(0))
                .build();

        server.startAsync().awaitRunning();
        assertThat("Server should be running", server.isRunning());

        LOGGER.info("server is running: " + server.isRunning());

        HttpResponse clearResponse = get("http://localhost:8080/search?q=fanta");
        assertThat(clearResponse.bodyAs(UTF_8), containsString("Response from http Connector"));

        HttpResponse secureResponse = get("https://localhost:8443/secure");
        assertThat(secureResponse.bodyAs(UTF_8), containsString("Response from https Connector"));


        server.stopAsync().awaitTerminated();
        assertThat("Server should not be running", !server.isRunning());
    }

    private HttpResponse get(String uri) {
        HttpRequest secureRequest = HttpRequest.get(uri).build();
        return execute(secureRequest);
    }

    private HttpResponse execute(HttpRequest request) {
        return await(client.sendRequest(request));
    }
}
