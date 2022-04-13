package com.hotels.styx.server.netty.connectors;

import com.hotels.styx.api.ContentOverflowException;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.exceptions.NoAvailableHostsException;
import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.api.exceptions.ResponseTimeoutException;
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.client.BadHttpResponseException;
import com.hotels.styx.client.StyxClientException;
import com.hotels.styx.client.connectionpool.ResourceExhaustedException;
import com.hotels.styx.server.BadRequestException;
import com.hotels.styx.server.NoServiceConfiguredException;
import com.hotels.styx.server.RequestTimeoutException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;

import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_TIMEOUT;
import static com.hotels.styx.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static com.hotels.styx.server.netty.connectors.ExceptionStatusMapperKt.buildExceptionStatusMapper;

public class StyxExceptionToHttpStatus {

    private static final ExceptionStatusMapper EXCEPTION_STATUSES = buildExceptionStatusMapper(statusMapper -> {
        statusMapper.add(REQUEST_TIMEOUT, RequestTimeoutException.class)
                .add(BAD_GATEWAY,
                        OriginUnreachableException.class,
                        NoAvailableHostsException.class,
                        NoServiceConfiguredException.class,
                        BadHttpResponseException.class,
                        ContentOverflowException.class
                )
                .add(SERVICE_UNAVAILABLE, ResourceExhaustedException.class)
                .add(GATEWAY_TIMEOUT, ResponseTimeoutException.class)
                .add(INTERNAL_SERVER_ERROR, StyxClientException.class);
    });

    public static HttpResponseStatus status(Throwable exception) {
        return EXCEPTION_STATUSES.statusFor(exception)
                .orElseGet(() -> {
                    if (exception instanceof DecoderException) {
                        Throwable cause = exception.getCause();

                        if (cause instanceof BadRequestException) {
                            if (cause.getCause() instanceof TooLongFrameException) {
                                return REQUEST_ENTITY_TOO_LARGE;
                            }

                            return BAD_REQUEST;
                        }
                    } else if (exception instanceof TransportLostException) {
                        return BAD_GATEWAY;
                    }

                    return INTERNAL_SERVER_ERROR;
                });
    }

}
