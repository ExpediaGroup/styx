package com.hotels.styx.proxy.interceptors;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.server.BadRequestException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ExceptionMappingInterceptorTest {
    private ExceptionMappingInterceptor interceptor;

    @BeforeClass
    public void setUp() {
        interceptor = new ExceptionMappingInterceptor();
    }

    @Test
    public void tooLongFrameException_mappedTo_RequestEntityTooLarge() {
        DecoderException exception = new DecoderException(new BadRequestException("just testing", new TooLongFrameException()));

        LiveHttpResponse response = Mono.from(interceptor.intercept(get("/").build(), request -> Eventual.error(exception)))
                .block();

        assertThat(response.status(), is(REQUEST_ENTITY_TOO_LARGE));
    }

    @Test
    public void otherBadRequestException_mappedTo_BadRequest() {
        DecoderException exception = new DecoderException(new BadRequestException("just testing"));

        LiveHttpResponse response = Mono.from(interceptor.intercept(get("/").build(), request -> Eventual.error(exception)))
                .block();

        assertThat(response.status(), is(BAD_REQUEST));
    }

    @Test
    public void otherDecoderException_mappedTo_InternalServerError() {
        DecoderException exception = new DecoderException();

        LiveHttpResponse response = Mono.from(interceptor.intercept(get("/").build(), request -> Eventual.error(exception)))
                .block();

        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
    }

    @Test
    public void otherException_mappedTo_InternalServerError() {
        Exception exception = new Exception();

        LiveHttpResponse response = Mono.from(interceptor.intercept(get("/").build(), request -> Eventual.error(exception)))
                .block();

        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
    }
}