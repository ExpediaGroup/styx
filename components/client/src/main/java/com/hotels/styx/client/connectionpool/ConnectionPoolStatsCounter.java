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
package com.hotels.styx.client.connectionpool;

/**
 * Accumulates statistics during the operation of on {@link ConnectionPool}.
 */
interface ConnectionPoolStatsCounter extends ConnectionPool.Stats {
    ConnectionPoolStatsCounter NULL_CONNECTION_POOL_STATS = new ConnectionPoolStatsCounter() {
        @Override
        public void recordTimeToFirstByte(long timeToFirstByteMillis) {
        }

        @Override
        public int busyConnectionCount() {
            return 0;
        }

        @Override
        public int availableConnectionCount() {
            return 0;
        }

        @Override
        public int pendingConnectionCount() {
            return 0;
        }

        @Override
        public long timeToFirstByteMs() {
            return 0;
        }

        @Override
        public int connectionAttempts() {
            return 0;
        }

        @Override
        public int connectionFailures() {
            return 0;
        }

        @Override
        public int closedConnections() {
            return 0;
        }

        @Override
        public int terminatedConnections() {
            return 0;
        }

    };

    /**
     * Records time to first byte for each HTTP transaction.
     *
     * @param timeToFirstByteMillis time to first byte in milliseconds
     */
    void recordTimeToFirstByte(long timeToFirstByteMillis);
}
