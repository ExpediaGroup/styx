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
package com.hotels.styx.client.retry;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.client.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.configuration.Configuration;

/**
 * Factory for creating retry policy based on configuration settings.
 */
public final class RetryPolicyFactory implements com.hotels.styx.api.client.retrypolicy.spi.RetryPolicyFactory {
    @Override
    public RetryPolicy create(Environment environment, Configuration retryPolicyConfiguration) {
        int retriesCount = retryPolicyConfiguration.get("count", Integer.class)
                .orElse(1);
        return new RetryNTimes(retriesCount);
    }
}
