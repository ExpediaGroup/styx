# STYX
## Quick Start Guide

1. TODO where is ZIP file?
2. Unzip into a location of your choosing, which we will call `<YOUR_INSTALL_DIR>`.
3. run `<YOUR_INSTALL_DIR>/bin/startup`
4. look for a log message similar to: 
    * `INFO  2017-10-18 15:42:10 [c.h.s.StyxServer] [build=release.label_IS_UNDEFINED] [main] - Started styx server in 65ms`
    
    if you see this message, Styx has started up!
5. You can check the admin interface by visiting [localhost:9000](http://localhost:9000/)
6. Once you are happy with this, you may want to try proxying, for which you need an origins configuration file.
7. Shut down Styx (you can use Ctrl+C from the terminal).
8. Create an origins file (see [origins-configuration-documentation.yml](../distribution/conf/origins/origins-configuration-documentation.yml) for an example) at a location of your choosing, which we will call <PATH_TO_ORIGINS_FILE> 
9. Modify your Styx config file by adding the following line:
    * `originsFile: <PATH_TO_ORIGINS_FILE>`
10. run `<YOUR_INSTALL_DIR>/bin/startup`
11. You should now be able to use the proxy by visiting [localhost:8080](http://localhost:8080/)

**Note:** the default Styx config file is located at `<YOUR_INSTALL_DIR>/conf/default.yml`

## Troubleshooting

**Problem**

Styx fail to start with the message: 

`Failed to load any of the given libraries: [netty_tcnative_osx_x86_64, netty_tcnative]`

**Explanation**

This may be caused by your system not supporting `OPENSSL`.

See the following section in `<YOUR_INSTALL_DIR>/conf/default.yml`:

        proxy:
          connectors:
            http:
              port: 8080
            https:
              port: 8443
              sslProvider: OPENSSL # Also supports JDK
              certificateFile: ${STYX_HOME:classpath:}/conf/tls/testCredentials.crt
              certificateKeyFile: ${STYX_HOME:classpath:}/conf/tls/testCredentials.key
              sessionTimeoutMillis: 300000
              sessionCacheSize: 20000

**Solution**

This can be solved in one of various ways.

1. If you do not want SSL support, you can remove the `https` connector from `<YOUR_INSTALL_DIR>/conf/default.yml`. 

2. If you do want SSL support, modify the file `<YOUR_INSTALL_DIR>/conf/default.yml` so that the sslProvider is set to `JDK`. Your `default.yml` will then look like this:

        proxy:
          connectors:
            http:
              port: 8080
            https:
              port: 8443
              sslProvider: JDK
              certificateFile: ${STYX_HOME:classpath:}/conf/tls/testCredentials.crt
              certificateKeyFile: ${STYX_HOME:classpath:}/conf/tls/testCredentials.key
              sessionTimeoutMillis: 300000
              sessionCacheSize: 20000
          
3. You can also supply a different configuration file by setting the environment variable `STYX_CONFIG` to the location of a different file. 




