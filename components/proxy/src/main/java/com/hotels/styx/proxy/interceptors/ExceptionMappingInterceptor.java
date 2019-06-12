package com.hotels.styx.proxy.interceptors;

import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.ContentOverflowException;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.exceptions.NoAvailableHostsException;
import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.api.exceptions.ResponseTimeoutException;
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.api.plugins.spi.PluginException;
import com.hotels.styx.client.BadHttpResponseException;
import com.hotels.styx.client.connectionpool.ResourceExhaustedException;
import com.hotels.styx.server.BadRequestException;
import com.hotels.styx.server.NoServiceConfiguredException;
import com.hotels.styx.server.RequestTimeoutException;
import com.hotels.styx.server.netty.connectors.ExceptionStatusMapper;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;
import reactor.core.publisher.Flux;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_TIMEOUT;
import static com.hotels.styx.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class ExceptionMappingInterceptor implements HttpInterceptor {
    private static final ExceptionStatusMapper EXCEPTION_STATUSES = new ExceptionStatusMapper.Builder()
            .add(REQUEST_TIMEOUT, RequestTimeoutException.class)
            .add(BAD_GATEWAY, OriginUnreachableException.class)
            .add(BAD_GATEWAY, NoAvailableHostsException.class)
            .add(BAD_GATEWAY, NoServiceConfiguredException.class)
            .add(BAD_GATEWAY, BadHttpResponseException.class)
            .add(BAD_GATEWAY, ContentOverflowException.class)
            .add(BAD_GATEWAY, TransportLostException.class)
            .add(SERVICE_UNAVAILABLE, ResourceExhaustedException.class)
            .add(GATEWAY_TIMEOUT, ResponseTimeoutException.class)
            .add(BAD_REQUEST, DecoderException.class, BadRequestException.class)
            .add(REQUEST_ENTITY_TOO_LARGE, DecoderException.class, BadRequestException.class, TooLongFrameException.class)
            .build();

    private final ExceptionStatusMapper exceptionStatuses;

    public ExceptionMappingInterceptor() {
        this(EXCEPTION_STATUSES);
    }

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
        return exceptionStatuses.statusFor(exception);
    }
}
