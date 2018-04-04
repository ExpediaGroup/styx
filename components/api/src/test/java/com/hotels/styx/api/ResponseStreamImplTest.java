package com.hotels.styx.api;

import org.testng.annotations.Test;

import java.util.Optional;

import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.ResponseStreamImpl.responseStream;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ResponseStreamImplTest {

    @Test
    public void joinsResponseStreamsSynchronously() throws Exception {
        ResponseStream s1 = responseStream(response(OK).build());

        ResponseStream s2 = s1.transform(response -> response.newBuilder().header("X-Test", "ok").build());

        HttpResponse response = ((ResponseStreamImpl) s2).observable().toBlocking().first();
        assertThat(response.header("X-Test"), is(Optional.of("ok")));
    }

    @Test
    public void joinsResponseStreamsAsynchronously() throws Exception {
        ResponseStream s1 = responseStream(response(OK).build());

        ResponseStream s2 = s1.transformAsync(response -> completedFuture(response.newBuilder().header("X-Test", "ok").build()));

        HttpResponse response = ((ResponseStreamImpl) s2).observable().toBlocking().first();
        assertThat(response.header("X-Test"), is(Optional.of("ok")));
    }

    @Test
    public void createsFromFuture() throws Exception {
        ResponseStream stream = responseStream(completedFuture(response(OK).build()));

        HttpResponse response = ((ResponseStreamImpl) stream).observable().toBlocking().first();
        assertThat(response.status(), is(OK));

    }
}