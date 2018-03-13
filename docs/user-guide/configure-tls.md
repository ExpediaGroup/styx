# Configuring Transport Layer Security

Transport Layer Security (TLS) can be enabled on Styx both for the incoming interface
(connections from clients) and the connections with the backend services.

TLS can be enabled independently for each port of the proxy server, 
and for each of the backend services. 
Styx will convert between the protocols where
necessary. For example, if you have insecure HTTP traffic coming in,
it will be converted to secure HTTPS if necessary.

## Server Side TLS Configuration

An example configuration block to enable TLS security on a Styx server
port is shown below. Note that you can enable both HTTP and
TLS-protected HTTPS ports simultaneously (on different ports).

```yaml
    proxy:
      connectors:
        https:
          port:                 8443
          sslProvider:          OPENSSL # Also supports JDK
          certificateFile:      /conf/tls/testCredentials.crt
          certificateKeyFile:   /conf/tls/testCredentials.key
          sessionTimeoutMillis: 300000
          sessionCacheSize:     20000
          protocols:
            - TLSv1.2
          cipherSuites:
            - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
            - ECDHE-RSA-AES256-GCM-SHA384
            - ECDHE-RSA-AES128-GCM-SHA256
```


### Server Side Attributes

  - *port* - Server port number. Mandatory.

  - *sslProvider* - Specifies a Java service provider implementation for the TLS protocol.
    Supported values are `JDK` and `OPENSSL`. Note that `OPENSSL` is platform dependent
    (Linux/Mac/Windows) implementation and you must use an appropriate Styx build for your
    deployment target.

  - *keyCertChainFile* - An X.509 certificate chain file in PEM format.

  - *keyFile* - A PKCS#8 private key file in PEM format.

  - *sessionCacheSize* - Sets the cache size for the storage of SSL session objects.
    Set `0` to use the default value. This is an optional attribute. When absent, it
    reverts to a default value.

  - *sessionTimeoutMillis* - Sets the timeout for the cached SSL session
    objects, in milliseconds. Set `0` to use the default value.
    This is an optional attribute. When absent, it reverts to a default value.

  - *cipherSuites* - A list of enabled cipher suites, in order
    of preference. Leave absent to use the SSL provider defaults.
    Note that the cipher suite names are specific to the SSL provider.

  - *protocols* - A list of TLS protocol versions to use.
    Use this attribute to enforce a more secure version like `TLSv1.2`.
    In this case Styx will only accept connections secured with `TLSv1.2`,
    and will reject any connection attempts with older versions.
    When absent, enables all default protocols depending on the `sslProvider`.
    Possible protocol names are: `TLS`, `TLSv1`, `TLSv1.1`, and `TLSv1.2`.

The accepted protocol names and cipher suite names for `JDK` provider
are listed in [Oracle Java Cryptography Architecture documentation](https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html).


## Backend Services Configuration

HTTPS protocol is enabled by inserting a *tlsSettings* attribute in the
backend services configuration, as follows:

```yaml
- id: "mySecureBackend"
  path: "/"
  ...
  tlsSettings:
    trustAllCerts:       false
    sslProvider:         OPENSSL     # Also supports JDK
    addlCerts:
      - alias:           "my certificate"
        path:            "/path/to/mycert"
      - alias:           "alt certificate"
        path:            "/path/to/altcert"
    trustStorePath:      "/path/to/truststore"
    trustStorePassword:  "your_password"
    protocols:
      - TLSv1.2
    cipherSuites:
      - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      - ECDHE-RSA-AES256-GCM-SHA384
      - ECDHE-RSA-AES128-GCM-SHA256
  ...
```

TLS must be enabled separately for each backend service.

Only one protocol, HTTP, or HTTPS, can be enabled at a time for a back-end.
If your backend application exposes both HTTP and HTTPS endpoints, they must
be specified as separate backends.


