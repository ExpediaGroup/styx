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
package com.hotels.styx.support.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;

import java.nio.charset.Charset;

import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpMessageSupport {

    public static HttpResponse httpResponse(HttpResponseStatus status, String body) {
        ByteBuf content = Unpooled.copiedBuffer(body.getBytes(Charset.forName("US-ASCII")));
        HttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1,
                status,
                content);
        HttpHeaders.setContentLength(response, content.writerIndex());
        return response;
    }

    public static ByteBuf httpResponseAsBuf(HttpResponseStatus status, String body) {
        return httpMessageToBytes(httpResponse(status, body));
    }

    public static HttpRequest httpRequest(HttpMethod method, String url, String body) {
        ByteBuf content = Unpooled.copiedBuffer(body.getBytes(Charset.forName("US-ASCII")));
        HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1,
                method,
                url,
                content);
        HttpHeaders.setContentLength(request, content.writerIndex());
        return request;
    }

    public static HttpRequest httpRequest(HttpMethod method, String url) {
        HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, method, url);
        request.headers().set(HOST, url);
        return request;
    }

    public static ByteBuf httpRequestAsBuf(HttpMethod method, String url, String body) {
        return httpMessageToBytes(httpRequest(method, url, body));
    }

    public static ByteBuf httpRequestAsBuf(HttpMethod method, String url) {
        return httpMessageToBytes(httpRequest(method, url));
    }

    public static ByteBuf httpMessageToBytes(HttpMessage message) {
        ChannelHandler codec;

        if (message instanceof HttpRequest) {
            codec = new HttpClientCodec();
        } else {
            codec = new HttpServerCodec();
        }

        EmbeddedChannel channel = new EmbeddedChannel(codec, new HttpObjectAggregator(100 * 1024));

        channel.writeOutbound(message);
        CompositeByteBuf httpBytes = Unpooled.compositeBuffer();
        Object result = channel.readOutbound();
        while (result != null) {
            httpBytes.addComponent((ByteBuf) result);
            result = channel.readOutbound();
        }
        return httpBytes.copy(0, httpBytes.capacity());
    }
}
