/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.api.exceptions;

import com.hotels.styx.api.Id;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * An exception to be thrown when there are no hosts available.
 */
public class NoAvailableHostsException extends RuntimeException implements StyxException, ExternalFault {
    private final Id applicationId;

    /**
     * Constructor.
     *
     * @param applicationId ID of the application for which there are no hosts
     */
    public NoAvailableHostsException(Id applicationId) {
        super(String.format("No hosts available for application %s", requireNonNull(applicationId)));
        this.applicationId = applicationId;
    }

    @Override
    public Optional<Id> origin() {
        return Optional.empty();
    }

    @Override
    public Id application() {
        return applicationId;
    }
}
