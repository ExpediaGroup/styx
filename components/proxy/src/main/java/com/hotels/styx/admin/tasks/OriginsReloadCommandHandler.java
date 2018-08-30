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
package com.hotels.styx.admin.tasks;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import org.slf4j.Logger;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

import java.util.concurrent.ExecutorService;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.StyxInternalObservables.fromRxObservable;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.extension.service.spi.Registry.Outcome.RELOADED;
import static com.hotels.styx.api.extension.service.spi.Registry.Outcome.UNCHANGED;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.slf4j.LoggerFactory.getLogger;
import com.hotels.styx.api.HttpRequest;

/**
 * Handler for the origins reloading command.
 */
public class OriginsReloadCommandHandler implements HttpHandler {
    private static final Logger LOG = getLogger(OriginsReloadCommandHandler.class);

    private final ExecutorService executor = newSingleThreadExecutor();

    private final Registry<BackendService> backendServicesRegistry;

    public OriginsReloadCommandHandler(Registry<BackendService> backendServicesRegistry) {
        this.backendServicesRegistry = requireNonNull(backendServicesRegistry);
    }

    @Override
    public StyxObservable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return fromRxObservable(Observable.<HttpResponse>create(this::reload)
                .subscribeOn(Schedulers.from(executor)));
    }

    private void reload(Subscriber<? super HttpResponse> subscriber) {
        backendServicesRegistry.reload()
                .handle((result, exception) -> {
                    if (exception == null) {
                        if (result.outcome() == RELOADED) {
                            subscriber.onNext(okResponse("Origins reloaded successfully.\n"));
                            subscriber.onCompleted();
                        } else if (result.outcome() == UNCHANGED) {
                            subscriber.onNext(okResponse(format("Origins were not reloaded because %s.\n", result.message())));
                            subscriber.onCompleted();
                        } else {
                            subscriber.onError(mapError(result));
                        }
                    } else {
                        subscriber.onNext(errorResponse(exception));
                        subscriber.onCompleted();
                    }
                    return null;
                });
    }

    private Throwable mapError(Registry.ReloadResult result) {
        if (result.cause().isPresent()) {
            return new RuntimeException(result.cause().get());
        } else {
            return new RuntimeException("Reload failure");
        }
    }

    private HttpResponse okResponse(String content) {
        return FullHttpResponse.response(OK)
                .header(CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                .body(content, UTF_8)
                .build()
                .toStreamingResponse();
    }

    private HttpResponse errorResponse(Throwable cause) {
        String errorId = randomUUID().toString();
        LOG.error("id={}", errorId, cause);

        if (deSerialisationError(cause)) {
            return errorResponse(BAD_REQUEST, format("There was an error processing your request. It has been logged (ID %s).\n", errorId));
        } else {
            return errorResponse(INTERNAL_SERVER_ERROR, format("There was an error processing your request. It has been logged (ID %s).\n", errorId));
        }
    }

    private boolean deSerialisationError(Throwable cause) {
        Throwable subCause = cause.getCause();
        if (subCause instanceof JsonMappingException) {
            return true;
        }

        subCause = subCause != null ? subCause.getCause() : null;
        if (subCause instanceof JsonMappingException) {
            return true;
        }

        return false;
    }

    private HttpResponse errorResponse(HttpResponseStatus code, String content) {
        return FullHttpResponse.response(code)
                .header(CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                .body(content, UTF_8)
                .build()
                .toStreamingResponse();
    }

}
