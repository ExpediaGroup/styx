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
package com.hotels.styx.client.netty.connectionpool;

import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.support.server.FakeHttpServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.common.HostAndPorts.localHostAndFreePort;
import static com.hotels.styx.common.HostAndPorts.localhost;
import static com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder;
import static com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class NettyConnectionFactoryTest {
    private final ConnectionSettings connectionSettings = new ConnectionSettings(100);
    private final FakeHttpServer server = new FakeHttpServer(0);

    private final NettyConnectionFactory connectionFactory = new NettyConnectionFactory.Builder()
            .httpRequestOperationFactory(httpRequestOperationFactoryBuilder().build())
            .build();

    private Origin healthyOrigin;
    private Origin deadOrigin;

    @BeforeClass
    public void startServer() {
        server.start();
    }

    @AfterClass
    public void stopServer() {
        server.stop();
    }

    @BeforeMethod
    public void setUp() {
        healthyOrigin = newOriginBuilder(localhost(server.port())).build();
        deadOrigin = newOriginBuilder(localHostAndFreePort()).build();
    }

    @Test
    public void createsAndOpensAConnection() {
        Observable<Connection> connection = connectionFactory.createConnection(healthyOrigin, connectionSettings);
        assertThat(connection, is(not(nullValue())));
    }

    @Test
    public void openingAConnectionFailsIfExceedsTimeOut() {
        TestSubscriber<Connection> subscriber = new TestSubscriber<>();
        connectionFactory.createConnection(deadOrigin, connectionSettings).subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        assertThat(connectionError(subscriber), is(instanceOf(OriginUnreachableException.class)));
    }

    private static Throwable connectionError(TestSubscriber<Connection> subscriber) {
        return subscriber.getOnErrorEvents().get(0);
    }

    @Test
    public void createsWorkingHttpConnection() {
        server.stub(urlStartingWith("/"), aResponse().withStatus(200));

        NettyConnection connection = (NettyConnection) connectionFactory.createConnection(healthyOrigin, connectionSettings)
                .toBlocking()
                .single();

        List<HttpObject> responseObjects = sendRequestAndReceiveResponse(requestToOrigin(), connection.channel());

        assertThat(responseObjects.size(), is(2));
        assertThat(responseObjects.get(0), is(instanceOf(HttpResponse.class)));
        assertThat(responseObjects.get(1), is(instanceOf(LastHttpContent.class)));

        HttpResponse response = (HttpResponse) responseObjects.get(0);
        assertThat(response.getStatus(), is(OK));
    }

    private FullHttpRequest requestToOrigin() {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/");
        request.headers().set(HOST, "localhost:" + server.port());
        return request;
    }

    private List<HttpObject> sendRequestAndReceiveResponse(FullHttpRequest request, Channel channel) {
        return list(channelRequestResponse(channel, request).toBlocking().toIterable());
    }

    private <T> List<T> list(Iterable<T> iterable) {
        return stream(iterable.spliterator(), false)
                .collect(toList());
    }

    private Observable<HttpObject> channelRequestResponse(Channel channel, FullHttpRequest request) {
        return Observable.create(subscriber -> {
            channel.pipeline().addLast(new SimpleChannelInboundHandler<HttpObject>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
                    subscriber.onNext(msg);

                    if (msg instanceof LastHttpContent) {
                        subscriber.onCompleted();
                    }
                }
            });

            channel.writeAndFlush(request);
        });
    }
}
