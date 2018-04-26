package plugins.groovy

import com.hotels.styx.api.HttpFilter
import com.hotels.styx.api.HttpHandler

import rx.Observable

import static com.hotels.styx.api.HttpHeaderNames.LOCATION
import static io.netty.handler.codec.http.HttpResponseStatus.MOVED_PERMANENTLY

class UrlRewrite implements HttpFilter {

    @Override
    Observable<HttpResponse> filter(HttpRequest request, HttpHandler handler) {
        return response(MOVED_PERMANENTLY)
                .header(LOCATION, "http://hotels.com/")
                .build();
    }
}
