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

import com.codahale.metrics.Meter;
import com.codahale.metrics.health.HealthCheck;
import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.proxy.healthchecks.HealthCheckTimestamp;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.netty.NettyServerBuilderSpec;

import static com.codahale.metrics.health.HealthCheck.Result.healthy;
import static com.codahale.metrics.health.HealthCheck.Result.unhealthy;
import static com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder.ENCODE_UNWISECHARS;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.hotels.styx.api.HttpRequest;

/**
 * A builder for a ProxyServer.
 */
public final class ProxyServerBuilder {
    private final Environment environment;
    private final ResponseInfoFormat responseInfoFormat;
    private final CharSequence styxInfoHeaderName;

    private HttpHandler httpHandler;
    private Runnable onStartupAction = () -> {
    };

    public ProxyServerBuilder(Environment environment) {
        this.environment = requireNonNull(environment);
        this.responseInfoFormat = new ResponseInfoFormat(environment);
        this.styxInfoHeaderName = environment.styxConfig().styxHeaderConfig().styxInfoHeaderName();
    }

    public HttpServer build() {
        ProxyServerConfig proxyConfig = environment.styxConfig().proxyServerConfig();
        String unwiseCharacters = environment.styxConfig().get(ENCODE_UNWISECHARS).orElse("");

        return new NettyServerBuilderSpec("Proxy", environment.serverEnvironment(),
                new ProxyConnectorFactory(proxyConfig, environment.metricRegistry(), environment.errorListener(), unwiseCharacters, this::addInfoHeader))
                .toNettyServerBuilder(proxyConfig)
                .httpHandler(httpHandler)
                // register health check
                .register(HealthCheckTimestamp.NAME, new HealthCheckTimestamp())
                .register("errors-rate-500", new ErrorsRateHealthCheck(environment.metricRegistry()))
                .doOnStartUp(onStartupAction)
                .build();
    }

    private HttpResponse.Builder addInfoHeader(HttpResponse.Builder responseBuilder, HttpRequest request) {
        return responseBuilder.header(styxInfoHeaderName, responseInfoFormat.format(request));
    }

    public ProxyServerBuilder httpHandler(HttpHandler httpHandler) {
        this.httpHandler = httpHandler;
        return this;
    }

    public ProxyServerBuilder onStartup(Runnable startupAction) {
        this.onStartupAction = startupAction;
        return this;
    }

    private static final class ErrorsRateHealthCheck extends HealthCheck {
        private final MetricRegistry metricRegistry;

        ErrorsRateHealthCheck(MetricRegistry metricRegistry) {
            this.metricRegistry = metricRegistry;
        }

        @Override
        protected Result check() throws Exception {
            Meter errorRate = metricRegistry.meter("requests.error-rate.500");
            double oneMinuteRate = errorRate.getOneMinuteRate();
            return oneMinuteRate > 1.0
                    ? unhealthy(format("error count=%d m1_rate=%s is greater than %s", errorRate.getCount(), oneMinuteRate, 1.0))
                    : healthy(format("error count=%d m1_rate=%.2f", errorRate.getCount(), oneMinuteRate));
        }
    }
}
