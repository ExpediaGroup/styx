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
package com.hotels.styx.utils;

import com.google.common.base.Suppliers;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.netty.channel.ChannelOption.ALLOCATOR;
import static io.netty.channel.ChannelOption.AUTO_READ;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static io.netty.channel.ChannelOption.TCP_NODELAY;
import static io.netty.util.ReferenceCountUtil.releaseLater;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

public class HttpTestClient {
    private static final NioEventLoopGroup eventLoopGroup =
            new NioEventLoopGroup(1, new ThreadFactoryBuilder().setNameFormat("Test-Client-%d").build());

    private final HostAndPort destination;
    private final LinkedBlockingDeque<Object> receivedResponses = new LinkedBlockingDeque<>();
    private final Supplier<Bootstrap> bootstrap;

    private ChannelFuture channelFuture;

    public HttpTestClient(HostAndPort destination, ChannelInitializer<Channel> initializer) {
        this.destination = requireNonNull(destination);

        this.bootstrap = lazy(() -> new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(initializer)
                .option(TCP_NODELAY, true)
                .option(AUTO_READ, true)
                .option(ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(CONNECT_TIMEOUT_MILLIS, 1000));
    }

    public HttpTestClient connect() throws InterruptedException {
        Bootstrap bootstrap = this.bootstrap.get();

        channelFuture = bootstrap.connect(destination.getHostText(), destination.getPort())
                .addListener((ChannelFutureListener) future -> future.channel().pipeline().addLast(new ReceivedResponseHandler()));

        channelFuture.await();
        return this;
    }

    public CompletableFuture<Void> disconnect() {
        return toCompletableFuture(channelFuture.channel().close());
    }

    private CompletableFuture<Void> toCompletableFuture(Future<?> nettyFuture) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        nettyFuture.addListener(it -> {
            if (it.isSuccess()) {
                completableFuture.complete(null);
            } else {
                completableFuture.completeExceptionally(it.cause());
            }
        });
        return completableFuture;
    }

    public ChannelFuture channelFuture() {
        return this.channelFuture;
    }

    public void write(Object msg) throws InterruptedException {
        if (!channelFuture.channel().isWritable()) {
            throw new ChannelNotWritableException("The channel is not writeable");
        }
        channelFuture.channel().writeAndFlush(msg).await();
    }

    public Object waitForResponse(long timeout, TimeUnit unit) throws InterruptedException {
        return receivedResponses.poll(timeout, unit);
    }

    public Object waitForResponse() throws InterruptedException {
        return waitForResponse(3, SECONDS);
    }

    public boolean isOpen() {
        return channelFuture.channel().isOpen();
    }

    private class ReceivedResponseHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            releaseLater(msg);
            receivedResponses.offer(msg);
        }
    }

    private static <T> Supplier<T> lazy(Supplier<T> supplier) {
        return Suppliers.memoize(supplier::get)::get;
    }
}
