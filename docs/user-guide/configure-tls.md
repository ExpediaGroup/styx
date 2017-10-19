# Configuring Transport Layer Security

Transport Layer Security (TLS) can be enabled on Styx network
interfaces. You can enable it on the proxy server port, and also for
each of the backend services, where necessary.

TLS can be enabled independently for each network interface. You can
enable it independently for proxy server port, and for each of the
back-end services. Styx will convert between the protocols where
necessary. For example, if you have insecure HTTP traffic coming in,
it will be converted to secure HTTPS if necessary.

## Server Side TLS Configuration

An example configuration block for enabling TLS security on Styx server
port is shown below. Note that you can enable both HTTP and
TLS-protected HTTPS ports simultaneously.

```yaml
    proxy:
      https:
        port:                 8443
        sslProvider:          OPENSSL # Also supports JDK
        certificateFile:      /conf/tls/testCredentials.crt
        certificateKeyFile:   /conf/tls/testCredentials.key
        sessionTimeoutMillis: 300000
        sessionCacheSize:     20000
```


### Server Side Attributes

  - *port* - Server port number. Mandatory.

  - *sslProvider* - Specifies a Java service provider implementation for the TLS protocol.
    Supported values are `JDK` and `OPENSSL`. Note that `OPENSSL` is a platform dependent
    (Linux/Mac/Windows) implementation and you must use an appropriate Styx build for your
    deployment target.

  - *keyCertChainFile* - An X.509 certificate chain file in PEM format.

  - *keyFile* - A PKCS#8 private key file in PEM format.

  - *sessionCacheSize* - Sets the cache size for storing SSL session objects.
    Set `0` to use the default value. This is an optional attribute. When absent, it
    reverts to a default value.

  - *sessionTimeoutMillis* - Sets the timeout for the cached SSL session
    objects, in seconds. Set `0` to use the default value.
    This is an optional attribute. When absent, it reverts to a default value.

  - *cipherSuites* - Specifies the cipher suites to enable, in the order
    of preference. Leave absent to use default cipher suites.


## Backend Applications Configuration

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
  ...
```

TLS must be enabled separately for each backend service.

Only one protocol, HTTP, or HTTPS, can be enabled at a time for a back-end.
If your backend application exposes both HTTP and HTTPS endpoints, they must
be specified as separate backends.


### Backend Application Attributes:

  - trustAllCerts - Whether (true) or not (false) to authenticate server side.

  - *sslProvider* - Specifies a Java service provider implementation for the TLS protocol.
    Supported values are `JDK` and `OPENSSL`. Note that `OPENSSL` is a platform dependent
    (Linux/Mac/Windows) implementation and you must use an appropriate Styx build for your
    deployment target.

  - *addlCerts* - A list of additional certificates that can be specified
    in addition to the certificates available in Trust Store. When Styx starts
    up it creates an in-memory keystore and loads these certificates there.
    Each specified certificate entry must have these two attributes:

      - *alias* - A symbolic name for the certificate. Used as an alias for keystore.

      - *path*  - A path to the certificate file.

  - *trustStorePath* - Styx can load the relevant secure material from Java truststore.
    This attribute specifies a path to the keystore file containing relevant trust material.

  - *trustStorePassword* - A password for the keystore file specified in
    *trustStorePath* attribute.

