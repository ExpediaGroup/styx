package com.hotels.styx.proxy.interceptors;

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.PluginException;
import com.hotels.styx.server.BadRequestException;
import com.hotels.styx.server.netty.connectors.ExceptionStatusMapper;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;
import reactor.core.publisher.Flux;

import java.util.Optional;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class ExceptionMappingInterceptor implements HttpInterceptor {
    private final ExceptionStatusMapper exceptionStatuses;

    public ExceptionMappingInterceptor(ExceptionStatusMapper exceptionStatuses) {
        this.exceptionStatuses = requireNonNull(exceptionStatuses);
    }

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        return chain.proceed(request).onError(exception ->
                Eventual.of(exceptionToResponse(exception)));
    }

    private LiveHttpResponse exceptionToResponse(Throwable exception) {
        HttpResponseStatus status = status(exception instanceof PluginException
                ? exception.getCause()
                : exception);

        String message = status.code() >= 500 ? "Site temporarily unavailable." : status.description();

        return LiveHttpResponse
                        .response(status)
                        .body(new ByteStream(Flux.just(new Buffer(message, UTF_8))))
                        .header(CONTENT_LENGTH, message.getBytes(UTF_8).length)
                        .build();
    }

    private HttpResponseStatus status(Throwable exception) {
        return exceptionStatuses.statusFor(exception)
                .orElseGet(() -> {
//                    Optional.of(exception)
//                            .filter(e -> e instanceof DecoderException)
//                            .map(Throwable::getCause)
//                            .filter(e -> e instanceof BadRequestException)
//                            .map(Throwable::getCause)
//                            .map(e -> e.getCause() instanceof TooLongFrameException
//                                    ? REQUEST_ENTITY_TOO_LARGE
//                                    : BAD_REQUEST)
//                            .orElse(INTERNAL_SERVER_ERROR);


                    if (exception instanceof DecoderException) {
                        Throwable cause = exception.getCause();

                        if (cause instanceof BadRequestException) {
                            return cause.getCause() instanceof TooLongFrameException
                                    ? REQUEST_ENTITY_TOO_LARGE
                                    : BAD_REQUEST;
                        }
                    }

                    return INTERNAL_SERVER_ERROR;
                });
    }
}
