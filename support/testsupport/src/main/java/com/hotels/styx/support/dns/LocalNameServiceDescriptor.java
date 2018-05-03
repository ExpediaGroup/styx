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
package com.hotels.styx.support.dns;

import sun.net.spi.nameservice.NameService;
import sun.net.spi.nameservice.NameServiceDescriptor;

import java.util.Optional;

/*
 * NOTE: This class is not suited for parallel tests.
 */
public class LocalNameServiceDescriptor implements NameServiceDescriptor {

    private static final String dnsName = "local-dns";
    private static volatile MockNameService nameService = null;

    @Override
    public NameService createNameService() {
        nameService = MockNameService.SELF;
        return nameService;
    }

    @Override
    public String getProviderName() {
        return dnsName;
    }

    @Override
    public String getType() {
        return "dns";
    }

    /**
     * Returns a shared MockNameService instance.
     *
     * @return a MockNameService.
     */
    public static MockNameService get() {
        return Optional.ofNullable(nameService)
                .orElseThrow(() -> new RuntimeException("MockNameService not configured"));
    }

}
