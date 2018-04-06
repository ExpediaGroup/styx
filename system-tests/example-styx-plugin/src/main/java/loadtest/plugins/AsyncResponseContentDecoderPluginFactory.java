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
package loadtest.plugins;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static rx.Observable.timer;

public class AsyncResponseContentDecoderPluginFactory implements PluginFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncResponseContentDecoder.class);

    @Override
    public Plugin create(PluginFactory.Environment environment) {
        AsyncPluginConfig config = environment.pluginConfig(AsyncPluginConfig.class);
        return new AsyncResponseContentDecoder(config);
    }

    private static class AsyncResponseContentDecoder extends AbstractTestPlugin {
        private final int delayMillis;
        private final int maxContentLength;

        AsyncResponseContentDecoder(AsyncPluginConfig config) {
            this.delayMillis = config.delayMillis();
            this.maxContentLength = config.maxContentLength();

            LOGGER.warn("AsyncResponseContentDecoder starting. delayMillis={}, maxContentLength={}",
                    new Object[]{this.delayMillis, this.maxContentLength});
        }

        @Override
        public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
            return chain.proceed(request)
                    .flatMap(response ->
                            response.decode(buf -> buf.toString(UTF_8), this.maxContentLength)
                                    .flatMap(decodedResponse -> timer(this.delayMillis, MILLISECONDS)
                                            .map(x -> decodedResponse.responseBuilder()
                                                    .body(decodedResponse.body())
                                                    .build())
                                    )
                    );
        }
    }
}
