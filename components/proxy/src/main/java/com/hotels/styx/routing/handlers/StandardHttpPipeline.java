/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.routing.handlers;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static rx.Observable.create;
import static rx.RxReactiveStreams.toObservable;
import static rx.RxReactiveStreams.toPublisher;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;

import com.hotels.styx.server.track.RequestTracker;
import rx.Observable;

/**
 * The pipeline consists of a chain of interceptors followed by a handler.
 */
class StandardHttpPipeline implements HttpHandler {
    private final List<HttpInterceptor> interceptors;
    private final HttpHandler handler;
    private final RequestTracker requestTracker;

    public StandardHttpPipeline(HttpHandler handler) {
        this(emptyList(), handler, RequestTracker.NO_OP);
    }

    public StandardHttpPipeline(List<HttpInterceptor> interceptors, HttpHandler handler, RequestTracker requestTracker) {
        this.interceptors = requireNonNull(interceptors);
        this.handler = requireNonNull(handler);
        this.requestTracker = requireNonNull(requestTracker);
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        HttpInterceptorChain interceptorsChain = new HttpInterceptorChain(interceptors, 0, handler, context, requestTracker);

        return interceptorsChain.proceed(request);
    }

    static final class HttpInterceptorChain implements HttpInterceptor.Chain {
        private final List<HttpInterceptor> interceptors;
        private final int index;
        private final HttpHandler client;
        private final HttpInterceptor.Context context;
        private final RequestTracker requestTracker;

        HttpInterceptorChain(List<HttpInterceptor> interceptors, int index, HttpHandler client, HttpInterceptor.Context context, RequestTracker requestTracker) {
            this.interceptors = interceptors;
            this.index = index;
            this.client = client;
            this.context = context;
            this.requestTracker = requireNonNull(requestTracker);
        }

        HttpInterceptorChain(HttpInterceptorChain adapter, int index) {
            this(adapter.interceptors, index, adapter.client, adapter.context, adapter.requestTracker);
        }

        @Override
        public HttpInterceptor.Context context() {
            return context;
        }

        @Override
        public Eventual<LiveHttpResponse> proceed(LiveHttpRequest request) {
            requestTracker.trackRequest(request);

            if (index < interceptors.size()) {
                HttpInterceptor.Chain chain = new HttpInterceptorChain(this, index + 1);
                HttpInterceptor interceptor = interceptors.get(index);

                try {
                    return interceptor.intercept(request, chain);
                } catch (Throwable e) {
                    return Eventual.error(e);
                }
            }

            requestTracker.markRequestAsSent(request);

            return new Eventual<>(toPublisher(toObservable(client.handle(request, this.context))
                    .compose(StandardHttpPipeline::sendErrorOnDoubleSubscription)));
        }
    }

    private static Observable<LiveHttpResponse> sendErrorOnDoubleSubscription(Observable<LiveHttpResponse> original) {
        AtomicInteger subscriptionCounter = new AtomicInteger();

        return create(subscriber -> {
            if (subscriptionCounter.incrementAndGet() > 1) {
                subscriber.onError(new IllegalStateException("Response already subscribed. Additional subscriptions forbidden."));
            } else {
                original.subscribe(subscriber);
            }
        });
    }

}
