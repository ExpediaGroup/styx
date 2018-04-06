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
package com.hotels.styx.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.net.HostAndPort;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Url;
import com.hotels.styx.api.client.ConnectionDestination;
import com.hotels.styx.api.client.Origin;
import rx.Observable;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static java.util.Objects.requireNonNull;

/**
 * A client that uses netty as transport.
 */
public final class SimpleNettyHttpClient implements HttpClient {
    private final LoadingCache<Origin, ConnectionDestination> connectionDestinationByOrigin;
    private final Optional<String> userAgent;

    private SimpleNettyHttpClient(Builder builder) {
        ConnectionDestination.Factory connectionDestinationFactory = requireNonNull(builder.connectionDestinationFactory);

        this.userAgent = Optional.ofNullable(builder.userAgent);
        this.connectionDestinationByOrigin = CacheBuilder.newBuilder().build(cacheLoader(connectionDestinationFactory::create));
    }

    @Override
    public Observable<HttpResponse> sendRequest(HttpRequest request) {
        HttpRequest networkRequest = addUserAgent(request);
        Origin origin = originFromRequest(networkRequest);
        ConnectionDestination connectionDestination = connectionDestination(origin);
        Observable<HttpResponse> response = connectionDestination.withConnection(connection -> connection.write(networkRequest));
        return new HttpTransaction.NonCancellableHttpTransaction(response).response();
    }

    private HttpRequest addUserAgent(HttpRequest request) {
        return Optional.of(request)
                .filter(req -> !req.header(USER_AGENT).isPresent())
                .flatMap(req -> userAgent.map(userAgent -> addUserAgent(request, userAgent)))
                .orElse(request);
    }

    private static HttpRequest addUserAgent(HttpRequest request, String userAgent) {
        return request.newBuilder()
                .addHeader(USER_AGENT, userAgent)
                .build();
    }

    private ConnectionDestination connectionDestination(Origin origin) {
        try {
            return connectionDestinationByOrigin.get(origin);
        } catch (ExecutionException e) {
            throw propagate(e.getCause());
        }
    }

    private static Origin originFromRequest(HttpRequest request) {
        String hostAndPort = request.header(HOST)
                .orElseGet(() -> {
                    checkArgument(request.url().isAbsolute(), "host header is not set for request=%s", request);
                    return request.url().authority().map(Url.Authority::hostAndPort)
                            .orElseThrow(() -> new IllegalArgumentException("Cannot send request " + request + " as URL is not absolute and no HOST header is present"));
                });

        return newOriginBuilder(HostAndPort.fromString(hostAndPort)).build();
    }

    // Annoyingly, Guava's CacheLoader is an abstract class, which cannot be a lambda/method reference.
    // This is a workaround for that.
    private static <O, C> CacheLoader<O, C> cacheLoader(Function<O, C> loader) {
        return new CacheLoader<O, C>() {
            @Override
            public C load(O key) throws Exception {
                return loader.apply(key);
            }
        };
    }

    /**
     * Builder for {@link SimpleNettyHttpClient}.
     */
    public static class Builder {
        private String userAgent;
        private ConnectionDestination.Factory connectionDestinationFactory;

        /**
         * Sets the factory that will produce connection-destination objects for origins.
         *
         * @param connectionDestinationFactory factory
         * @return this builder
         */
        public Builder connectionDestinationFactory(ConnectionDestination.Factory connectionDestinationFactory) {
            this.connectionDestinationFactory = connectionDestinationFactory;
            return this;
        }

        /**
         * Sets the user-agent header value to be included in requests.
         *
         * @param userAgent user-agent
         * @return this builder
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Construct a client instance.
         *
         * @return a new instance
         */
        public HttpClient build() {
            return new SimpleNettyHttpClient(this);
        }
    }
}
