package com.hotels.styx;

import com.hotels.styx.api.*;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import static com.hotels.styx.api.LiveHttpRequest.get;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ReplaceLiveContentExampleTest {

    @Test
    public void replacesLiveContent() {
        // Set up
        ReplaceLiveContentExampleConfig config = new ReplaceLiveContentExampleConfig("myNewContent");

        ReplaceLiveContentExample plugin = new ReplaceLiveContentExample(config);

        LiveHttpRequest request = get("/").build();
        HttpInterceptor.Chain chain = request1 -> Eventual.of(LiveHttpResponse.response().build());

        // Execution
        HttpResponse response = Mono.from(plugin.intercept(request, chain)
                .flatMap(liveResponse -> liveResponse.aggregate(100)))
                .block();

        // Assertion
        assertThat(response.bodyAs(UTF_8), is("myNewContent"));
    }
}