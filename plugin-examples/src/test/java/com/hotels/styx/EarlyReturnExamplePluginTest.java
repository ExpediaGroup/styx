package com.hotels.styx;

import com.hotels.styx.api.*;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


/**
 * This tests the behaviours added in the example plugin.
 */

public class EarlyReturnExamplePluginTest {
    @Test
    public void returnsEarly() {
        EarlyReturnExamplePlugin plugin = new EarlyReturnExamplePlugin();

        LiveHttpRequest request = LiveHttpRequest.get("/")
                .header("X-Respond", "foo")
                .build();
        HttpInterceptor.Chain chain = request1 -> Eventual.of(LiveHttpResponse.response().build());

        Eventual<LiveHttpResponse> eventualLive = plugin.intercept(request, chain);
        Eventual<HttpResponse> eventual = eventualLive.flatMap(response -> response.aggregate(100));

        HttpResponse response = Mono.from(eventual).block();

        assertThat(response.bodyAs(UTF_8), is("Responding from plugin"));
    }

}
