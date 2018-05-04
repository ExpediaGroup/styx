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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.net.spi.nameservice.NameService;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MockNameService implements NameService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalNameServiceDescriptor.class);
    static final MockNameService SELF = new MockNameService();

    private final ThreadLocal<NameService> delegate = new ThreadLocal<>();

    /**
     * Sets the delegate name server to allow consumers to customise the NameServer behaviour.
     *
     * @param delegate
     */
    public void setDelegate(NameService delegate) {
        LOGGER.info("Setting delegate to: {}", delegate);
        this.delegate.set(delegate);
    }

    public void unset() {
        LOGGER.info("Unsetting delegate");
        this.delegate.remove();
    }

    @Override
    public InetAddress[] lookupAllHostAddr(String hostName) throws UnknownHostException {

        NameService nameService = delegate.get();
        if (nameService != null) {
            InetAddress[] result = nameService.lookupAllHostAddr(hostName);
            Integer resultLength = Optional.ofNullable(result)
                    .map(addresses -> addresses.length)
                    .orElse(null);

            LOGGER.info("lookup addresses for host: '{}' results={}", hostName, resultLength);
            return result;
        }

        throw new UnknownHostException("MockNameServer is not configured");
    }

    @Override
    public String getHostByAddr(byte[] bytes) throws UnknownHostException {
        LOGGER.info("lookup hosts for address: " + toByteString(bytes));

        NameService nameService = delegate.get();
        if (nameService != null) {
            String result = nameService.getHostByAddr(bytes);
            LOGGER.info("lookup hosts for address: '{}' result='{}'", toByteString(bytes), result);
            return result;
        }

        throw new UnknownHostException("MockNameServer is not configured");
    }

    private static String toByteString(byte[] bytes) {
        return Stream.of(bytes)
                .map(Object::toString)
                .collect(Collectors.joining("."));
    }
}
