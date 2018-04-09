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
package com.hotels.styx.api.client;

import com.google.common.base.Throwables;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpHeader;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import rx.Observable;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.io.ByteStreams.toByteArray;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.common.Joiners.JOINER_ON_COMMA;

/**
 * An Implementation of HttpClient that uses the java sun URL connection object.
 * <p>
 * todo should be moved out of api
 */
public class UrlConnectionHttpClient implements HttpClient {
    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    };

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final SSLSocketFactory sslSocketFactory;

    /**
     * Constructs a new client.
     *
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     * @param readTimeoutMillis    socket read/write timeout in milliseconds
     */
    public UrlConnectionHttpClient(int connectTimeoutMillis, int readTimeoutMillis) {
        this(connectTimeoutMillis, readTimeoutMillis, sslSocketFactory("TLS"));
    }

    /**
     * Constructs a new client.
     *
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     * @param readTimeoutMillis    socket read/write timeout in milliseconds
     * @param protocol             supported TLS protocol, TLSv1.1, TLSv1.2, etc.
     */
    public UrlConnectionHttpClient(int connectTimeoutMillis, int readTimeoutMillis, String protocol) {
        this(connectTimeoutMillis, readTimeoutMillis, sslSocketFactory(protocol));
    }

    private UrlConnectionHttpClient(int connectTimeoutMillis, int readTimeoutMillis, SSLSocketFactory sslSocketFactory) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.sslSocketFactory = checkNotNull(sslSocketFactory);
    }

    private static SSLSocketFactory sslSocketFactory(String protocol) {
        try {
            SSLContext context = SSLContext.getInstance(protocol);
            context.init(null, new TrustManager[]{TRUST_ALL}, new SecureRandom());
            return context.getSocketFactory();
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Observable<HttpResponse> sendRequest(HttpRequest request) {
        try {
            HttpURLConnection connection = openConnection(request);
            prepareRequest(connection, request);
            return Observable.just(readResponse(connection));
        } catch (IOException e) {
            return Observable.error(e);
        }
    }

    private HttpURLConnection openConnection(HttpRequest request) throws IOException {
        URL url = request.url().toURL();
        URLConnection urlConnection = url.openConnection();
        HttpURLConnection connection = "https".equals(url.getProtocol())
                ? httpsURLConnection((HttpsURLConnection) urlConnection)
                : (HttpURLConnection) urlConnection;
        connection.setConnectTimeout(this.connectTimeoutMillis);
        connection.setReadTimeout(this.readTimeoutMillis);
        return connection;
    }

    private HttpsURLConnection httpsURLConnection(HttpsURLConnection urlConnection) {
        urlConnection.setSSLSocketFactory(sslSocketFactory);
        return urlConnection;
    }

    private void prepareRequest(HttpURLConnection connection, HttpRequest request) throws IOException {
        connection.setRequestMethod(request.method().name());
        connection.setRequestProperty("Connection", "close");
        connection.setDoInput(true);
        for (HttpHeader httpHeader : request.headers()) {
            connection.addRequestProperty(httpHeader.name(), httpHeader.value());
        }
    }

    private HttpResponse readResponse(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        HttpResponse.Builder response = response(HttpResponseStatus.valueOf(status));

        try (InputStream stream = getInputStream(connection, status)) {
            byte[] content = toByteArray(stream);
            if (content.length > 0) {
                response.body(content);
            }
            connection.getHeaderFields().forEach((key, value) -> {
                if (!isNullOrEmpty(key)) {
                    response.header(key, JOINER_ON_COMMA.join(value));
                }
            });
        }

        return response.build();
    }

    private static InputStream getInputStream(HttpURLConnection connection, int status) throws IOException {
        if (status >= 400) {
            return Optional.ofNullable(connection.getErrorStream())
                    .orElseGet(EmptyInputStream::new);
        }

        return connection.getInputStream();
    }

    private static class EmptyInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            return -1;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.connectTimeoutMillis, this.readTimeoutMillis);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        UrlConnectionHttpClient other = (UrlConnectionHttpClient) obj;
        return Objects.equals(this.connectTimeoutMillis, other.connectTimeoutMillis) && Objects.equals(this.readTimeoutMillis, other.readTimeoutMillis);
    }
}
