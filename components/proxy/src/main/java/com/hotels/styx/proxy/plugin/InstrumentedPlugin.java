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
package com.hotels.styx.proxy.plugin;

import com.codahale.metrics.Meter;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginException;
import com.hotels.styx.common.SimpleCache;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Collects metrics on plugin.
 */
public class InstrumentedPlugin implements NamedPlugin {
    private static final Logger LOGGER = getLogger(InstrumentedPlugin.class);

    private final NamedPlugin plugin;
    private final SimpleCache<HttpResponseStatus, Meter> errorStatusMetrics;
    private final SimpleCache<Class<? extends Throwable>, Meter> exceptionMetrics;
    private final Meter errors;

    public InstrumentedPlugin(NamedPlugin plugin, Environment environment) {
        requireNotAlreadyInstrumented(plugin);

        this.plugin = requireNonNull(plugin);
        requireNonNull(environment);

        this.errorStatusMetrics = new SimpleCache<>(statusCode ->
                environment.metricRegistry().meter("plugins." + plugin.name() + ".response.status." + statusCode.code()));

        this.exceptionMetrics = new SimpleCache<>(type ->
                environment.metricRegistry().meter("plugins." + plugin.name() + ".exception." + formattedExceptionName(type)));

        this.errors = environment.metricRegistry().meter("plugins." + plugin.name() + ".errors");

        LOGGER.info("Plugin {} instrumented", plugin.name());
    }

    private void requireNotAlreadyInstrumented(NamedPlugin plugin) {
        if (plugin instanceof InstrumentedPlugin) {
            throw new IllegalArgumentException("Plugin " + plugin.name() + " is already instrumented");
        }
    }

    static String formattedExceptionName(Class<? extends Throwable> type) {
        return type.getName().replace('.', '_');
    }

    @Override
    public void styxStarting() {
        plugin.styxStarting();
    }

    @Override
    public void styxStopping() {
        plugin.styxStopping();
    }

    @Override
    public Map<String, HttpHandler> adminInterfaceHandlers() {
        return plugin.adminInterfaceHandlers();
    }

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain originalChain) {
        StatusRecordingChain chain = new StatusRecordingChain(originalChain);
        try {
            return new Eventual<>(Flux.from(plugin.intercept(request, chain))
                    .doOnNext(response -> recordStatusCode(chain, response))
                    .onErrorResume(error -> Flux.error(recordAndWrapError(chain, error))));
        } catch (Throwable e) {
            recordException(e);
            return Eventual.error(new PluginException(e, plugin.name()));
        }
    }

    private void recordException(Throwable e) {
        exceptionMetrics.get(e.getClass()).mark();
        errorStatusMetrics.get(INTERNAL_SERVER_ERROR).mark();
        errors.mark();
    }

    private Throwable recordAndWrapError(StatusRecordingChain chain, Throwable error) {
        if (chain.upstreamException) {
            return error;
        }

        recordException(error);
        return new PluginException(error, plugin.name());
    }

    private void recordStatusCode(StatusRecordingChain chain, LiveHttpResponse response) {
        boolean isError = response.status().code() >= BAD_REQUEST.code();
        boolean fromPlugin = response.status() != chain.upstreamStatus;

        if (isError && fromPlugin) {
            errorStatusMetrics.get(response.status()).mark();

            if (response.status().equals(INTERNAL_SERVER_ERROR)) {
                errors.mark();
            }
        }
    }

    @Override
    public Plugin originalPlugin() {
        return plugin;
    }

    @Override
    public String name() {
        return plugin.name();
    }

    @Override
    public void setEnabled(boolean enabled) {
        plugin.setEnabled(enabled);
    }

    @Override
    public boolean enabled() {
        return plugin.enabled();
    }

    @Override
    public String toString() {
        return "InstrumentedPlugin{" + plugin + '}';
    }

    private static class StatusRecordingChain implements Chain {
        private final Chain chain;
        private volatile HttpResponseStatus upstreamStatus;
        private volatile boolean upstreamException;

        StatusRecordingChain(Chain chain) {
            this.chain = chain;
        }

        @Override
        public Context context() {
            return chain.context();
        }

        @Override
        public Eventual<LiveHttpResponse> proceed(LiveHttpRequest request) {
            try {
                return new Eventual<>(Flux.from(chain.proceed(request))
                        .doOnNext(response -> upstreamStatus = response.status())
                        .doOnError(error -> upstreamException = true));
            } catch (RuntimeException | Error e) {
                upstreamException = true;
                throw e;
            } catch (Exception e) {
                upstreamException = true;
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return "StatusRecordingChain{" +
                    "chain=" + chain +
                    ", upstreamStatus=" + upstreamStatus +
                    ", upstreamException=" + upstreamException +
                    '}';
        }
    }
}
