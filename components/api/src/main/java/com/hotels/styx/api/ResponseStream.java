package com.hotels.styx.api;

import io.reactivex.Flowable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface ResponseStream {
    ResponseStream transform(Function<HttpResponse, HttpResponse> mapper);
    ResponseStream transformAsync(Function<HttpResponse, CompletableFuture<HttpResponse>> mapper);
}
