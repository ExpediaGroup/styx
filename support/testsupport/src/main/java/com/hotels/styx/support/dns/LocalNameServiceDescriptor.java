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
import sun.net.spi.nameservice.NameServiceDescriptor;
import sun.net.spi.nameservice.dns.DNSNameService;

import java.net.InetAddress;
import java.net.UnknownHostException;

/*
 * NOTE: This class is not suited for parallel tests.
 */
public class LocalNameServiceDescriptor implements NameServiceDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalNameServiceDescriptor.class);

    @Override
    public NameService createNameService() throws Exception {
        return new MockNameService();
    }

    @Override
    public String getProviderName() {
        return MockNameService.dnsName;
    }

    @Override
    public String getType() {
        return "dns";
    }

    static class MockNameService implements NameService {

        static final String dnsName = "local-dns";
        private static final DNSNameService delegate = getDelegate();

        @Override
        public InetAddress[] lookupAllHostAddr(String hostName) throws UnknownHostException {
            LOGGER.warn("lookup addresses for host: " + hostName);

            try {
                return delegate.lookupAllHostAddr(hostName);
            } catch (Throwable cause) {
                LOGGER.warn("Lookup failure: " + cause);
                cause.printStackTrace();
                throw cause;
            }
        }

        @Override
        public String getHostByAddr(byte[] bytes) throws UnknownHostException {
            LOGGER.warn("lookup hosts for address: " + bytes);
            return delegate.getHostByAddr(bytes);
        }

        private static DNSNameService getDelegate() {
            try {
                return new DNSNameService();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
