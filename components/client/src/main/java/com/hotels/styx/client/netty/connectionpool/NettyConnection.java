/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.Announcer;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.HttpConfig;
import com.hotels.styx.client.HttpRequestOperationFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import rx.Observable;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Objects.toStringHelper;
import static java.util.Objects.requireNonNull;

/**
 * A connection using a netty channel.
 */
public class NettyConnection implements Connection, TimeToFirstByteListener {
    private static final AttributeKey<Object> CLOSED_BY_STYX = AttributeKey.newInstance("CLOSED_BY_STYX");

    private final Origin origin;
    private final Channel channel;
    private final HttpRequestOperationFactory requestOperationFactory;

    private volatile long timeToFirstByteMs;
    private final Announcer<Listener> listeners = Announcer.to(Listener.class);


    /**
     * Constructs an instance with an arbitrary UUID.
     *
     * @param origin                  the origin connected to
     * @param channel                 the netty channel used
     * @param requestOperationFactory used to create operation objects that send http requests via this connection
     * @param httpConfig              configuration settings for the <b>origin</b>
     * @param sslContext              TLS context in case of secure connections
     * @param sendSni                 include the servername extension (server name indicator) in the TLS handshake
     * @param sniHost                 hostname override for the server name indicator
     */
    public NettyConnection(Origin origin, Channel channel, HttpRequestOperationFactory requestOperationFactory,
                           HttpConfig httpConfig, SslContext sslContext, boolean sendSni, Optional<String> sniHost) {
        this.origin = requireNonNull(origin);
        this.channel = requireNonNull(channel);
        this.requestOperationFactory = requestOperationFactory;
        this.channel.pipeline().addLast(new TimeToFirstByteHandler(this));
        this.channel.closeFuture().addListener(future ->
                listeners.announce().connectionClosed(NettyConnection.this));
        addChannelHandlers(channel, httpConfig, sslContext, sendSni, sniHost.orElse(origin.host()));
    }

    private static void addChannelHandlers(Channel channel, HttpConfig httpConfig, SslContext sslContext, boolean sendSni, String targetHost) {
        ChannelPipeline pipeline = channel.pipeline();

        if (sslContext != null) {
            SslHandler sslHandler = sendSni
                    ? sslContext.newHandler(channel.alloc(), targetHost, -1)
                    : sslContext.newHandler(channel.alloc());
            pipeline.addLast("ssl", sslHandler);
        }

        pipeline.addLast("http-codec", new HttpClientCodec(httpConfig.maxInitialLineLength(), httpConfig.maxHeadersSize(), httpConfig.maxChunkSize()));
        if (httpConfig.compress()) {
            pipeline.addLast("decompressor", new HttpContentDecompressor());
        }
    }

    @Override
    public Observable<LiveHttpResponse> write(LiveHttpRequest request) {
        return this.requestOperationFactory.newHttpRequestOperation(request).execute(this);
    }


    /**
     * The netty channel associated with this connection.
     *
     * @return netty channel
     */
    public Channel channel() {
        return channel;
    }

    @Override
    public boolean isConnected() {
        return channel.isActive();
    }

    @Override
    public Origin getOrigin() {
        return this.origin;
    }

    @Override
    public long getTimeToFirstByteMillis() {
        return this.timeToFirstByteMs;
    }

    @Override
    public void addConnectionListener(Listener listener) {
        this.listeners.addListener(listener);
    }

    @Override
    public void close() {
        if (channel.isOpen()) {
            channel.attr(CLOSED_BY_STYX).set(true);
            channel.close();
        }
    }

    @Override
    public void notifyTimeToFirstByte(long timeToFirstByte, TimeUnit timeUnit) {
        this.timeToFirstByteMs = timeUnit.toMillis(timeToFirstByte);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("host", this.origin.hostAndPortString())
                .add("channel", toString(channel))
                .toString();
    }

    private static String toString(Channel channel) {
        return toStringHelper(channel)
                .add("active", channel.isActive())
                .add("open", channel.isOpen())
                .add("registered", channel.isRegistered())
                .add("writable", channel.isWritable())
                .add("localAddress", channel.localAddress())
                .add("clientAddress", channel.remoteAddress())
                .add("hashCode", channel.hashCode())
                .toString();
    }
}
