/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import com.hotels.styx.api.extension.service.spi.StyxService;

/**
 * A helper class to manipulate StyxServer objects.
 */
public final class StyxServers {
    private StyxServers() {
    }

    /**
     * Convert a StyxService to a Guava Service.
     *
     * @param styxService
     * @return
     */
    public static Service toGuavaService(StyxService styxService) {
        return new AbstractService() {
            @Override
            protected void doStart() {
                styxService.start()
                        .thenAccept(x -> notifyStarted())
                        .exceptionally(e -> {
                            notifyFailed(e);
                            return null;
                        });
            }

            @Override
            protected void doStop() {
                styxService.stop()
                        .thenAccept(x -> notifyStopped())
                        .exceptionally(e -> {
                            notifyFailed(e);
                            return null;
                        });
            }
        };
    }
}
