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
package com.hotels.styx.testapi;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.client.StyxHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.testapi.Origins.origin;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StyxServerTest {
    private final HttpClient client = new StyxHttpClient.Builder()
            .build();

    private StyxServer styxServer;

    private WireMockServer originServer1;
    private WireMockServer originServer2;
    private WireMockServer secureOriginServer;

    @BeforeEach
    public void startOrigins() {
        originServer1 = new WireMockServer(wireMockConfig()
                .dynamicPort());

        originServer2 = new WireMockServer(wireMockConfig()
                .dynamicPort());

        secureOriginServer = new WireMockServer(wireMockConfig()
                .dynamicHttpsPort()
        );

        originServer1.start();
        originServer2.start();
        secureOriginServer.start();

        configureFor(originServer1.port());
        stubFor(WireMock.get(anyUrl()).willReturn(aResponse()
                .withHeader("origin", "first")
                .withStatus(OK.code())));

        configureFor(originServer2.port());
        stubFor(WireMock.get(anyUrl()).willReturn(aResponse()
                .withHeader("origin", "second")
                .withStatus(OK.code())));

        // HTTP port is still used to identify the WireMockServer, even when we are using it for HTTPS
        configureFor(secureOriginServer.port());
        stubFor(WireMock.get(anyUrl()).willReturn(aResponse()
                .withHeader("origin", "secure")
                .withStatus(OK.code())));
    }

    @AfterEach
    public void stopStyx() {
        if (styxServer != null) {
            styxServer.stop();
        }
    }

    @AfterEach
    public void stopOrigins() {
        originServer1.stop();
        originServer2.stop();
        secureOriginServer.stop();
    }

    @Test
    public void proxiesToOrigin() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", originServer1.port())
                .start();

        HttpResponse response = await(client.send(get(format("https://localhost:%d/", styxServer.proxyHttpPort())).build()));

        assertThat(response.status(), is(OK));
        configureFor(originServer1.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/")));
    }

    @Test
    public void startsProxyOnSpecifiedHttpPort() {
        styxServer = new StyxServer.Builder()
                .proxyHttpPort(0)
                .addRoute("/", originServer1.port())
                .start();

        HttpResponse response = await(client.send(get(format("https://localhost:%d/", styxServer.proxyHttpPort())).build()));

        assertThat(response.status(), is(OK));
    }

    @Test
    public void startsAdminOnSpecifiedHttpPort() {
        styxServer = new StyxServer.Builder()
                .adminHttpPort(0)
                .addRoute("/", originServer1.port())
                .start();

        HttpResponse response = await(client.send(get(format("https://localhost:%d/admin", styxServer.adminPort())).build()));

        assertThat(response.status(), is(OK));
    }

    @Test
    public void startsProxyOnSpecifiedHttpsPort() {
        styxServer = new StyxServer.Builder()
                .proxyHttpsPort(0)
                .addRoute("/", originServer1.port())
                .start();

        StyxHttpClient tlsClient = new StyxHttpClient.Builder()
                .tlsSettings(new TlsSettings.Builder().build())
                .build();

        HttpResponse response = await(tlsClient.send(get(format("https://localhost:%d/", styxServer.proxyHttpsPort())).build()));

        assertThat(response.status(), is(OK));
    }

    @Test
    public void choosesFreeAdminPortNumbers() throws Exception {
        Optional<StyxServer> styx1 = Optional.empty();
        Optional<StyxServer> styx2 = Optional.empty();
        try {
            styx1 = Optional.of(new StyxServer.Builder().start());
            styx2 = Optional.of(new StyxServer.Builder().start());

            assertThat(styx1.get().adminPort(), is(not(styx2.get().adminPort())));
        } finally {
            styx1.ifPresent(StyxServer::stop);
            styx2.ifPresent(StyxServer::stop);
        }
    }

    @Test
    public void proxiesToOriginViaHttps() {
        BackendService backendService = new BackendService()
                .ssl()
                .addOrigin(secureOriginServer.httpsPort());

        styxServer = new StyxServer.Builder()
                .addRoute("/", backendService)
                .start();

        StyxHttpClient tlsClient = new StyxHttpClient.Builder()
                .tlsSettings(new TlsSettings.Builder().build())
                .build();

        HttpResponse response = await(tlsClient.send(get(format("https://localhost:%d/", styxServer.proxyHttpsPort())).build()));

        assertThat(response.status(), is(OK));
        configureFor(secureOriginServer.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/")));
    }

    @Test
    public void proxiesToOriginViaHttpsWithRequestOriginallyHttp() {
        BackendService backendService = new BackendService()
                .ssl()
                .addOrigin(secureOriginServer.httpsPort());

        styxServer = new StyxServer.Builder()
                .addRoute("/", backendService)
                .start();

        HttpResponse response = await(client.send(get(format("http://localhost:%d/", styxServer.proxyHttpPort())).build()));

        assertThat(response.status(), is(OK));
        configureFor(secureOriginServer.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/")));
    }

    @Test
    public void routesCorrectly() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", originServer1.port())
                .addRoute("/o2/", originServer2.port())
                .start();

        HttpResponse response1 = await(client.send(get(format("http://localhost:%d/foo", styxServer.proxyHttpPort())).build()));

        assertThat(response1.status(), is(OK));
        assertThat(response1.header("origin"), isValue("first"));

        HttpResponse response2 = await(client.send(get(format("http://localhost:%d/o2/foo", styxServer.proxyHttpPort())).build()));
        assertThat(response2.status(), is(OK));
        assertThat(response2.header("origin"), isValue("second"));

        configureFor(originServer1.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/foo")));
        configureFor(originServer2.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/o2/foo")));
    }

    @Test
    public void executesPluginsWhenProxying() {
        Plugin responseDecorator = (request, chain) -> chain.proceed(request)
                .map(response -> response.newBuilder()
                        .header("plugin-executed", "yes")
                        .build());

        PluginFactory pluginFactory = environment -> responseDecorator;

        styxServer = new StyxServer.Builder()
                .addRoute("/", originServer1.port())
                .addPluginFactory("response-decorator", pluginFactory, null)
                .start();

        HttpResponse response = await(client.send(get(format("http://localhost:%d/foo", styxServer.proxyHttpPort())).build()));
        assertThat(response.status(), is(OK));
        assertThat(response.header("origin"), isValue("first"));
        assertThat(response.header("plugin-executed"), isValue("yes"));

        configureFor(originServer1.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/foo")));
    }

    @Test
    public void addsPluginsLinkToAdminIndex() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", originServer1.port())
                .start();

        HttpResponse response = doAdminRequest("/");
        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), containsString("/admin/plugins"));
    }

    @Test
    public void addsPluginLinkToPluginsIndex() {
        setUpStyxAndPlugins("plugin-foo", "plugin-bar", "plugin-baz");

        HttpResponse response = doAdminRequest("/admin/plugins");
        assertThat(response.status(), is(OK));

        assertThat(response.bodyAs(UTF_8), allOf(
                containsString("/admin/plugins/plugin-foo"),
                containsString("/admin/plugins/plugin-bar"),
                containsString("/admin/plugins/plugin-baz")));
    }

    @Test
    public void addsEndpointLinksToPluginPage() {
        setUpStyxAndPluginWithAdminPages(Map.of(
                "adminPage1", (request, ctx) -> Eventual.of(LiveHttpResponse.response().build()),
                "adminPage2", (request, ctx) -> Eventual.of(LiveHttpResponse.response().build())
        ));

        HttpResponse response = doAdminRequest("/admin/plugins/plugin-with-admin-pages");
        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), allOf(
                containsString("/admin/plugins/plugin-with-admin-pages/adminPage1"),
                containsString("/admin/plugins/plugin-with-admin-pages/adminPage2")));
    }

    @Test
    public void exposesAdminEndpoints() {
        setUpStyxAndPluginWithAdminPages(Map.of(
                "adminPage1", (request, ctx) -> Eventual.of(LiveHttpResponse.response().header("AdminPage1", "yes").build()),
                "adminPage2", (request, ctx) -> Eventual.of(LiveHttpResponse.response().header("AdminPage2", "yes").build())
        ));

        HttpResponse response = doAdminRequest("/admin/plugins/plugin-with-admin-pages/adminPage1");
        assertThat(response.status(), is(OK));
        assertThat(response.header("AdminPage1"), isValue("yes"));

        response = doAdminRequest("/admin/plugins/plugin-with-admin-pages/adminPage2");
        assertThat(response.status(), is(OK));
        assertThat(response.header("AdminPage2"), isValue("yes"));
    }

    private void setUpStyxAndPluginWithAdminPages(Map<String, HttpHandler> adminInterfaceHandlers) {
        styxServer = new StyxServer.Builder()
                .addRoute("/", originServer1.port())
                .addPlugin("plugin-with-admin-pages", mockPlugin(adminInterfaceHandlers))
                .start();
    }

    private void setUpStyxAndPlugins(String... pluginNames) {
        StyxServer.Builder builder = new StyxServer.Builder()
                .addRoute("/", originServer1.port());

        for (String pluginName : pluginNames) {
            builder.addPlugin(pluginName, mockPlugin(emptyMap()));
        }

        styxServer = builder.start();
    }

    private Plugin mockPlugin(Map<String, HttpHandler> adminInterfaceHandlers) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.adminInterfaceHandlers()).thenReturn(adminInterfaceHandlers);
        return plugin;
    }

    private HttpResponse doAdminRequest(String path) {
        String url = format("%s://localhost:%s%s", "http", styxServer.adminPort(), startWithSlash(path));
        return await(client.send(get(url).build()));
    }

    @Test
    public void informsPluginOfStopping() {
        Plugin plugin = mock(Plugin.class);

        styxServer = new StyxServer.Builder()
                .addRoute("/", originServer1.port())
                .addPlugin("plugin-with-shutdownBehaviour", plugin)
                .start();

        styxServer.stop();

        verify(plugin).styxStopping();
    }

    @Test
    public void canConfigureWithStyxOrigins() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", origin(originServer1.port()))
                .addRoute("/o2/", origin(originServer2.port()))
                .start();

        HttpResponse response1 = await(client.send(get(format("http://localhost:%d/foo", styxServer.proxyHttpPort())).build()));
        assertThat(response1.status(), is(OK));
        assertThat(response1.header("origin"), isValue("first"));

        HttpResponse response2 = await(client.send(get(format("http://localhost:%d/o2/foo", styxServer.proxyHttpPort())).build()));
        assertThat(response2.status(), is(OK));
        assertThat(response2.header("origin"), isValue("second"));

        configureFor(originServer1.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/foo")));
        configureFor(originServer2.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/o2/foo")));
    }

    @Test
    public void canConfigureWithBackendService() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", new BackendService().addOrigin(originServer1.port()))
                .addRoute("/o2/", new BackendService().addOrigin(originServer2.port()))
                .start();

        HttpResponse response1 = await(client.send(get(format("http://localhost:%d/foo", styxServer.proxyHttpPort())).build()));
        assertThat(response1.status(), is(OK));
        assertThat(response1.header("origin"), isValue("first"));

        HttpResponse response2 = await(client.send(get(format("http://localhost:%d/o2/foo", styxServer.proxyHttpPort())).build()));
        assertThat(response2.status(), is(OK));
        assertThat(response2.header("origin"), isValue("second"));

        configureFor(originServer1.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/foo")));
        configureFor(originServer2.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/o2/foo")));
    }

    private String startWithSlash(String path) {
        return !path.isEmpty() && path.charAt(0) == '/' ? path : "/" + path;
    }

    static {
        System.setProperty("org.mortbay.log.class", "com.github.tomakehurst.wiremock.jetty.LoggerAdapter");
    }
}