### Backend Service Attributes:


  - *trustAllCerts* -  When `trustAllCerts` is false, origin servers need to provide a 
  trusted certificate or the connection will be rejected. If true, all the server certificates
  will be allowed.

  - *sslProvider* - Specifies a Java service provider implementation for the TLS protocol.
    Supported values are `JDK` and `OPENSSL`. Note that `OPENSSL` is a platform dependent
    (Linux/Mac/Windows) implementation and you must use an appropriate Styx build for your
    deployment target.

  - *addlCerts* - A list of additional certificates that can be specified
    in addition to the certificates available in the Trust Store. When Styx starts
    up, it creates an in-memory keystore and loads these certificates there.
    Each specified certificate entry must have these two attributes:

      - *alias* - A symbolic name for the certificate. Used as an alias in the keystore.

      - *path*  - A path to the certificate file.

  - *trustStorePath* - Styx can load the list of trusted certificates from a Java truststore. This attribute specifies a path to the truststore.

  - *trustStorePassword* - A password for the keystore file specified in
    *trustStorePath* attribute.

  - *cipherSuites* - A list of enabled cipher suites, in order
    of preference. Omit this property to use the SSL provider defaults.
    Note that the cipher suite names are specific to a SSL provider.

  - *protocols* - A list of TLS protocol versions to use.
    Use this attribute to enforce a more secure version like `TLSv1.2`.
    When absent, enables all default protocols for the `sslProvider`.
    Possible protocol names are: `TLS`, `TLSv1`, `TLSv1.1`, and `TLSv1.2`.

Attributes that accept lists can be defined with the following format: ['ITEM1', 'ITEM2']

## Troubleshooting TLS Configuration

### Failing SSL Handshake attempts on Styx server

Unsuccessful SSL handshake attempts from remote clients to Styx server are logged by *HttpErrorStatusCauseLogger*
at *ERROR* level. This error message contains a stack trace with the keyword *SSLHandshakeException*. 
The content of this message will vary depending on the configured SSL provider and the exact cause. 
To discover failed handshake attempts, look for *SSLHandshakeException* in the logs.

Additionally, the`styx.exception.io_netty_handler_codec_DecoderException` counter is also incremented when a SSL
 handshake error occurs.

An example stack trace looks like:

```
ERROR 2018-01-22 08:59:49 [c.h.s.a.m.HttpErrorStatusCauseLogger] [Proxy-Worker-0-Thread] - Failure status="500 Internal Server Error"
[exceptionClass=io.netty.handler.codec.ByteToMessageDecoder, exceptionMethod=callDecode, exceptionID=4c1cc82c]
io.netty.handler.codec.DecoderException: javax.net.ssl.SSLHandshakeException: error:1407609C:SSL routines:SSL23_GET_CLIENT_HELLO:http request
	at io.netty.handler.codec.ByteToMessageDecoder.callDecode(ByteToMessageDecoder.java:459) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:265) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:362) [netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:348) [netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:340) [netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1359) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:362) [netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:348) [netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:935) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:134) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:645) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:580) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:497) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:459) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.util.concurrent.SingleThreadEventExecutor$5.run(SingleThreadEventExecutor.java:858) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at java.lang.Thread.run(Thread.java:748) ~[na:1.8.0_131]
Caused by: javax.net.ssl.SSLHandshakeException: error:1407609C:SSL routines:SSL23_GET_CLIENT_HELLO:http request
	at io.netty.handler.ssl.ReferenceCountedOpenSslEngine.shutdownWithError(ReferenceCountedOpenSslEngine.java:869) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.ssl.ReferenceCountedOpenSslEngine.sslReadErrorResult(ReferenceCountedOpenSslEngine.java:1108) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.ssl.ReferenceCountedOpenSslEngine.unwrap(ReferenceCountedOpenSslEngine.java:1064) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.ssl.ReferenceCountedOpenSslEngine.unwrap(ReferenceCountedOpenSslEngine.java:1127) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.ssl.ReferenceCountedOpenSslEngine.unwrap(ReferenceCountedOpenSslEngine.java:1170) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.ssl.SslHandler$SslEngineType$1.unwrap(SslHandler.java:215) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.ssl.SslHandler.unwrap(SslHandler.java:1215) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.ssl.SslHandler.decodeNonJdkCompatible(SslHandler.java:1139) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.ssl.SslHandler.decode(SslHandler.java:1164) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.codec.ByteToMessageDecoder.decodeRemovalReentryProtection(ByteToMessageDecoder.java:489) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	at io.netty.handler.codec.ByteToMessageDecoder.callDecode(ByteToMessageDecoder.java:428) ~[netty-all-4.1.15.Final.jar:4.1.15.Final]
	... 15 common frames omitted
```
