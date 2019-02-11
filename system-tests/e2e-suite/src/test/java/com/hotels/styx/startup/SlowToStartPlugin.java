/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.startup;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;

/**
 * Plugin that takes a long time to start up
 */
public class SlowToStartPlugin implements Plugin {
    // Non-final static field, a.k.a. dirty hack, however, it allows us to test the start-up without real class-loading for plugins
    // WARNING: this means you can't reuse this class for multiple tests
    public static volatile boolean delay = true;

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        // Plugin doesn't need to actually do anything
        return chain.proceed(request);
    }

    public static class Factory implements PluginFactory {
        @Override
        public Plugin create(Environment environment) {
            try {
                while (delay) {
//                    System.out.println("Being slow intentionally");
                    Thread.sleep(500L);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            return new SlowToStartPlugin();
        }
    }
}
