/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.client.netty.connectionpool;

import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.support.server.FakeHttpServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.client.HttpConfig.newHttpConfigBuilder;
import static com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder;
import static com.hotels.styx.common.FreePorts.freePort;
import static com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.util.Collections.nCopies;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class NettyConnectionFactoryTest {
    private final ConnectionSettings connectionSettings = new ConnectionSettings(100);
    private final FakeHttpServer server = new FakeHttpServer(0);

    private final NettyConnectionFactory connectionFactory = new NettyConnectionFactory.Builder()
            .httpRequestOperationFactory(httpRequestOperationFactoryBuilder().build())
            .httpConfig(newHttpConfigBuilder().setMaxHeadersSize(100).build())
            .build();

    private Origin healthyOrigin;
    private Origin deadOrigin;

    @BeforeAll
    public void startServer() {
        server.start();
    }

    @AfterAll
    public void stopServer() {
        server.stop();
    }

    @BeforeEach
    public void setUp() {
        healthyOrigin = newOriginBuilder("localhost", server.port()).build();
        deadOrigin = newOriginBuilder("localhost", freePort()).build();
    }

    @Test
    public void createsAndOpensAConnection() {
        Mono<Connection> connection = connectionFactory.createConnection(healthyOrigin, connectionSettings);
        assertThat(connection, is(not(nullValue())));
    }

    @Test
    public void openingAConnectionFailsIfExceedsTimeOut() {
        StepVerifier.create(connectionFactory.createConnection(deadOrigin, connectionSettings))
                .expectError(OriginUnreachableException.class);
    }

    @Test
    public void createsWorkingHttpConnection() {
        server.stub(urlStartingWith("/"), aResponse().withStatus(200));

        NettyConnection connection = (NettyConnection) connectionFactory.createConnection(healthyOrigin, connectionSettings).block();

        List<HttpObject> responseObjects = sendRequestAndReceiveResponse(requestToOrigin(), connection.channel());

        assertThat(responseObjects.size(), is(2));
        assertThat(responseObjects.get(0), is(instanceOf(HttpResponse.class)));
        assertThat(responseObjects.get(1), is(instanceOf(LastHttpContent.class)));

        HttpResponse response = (HttpResponse) responseObjects.get(0);
        assertThat(response.getStatus(), is(OK));
    }

    @Test
    public void responseFailsIfItExceedsMaxHeaderSize() {
        server.stub(urlStartingWith("/"), aResponse().withStatus(200).withHeader("AHEADER", String.join("", nCopies(100, "A"))));

        assertThrows(TooLongFrameException.class, () ->
                sendRequestAndReceiveResponse(
                        requestToOrigin(),
                        ((NettyConnection) connectionFactory.createConnection(healthyOrigin, connectionSettings).block()).channel())
        );

    }

    private FullHttpRequest requestToOrigin() {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/");
        request.headers().set(HOST, "localhost:" + server.port());
        return request;
    }

    private List<HttpObject> sendRequestAndReceiveResponse(FullHttpRequest request, Channel channel) {
        return list(channelRequestResponse(channel, request).toIterable());
    }

    private <T> List<T> list(Iterable<T> iterable) {
        return stream(iterable.spliterator(), false)
                .collect(toList());
    }

    private Flux<HttpObject> channelRequestResponse(Channel channel, FullHttpRequest request) {
        return Flux.create(sink -> {
            channel.pipeline().addLast(new SimpleChannelInboundHandler<HttpObject>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
                    sink.next(msg);

                    if (msg instanceof DefaultHttpResponse) {
                        DefaultHttpResponse response = (DefaultHttpResponse) msg;
                        if (response.decoderResult().isFailure()) {
                            sink.error(response.decoderResult().cause());
                        }
                    }
                    if (msg instanceof LastHttpContent) {
                        sink.complete();
                    }
                }
            });

            channel.writeAndFlush(request);
        });
    }
}
