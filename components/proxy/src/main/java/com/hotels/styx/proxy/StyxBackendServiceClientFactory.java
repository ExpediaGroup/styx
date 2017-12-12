/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.proxy;

import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.client.ConnectionPoolProvider;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.retry.RetryNTimes;
import org.slf4j.Logger;

import static com.hotels.styx.serviceproviders.ServiceProvision.loadService;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Creates HTTP clients for connecting to backend services.
 */
public class StyxBackendServiceClientFactory implements BackendServiceClientFactory {
    private static final Logger LOG = getLogger(BackendServiceClientFactory.class);

    private final Environment environment;

    // Todo: This can be package private if/when backend service router is created in a separate builder in styx.proxy package.
    public StyxBackendServiceClientFactory(Environment environment) {
        this.environment = environment;
    }

    @Override
    public HttpClient createClient(BackendService backendService, ConnectionPoolProvider connectionPoolProvider) {
        RetryPolicy retryPolicy = loadService(environment.configuration(), environment, "retrypolicy.policy.factory", RetryPolicy.class)
                .orElseGet(() -> defaultRetryPolicy(environment));

        boolean requestLoggingEnabled = environment.styxConfig().get("request-logging.outbound.enabled", Boolean.class)
                .orElse(false);

        boolean longFormat = environment.styxConfig().get("request-logging.outbound.longFormat", Boolean.class)
                .orElse(false);
        OriginStatsFactory originStatsFactory = new OriginStatsFactory(environment.metricRegistry());


        return new StyxHttpClient.Builder(backendService)
                .styxHeaderNames(environment.styxConfig().styxHeaderConfig())
                .metricsRegistry(environment.metricRegistry())
                .retryPolicy(retryPolicy)
                .flowControlEnabled(true)
                .enableContentValidation()
                .rewriteRules(backendService.rewrites())
                .requestLoggingEnabled(requestLoggingEnabled)
                .longFormat(longFormat)
                .connectionPoolProvider(connectionPoolProvider)
                .originStatsFactory(originStatsFactory)
                .build();
    }

    private static RetryPolicy defaultRetryPolicy(com.hotels.styx.api.Environment environment) {
        RetryNTimes retryOnce = new RetryNTimes(1);
        LOG.warn("No configured retry policy found in {}. Using {}", environment.configuration(), retryOnce);
        return retryOnce;
    }
}
