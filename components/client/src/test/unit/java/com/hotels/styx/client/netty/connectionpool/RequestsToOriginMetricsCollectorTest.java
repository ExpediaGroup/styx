/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.applications.metrics.OriginMetrics;
import io.micrometer.core.instrument.Timer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;

import java.util.Optional;

import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpRequest;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpResponseAsBuf;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.MOVED_PERMANENTLY;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RequestsToOriginMetricsCollectorTest {
    private final Origin origin;

    private final OriginMetrics originMetrics = mock(OriginMetrics.class);
    private final ChannelHandlerContext ctx;

    private static final String STOCK_BODY =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin fermentum purus"
                    + " non lectus fermentum hendrerit. Donec est magna, ornare facilisis nunc sit amet,"
                    + " elementum congue tortor. Nullam et diam erat. Sed facilisis, tortor id sodales"
                    + " porttitor, elit metus congue tortor, vel elementum ligula urna sit amet erat."
                    + " Aliquam et gravida nisl. Nunc sagittis neque sapien, id varius mauris interdum"
                    + " vitae. Aliquam ac suscipit leo, ultrices malesuada magna. Morbi risus nulla,"
                    + " vehicula eget eros posuere.";

    public RequestsToOriginMetricsCollectorTest() {
        Id appId = id("MyApp");
        origin = newOriginBuilder("hostA", 80)
                .applicationId(appId)
                .id("h1")
                .build();
        ctx = mock(ChannelHandlerContext.class);
    }

    private EmbeddedChannel buildEmbeddedChannel() {
        return new EmbeddedChannel(
                new HttpClientCodec(),
                new RequestsToOriginMetricsCollector(originMetrics),
                new NettyToStyxResponsePropagator(mock(FluxSink.class), this.origin)
        );
    }

    /**
     * Concatenates result of outbound Netty pipeline into an ByteBuf object.
     * <p/>
     * The optional byte buffer is returned only if a pipeline processing
     * resulted in outgoing bytes. Otherwise optional return value is absent.
     */
    private static Optional<ByteBuf> grabSentBytes(EmbeddedChannel channel) {
        CompositeByteBuf outboundBytes = Unpooled.compositeBuffer();

        Object result = channel.readOutbound();
        while (result != null) {
            outboundBytes.addComponent((ByteBuf) result);
            result = channel.readOutbound();
        }

        if (outboundBytes.numComponents() > 0) {
            return Optional.of(outboundBytes);
        }
        return Optional.empty();
    }

    @Test
    public void failIfCreatedWithoutOriginMetrics() {
        assertThrows(NullPointerException.class,
                () -> new RequestsToOriginMetricsCollector(null));
    }

    @Test
    public void receivedResponse200OkUpdatesSuccessCounters() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        //
        // Send out a HttpRequest in outbound direction, towards origins:
        //
        HttpRequest request = httpRequest(GET, "http://www.hotels.com/foo/bar/request");
        channel.writeOutbound(request);
        assertThat(grabSentBytes(channel).isPresent(), is(true));

        //
        // Receive a response from Origin:
        //
        ByteBuf response = httpResponseAsBuf(OK, STOCK_BODY);
        channel.writeInbound(response);

        //
        // Ensure that counters are updated:
        //
        verify(originMetrics).requestSuccess();
    }

    @Test
    public void successCountersUpdatedWhenFirstChunkReceived() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        //
        // Send out a HttpRequest in outbound direction, towards origins:
        //
        HttpRequest request = httpRequest(GET, "http://www.hotels.com/foo/bar/request");
        channel.writeOutbound(request);
        assertThat(grabSentBytes(channel).isPresent(), is(true));

        ByteBuf response = httpResponseAsBuf(OK, STOCK_BODY).retain();
        int len = response.writerIndex() - response.readerIndex();

        //
        // Send the response in two chunks. The success counters are updated immediately when
        // the first chunk is received:
        //
        channel.writeInbound(response.slice(0, 100));
        verify(originMetrics).requestSuccess();

        //
        // Send the next chunk. Demonstrate that counters remain unchanged. This is to ensure
        // they don't get incremented twice:
        //
        channel.writeInbound(response.slice(100, len - 100));
        verify(originMetrics, atMostOnce()).requestSuccess();
    }

    @Test
    public void receivedResponse400BadRequestUpdatesSuccessCounters() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        //
        // Send out a HttpRequest in outbound direction, towards origins:
        //
        HttpRequest request = httpRequest(GET, "http://www.hotels.com/foo/bar/request");
        channel.writeOutbound(request);
        assertThat(grabSentBytes(channel).isPresent(), is(true));

        //
        // Receive a response from Origin:
        //
        ByteBuf response = httpResponseAsBuf(BAD_REQUEST, STOCK_BODY);
        channel.writeInbound(response);

        //
        // Ensure that counters are not updated. The error is on the client side rather than
        // in applications/origins. Therefore we don't count this as an error.
        //
        verify(originMetrics, never()).requestError();

        verify(originMetrics).requestSuccess();
    }

    @Test
    public void errorCountersUpdatedWhenFirstChunkReceived() {

        EmbeddedChannel channel = buildEmbeddedChannel();

        //
        // Send out a HttpRequest in outbound direction, towards origins:
        //
        HttpRequest request = httpRequest(GET, "http://www.hotels.com/foo/bar/request");
        channel.writeOutbound(request);
        assertThat(grabSentBytes(channel).isPresent(), is(true));

        ByteBuf response = httpResponseAsBuf(INTERNAL_SERVER_ERROR, STOCK_BODY).retain();
        int len = response.writerIndex() - response.readerIndex();

        //
        // Send the response in two chunks. The success counters are updated immediately when
        // the first chunk is received:
        //
        channel.writeInbound(response.slice(0, 100));
        verify(originMetrics).requestError();

        //
        // Send the next chunk. Demonstrate that counters remain unchanged. This is to ensure
        // they don't get incremented twice:
        //
        channel.writeInbound(response.slice(100, len - 100));
        verify(originMetrics, atMostOnce()).requestError();
    }

    @Test
    public void latencyHistogramUpdatedOnlyByLastHttpContent() {
        Timer.Sample sample = mock(Timer.Sample.class);
        when(originMetrics.startTimer()).thenReturn(sample);
        Timer timer = mock(Timer.class);
        when(originMetrics.requestLatencyTimer()).thenReturn(timer);

        EmbeddedChannel channel = buildEmbeddedChannel();

        //
        // Send out a HttpRequest in outbound direction, towards origins:
        //
        HttpRequest request = httpRequest(GET, "http://www.hotels.com/foo/bar/request");
        channel.writeOutbound(request);
        assertThat(grabSentBytes(channel).isPresent(), is(true));
        verify(originMetrics, never()).requestLatencyTimer();

        ByteBuf response = httpResponseAsBuf(OK, STOCK_BODY).retain();
        int len = response.writerIndex() - response.readerIndex();

        //
        // Send the response in two chunks.
        //
        // Send the first chunk, and demonstrate that timer is not yet updated.
        // This is because the HTTP response is not yet fully received.
        //
        channel.writeInbound(response.slice(0, 100));
        verify(originMetrics, never()).requestLatencyTimer();

        //
        // Send the next chunk. HTTP response is now fully received. Demonstrate
        // that timer is now correctly updated.
        //
        channel.writeInbound(response.slice(100, len - 100));
        verify(sample).stop(timer);
    }

    @Test
    public void timeToFirstByteHistogramUpdatedWhenFirstContentChunkReceived() {
        Timer.Sample sample = mock(Timer.Sample.class);
        when(originMetrics.startTimer()).thenReturn(sample);
        Timer timer = mock(Timer.class);
        when(originMetrics.timeToFirstByteTimer()).thenReturn(timer);

//        Timer timer = this.metricRegistry.timer(name(ORIGIN_METRIC_PREFIX, "requests.time-to-first-byte"));
//        assertThat(timer.getCount(), is(0L));

        EmbeddedChannel channel = buildEmbeddedChannel();

        //
        // Send out a HttpRequest in outbound direction, towards origins:
        //
        HttpRequest request = httpRequest(GET, "http://www.hotels.com/foo/bar/request");
        channel.writeOutbound(request);
        assertThat(grabSentBytes(channel).isPresent(), is(true));
        verify(originMetrics, never()).timeToFirstByteTimer();

        ByteBuf response = httpResponseAsBuf(OK, STOCK_BODY).retain();
        int len = response.writerIndex() - response.readerIndex();

        //
        // Send the response in two chunks. The timer is updated immediately when
        // the first chunk is received:
        //
        channel.writeInbound(response.slice(0, 100));
        verify(sample).stop(timer);

        //
        // Send the next chunk. Demonstrate that timer remains unchanged. This is to ensure
        // it doesn't get recorded twice:
        //
        channel.writeInbound(response.slice(100, len - 100));
        verify(sample, atMostOnce()).stop(timer);
    }


    @Test
    public void response100ContinueUpdatesInformationalMeterOnly() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        ByteBuf response = httpResponseAsBuf(CONTINUE, STOCK_BODY).retain();
        channel.writeInbound(response);

        verify(originMetrics).responseWithStatusCode(100);
        verify(originMetrics).requestSuccess();
    }

    @Test
    public void response200OkUpdatesSuccessfulMeterOnly() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        ByteBuf response = httpResponseAsBuf(OK, STOCK_BODY).retain();
        channel.writeInbound(response);

        verify(originMetrics).responseWithStatusCode(200);
        verify(originMetrics).requestSuccess();
    }

    @Test
    public void response301MovedPermanentlyUpdatesRedirectionMeterOnly() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        ByteBuf response = httpResponseAsBuf(MOVED_PERMANENTLY, STOCK_BODY).retain();
        channel.writeInbound(response);

        verify(originMetrics).responseWithStatusCode(301);
        verify(originMetrics).requestSuccess();
    }

    @Test
    public void response400BadRequestUpdatesClientErrorMeterOnly() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        ByteBuf response = httpResponseAsBuf(BAD_REQUEST, STOCK_BODY).retain();
        channel.writeInbound(response);

        verify(originMetrics).responseWithStatusCode(400);
        verify(originMetrics).requestSuccess();
    }

    @Test
    public void response501NotImplementedUpdatesRedirectionMeterOnly() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        ByteBuf response = httpResponseAsBuf(NOT_IMPLEMENTED, STOCK_BODY).retain();
        channel.writeInbound(response);

        verify(originMetrics).responseWithStatusCode(501);
        verify(originMetrics, never()).requestSuccess();
    }

    @Test
    public void updateSuccessCountersWhen200OkReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(200);
        handler.channelRead(this.ctx, msg);

        verify(originMetrics).requestSuccess();
    }

    @Test
    public void updateSuccessCountersWhen201CreatedReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(201);
        handler.channelRead(this.ctx, msg);

        verify(originMetrics).requestSuccess();
    }

    @Test
    public void updateSuccessCountersWhen206PartialContentReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(206);
        handler.channelRead(this.ctx, msg);

        verify(originMetrics).requestSuccess();
    }

    @Test
    public void updateSuccessCountersWhen300MultipleChoicesReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(300);
        handler.channelRead(this.ctx, msg);

        verify(originMetrics).requestSuccess();
    }

    @Test
    public void updateSuccessCountersWhen308MultipleChoicesReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(308);
        handler.channelRead(this.ctx, msg);

        verify(originMetrics).requestSuccess();
    }

    @Test
    public void updateErrorCountersWhen500InternalServerErrorReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(500);
        handler.channelRead(this.ctx, msg);

        verify(originMetrics).requestError();
        verify(originMetrics, never()).requestSuccess();
    }

    @Test
    public void updateErrorCountersWhen503ServiceUnavailableReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(503);
        handler.channelRead(this.ctx, msg);

        verify(originMetrics).requestError();
        verify(originMetrics, never()).requestSuccess();
    }

    @Test
    public void httpContentObjectDoesNotUpdateCounters() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpContent msg = mock(HttpContent.class);
        handler.channelRead(this.ctx, msg);

        verify(originMetrics, never()).requestError();
        verify(originMetrics, never()).requestSuccess();
    }

    @Test
    public void lastHttpContentObjectDoesNotUpdateCounters() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        LastHttpContent msg = mock(LastHttpContent.class);
        handler.channelRead(this.ctx, msg);

        verify(originMetrics, never()).requestError();
        verify(originMetrics, never()).requestSuccess();
    }

    private static HttpResponse mockHttpResponseWithCode(int code) {
        HttpResponse msg = mock(HttpResponse.class);
        HttpResponseStatus status = mock(HttpResponseStatus.class);
        when(status.code()).thenReturn(code);
        when(msg.status()).thenReturn(status);
        return msg;
    }
}
