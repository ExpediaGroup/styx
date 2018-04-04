package com.hotels.styx.common;

import rx.Observable;

import java.util.concurrent.CompletableFuture;

public final class CompletableFutures {
    private CompletableFutures() {
    }

    // TODO: We need to substitute this in favour of Styx APIs
    //
    // Credit: http://www.nurkiewicz.com/2014/11/converting-between-completablefuture.html
    public static <T> CompletableFuture<T> fromSingleObservable(Observable<T> observable) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        observable
                .doOnError(future::completeExceptionally)
                .single()
                .forEach(future::complete);
        return future;
    }

}
