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

import com.codahale.metrics.Timer;
import java.util.Optional;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.applications.metrics.ApplicationMetrics;
import com.hotels.styx.client.applications.metrics.OriginMetrics;
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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Subscriber;

import java.util.List;

import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpRequest;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpResponseAsBuf;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.client.applications.OriginStats.REQUEST_FAILURE;
import static com.hotels.styx.client.applications.OriginStats.REQUEST_SUCCESS;
import static com.hotels.styx.client.netty.MetricsSupport.IsNotUpdated.hasNotReceivedUpdatesExcept;
import static com.hotels.styx.client.netty.MetricsSupport.name;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.MOVED_PERMANENTLY;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequestsToOriginMetricsCollectorTest {

    private static final List<String> APP_METRIC_PREFIX = singletonList("MyApp");
    private static final List<String> ORIGIN_METRIC_PREFIX = asList("MyApp", "h1");

    private final Id appId;
    private final Origin origin;

    private MetricRegistry metricRegistry;
    private OriginMetrics originMetrics;
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
        this.appId = id("MyApp");
        this.origin = newOriginBuilder("hostA", 80)
                .applicationId(this.appId)
                .id("h1")
                .build();
        this.ctx = mock(ChannelHandlerContext.class);
    }

    @BeforeMethod
    private void setUp() {
        this.metricRegistry = new CodaHaleMetricRegistry();
        ApplicationMetrics appMetrics = new ApplicationMetrics(this.appId, this.metricRegistry);
        this.originMetrics = new OriginMetrics(appMetrics, this.origin);
    }

    @AfterMethod
    private void tearDown() {
        clearMetricsRegistry();
    }

    private void clearMetricsRegistry() {
        for (String name : this.metricRegistry.getNames()) {
            this.metricRegistry.deregister(name);
        }
    }

    private EmbeddedChannel buildEmbeddedChannel() {
        ApplicationMetrics appMetrics = new ApplicationMetrics(this.origin.applicationId(), this.metricRegistry);
        OriginMetrics originMetrics = new OriginMetrics(appMetrics, this.origin);

        return new EmbeddedChannel(
                new HttpClientCodec(),
                new RequestsToOriginMetricsCollector(originMetrics),
                new NettyToStyxResponsePropagator(mock(Subscriber.class), this.origin)
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
            return Optional.of((ByteBuf) outboundBytes);
        }
        return Optional.empty();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void failIfCreatedWithoutOriginMetrics() {
        new RequestsToOriginMetricsCollector(null);
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
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
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
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));

        //
        // Send the next chunk. Demonstrate that counters remain unchanged. This is to ensure
        // they don't get incremented twice:
        //
        channel.writeInbound(response.slice(100, len - 100));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
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
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(0L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(0L));

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
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
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_FAILURE)).getCount(),
                is(1L));

        //
        // Send the next chunk. Demonstrate that counters remain unchanged. This is to ensure
        // they don't get incremented twice:
        //
        channel.writeInbound(response.slice(100, len - 100));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_FAILURE)).getCount(),
                is(1L));
    }

    @Test
    public void latencyHistogramUpdatedOnlyByLastHttpContent() {

        Timer timer = this.metricRegistry.timer(name(ORIGIN_METRIC_PREFIX, "requests.latency"));
        assertThat(timer.getCount(), is(0L));

        EmbeddedChannel channel = buildEmbeddedChannel();

        //
        // Send out a HttpRequest in outbound direction, towards origins:
        //
        HttpRequest request = httpRequest(GET, "http://www.hotels.com/foo/bar/request");
        channel.writeOutbound(request);
        assertThat(grabSentBytes(channel).isPresent(), is(true));
        assertThat(timer.getCount(), is(0L));

        ByteBuf response = httpResponseAsBuf(OK, STOCK_BODY).retain();
        int len = response.writerIndex() - response.readerIndex();

        //
        // Send the response in two chunks.
        //
        // Send the first chunk, and demonstrate that timer is not yet updated.
        // This is because the HTTP response is not yet fully received.
        //
        channel.writeInbound(response.slice(0, 100));
        assertThat(timer.getCount(), is(0L));

        //
        // Send the next chunk. HTTP response is now fully received. Demonstrate
        // that timer is now correctly updated.
        //
        channel.writeInbound(response.slice(100, len - 100));
        assertThat(timer.getCount(), is(1L));
    }

    @Test
    public void response100ContinueUpdatesInformationalMeterOnly() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        ByteBuf response = httpResponseAsBuf(CONTINUE, STOCK_BODY).retain();
        channel.writeInbound(response);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.100")).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.100")).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, REQUEST_SUCCESS),
                name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS),
                name(APP_METRIC_PREFIX, "requests.response.status.100"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.100"))));
    }

    @Test
    public void response200OkUpdatesSuccessfulMeterOnly() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        ByteBuf response = httpResponseAsBuf(OK, STOCK_BODY).retain();
        channel.writeInbound(response);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.200")).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.200")).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, REQUEST_SUCCESS),
                name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS),
                name(APP_METRIC_PREFIX, "requests.response.status.200"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.200"))));
    }

    @Test
    public void response301MovedPermanentlyUpdatesRedirectionMeterOnly() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        ByteBuf response = httpResponseAsBuf(MOVED_PERMANENTLY, STOCK_BODY).retain();
        channel.writeInbound(response);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.301")).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.301")).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, REQUEST_SUCCESS),
                name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS),
                name(APP_METRIC_PREFIX, "requests.response.status.301"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.301"))));
    }

    @Test
    public void response400BadRequestUpdatesClientErrorMeterOnly() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        ByteBuf response = httpResponseAsBuf(BAD_REQUEST, STOCK_BODY).retain();
        channel.writeInbound(response);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.400")).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.400")).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, REQUEST_SUCCESS),
                name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS),
                name(APP_METRIC_PREFIX, "requests.response.status.400"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.400"))));
    }

    @Test
    public void response501NotImplementedUpdatesRedirectionMeterOnly() {
        EmbeddedChannel channel = buildEmbeddedChannel();

        ByteBuf response = httpResponseAsBuf(NOT_IMPLEMENTED, STOCK_BODY).retain();
        channel.writeInbound(response);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.501")).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.501")).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(1L));
        assertThat(this.metricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, REQUEST_FAILURE),
                name(ORIGIN_METRIC_PREFIX, REQUEST_FAILURE),
                name(APP_METRIC_PREFIX, "requests.response.status.501"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.501"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.5xx")
                )));
    }

    @Test
    public void updateSuccessCountersWhen200OkReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(200);
        handler.channelRead(this.ctx, msg);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
    }

    @Test
    public void updateSuccessCountersWhen201CreatedReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(201);
        handler.channelRead(this.ctx, msg);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
    }

    @Test
    public void updateSuccessCountersWhen206PartialContentReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(206);
        handler.channelRead(this.ctx, msg);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
    }

    @Test
    public void updateSuccessCountersWhen300MultipleChoicesReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(300);
        handler.channelRead(this.ctx, msg);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
    }

    @Test
    public void updateSuccessCountersWhen308MultipleChoicesReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(308);
        handler.channelRead(this.ctx, msg);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(1L));
    }

    @Test
    public void updateErrorCountersWhen500InternalServerErrorReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(500);
        handler.channelRead(this.ctx, msg);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(0L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(0L));
    }

    @Test
    public void updateErrorCountersWhen503ServiceUnavailableReceived() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpResponse msg = mockHttpResponseWithCode(503);
        handler.channelRead(this.ctx, msg);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(1L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(0L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(0L));
    }

    @Test
    public void httpContentObjectDoesNotUpdateCounters() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        HttpContent msg = mock(HttpContent.class);
        handler.channelRead(this.ctx, msg);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(0L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(0L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(0L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(0L));
    }

    @Test
    public void lastHttpContentObjectDoesNotUpdateCounters() throws Exception {
        RequestsToOriginMetricsCollector handler = new RequestsToOriginMetricsCollector(this.originMetrics);
        LastHttpContent msg = mock(LastHttpContent.class);
        handler.channelRead(this.ctx, msg);

        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(0L));
        assertThat(this.metricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(0L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS)).getCount(), is(0L));
        assertThat(this.metricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_FAILURE)).getCount(), is(0L));
    }

    private static HttpResponse mockHttpResponseWithCode(int code) {
        HttpResponse msg = mock(HttpResponse.class);
        HttpResponseStatus status = mock(HttpResponseStatus.class);
        when(status.code()).thenReturn(code);
        when(msg.getStatus()).thenReturn(status);
        return msg;
    }

}
