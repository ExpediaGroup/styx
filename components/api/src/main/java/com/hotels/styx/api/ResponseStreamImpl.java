package com.hotels.styx.api;

import rx.Observable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ResponseStreamImpl implements ResponseStream {
    private Observable<HttpResponse> delegate;

    private ResponseStreamImpl(HttpResponse response) {
        this.delegate = Observable.just(response);
    }
    private ResponseStreamImpl(Observable<HttpResponse> responseObservable) {
        this.delegate = responseObservable;
    }

    public static ResponseStream responseStream(HttpResponse response) {
        return new ResponseStreamImpl(response);
    }

    public static ResponseStream responseStream(CompletableFuture<HttpResponse> responseFuture) {
        return new ResponseStreamImpl(toObservable(responseFuture));
    }

    @Override
    public ResponseStream transform(Function<HttpResponse, HttpResponse> mapper) {
        return new ResponseStreamImpl(delegate.map(mapper::apply));
    }

    @Override
    public ResponseStream transformAsync(Function<HttpResponse, CompletableFuture<HttpResponse>> mapper) {
        return new ResponseStreamImpl(delegate.flatMap(response -> toObservable(mapper.apply(response))));
    }

    private static Observable<HttpResponse> toObservable(CompletableFuture<HttpResponse> responseFuture) {
        return Observable.create(subscriber -> responseFuture.whenComplete((result, error) -> {
            if (error != null) {
                subscriber.onError(error);
            } else {
                subscriber.onNext(result);
                subscriber.onCompleted();
            }
        }));
    }

    public Observable<HttpResponse> observable() {
        return delegate;
    }
}
