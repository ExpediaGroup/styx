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

  - *cipherSuites* - A list of enabled cipher suites, in order
    of preference. Leave absent to use the SSL provider defaults.
    Note that the cipher suite names are specific to a SSL provider.

  - *protocols* - A list of TLS protocol versions to use.
    Use this attribute to enforce a more secure version like `TLSv1.2`.
    In this case Styx will only accept connections secured with `TLSv1.2`,
    and will reject any connection attempts with older versions.
    When absent, enables all default protocols depending on the `sslProvider`.
    Possible protocol names are: `TLS`, `TLSv1`, `TLSv1.1`, and `TLSv1.2`.

The accepted protocol names and cipher suite names for `JDK` provider
are listed in [Oracle Java Cryptography Architecture documentation](https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html).


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
    protocols:
      - TLSv1.2
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

  - *protocols* - A list of TLS protocol versions to use.
    Use this attribute to enforce a more secure version like `TLSv1.2`.
    When absent, enables all default protocols depending on the `sslProvider`.
    Possible protocol names are: `TLS`, `TLSv1`, `TLSv1.1`, and `TLSv1.2`.
