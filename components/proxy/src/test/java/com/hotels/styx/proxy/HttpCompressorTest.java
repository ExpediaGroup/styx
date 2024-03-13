/*
  Copyright (C) 2013-2024 Expedia Inc.

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
package com.hotels.styx.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContentEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;


public class HttpCompressorTest {
    private static final String COMPRESSIBLE_MIME_TYPE = "application/json";
    private static final String NOT_COMPRESSIBLE_MIME_TYPE = "image/jpeg";
    private HttpCompressor compressor;
    private ChannelHandlerContext ctx;


    @BeforeEach
    public void setUp() throws Exception {
        compressor = new HttpCompressor();
        ctx = mock(ChannelHandlerContext.class, RETURNS_DEEP_STUBS);
        compressor.handlerAdded(ctx);
    }


    @ParameterizedTest
    @MethodSource("responseEncodingParameters")
    public void validateResponseEncoding(final String acceptEncoding, final String contentType, final String expectedEncoding) throws Exception {
        HttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        httpResponse.headers().add("Content-Type", contentType);
        HttpContentEncoder.Result result = compressor.beginEncode(httpResponse, acceptEncoding);
        if (expectedEncoding == null) {
            assertNull(result);
        } else {
            assertEquals(result.targetContentEncoding(), expectedEncoding);
            assertNotNull(result.contentEncoder());
        }
    }

    private static Stream<Arguments> responseEncodingParameters() {
        // Note:  "br" is brotli compression
        return Stream.of(
            Arguments.of("br", COMPRESSIBLE_MIME_TYPE, "br"),
            Arguments.of("br", NOT_COMPRESSIBLE_MIME_TYPE, null),
            Arguments.of("gzip", COMPRESSIBLE_MIME_TYPE, "gzip"),
            Arguments.of("gzip", NOT_COMPRESSIBLE_MIME_TYPE, null),
            Arguments.of("br, gzip", COMPRESSIBLE_MIME_TYPE, "br"),
            Arguments.of("gzip, br", COMPRESSIBLE_MIME_TYPE, "br"),
            Arguments.of("", COMPRESSIBLE_MIME_TYPE, null),
            Arguments.of("", NOT_COMPRESSIBLE_MIME_TYPE, null),
            Arguments.of("br, garbage", COMPRESSIBLE_MIME_TYPE, "br"),
            Arguments.of("gzip, garbage", COMPRESSIBLE_MIME_TYPE, "gzip"),
            Arguments.of("garbage", COMPRESSIBLE_MIME_TYPE, null),
            Arguments.of("?", COMPRESSIBLE_MIME_TYPE, null),
            Arguments.of(",", COMPRESSIBLE_MIME_TYPE, null)
        );
    }
}
