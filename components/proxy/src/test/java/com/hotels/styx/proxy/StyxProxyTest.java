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
package com.hotels.styx.proxy;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.FullHttpClient;
import com.hotels.styx.api.FullHttpRequest;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.client.SimpleHttpClient;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.StandardHttpRouter;
import com.hotels.styx.server.netty.NettyServerBuilder;
import com.hotels.styx.server.netty.NettyServerBuilderSpec;
import com.hotels.styx.server.netty.NettyServerConfig;
import com.hotels.styx.server.netty.ServerConnector;
import com.hotels.styx.server.netty.WebServerConnectorFactory;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.hotels.styx.common.io.ResourceFactory.newResource;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.HostAndPorts.freePort;
import static com.hotels.styx.common.StyxFutures.await;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

public class StyxProxyTest extends SSLSetup {
    final FullHttpClient client = new SimpleHttpClient.Builder()
            .build();

    public static void main(String[] args) {
        NettyServerConfig config = new YamlConfig(newResource("classpath:default.yaml"))
                .get("proxy", NettyServerConfig.class).get();

        HttpServer server = new NettyServerBuilderSpec()
                .toNettyServerBuilder(config)
                .build();

        server.startAsync().awaitRunning();
    }

    @Test
    public void startsAndStopsAServer() {
        HttpServer server = new NettyServerBuilder()
                .setHttpConnector(connector(freePort()))
                .build();

        server.startAsync().awaitRunning();
        assertThat("Server should be running", server.isRunning());

        server.stopAsync().awaitTerminated();
        assertThat("Server should not be running", !server.isRunning());
    }

    @Test
    public void startsServerWithHttpConnector() {
        HttpInterceptor echoInterceptor = (request, chain) -> textResponse("Response from http connector");

        int port = freePort();
        HttpServer server = NettyServerBuilder.newBuilder()
                .setHttpConnector(connector(port))
                .httpHandler(new HttpInterceptorPipeline(ImmutableList.of(echoInterceptor), new StandardHttpRouter()))
                .build();
        server.startAsync().awaitRunning();
        assertThat("Server should be running", server.isRunning());

        FullHttpResponse secureResponse = get("http://localhost:" + port);
        assertThat(secureResponse.bodyAs(UTF_8), containsString("Response from http connector"));

        server.stopAsync().awaitTerminated();
        assertThat("Server should not be running", !server.isRunning());
    }

    private StyxObservable<HttpResponse> textResponse(String body) {
        return StyxObservable.of(FullHttpResponse.response(OK)
                .body("Response from http connector", UTF_8)
                .build()
                .toStreamingResponse());
    }

    private ServerConnector connector(int port) {
        return connector(new HttpConnectorConfig(port));
    }

    private ServerConnector connector(HttpConnectorConfig config) {
        return new WebServerConnectorFactory().create(config);
    }

//    @Test(enabled = false)
//    public void proxyRequestsWithTheConfiguredClient() throws IOException {
//        HttpRouter proxyRouter = new DynamicHttpProxyRouter(new ServiceClientFactory(new Environment()))
//                .addRoute("/", new HttpHandler() {
//                    @Override
//                    public Observable<HttpResponse> handle(HttpRequest request) {
//                        return null;
//                    }
//                });
//
//        HttpServer server = NettyServerBuilder.newBuilder()
//                .setHttpConnectorConfig(new HttpConnectorConfig(freePort()))
//                .setHttpsConnectorConfig(new HttpsConnectorConfig(freePort(), tlsConfig))
//                .httpHandler(proxyRouter)
//                .build();
//
//        server.startAsync().awaitRunning();
//
//        HttpResponse clearResponse = execute(new HttpRequest.Builder(GET, "/search?q=fanta")
//                .addHeader(HOST, "www.google.com:80")
//                .build());
//
//        assertThat(content(clearResponse), containsString("Response from http Connector"));
//
//        server.stopAsync().awaitTerminated();
//
//        assertThat("Server should not be running", !server.isRunning());
//    }

    @Test(enabled = false)
    public void startsServerWithBothHttpAndHttpsConnectors() throws IOException {
        HttpServer server = NettyServerBuilder.newBuilder()
                .setHttpConnector(connector(freePort()))
                .build();

        server.startAsync().awaitRunning();
        assertThat("Server should be running", server.isRunning());

        System.out.println("server is running: " + server.isRunning());

        FullHttpResponse clearResponse = get("http://localhost:8080/search?q=fanta");
        assertThat(clearResponse.bodyAs(UTF_8), containsString("Response from http Connector"));

        FullHttpResponse secureResponse = get("https://localhost:8443/secure");
        assertThat(secureResponse.bodyAs(UTF_8), containsString("Response from https Connector"));


        server.stopAsync().awaitTerminated();
        assertThat("Server should not be running", !server.isRunning());
    }

    private FullHttpResponse get(String uri) {
        FullHttpRequest secureRequest = FullHttpRequest.get(uri).build();
        return execute(secureRequest);
    }

    private FullHttpResponse execute(FullHttpRequest request) {
        return await(client.sendRequest(request));
    }
}
