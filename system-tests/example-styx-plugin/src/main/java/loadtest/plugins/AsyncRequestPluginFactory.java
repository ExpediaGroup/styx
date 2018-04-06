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
import rx.Observable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static rx.Observable.timer;

public class AsyncRequestPluginFactory implements PluginFactory {

    @Override
    public Plugin create(PluginFactory.Environment environment) {
        AsyncPluginConfig config = environment.pluginConfig(AsyncPluginConfig.class);
        return new AsyncRequestInterceptor(config);
    }

    private static class AsyncRequestInterceptor extends AbstractTestPlugin {
        private final AsyncPluginConfig config;

        AsyncRequestInterceptor(AsyncPluginConfig config) {
            this.config = config;
        }

        @Override
        public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
            return timer(config.delayMillis(), MILLISECONDS)
                    .flatMap(x -> chain.proceed(request));
        }
    }
}
