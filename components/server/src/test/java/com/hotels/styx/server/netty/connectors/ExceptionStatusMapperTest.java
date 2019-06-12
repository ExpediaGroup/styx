package com.hotels.styx.server.netty.connectors;

import com.hotels.styx.server.BadRequestException;
import com.hotels.styx.server.netty.connectors.ExceptionStatusMapper.Matched;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;
import org.testng.annotations.Test;

import java.util.List;

import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

public class ExceptionStatusMapperTest {
    private final ExceptionStatusMapper mapper = new ExceptionStatusMapper.Builder()
            .add(BAD_GATEWAY, BadGateway1Exception.class)
            .add(BAD_GATEWAY, BadGateway2Exception.class)
            .add(BAD_REQUEST, DecoderException.class, BadRequestException.class)
            .add(REQUEST_ENTITY_TOO_LARGE, DecoderException.class, BadRequestException.class, TooLongFrameException.class)
            .build();

    @Test
    public void matchesAgainstDeepest() {
        assertThat(mapper.statusFor(new DecoderException()), is(INTERNAL_SERVER_ERROR));
        assertThat(mapper.statusFor(new DecoderException(new BadRequestException("test"))), is(BAD_REQUEST));
        assertThat(mapper.statusFor(new DecoderException(new BadRequestException("test", new TooLongFrameException()))), is(REQUEST_ENTITY_TOO_LARGE));
    }

    @Test
    public void hasExpectedLevels() {
        List<Matched> matches = mapper.matches(new DecoderException(new BadRequestException("test", new TooLongFrameException()))).collect(toList());

        assertThat(matches, contains(
                new Matched(3, REQUEST_ENTITY_TOO_LARGE),
                new Matched(2, BAD_REQUEST)
        ));
    }

    @Test
    public void multipleExceptionsCanMapToTheSameStatus() {
        assertThat(mapper.statusFor(new BadGateway1Exception()), is(BAD_GATEWAY));
        assertThat(mapper.statusFor(new BadGateway2Exception()), is(BAD_GATEWAY));
    }

    private static class BadGateway1Exception extends Exception {
    }

    private static class BadGateway2Exception extends Exception {
    }
}