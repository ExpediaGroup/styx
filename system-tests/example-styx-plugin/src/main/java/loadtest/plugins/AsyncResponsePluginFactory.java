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
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.common.CompletableFutures;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static rx.Observable.timer;

public class AsyncResponsePluginFactory implements PluginFactory {
    @Override
    public Plugin create(Environment environment) {
        AsyncPluginConfig config = environment.pluginConfig(AsyncPluginConfig.class);
        return new AsyncResponseInterceptor(config);
    }

    private static class AsyncResponseInterceptor extends AbstractTestPlugin {
        private final AsyncPluginConfig config;

        AsyncResponseInterceptor(AsyncPluginConfig config) {
            this.config = config;
        }

        @Override
        public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
            return chain.proceed(request)
                    .flatMap(response -> StyxObservable.from(processAsynchronously(response, config.delayMillis())));
        }

        private static CompletableFuture<HttpResponse> processAsynchronously(HttpResponse response, int delayMillis) {
            return CompletableFutures.fromSingleObservable(timer(delayMillis, MILLISECONDS)
                    .map(x -> response));
        }
    }
}

