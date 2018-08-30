
<h1 align="left">
  <img width="420" alt="STYX" src="./docs/assets/styx-logo.svg">
</h1>

[![Build Status](https://travis-ci.org/HotelsDotCom/styx.svg?branch=master)](https://travis-ci.org/HotelsDotCom/styx)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![Version](https://img.shields.io/maven-central/v/com.hotels.styx/styx-server.svg)

Styx is a programmable reverse proxy for JVM (Java Virtual Machine). It can be used
as a stand-alone application, or as a platform to build custom networking applications. 
It is non-blocking, fully asynchronous and event driven therefore very scalable.

### Upcoming Release Note

[Styx 1.0](https://github.com/HotelsDotCom/styx/wiki/Styx-1.0-Release) will be publicly released soon. The `master` is now the bleeding edge for Styx 1.0.
Styx 0.7 code is available in the `styx-0.7` branch which is in maintenance mode (bug fixes only).
Follow the [Styx Wiki](https://github.com/HotelsDotCom/styx/wiki) for the latest developments.

### Features

Styx Proxies HTTP requests to a configurable set of *Backend Services* typically a cluster 
of web servers (multiple origins) or load balancers (e.g. AWS ELB).

Requests are subjected to an *interceptor chain* (conceptualy a HTTP filter chain), which can 
respond or modify and pass through the request to the backend services. The interceptor
chain can be easily extended by *Plugins* written in Java.

Styx additional capabilities:
- Load balancing
- Origin health checking
- Retry mechanisms
- Connection pooling
- Admin dashboard (inspired and based on [Hystrix Dashboard](https://github.com/Netflix/Hystrix/wiki/Dashboard))
- Performance and system metrics collection and reporting (Graphite compatible)

Most additional features can be *extended* via the Java Service Provider Interface (SPI) model.

The plugin and the SPI (Service Provider Interface) model enables developers
to build custom HTTP applications easily on top of Styx, which takes care
of common proxy server related functionality. Developers can then concentrate on
the value-add business logic.

### Use cases of Styx Plugins

* [Hotels.com](http://www.hotels.com) - Built authentication, UI rendering,
URL redirection and cookie cleaning plugins in front of backend services.

* [Expedia](http://www.expedia.com) - Built routing and bot detection capabilities on top of Styx. [More
info here](https://conferences.oreilly.com/software-architecture/sa-eu/public/schedule/detail/61826).

* [Homeaway](http://www.homeaway.com) - Built various plugins.


## Useful Information

### Quick Start

A [quick-start guide](https://github.com/HotelsDotCom/styx/wiki/Quick-Start-Guide) can be found on our [wiki](https://github.com/HotelsDotCom/styx/wiki). 

### User Manual

[User guide](./docs/user-guide.md) explains how to run and operate Styx as a standalone application.

### Developer Resources

Our [Developer guide](./docs/developer-guide.md) explains how to build applications on top of Styx.
It also explains how to build and run Styx.

If you want to help to contribute to Styx project, please check [Contributor guide](./CONTRIBUTING.md) to find out how to start.

### Got a Question?

Contact us via [styx-user](https://groups.google.com/forum/#!forum/styx-user) group.

### Links

* [Styx Wiki](https://github.com/HotelsDotCom/styx/wiki)
* [Binary Downloads](https://github.com/HotelsDotCom/styx/releases)
* [User guide](./docs/user-guide.md)
* [Developer guide](./docs/developer-guide.md)
* [styx-user](https://groups.google.com/forum/#!forum/styx-user) @ Google Groups


## Dependencies

* [Oracle JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - It can be built with 1.8.0_45. 
  Earlier maintenance releases may, but are not guaranteed to work.
* [Apache Maven v3](http://maven.apache.org)
* Makefile (Optional)- There are fairly many ways of running Styx tests with different Maven profiles. Therefore, the 
  shortcuts for most common usages are compiled into a separate (GNU) Makefile for developer's convenience. To 
  take advantage of these shortcuts, a GNU Make build tool must be installed.

## Notice
Not the Styx project that you were expecting to find?

Other open source projects called *Styx* on GitHub:
[Github Styx Projects](https://github.com/search?utf8=%E2%9C%93&q=styx&type=)

## License

This project is licensed under the Apache License v2.0 - see the [LICENSE.txt](LICENSE.txt) file for details.

Copyright 2013-2017 Expedia Inc.

Licencing terms for any derived work and dependant libraries are documented in `NOTICE` files.

