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
package com.hotels.styx.api.extension.service.spi;

/**
 * State of the Styx Service.
 */
public enum StyxServiceStatus {
    /**
     * The service has been instantiated, but not started.
     */
    CREATED,

    /**
     * The start() has been called. The service is running
     * its startup actions to become fully operational.
     */
    STARTING,

    /**
     * The service is fully operational.
     */
    RUNNING,

    /**
     * The stop() method has been called. The service is
     * running its shutdown actions.
     */
    STOPPING,

    /**
     * The service has been fully stopped.
     */
    STOPPED,

    /**
     * The service has failed for some reason.
     */
    FAILED
}
