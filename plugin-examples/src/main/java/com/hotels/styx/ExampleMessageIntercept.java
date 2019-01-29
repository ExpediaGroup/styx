package com.hotels.styx;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This shows an example of an inteceptor that responds to a received request
 */

public class Example implements Plugin {

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        if (request.header("X-Respond").isPresent()) {
            // request.consume();
            return Eventual.of(HttpResponse.response(OK)
                    .header(CONTENT_TYPE, "text/plain; charset=utf-8")
                    .body("Responding from plugin", UTF_8)
                    .build()
                    .stream());
        } else {
            return chain.proceed(request);
        }
    }
}

/**
 * You can replace live content
 * If you need to replace a message body based on information in the message headers and don't care what is in
 * the message body. (E.g you need to add a HTTP message body based on a HTTP error code)
 *
 * You can transform a live HTTP message body using a replaceWith Bytestream operator such as in the example.
 */

//Example:

chain.proceed(request)
        .map(response -> response.newBuilder()
        .body(body -> body.replaceWith(ByteStream.from("replacement", UTF_8)))
        .build());
/*
This can be used to replace a message body without having to look into it, this will also save heap as the
 live upstream response body is never stored in heap in full
*/


/**
 * You can replace content by aggregation
 * For example the message content contains a JSON object and you need to modify the object somehow.
 * You can aggregate the live HTTP message into a full HTTP message. Transform the content into a full message context
 * and convert the results back to live HTTP message
 */

//Example:

        chain.proceed(request)
        .flatMap(response -> it.aggregate(10000))
        .map(response -> {
        String body = response.bodyAs(UTF_8);
        return response.newBuilder()
        .body(modify(body), UTF_8)
        .build();
        })
        .map(HttpResponse::stream);

/*
This can be used when you need to do something with full content and when you need to replace content after looking
into it
You need to do something with full content. This uses more heap as the full response it transiently stored in heap
 */

