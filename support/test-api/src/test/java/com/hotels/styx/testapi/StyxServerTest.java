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
package com.hotels.styx.testapi;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.FullHttpClient;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.client.SimpleHttpClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.FullHttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.HostAndPorts.freePort;
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
    private final FullHttpClient client = new SimpleHttpClient.Builder()
            .build();

    private StyxServer styxServer;

    private WireMockServer originServer1;
    private WireMockServer originServer2;
    private WireMockServer secureOriginServer;

    @BeforeMethod
    public void startOrigins() {
        originServer1 = new WireMockServer(wireMockConfig()
                .port(freePort()));

        originServer2 = new WireMockServer(wireMockConfig()
                .port(freePort()));

        secureOriginServer = new WireMockServer(wireMockConfig()
                .httpsPort(freePort())
        );

        originServer1.start();
        originServer2.start();
        secureOriginServer.start();

        configureFor(originServer1.port());
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("origin", "first")
                .withStatus(OK.code())));

        configureFor(originServer2.port());
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("origin", "second")
                .withStatus(OK.code())));

        // HTTP port is still used to identify the WireMockServer, even when we are using it for HTTPS
        configureFor(secureOriginServer.port());
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("origin", "secure")
                .withStatus(OK.code())));
    }

    @AfterMethod
    public void stopStyx() {
        styxServer.stop();
    }

    @AfterMethod
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

        FullHttpResponse response = await(client.sendRequest(get(format("https://localhost:%d/", styxServer.proxyHttpPort())).build()));

        assertThat(response.status(), is(OK));
        configureFor(originServer1.port());
        WireMock.verify(getRequestedFor(urlPathEqualTo("/")));
    }

    @Test
    public void startsProxyOnSpecifiedHttpPort() {
        int proxyPort = freePort();
        styxServer = new StyxServer.Builder()
                .proxyHttpPort(proxyPort)
                .addRoute("/", originServer1.port())
                .start();

        FullHttpResponse response = await(client.sendRequest(get(format("https://localhost:%d/", proxyPort)).build()));

        assertThat(response.status(), is(OK));
    }

    @Test
    public void startsAdminOnSpecifiedHttpPort() {
        int adminPort = freePort();
        styxServer = new StyxServer.Builder()
                .adminHttpPort(adminPort)
                .addRoute("/", originServer1.port())
                .start();

        FullHttpResponse response = await(client.sendRequest(get(format("https://localhost:%d/admin", adminPort)).build()));

        assertThat(response.status(), is(OK));
    }

    @Test
    public void startsProxyOnSpecifiedHttpsPort() {
        int httpsPort = freePort();

        styxServer = new StyxServer.Builder()
                .proxyHttpsPort(httpsPort)
                .addRoute("/", originServer1.port())
                .start();

        SimpleHttpClient tlsClient = new SimpleHttpClient.Builder()
                .tlsSettings(new TlsSettings.Builder().build())
                .build();

        FullHttpResponse response = await(tlsClient.sendRequest(get(format("https://localhost:%d/", httpsPort)).build()));

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

        SimpleHttpClient tlsClient = new SimpleHttpClient.Builder()
                .tlsSettings(new TlsSettings.Builder().build())
                .build();

        FullHttpResponse response = await(tlsClient.sendRequest(get(format("https://localhost:%d/", styxServer.proxyHttpsPort())).build()));

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

        FullHttpResponse response = await(client.sendRequest(get(format("http://localhost:%d/", styxServer.proxyHttpPort())).build()));

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

        FullHttpResponse response1 = await(client.sendRequest(get(format("http://localhost:%d/foo", styxServer.proxyHttpPort())).build()));

        assertThat(response1.status(), is(OK));
        assertThat(response1.header("origin"), isValue("first"));

        FullHttpResponse response2 = await(client.sendRequest(get(format("http://localhost:%d/o2/foo", styxServer.proxyHttpPort())).build()));
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

        FullHttpResponse response = await(client.sendRequest(get(format("http://localhost:%d/foo", styxServer.proxyHttpPort())).build()));
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

        FullHttpResponse response = doAdminRequest("/");
        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), containsString("/admin/plugins"));
    }

    @Test
    public void addsPluginLinkToPluginsIndex() {
        setUpStyxAndPlugins("plugin-foo", "plugin-bar", "plugin-baz");

        FullHttpResponse response = doAdminRequest("/admin/plugins");
        assertThat(response.status(), is(OK));

        assertThat(response.bodyAs(UTF_8), allOf(
                containsString("/admin/plugins/plugin-foo"),
                containsString("/admin/plugins/plugin-bar"),
                containsString("/admin/plugins/plugin-baz")));
    }

    @Test
    public void addsEndpointLinksToPluginPage() {
        setUpStyxAndPluginWithAdminPages(ImmutableMap.of(
                "adminPage1", (request, ctx) -> StyxObservable.of(HttpResponse.response().build()),
                "adminPage2", (request, ctx) -> StyxObservable.of(HttpResponse.response().build())
        ));

        FullHttpResponse response = doAdminRequest("/admin/plugins/plugin-with-admin-pages");
        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), allOf(
                containsString("/admin/plugins/plugin-with-admin-pages/adminPage1"),
                containsString("/admin/plugins/plugin-with-admin-pages/adminPage2")));
    }

    @Test
    public void exposesAdminEndpoints() {
        setUpStyxAndPluginWithAdminPages(ImmutableMap.of(
                "adminPage1", (request, ctx) -> StyxObservable.of(HttpResponse.response().header("AdminPage1", "yes").build()),
                "adminPage2", (request, ctx) -> StyxObservable.of(HttpResponse.response().header("AdminPage2", "yes").build())
        ));

        FullHttpResponse response = doAdminRequest("/admin/plugins/plugin-with-admin-pages/adminPage1");
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

    private FullHttpResponse doAdminRequest(String path) {
        String url = format("%s://localhost:%s%s", "http", styxServer.adminPort(), startWithSlash(path));
        return await(client.sendRequest(get(url).build()));
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

        FullHttpResponse response1 = await(client.sendRequest(get(format("http://localhost:%d/foo", styxServer.proxyHttpPort())).build()));
        assertThat(response1.status(), is(OK));
        assertThat(response1.header("origin"), isValue("first"));

        FullHttpResponse response2 = await(client.sendRequest(get(format("http://localhost:%d/o2/foo", styxServer.proxyHttpPort())).build()));
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

        FullHttpResponse response1 = await(client.sendRequest(get(format("http://localhost:%d/foo", styxServer.proxyHttpPort())).build()));
        assertThat(response1.status(), is(OK));
        assertThat(response1.header("origin"), isValue("first"));

        FullHttpResponse response2 = await(client.sendRequest(get(format("http://localhost:%d/o2/foo", styxServer.proxyHttpPort())).build()));
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