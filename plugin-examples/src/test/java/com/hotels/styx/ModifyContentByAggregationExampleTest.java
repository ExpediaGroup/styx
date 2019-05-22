package com.hotels.styx;

import com.hotels.styx.ModifyContentByAggregationExample.Config;
import com.hotels.styx.api.*;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ModifyContentByAggregationExampleTest {

    @Test
    public void modifiesContent() {
        // Set up
        Config config = new Config("MyExtraText");
        ModifyContentByAggregationExample plugin = new ModifyContentByAggregationExample(config);

        LiveHttpRequest request = get("/").build();

        HttpInterceptor.Chain chain = anyRequest -> Eventual.of(response()
                .body("OriginalBody", UTF_8)
                .build()
                .stream());

        // Execution

        HttpResponse response = Mono.from(plugin.intercept(request, chain)
                .flatMap(liveResponse -> liveResponse.aggregate(100)))
                .block();

        // Assertion
        assertThat(response.bodyAs(UTF_8), is("OriginalBodyMyExtraText"));
    }
}
