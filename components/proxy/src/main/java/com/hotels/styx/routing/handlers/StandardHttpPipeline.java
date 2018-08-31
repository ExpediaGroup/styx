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
package com.hotels.styx.routing.handlers;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import rx.Observable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hotels.styx.api.StyxInternalObservables.fromRxObservable;
import static com.hotels.styx.api.StyxInternalObservables.toRxObservable;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static rx.Observable.create;
import com.hotels.styx.api.HttpRequest;

/**
 * The pipeline consists of a chain of interceptors followed by a handler.
 */
class StandardHttpPipeline implements HttpHandler {
    private final List<HttpInterceptor> interceptors;
    private final HttpHandler handler;

    public StandardHttpPipeline(HttpHandler handler) {
        this(emptyList(), handler);
    }

    public StandardHttpPipeline(List<HttpInterceptor> interceptors, HttpHandler handler) {
        this.interceptors = requireNonNull(interceptors);
        this.handler = requireNonNull(handler);
    }

    @Override
    public StyxObservable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        HttpInterceptorChain interceptorsChain = new HttpInterceptorChain(interceptors, 0, handler, context);

        return interceptorsChain.proceed(request);
    }

    static final class HttpInterceptorChain implements HttpInterceptor.Chain {
        private final List<HttpInterceptor> interceptors;
        private final int index;
        private final HttpHandler client;
        private final HttpInterceptor.Context context;

        HttpInterceptorChain(List<HttpInterceptor> interceptors, int index, HttpHandler client, HttpInterceptor.Context context) {
            this.interceptors = interceptors;
            this.index = index;
            this.client = client;
            this.context = context;
        }

        HttpInterceptorChain(HttpInterceptorChain adapter, int index) {
            this(adapter.interceptors, index, adapter.client, adapter.context);
        }

        @Override
        public HttpInterceptor.Context context() {
            return context;
        }

        @Override
        public StyxObservable<HttpResponse> proceed(HttpRequest request) {
            if (index < interceptors.size()) {
                HttpInterceptor.Chain chain = new HttpInterceptorChain(this, index + 1);
                HttpInterceptor interceptor = interceptors.get(index);

                try {
                    return interceptor.intercept(request, chain);
                } catch (Throwable e) {
                    return StyxObservable.error(e);
                }
            }
            return fromRxObservable(toRxObservable(client.handle(request, this.context))
                    .compose(StandardHttpPipeline::sendErrorOnDoubleSubscription));
        }
    }

    private static Observable<HttpResponse> sendErrorOnDoubleSubscription(Observable<HttpResponse> original) {
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
