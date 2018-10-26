
<a name="styx-1.0.0.beta1"></a>
## [styx-1.0.0.beta1](https://github.com/HotelsDotCom/styx/compare/styx-0.7.10...styx-1.0.0.beta1) (2018-10-26)

### Pull Requests

* Update example plugin to latest api ([#316](https://github.com/HotelsDotCom/styx/issues/316))
* Rename streaming messages ([#314](https://github.com/HotelsDotCom/styx/issues/314))
* Styx Eventual class for asynchronous events. ([#309](https://github.com/HotelsDotCom/styx/issues/309))
* Correct time output ([#313](https://github.com/HotelsDotCom/styx/issues/313))
* A ByteStream abstraction to represent streaming content. ([#298](https://github.com/HotelsDotCom/styx/issues/298))
* Fix incorrect round-robin strategy behaviour when there are no available hosts. ([#307](https://github.com/HotelsDotCom/styx/issues/307))
* Remove unused cancel() method from HttpTransaction and Transport classes. ([#305](https://github.com/HotelsDotCom/styx/issues/305))
* Fix incorrect changelog for 0.7.10 ([#303](https://github.com/HotelsDotCom/styx/issues/303))
* HttpResponseWriter: Log number of bytes written to socket ([#302](https://github.com/HotelsDotCom/styx/issues/302))
* Update CHANGELOG.md ([#301](https://github.com/HotelsDotCom/styx/issues/301))
* Lazily create expensive Optional default values. ([#295](https://github.com/HotelsDotCom/styx/issues/295))
* Use Optional.orElseGet to prevent constructing the Exception on each request ([#292](https://github.com/HotelsDotCom/styx/issues/292))
* Fix origins reload thread leak ([#290](https://github.com/HotelsDotCom/styx/issues/290))
* Improve styx client ([#288](https://github.com/HotelsDotCom/styx/issues/288))
* Update origin health-check metric name (fixes [#269](https://github.com/HotelsDotCom/styx/issues/269)) ([#289](https://github.com/HotelsDotCom/styx/issues/289))
* User guide: Fix a typo. ([#286](https://github.com/HotelsDotCom/styx/issues/286))
* Fix PluginErrorHandlingSpec ([#287](https://github.com/HotelsDotCom/styx/issues/287))
* Move Announcer from API to Styx-Common module ([#285](https://github.com/HotelsDotCom/styx/issues/285))
* End-to-end tests: fix leak threads. ([#283](https://github.com/HotelsDotCom/styx/issues/283))
* Remove client address ([#280](https://github.com/HotelsDotCom/styx/issues/280))
* Streamline Styx HTTP Client interfaces ([#282](https://github.com/HotelsDotCom/styx/issues/282))
* Fix DoubleSubscriptionPluginSpec for Styx 1.0 ([#281](https://github.com/HotelsDotCom/styx/issues/281))
* Log SSL handshake failures ([#278](https://github.com/HotelsDotCom/styx/issues/278))
* Update configure-origins.md ([#279](https://github.com/HotelsDotCom/styx/issues/279))
* Remove `isSecure` flag from the HTTP request classes. ([#277](https://github.com/HotelsDotCom/styx/issues/277))
* Add HttpInterceptor.Context to HttpRouter.route() API. ([#276](https://github.com/HotelsDotCom/styx/issues/276))
* Improve Javadocs for styx-api module ([#271](https://github.com/HotelsDotCom/styx/issues/271))
* Tidy up styx-api module for 1.0 release ([#272](https://github.com/HotelsDotCom/styx/issues/272))
* Fix compilation error in ExamplePluginTest.java. ([#273](https://github.com/HotelsDotCom/styx/issues/273))
* Injectable metricsRegistry for StyxServerComponents.Builder ([#263](https://github.com/HotelsDotCom/styx/issues/263))
* Tidy up styx-api module. ([#267](https://github.com/HotelsDotCom/styx/issues/267))
* Issue [#264](https://github.com/HotelsDotCom/styx/issues/264): Remove unnecessary calls to `freePort`. ([#265](https://github.com/HotelsDotCom/styx/issues/265))
* Remove HostAndPort from public API ([#247](https://github.com/HotelsDotCom/styx/issues/247))
* Removed unused method ([#241](https://github.com/HotelsDotCom/styx/issues/241))
* Changelog for releases up to styx 0.7.9 ([#260](https://github.com/HotelsDotCom/styx/issues/260))
* Add changelog for release 0.7.7 ([#238](https://github.com/HotelsDotCom/styx/issues/238)) ([#259](https://github.com/HotelsDotCom/styx/issues/259))
* Fixes [#222](https://github.com/HotelsDotCom/styx/issues/222): A memory leak in DashboardData. ([#224](https://github.com/HotelsDotCom/styx/issues/224)) ([#258](https://github.com/HotelsDotCom/styx/issues/258))
* Fix intermittently failing ChunkedDownloadSpec ([#256](https://github.com/HotelsDotCom/styx/issues/256))
* Update to Guava 18.0 ([#254](https://github.com/HotelsDotCom/styx/issues/254))
* README.md: Add short release note about Styx 1.0 API. ([#248](https://github.com/HotelsDotCom/styx/issues/248))
* Add 'filter' query parameter to metrics endpoint ([#221](https://github.com/HotelsDotCom/styx/issues/221)) ([#252](https://github.com/HotelsDotCom/styx/issues/252))
* Fix a small typo ([#239](https://github.com/HotelsDotCom/styx/issues/239))
* fix documentation, instructions for release download ([#233](https://github.com/HotelsDotCom/styx/issues/233))
* Add Styx logo in readme file ([#195](https://github.com/HotelsDotCom/styx/issues/195)) ([#251](https://github.com/HotelsDotCom/styx/issues/251))
* Fix styx 1.0 related documentation issues. ([#245](https://github.com/HotelsDotCom/styx/issues/245))
* Replaced all checkNotnull with requireNonNull ([#244](https://github.com/HotelsDotCom/styx/issues/244))
* Move metrics registry from styx-api module ([#242](https://github.com/HotelsDotCom/styx/issues/242))
* Simplify package structure for 1.0 styx-api module ([#240](https://github.com/HotelsDotCom/styx/issues/240))
* Remove dead code: applicationConfigurationMatcher ([#237](https://github.com/HotelsDotCom/styx/issues/237))
* Issue 183 remove admin endpoints ([#236](https://github.com/HotelsDotCom/styx/issues/236))
* Remove MediaType from public methods in API module ([#232](https://github.com/HotelsDotCom/styx/issues/232))
* Remove leaking Netty definition from HttpMessageSupport class. ([#231](https://github.com/HotelsDotCom/styx/issues/231))
* Tidy up cookies API ([#230](https://github.com/HotelsDotCom/styx/issues/230))
* Move api.io package to styx-common module. ([#229](https://github.com/HotelsDotCom/styx/issues/229))
* Move utility classes and HTTP handlers from styx-api to styx-common module. ([#226](https://github.com/HotelsDotCom/styx/issues/226))
* Remove SystemSettings & related classes from styx-api module. ([#225](https://github.com/HotelsDotCom/styx/issues/225))
* Migrate ConnectionPool related API classes to Styx Client module ([#223](https://github.com/HotelsDotCom/styx/issues/223))
* Added support for Kotlin ([#220](https://github.com/HotelsDotCom/styx/issues/220))
* Cookies 1.0 api ([#217](https://github.com/HotelsDotCom/styx/issues/217))
* Fix StyxObservable type parameters ([#219](https://github.com/HotelsDotCom/styx/issues/219))
* Add admin endpoint for querying specific metrics by name ([#206](https://github.com/HotelsDotCom/styx/issues/206)) ([#218](https://github.com/HotelsDotCom/styx/issues/218))
* Add new SENDING_RESPONSE_CLIENT_CLOSED state into HttpPipelineHandler FSM ([#214](https://github.com/HotelsDotCom/styx/issues/214)) ([#216](https://github.com/HotelsDotCom/styx/issues/216))
* Allow custom plugin loader injection ([#213](https://github.com/HotelsDotCom/styx/issues/213)) ([#215](https://github.com/HotelsDotCom/styx/issues/215))
* Use Connection.Settings in SimpleHttpClient ([#212](https://github.com/HotelsDotCom/styx/issues/212))
* Rename com.hotels.styx.api.netty package ([#211](https://github.com/HotelsDotCom/styx/issues/211))
* Fix issue [#199](https://github.com/HotelsDotCom/styx/issues/199): origins.ID.requests.cancelled metric. ([#209](https://github.com/HotelsDotCom/styx/issues/209)) ([#210](https://github.com/HotelsDotCom/styx/issues/210))
* New 1.0 API: Tidy up Address outstanding issues. ([#204](https://github.com/HotelsDotCom/styx/issues/204))
* Remove HttpRequest.Builder.body(String) method ([#205](https://github.com/HotelsDotCom/styx/issues/205))
* Tests for styx core observable ([#198](https://github.com/HotelsDotCom/styx/issues/198))
* Add config store ([#193](https://github.com/HotelsDotCom/styx/issues/193))
* Fix failing health checks to HTTPS/TLS origins. ([#191](https://github.com/HotelsDotCom/styx/issues/191))
* Remove unnecessary WARN log when SimpleHttpClient fails to connect to a remote peer. ([#190](https://github.com/HotelsDotCom/styx/issues/190))
* Cookie names should be treatead case-sensitively  ([#186](https://github.com/HotelsDotCom/styx/issues/186)) ([#189](https://github.com/HotelsDotCom/styx/issues/189))
* SimpleHttpClient: Use default HTTP/HTTPS ports ([#188](https://github.com/HotelsDotCom/styx/issues/188))
* Allow Styx Test API users to configure server ports. ([#187](https://github.com/HotelsDotCom/styx/issues/187))
* Allow plugin factories to be configured for styx test API server. ([#184](https://github.com/HotelsDotCom/styx/issues/184))
* Remove HttpClient interface from Styx API  ([#179](https://github.com/HotelsDotCom/styx/issues/179))
* Refactor Styx Client API consumers ([#178](https://github.com/HotelsDotCom/styx/issues/178))
* Expose HttpInterceptor.Context implementation via styx-test-api. ([#176](https://github.com/HotelsDotCom/styx/issues/176))
* Dev docs: update plugin interceptor examples. ([#173](https://github.com/HotelsDotCom/styx/issues/173))
* Use HttpServerCodec in Styx server Netty pipeline ([#175](https://github.com/HotelsDotCom/styx/issues/175))
* Updated example plugin to 1.0 API ([#172](https://github.com/HotelsDotCom/styx/issues/172))
* Add Styx API Overview page to the developer docs. ([#171](https://github.com/HotelsDotCom/styx/issues/171))
* Remove unused HttpMessage, HttpMessageBody, and HttpMessageBuilder classes. ([#170](https://github.com/HotelsDotCom/styx/issues/170))
* Rename message converters between full and streaming variants ([#169](https://github.com/HotelsDotCom/styx/issues/169))
* Add HttpInterceptorContext to styx-test-api. ([#168](https://github.com/HotelsDotCom/styx/issues/168))
* New interceptor and routing API for styx - Initial Commit - ([#166](https://github.com/HotelsDotCom/styx/issues/166))


<a name="styx-0.7.10"></a>
## [styx-0.7.10](https://github.com/HotelsDotCom/styx/compare/styx-0.7.9...styx-0.7.10) (2018-10-03)

### Pull Requests

* Fix intermittently failing ChunkedDownloadSpec ([#256](https://github.com/HotelsDotCom/styx/issues/256)) ([#297](https://github.com/HotelsDotCom/styx/issues/297))
* Backport [#292](https://github.com/HotelsDotCom/styx/issues/292) to 0.7 ([#293](https://github.com/HotelsDotCom/styx/issues/293))


<a name="styx-0.7.9"></a>
## [styx-0.7.9](https://github.com/HotelsDotCom/styx/compare/styx-0.7.8...styx-0.7.9) (2018-08-24)

### Pull Requests

* Add changelog for release 0.7.7 ([#238](https://github.com/HotelsDotCom/styx/issues/238))
* Fixed a small typo ([#239](https://github.com/HotelsDotCom/styx/issues/239))
* fix documentation, instructions for release download ([#233](https://github.com/HotelsDotCom/styx/issues/233))
* Add 'filter' query parameter to metrics endpoint ([#221](https://github.com/HotelsDotCom/styx/issues/221))
* Update Google Guava to 18.0. ([#228](https://github.com/HotelsDotCom/styx/issues/228))
* Fixes [#222](https://github.com/HotelsDotCom/styx/issues/222): A memory leak in DashboardData. ([#224](https://github.com/HotelsDotCom/styx/issues/224))


<a name="styx-0.7.8"></a>
## [styx-0.7.8](https://github.com/HotelsDotCom/styx/compare/styx-0.7.7...styx-0.7.8) (2018-08-23)

### Pull Requests

* Add changelog for release 0.7.7 ([#238](https://github.com/HotelsDotCom/styx/issues/238))
* Add new SENDING_RESPONSE_CLIENT_CLOSED state into HttpPipelineHandler FSM ([#214](https://github.com/HotelsDotCom/styx/issues/214))
* Allow custom plugin loader injection ([#213](https://github.com/HotelsDotCom/styx/issues/213))
* Fix issue [#199](https://github.com/HotelsDotCom/styx/issues/199): origins.ID.requests.cancelled metric. ([#209](https://github.com/HotelsDotCom/styx/issues/209))
* Add admin endpoint for querying specific metrics by name ([#206](https://github.com/HotelsDotCom/styx/issues/206))
* Add Styx logo in readme file ([#195](https://github.com/HotelsDotCom/styx/issues/195))
* Cookie names should be treatead case-sensitively  ([#186](https://github.com/HotelsDotCom/styx/issues/186))
* CHANGELOG for 0.7.6 release ([#165](https://github.com/HotelsDotCom/styx/issues/165))
* Implement health check hide if disabled ([#158](https://github.com/HotelsDotCom/styx/issues/158))
* Deletes a file that does not belong to the project. ([#164](https://github.com/HotelsDotCom/styx/issues/164))
* Refactor YamlReader ([#163](https://github.com/HotelsDotCom/styx/issues/163))
* Improve log messages when origin reload fails. ([#159](https://github.com/HotelsDotCom/styx/issues/159))
* Remove fasterxml annotations from Styx API [#132](https://github.com/HotelsDotCom/styx/issues/132) ([#147](https://github.com/HotelsDotCom/styx/issues/147))


<a name="styx-0.7.7"></a>
## [styx-0.7.7](https://github.com/HotelsDotCom/styx/compare/styx-0.7.6...styx-0.7.7) (2018-05-17)

### Pull Requests

* Fixes [#152](https://github.com/HotelsDotCom/styx/issues/152): GraphiteReporter retries connection to the server too often. ([#155](https://github.com/HotelsDotCom/styx/issues/155))
* A Mock DNS Service for testing purposes ([#156](https://github.com/HotelsDotCom/styx/issues/156))
* Fixes issue [#105](https://github.com/HotelsDotCom/styx/issues/105): Remove unused `socketTimeoutMillis` option. ([#150](https://github.com/HotelsDotCom/styx/issues/150))
* Multiple jar plugin ([#138](https://github.com/HotelsDotCom/styx/issues/138))
* Prevent Graphite IP address from being cached. ([#153](https://github.com/HotelsDotCom/styx/issues/153))
* Implements [#143](https://github.com/HotelsDotCom/styx/issues/143): Add support for Map types to schema ([#148](https://github.com/HotelsDotCom/styx/issues/148))
* Fix for [#142](https://github.com/HotelsDotCom/styx/issues/142): Improve type detection for config validator. ([#146](https://github.com/HotelsDotCom/styx/issues/146))
* Move UniqueIdSupplier(s) from styx-api module into styx-server module. ([#149](https://github.com/HotelsDotCom/styx/issues/149))
* Improve warning message regarding idle persistent connections. ([#145](https://github.com/HotelsDotCom/styx/issues/145))
* Schema based server config validator ([#137](https://github.com/HotelsDotCom/styx/issues/137))
* Prevent duplicate backend service paths ([#140](https://github.com/HotelsDotCom/styx/issues/140))
* Document Styx 4xx response codes ([#135](https://github.com/HotelsDotCom/styx/issues/135))
* Additional argument for gpg plugin that allows avoid password prompt for gpg 2.1 ([#133](https://github.com/HotelsDotCom/styx/issues/133))
* Call System.exit() when StyxServer.createServer() fails ([#136](https://github.com/HotelsDotCom/styx/issues/136))
* Use a non-Corba based uuid generator ([#123](https://github.com/HotelsDotCom/styx/issues/123))
* Fix license headers ([#131](https://github.com/HotelsDotCom/styx/issues/131))
* Fixes [#118](https://github.com/HotelsDotCom/styx/issues/118) Improve FlowControllingHttpContentProducer state machine. ([#120](https://github.com/HotelsDotCom/styx/issues/120))
* Fix Styx startup script: return an error when a command line argument is unknown. ([#129](https://github.com/HotelsDotCom/styx/issues/129))
* Fix broken startup ([#127](https://github.com/HotelsDotCom/styx/issues/127))
* Styx server refactoring ([#116](https://github.com/HotelsDotCom/styx/issues/116))
* Open up SPI interface for Backend Service providers ([#115](https://github.com/HotelsDotCom/styx/issues/115))
* Plugin documentation and example pom.xml for plugins reviewed. ([#121](https://github.com/HotelsDotCom/styx/issues/121))
* Additional pom properties for custom gpg2 settings for artifacts signing ([#117](https://github.com/HotelsDotCom/styx/issues/117))
* Update origins-configuration-documentation.yml ([#112](https://github.com/HotelsDotCom/styx/issues/112))
* Add default origins file. ([#111](https://github.com/HotelsDotCom/styx/issues/111))
* Decouple configuration parsing ([#109](https://github.com/HotelsDotCom/styx/issues/109))
* Styx tests should pass in windows ([#101](https://github.com/HotelsDotCom/styx/issues/101))
* Refactor load balancer APIs ([#96](https://github.com/HotelsDotCom/styx/issues/96))


<a name="styx-0.7.6"></a>
## [styx-0.7.6](https://github.com/HotelsDotCom/styx/compare/styx-0.7.5...styx-0.7.6) (2018-04-12)

### Pull Requests

* Document origin configuration and improve user guide. ([#106](https://github.com/HotelsDotCom/styx/issues/106))
* Add response codes diagram and page ([#104](https://github.com/HotelsDotCom/styx/issues/104))
* Origins file monitor ([#97](https://github.com/HotelsDotCom/styx/issues/97))


<a name="styx-0.7.5"></a>
## [styx-0.7.5](https://github.com/HotelsDotCom/styx/compare/styx-0.7.4...styx-0.7.5) (2018-03-13)

### Pull Requests

* Fixed resource leak in FileBackedRegistry: the FileInputStream wasn't being properly closed. ([#99](https://github.com/HotelsDotCom/styx/issues/99))
* fix for issue [#90](https://github.com/HotelsDotCom/styx/issues/90): Race condition in e2e test: ExpiringConnectionSpec ([#95](https://github.com/HotelsDotCom/styx/issues/95))
* Upgrading to newest metrics version ([#93](https://github.com/HotelsDotCom/styx/issues/93))
* Refactor Styx HTTP Client ([#88](https://github.com/HotelsDotCom/styx/issues/88))
* Fix intermittently failing test e2e test: ExpiringConnectionSpec ([#89](https://github.com/HotelsDotCom/styx/issues/89))
* Fix issue [#86](https://github.com/HotelsDotCom/styx/issues/86): Reload of backend service id change. ([#87](https://github.com/HotelsDotCom/styx/issues/87))
* Add a config option to expiry pooled connections ([#84](https://github.com/HotelsDotCom/styx/issues/84))
* Fix an unsuccessful attempt to cast MemoryBackedRegistry into ([#82](https://github.com/HotelsDotCom/styx/issues/82))
* Serialise `cipherSuites` attribute of TlsSettings. ([#83](https://github.com/HotelsDotCom/styx/issues/83))
* Refactor builders: NettyConnectionFactory ([#81](https://github.com/HotelsDotCom/styx/issues/81))
* Separate StyxService from AbstractRegistry. ([#80](https://github.com/HotelsDotCom/styx/issues/80))
* Refactor origin inventory builder ([#78](https://github.com/HotelsDotCom/styx/issues/78))
* Added "connectors" node to example configuration ([#79](https://github.com/HotelsDotCom/styx/issues/79))
* Fix for NPE in case of retry. ([#77](https://github.com/HotelsDotCom/styx/issues/77))
* Add cipher suite controls to backend services configuration. ([#36](https://github.com/HotelsDotCom/styx/issues/36))
* OriginStatsFactory instantiation moved upper in hierarchy. ([#71](https://github.com/HotelsDotCom/styx/issues/71))
* Dynamically allocate port numbers in RetryHandlingSpec and StickySessionSpec. ([#67](https://github.com/HotelsDotCom/styx/issues/67))
* Stop NullPointerExceptions in HttpRequestMessageLogger ([#43](https://github.com/HotelsDotCom/styx/issues/43))
* Refactor OriginsInventory. ([#62](https://github.com/HotelsDotCom/styx/issues/62))
* Moving underlying request send logic from client implementstions to connections. ([#58](https://github.com/HotelsDotCom/styx/issues/58))
* Refactor styx routing objects ([#53](https://github.com/HotelsDotCom/styx/issues/53))
* Decouple origin inventory from StyxHttpClient ([#49](https://github.com/HotelsDotCom/styx/issues/49))
* Maven version shield add ([#63](https://github.com/HotelsDotCom/styx/issues/63))
* Dynamically allocate port in some e2e tests. ([#65](https://github.com/HotelsDotCom/styx/issues/65))
* Removed un-used github pages dependencies Gemfile ([#64](https://github.com/HotelsDotCom/styx/issues/64))
* Add streaming classes ([#44](https://github.com/HotelsDotCom/styx/issues/44))
* Decouple StateMachine mechanics from the QueueDrainingEventProcessor. ([#54](https://github.com/HotelsDotCom/styx/issues/54))
* Remove bodyAs(Function) method from FullHttpMessage API ([#52](https://github.com/HotelsDotCom/styx/issues/52))
* Remove Guava Service from Registry API. ([#45](https://github.com/HotelsDotCom/styx/issues/45))
* Extract OriginsInventory outside of StyxHttpClient ([#47](https://github.com/HotelsDotCom/styx/issues/47))


<a name="styx-0.7.4"></a>
## [styx-0.7.4](https://github.com/HotelsDotCom/styx/compare/styx-0.7.3...styx-0.7.4) (2018-01-11)

### Pull Requests

* Tidy up tests: Use new FullHttpRequest/Response message API ([#42](https://github.com/HotelsDotCom/styx/issues/42))
* Add FullHttpRequest and FullHttpResponse classes to Styx API ([#40](https://github.com/HotelsDotCom/styx/issues/40))
* Keep percent-encoding in downstream request URLs when proxying to backend services ([#15](https://github.com/HotelsDotCom/styx/issues/15))
* Add initial changelog file and make rule for changelog generation ([#41](https://github.com/HotelsDotCom/styx/issues/41))
* Pretty print admin interface pages by default. ([#35](https://github.com/HotelsDotCom/styx/issues/35))


<a name="styx-0.7.3"></a>
## [styx-0.7.3](https://github.com/HotelsDotCom/styx/compare/styx-0.7.1...styx-0.7.3) (2017-11-27)

### Pull Requests

* Maven central publishing profile added ([#37](https://github.com/HotelsDotCom/styx/issues/37))
* Change file headers to expected format ([#34](https://github.com/HotelsDotCom/styx/issues/34))
* Issue28: Specify TLS protocol version for backend services ([#33](https://github.com/HotelsDotCom/styx/issues/33))
* Implement Wiremock API on top of Styx/Netty HTTP servers. ([#29](https://github.com/HotelsDotCom/styx/issues/29))
* Create ISSUE_TEMPLATE.md ([#31](https://github.com/HotelsDotCom/styx/issues/31))
* Styx server tls protocol ([#27](https://github.com/HotelsDotCom/styx/issues/27))
* Add link to styx-user Google Groups forum. ([#30](https://github.com/HotelsDotCom/styx/issues/30))
* Map OutOfDirectMemoryError to 500 Internal Server Error status code. ([#26](https://github.com/HotelsDotCom/styx/issues/26))
* Enable "quality" profile in Travis configuration. ([#25](https://github.com/HotelsDotCom/styx/issues/25))
* Deprecate HTTP Context.get() in favour of an Optional returning getIfAvailable(). ([#16](https://github.com/HotelsDotCom/styx/issues/16))
* Change styx version format. ([#20](https://github.com/HotelsDotCom/styx/issues/20))
* Add styx load test script ([#18](https://github.com/HotelsDotCom/styx/issues/18))
* Make sure we don't end up with 0 threads ([#14](https://github.com/HotelsDotCom/styx/issues/14))
* fix documentation ([#13](https://github.com/HotelsDotCom/styx/issues/13))


<a name="styx-0.7.1"></a>
## styx-0.7.1 (2017-10-19)

### Pull Requests

* Release styx-0.7.1. ([#11](https://github.com/HotelsDotCom/styx/issues/11))
* Improve distribution zip file: ([#10](https://github.com/HotelsDotCom/styx/issues/10))
* Adding sections of pom requested by central repository ([#6](https://github.com/HotelsDotCom/styx/issues/6))
* Adding guides to get started with Styx quickly ([#7](https://github.com/HotelsDotCom/styx/issues/7))

