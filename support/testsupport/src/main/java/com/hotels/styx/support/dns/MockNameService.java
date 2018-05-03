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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MockNameService implements NameService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalNameServiceDescriptor.class);
    static final MockNameService SELF = new MockNameService();

    private AtomicReference<NameService> delegate = new AtomicReference<>(null);

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
        this.delegate.set(null);
    }

    @Override
    public InetAddress[] lookupAllHostAddr(String hostName) throws UnknownHostException {

        if (delegate.get() != null) {
            InetAddress[] result = delegate.get().lookupAllHostAddr(hostName);
            LOGGER.info("lookup addresses for host: '{}' results={}", hostName, result.length);
            return result;
        }

        throw new UnknownHostException("MockNameServer is not configured");
    }

    @Override
    public String getHostByAddr(byte[] bytes) throws UnknownHostException {
        LOGGER.info("lookup hosts for address: " + toByteString(bytes));

        if (delegate.get() != null) {
            String result = delegate.get().getHostByAddr(bytes);
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
