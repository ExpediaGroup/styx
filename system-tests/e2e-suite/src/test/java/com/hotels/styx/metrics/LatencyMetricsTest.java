/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.metrics;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hotels.styx.TempKt;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.MicrometerRegistry;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.plugins.DelayPlugin;
import com.hotels.styx.testapi.StyxServer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

public class LatencyMetricsTest {
    private final HttpClient client = new StyxHttpClient.Builder().build();

    private StyxServer styxServer;

    private WireMockServer origin;

    @BeforeEach
    public void startOrigins() {
        origin = new WireMockServer(wireMockConfig().dynamicPort());
        origin.start();

        configureFor(origin.port());
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("origin", "first")
                .withBody("foo")
                .withStatus(OK.code())));
    }

    @AfterEach
    public void stopStyx() {
        styxServer.stop();
    }

    @AfterEach
    public void stopOrigins() {
        origin.stop();
    }

    @Test
    public void foo() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", origin.port())
                .start();

        // TODO notice that with CompositeMeterRegistry, it doesn't increment the timer count?

        TempKt.timerExperiment("Raw simple registry", new SimpleMeterRegistry());
        TempKt.timerExperiment("Raw composite registry", new CompositeMeterRegistry());
        TempKt.timerExperiment("Styx wrapper with simple rr", new MicrometerRegistry(new SimpleMeterRegistry()));
        TempKt.timerExperiment("Styx wrapper with composite rr", new MicrometerRegistry(new CompositeMeterRegistry()));
        TempKt.timerExperiment("Registry from server", styxServer.meterRegistry());
        TempKt.timerExperiment("Registry from server", styxServer.meterRegistry(), true);
        TempKt.timerExperiment(new CentralisedMetrics(styxServer.meterRegistry()));
    }

    @Test
    public void recordsLatency() {
        styxServer = new StyxServer.Builder()
                .addRoute("/", origin.port())
                .addPlugin("delayPlugin", new DelayPlugin(Duration.ofMillis(1), Duration.ofMillis(2)))
                .start();

        HttpResponse response = doGet("/");

        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), is("foo"));

        assertThat(styxServer.meterRegistry().timer("proxy.request.latency").count(), is(1L));
        assertThat(styxServer.meterRegistry().timer("proxy.response.latency").count(), is(1L));

        assertThat(styxServer.meterRegistry().timer("proxy.request.latency").totalTime(MILLISECONDS), is(greaterThanOrEqualTo(1.0)));
        assertThat(styxServer.meterRegistry().timer("proxy.response.latency").totalTime(MILLISECONDS), is(greaterThanOrEqualTo(2.0)));
    }

    private HttpResponse doGet(String path) {
        return doRequest(client, "http", styxServer.proxyHttpPort(), startWithSlash(path));
    }

    private static HttpResponse doRequest(HttpClient client, String protocol, int port, String path) {
        String url = format("%s://localhost:%s%s", protocol, port, startWithSlash(path));

        HttpRequest request = get(url)
                .body("foobarbaz", UTF_8)
                .build();

        return await(client.send(request));
    }

    private static String startWithSlash(String path) {
        return !path.isEmpty() && path.charAt(0) == '/' ? path : "/" + path;
    }

}
