package com.hotels.styx;

import com.hotels.styx.api.*;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ReplaceLiveContentExampleTest {

    private LiveHttpRequest request;
    private HttpInterceptor.Chain chain;

    @Test
    public void replacesLiveContent() {
        // Set up
        ReplaceLiveContentExampleConfig config = new ReplaceLiveContentExampleConfig();

        ReplaceLiveContentExample plugin = new ReplaceLiveContentExample(config);

        LiveHttpRequest request = LiveHttpRequest.get("/")
                .header("X-Respond", "foo")
                .build();
        HttpInterceptor.Chain chain = request1 -> Eventual.of(LiveHttpResponse.response().build());

        // Execution
        Eventual<LiveHttpResponse> eventualLive = plugin.intercept(request, chain);
        Eventual<HttpResponse> eventual = eventualLive.flatMap(response -> response.aggregate(100));

        HttpResponse response = Mono.from(eventual).block();

        // Assertion
        assertThat(response.bodyAs(UTF_8), is("replacement"));
    }
}