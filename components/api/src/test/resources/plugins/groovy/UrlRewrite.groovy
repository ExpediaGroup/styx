package plugins.groovy

import com.hotels.styx.api.HttpFilter
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import rx.Observable

import static com.hotels.styx.api.HttpHeaderNames.LOCATION
import static com.hotels.styx.api.HttpResponse.Builder.response
import static io.netty.handler.codec.http.HttpResponseStatus.MOVED_PERMANENTLY

class UrlRewrite implements HttpFilter {

    @Override
    Observable<HttpResponse> filter(HttpRequest request, HttpHandler handler) {
        return response(MOVED_PERMANENTLY)
                .header(LOCATION, "http://hotels.com/")
                .build();
    }
}
