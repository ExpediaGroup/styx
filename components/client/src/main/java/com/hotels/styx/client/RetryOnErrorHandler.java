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
package com.hotels.styx.client;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import org.slf4j.Logger;
import rx.Observable;
import rx.functions.Func1;

import java.util.Optional;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.collect.Iterables.concat;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Handles retries.
 */
final class RetryOnErrorHandler implements Func1<Throwable, Observable<? extends HttpResponse>> {
    private static final Logger LOGGER = getLogger(RetryOnErrorHandler.class);

    private final StyxHttpClient client;
    private final int attemptCount;
    private final HttpRequest request;
    private final Iterable<ConnectionPool> previouslyUsedOrigins;
    private HttpTransaction txn;

    private RetryOnErrorHandler(Builder builder) {
        this.client = builder.client;
        this.attemptCount = builder.attemptCount;
        this.request = builder.request;
        this.previouslyUsedOrigins = builder.previouslyUsedOrigins;
        this.txn = builder.transaction;
    }

    @Override
    public Observable<? extends HttpResponse> call(Throwable throwable) {
        if (txn.isCancelled()) {
            return Observable.error(throwable);
        }
        RetryPolicyContext context = new RetryPolicyContext(client.id(), attemptCount, throwable, request, previouslyUsedOrigins, client.originsInventory().snapshot());
        RetryPolicy.Outcome outcome = client.retryPolicy().evaluate(context, client.loadBalancingStrategy());
        if (!outcome.shouldRetry() || !outcome.nextOrigin().isPresent()) {
            return Observable.error(throwable);
        }

        synchronized (this) {
            txn = client.transport().send(request, outcome.nextOrigin());
        }
        LOGGER.info("Retrying with new context {}", context);
        return txn.response()
                .delaySubscription(outcome.retryIntervalMillis(), MILLISECONDS)
                .onErrorResumeNext(nextAttemptHandler(outcome));
    }

    private RetryOnErrorHandler nextAttemptHandler(RetryPolicy.Outcome outcome) {
        return new Builder()
                .client(client)
                .attemptCount(attemptCount + 1)
                .request(request)
                .previouslyUsedOrigins(triedOrigins(outcome.nextOrigin()))
                .transaction(txn)
                .build();
    }

    private Iterable<ConnectionPool> triedOrigins(Optional<ConnectionPool> lastUsed) {
        return lastUsed.map(origin -> concat(previouslyUsedOrigins, singleton(origin)))
                .orElse(previouslyUsedOrigins);
    }

    public synchronized void cancel() {
        txn.cancel();
    }

    synchronized boolean isCancelled() {
        return txn.isCancelled();
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("client", client)
                .add("attemptCount", attemptCount)
                .add("request", request)
                .add("previouslyUsedOrigins", previouslyUsedOrigins)
                .add("txn", txn)
                .toString();
    }

    private static final class RetryPolicyContext implements RetryPolicy.Context {
        private final Id appId;
        private final int retryCount;
        private final Throwable lastException;
        private final HttpRequest request;
        private final Iterable<ConnectionPool> previouslyUsedOrigins;
        private final Iterable<ConnectionPool> origins;

        RetryPolicyContext(Id appId, int retryCount, Throwable lastException, HttpRequest request,
                           Iterable<ConnectionPool> previouslyUsedOrigins, Iterable<ConnectionPool> origins) {
            this.appId = appId;
            this.retryCount = retryCount;
            this.lastException = lastException;
            this.request = request;
            this.previouslyUsedOrigins = previouslyUsedOrigins;
            this.origins = origins;
        }

        @Override
        public Id appId() {
            return appId;
        }

        @Override
        public int currentRetryCount() {
            return retryCount;
        }

        @Override
        public Optional<Throwable> lastException() {
            return Optional.ofNullable(lastException);
        }

        @Override
        public Iterable<ConnectionPool> origins() {
            return origins;
        }

        @Override
        public HttpRequest currentRequest() {
            return request;
        }

        @Override
        public Iterable<ConnectionPool> previousOrigins() {
            return previouslyUsedOrigins;
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("appId", appId)
                    .add("retryCount", retryCount)
                    .add("lastException", lastException)
                    .add("request", request.url())
                    .add("previouslyUsedOrigins", hosts(previouslyUsedOrigins))
                    .add("origins", hosts(origins))
                    .toString();
        }

        private static Iterable<String> hosts(Iterable<ConnectionPool> origins) {
            return stream(origins.spliterator(), false)
                    .map(origin -> origin.getOrigin().hostAsString())
                    .collect(toList());
        }
    }

    static final class Builder {
        private StyxHttpClient client;
        private int attemptCount;
        private HttpRequest request;
        private Iterable<ConnectionPool> previouslyUsedOrigins = emptyList();
        private HttpTransaction transaction;

        public Builder client(StyxHttpClient client) {
            this.client = client;
            return this;
        }

        public Builder attemptCount(int attemptCount) {
            this.attemptCount = attemptCount;
            return this;
        }

        public Builder request(HttpRequest request) {
            this.request = request;
            return this;
        }

        public Builder previouslyUsedOrigin(ConnectionPool previouslyUsedOrigin) {
            this.previouslyUsedOrigins = previouslyUsedOrigin == null ? emptyList() : singletonList(previouslyUsedOrigin);
            return this;
        }

        public Builder previouslyUsedOrigins(Iterable<ConnectionPool> previouslyUsedOrigins) {
            this.previouslyUsedOrigins = previouslyUsedOrigins;
            return this;
        }

        public Builder transaction(HttpTransaction transaction) {
            this.transaction = transaction;
            return this;
        }

        public RetryOnErrorHandler build() {
            return new RetryOnErrorHandler(this);
        }
    }
}
