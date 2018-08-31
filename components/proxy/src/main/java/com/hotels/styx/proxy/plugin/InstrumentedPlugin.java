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
package com.hotels.styx.proxy.plugin;

import com.codahale.metrics.Meter;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginException;
import com.hotels.styx.common.SimpleCache;
import org.slf4j.Logger;

import java.util.Map;

import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.api.StyxInternalObservables.fromRxObservable;
import static com.hotels.styx.api.StyxInternalObservables.toRxObservable;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.error;

/**
 * Collects metrics on plugin.
 */
public class InstrumentedPlugin implements Plugin {
    private static final Logger LOGGER = getLogger(InstrumentedPlugin.class);

    private final NamedPlugin plugin;
    private final SimpleCache<HttpResponseStatus, Meter> errorStatusMetrics;
    private final SimpleCache<Class<? extends Throwable>, Meter> exceptionMetrics;
    private final Meter errors;

    public InstrumentedPlugin(NamedPlugin plugin, Environment environment) {
        this.plugin = requireNonNull(plugin);
        requireNonNull(environment);

        this.errorStatusMetrics = new SimpleCache<>(statusCode ->
                environment.metricRegistry().meter("plugins." + plugin.name() + ".response.status." + statusCode.code()));

        this.exceptionMetrics = new SimpleCache<>(type ->
                environment.metricRegistry().meter("plugins." + plugin.name() + ".exception." + formattedExceptionName(type)));

        this.errors = environment.metricRegistry().meter("plugins." + plugin.name() + ".errors");

        LOGGER.info("Plugin {} instrumented", plugin.name());
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
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain originalChain) {
        StatusRecordingChain chain = new StatusRecordingChain(originalChain);
        try {
            return fromRxObservable(
                    toRxObservable(plugin.intercept(request, chain))
                            .doOnNext(response -> recordStatusCode(chain, response))
                            .onErrorResumeNext(error -> error(recordAndWrapError(chain, error))));
        } catch (Throwable e) {
            recordException(e);
            return StyxObservable.error(new PluginException(e, plugin.name()));
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

    private void recordStatusCode(StatusRecordingChain chain, HttpResponse response) {
        boolean isError = response.status().code() >= BAD_REQUEST.code();
        boolean fromPlugin = response.status() != chain.upstreamStatus;

        if (isError && fromPlugin) {
            errorStatusMetrics.get(response.status()).mark();

            if (response.status().equals(INTERNAL_SERVER_ERROR)) {
                errors.mark();
            }
        }
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
        public StyxObservable<HttpResponse> proceed(HttpRequest request) {
            try {
                return fromRxObservable(toRxObservable(chain.proceed(request))
                        .doOnNext(response -> upstreamStatus = response.status())
                        .doOnError(error -> upstreamException = true));
            } catch (Throwable e) {
                upstreamException = true;
                throw propagate(e);
            }
        }
    }
}
