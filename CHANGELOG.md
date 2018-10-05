# Change Log

## [styx-0.7.10](https://github.com/HotelsDotCom/styx/tree/styx-0.7.10) (2018-10-03)
[Full Changelog](https://github.com/HotelsDotCom/styx/compare/styx-0.7.9...styx-0.7.10)

**Closed issues:**

- ChunkedDownloadSpec fails intermittently on 0.7 branch [\#296](https://github.com/HotelsDotCom/styx/issues/297)

**Merged pull requests:**

- Fix issue #296: Back port PR #256 to styx-0.7 branch [\#297](https://github.com/HotelsDotCom/styx/pull/297) ([mikkokar](https://github.com/mikkokar))
- Backport #292 to 0.7. [\#293](https://github.com/HotelsDotCom/styx/pull/293) ([taer](https://github.com/taer))

## [styx-0.7.9](https://github.com/HotelsDotCom/styx/tree/styx-0.7.9) (2018-08-24)
[Full Changelog](https://github.com/HotelsDotCom/styx/compare/styx-0.7.8...styx-0.7.9)

**Fixed bugs:**

- java.lang.NoClassDefFoundError caused by dependency conflict issue due to multiple versions of com.google.guava:guava:jar [\#227](https://github.com/HotelsDotCom/styx/issues/227)

**Closed issues:**

- Memory leak in styx V 0.14.0 [\#222](https://github.com/HotelsDotCom/styx/issues/222)
- Add a metrics endpoint that allows all metrics starting with a given string to be found. [\#208](https://github.com/HotelsDotCom/styx/issues/208)

**Merged pull requests:**

- Fix a small typo [\#239](https://github.com/HotelsDotCom/styx/pull/239) ([VivianLopes](https://github.com/VivianLopes))
- Add changelog for release 0.7.7 [\#238](https://github.com/HotelsDotCom/styx/pull/238) ([kvosper](https://github.com/kvosper))
- fix documentation, instructions for release download [\#233](https://github.com/HotelsDotCom/styx/pull/233) ([VivianLopes](https://github.com/VivianLopes))
- Update Google Guava to 18.0. [\#228](https://github.com/HotelsDotCom/styx/pull/228) ([mikkokar](https://github.com/mikkokar))
- Fixes \#222: A memory leak in DashboardData. [\#224](https://github.com/HotelsDotCom/styx/pull/224) ([mikkokar](https://github.com/mikkokar))
- Metrics endpoint enhancement [\#221](https://github.com/HotelsDotCom/styx/pull/221) ([kvosper](https://github.com/kvosper))

## [styx-0.7.8](https://github.com/HotelsDotCom/styx/tree/styx-0.7.8) (2018-07-13)
[Full Changelog](https://github.com/HotelsDotCom/styx/compare/styx-0.7.7...styx-0.7.8)

**Implemented enhancements:**

- Remove fasterxml objects from the BackendService etc. classes [\#132](https://github.com/HotelsDotCom/styx/issues/132)

**Fixed bugs:**

- Origin metrics for request cancellations count all requests [\#199](https://github.com/HotelsDotCom/styx/issues/199)

**Closed issues:**

- Cookie names should be treated case-sensitively [\#185](https://github.com/HotelsDotCom/styx/issues/185)
- Add admin endpoint for querying specific metrics [\#182](https://github.com/HotelsDotCom/styx/issues/182)

**Merged pull requests:**

- Add SENDING\_RESPONSE\_CLIENT\_CLOSED state into HttpPipelineHandler FSM [\#214](https://github.com/HotelsDotCom/styx/pull/214) ([mikkokar](https://github.com/mikkokar))
- Allow custom plugin loader injection [\#213](https://github.com/HotelsDotCom/styx/pull/213) ([fantayeneh](https://github.com/fantayeneh))
- Fix issue \#199: Request cancellation per origin metric. [\#209](https://github.com/HotelsDotCom/styx/pull/209) ([mikkokar](https://github.com/mikkokar))
- Add admin endpoint for querying specific metrics by name [\#206](https://github.com/HotelsDotCom/styx/pull/206) ([kvosper](https://github.com/kvosper))
- Styx logo in readme file centered [\#195](https://github.com/HotelsDotCom/styx/pull/195) ([ClaudioCorridore](https://github.com/ClaudioCorridore))
- Cookies should not be case insensitive  [\#186](https://github.com/HotelsDotCom/styx/pull/186) ([dvlato](https://github.com/dvlato))

## [styx-0.7.7](https://github.com/HotelsDotCom/styx/tree/styx-0.7.7) (2018-05-17)
[Full Changelog](https://github.com/HotelsDotCom/styx/compare/styx-0.7.6...styx-0.7.7)

**Implemented enhancements:**

- Admin page shows health check configuration even when it is absent [\#122](https://github.com/HotelsDotCom/styx/issues/122)

**Fixed bugs:**

- Graphite Reporter: retries too often [\#152](https://github.com/HotelsDotCom/styx/issues/152)
- Graphite Reporter: The graphite IP address gets cached. [\#151](https://github.com/HotelsDotCom/styx/issues/151)
- socketTimeoutMillis property in the connection pool configuration is not honoured.  [\#105](https://github.com/HotelsDotCom/styx/issues/105)
- Plugin cannot consist of multiple JAR files [\#56](https://github.com/HotelsDotCom/styx/issues/56)

**Closed issues:**

- Do not allow backend services to load or reload when path prefix is duplicated [\#139](https://github.com/HotelsDotCom/styx/issues/139)
- Move unique ID supplier class from styx-api into styx-server module [\#134](https://github.com/HotelsDotCom/styx/issues/134)
- Add 4xx server response codes to styx documentation [\#130](https://github.com/HotelsDotCom/styx/issues/130)

**Merged pull requests:**

- Deletes a file that does not belong to the project. [\#164](https://github.com/HotelsDotCom/styx/pull/164) ([mikkokar](https://github.com/mikkokar))
- Refactor YamlReader [\#163](https://github.com/HotelsDotCom/styx/pull/163) ([kvosper](https://github.com/kvosper))
- Improve log messages when origin reload fails. [\#159](https://github.com/HotelsDotCom/styx/pull/159) ([mikkokar](https://github.com/mikkokar))
- Dont show health check if absent [\#158](https://github.com/HotelsDotCom/styx/pull/158) ([kvosper](https://github.com/kvosper))
- Mock dns server [\#156](https://github.com/HotelsDotCom/styx/pull/156) ([mikkokar](https://github.com/mikkokar))
- Fixes \#152: GraphiteReporter retries connection to the server too often. [\#155](https://github.com/HotelsDotCom/styx/pull/155) ([dvlato](https://github.com/dvlato))
- Prevent Graphite IP address from being cached. [\#153](https://github.com/HotelsDotCom/styx/pull/153) ([mikkokar](https://github.com/mikkokar))
- Fixes issue \#105: Remove unused `socketTimeoutMillis` option. [\#150](https://github.com/HotelsDotCom/styx/pull/150) ([mikkokar](https://github.com/mikkokar))
- Move UniqueIdSupplier\(s\) from styx-api module into styx-server module. [\#149](https://github.com/HotelsDotCom/styx/pull/149) ([mikkokar](https://github.com/mikkokar))
- Implements \#143: Add map types to schema [\#148](https://github.com/HotelsDotCom/styx/pull/148) ([mikkokar](https://github.com/mikkokar))
- Remove fasterxml annotations from api with mixins\#132 [\#147](https://github.com/HotelsDotCom/styx/pull/147) ([alobodzki](https://github.com/alobodzki))
- Fix for \#142: Improve type detection for config validator. [\#146](https://github.com/HotelsDotCom/styx/pull/146) ([mikkokar](https://github.com/mikkokar))
- Improve warning message regarding idle persistent connections. [\#145](https://github.com/HotelsDotCom/styx/pull/145) ([mikkokar](https://github.com/mikkokar))
- Prevent duplicate backend service paths [\#140](https://github.com/HotelsDotCom/styx/pull/140) ([kvosper](https://github.com/kvosper))
- Multiple jar plugin [\#138](https://github.com/HotelsDotCom/styx/pull/138) ([kvosper](https://github.com/kvosper))
- Schema based server config validator [\#137](https://github.com/HotelsDotCom/styx/pull/137) ([mikkokar](https://github.com/mikkokar))
- Add extra documentation [\#135](https://github.com/HotelsDotCom/styx/pull/135) ([kvosper](https://github.com/kvosper))
- Additional argument for gpg plugin  [\#133](https://github.com/HotelsDotCom/styx/pull/133) ([alobodzki](https://github.com/alobodzki))

## [styx-0.7.6](https://github.com/HotelsDotCom/styx/tree/styx-0.7.6) (2018-04-12)
[Full Changelog](https://github.com/HotelsDotCom/styx/compare/styx-0.7.5...styx-0.7.6)

**Merged pull requests:**

- Origins file monitor [\#97](https://github.com/HotelsDotCom/styx/pull/97) ([mikkokar](https://github.com/mikkokar))
- Add response codes diagram and page [\#104](https://github.com/HotelsDotCom/styx/pull/104) ([kvosper](https://github.com/kvosper))
- Document origin configuration and improve user guide. [\#106](https://github.com/HotelsDotCom/styx/pull/106) ([dvlato](https://github.com/dvlato))
- Improve origins reload visibility [\#108](https://github.com/HotelsDotCom/styx/pull/108) ([mikkokar](https://github.com/mikkokar))


## [styx-0.7.5](https://github.com/HotelsDotCom/styx/tree/styx-0.7.5) (2018-03-13)
[Full Changelog](https://github.com/HotelsDotCom/styx/compare/styx-0.7.4...styx-0.7.5)

**Fixed bugs:**

- Race condition in e2e test: ExpiringConnectionSpec [\#90](https://github.com/HotelsDotCom/styx/issues/90)
- A change of backend service name \(id\) fails [\#86](https://github.com/HotelsDotCom/styx/issues/86)
- Serialise `cipherSuites` attribute of TlsSettings. [\#83](https://github.com/HotelsDotCom/styx/pull/83) ([mikkokar](https://github.com/mikkokar))
- Class cast exception from FileBackedBackendServiceRegistry.Factory  [\#82](https://github.com/HotelsDotCom/styx/pull/82) ([mikkokar](https://github.com/mikkokar))

**Merged pull requests:**

- Fixed resource leak in FileBackedRegistry: the fileinputstream wasn't being properly closed [\#99](https://github.com/HotelsDotCom/styx/pull/99) ([dvlato](https://github.com/dvlato))
- Upgrading to newest metrics version [\#93](https://github.com/HotelsDotCom/styx/pull/93) ([alobodzki](https://github.com/alobodzki))
- Remove 5xx check from BusyConnectionsStrategy [\#92](https://github.com/HotelsDotCom/styx/pull/92) ([kvosper](https://github.com/kvosper))
- Add a note to logging to explain why console output may disappear after LOGBackConfigurer takes effect [\#91](https://github.com/HotelsDotCom/styx/pull/91) ([kvosper](https://github.com/kvosper))
- Fix intermittently failing e2e test: ExpiringConnectionSpec.   [\#89](https://github.com/HotelsDotCom/styx/pull/89) ([mikkokar](https://github.com/mikkokar))
- Refactor Styx HTTP Client [\#88](https://github.com/HotelsDotCom/styx/pull/88) ([mikkokar](https://github.com/mikkokar))
- Fix issue \#86: Reload of backend service id change.  [\#87](https://github.com/HotelsDotCom/styx/pull/87) ([mikkokar](https://github.com/mikkokar))
- Connection expiration [\#84](https://github.com/HotelsDotCom/styx/pull/84) ([alobodzki](https://github.com/alobodzki))
- Refactor builders: NettyConnectionFactory [\#81](https://github.com/HotelsDotCom/styx/pull/81) ([mikkokar](https://github.com/mikkokar))
- Separate StyxService from AbstractRegistry. [\#80](https://github.com/HotelsDotCom/styx/pull/80) ([mikkokar](https://github.com/mikkokar))
- Added "connectors" node to example configuration [\#79](https://github.com/HotelsDotCom/styx/pull/79) ([noel-smith](https://github.com/noel-smith))
- Refactor origin inventory builder [\#78](https://github.com/HotelsDotCom/styx/pull/78) ([mikkokar](https://github.com/mikkokar))
- Fix for NPE in case of retry. [\#77](https://github.com/HotelsDotCom/styx/pull/77) ([alobodzki](https://github.com/alobodzki))
- Refactor OriginsInventory. \(\#62\) [\#75](https://github.com/HotelsDotCom/styx/pull/75) ([alobodzki](https://github.com/alobodzki))
- Styx user manual: add "Troubleshooting TLS configuration" section. [\#73](https://github.com/HotelsDotCom/styx/pull/73) ([mikkokar](https://github.com/mikkokar))
- OriginStatsFactory instantiation moved upper in hierarchy. [\#71](https://github.com/HotelsDotCom/styx/pull/71) ([alobodzki](https://github.com/alobodzki))
- Merge SPI refactoring work to master, batch 1 [\#69](https://github.com/HotelsDotCom/styx/pull/69) ([alobodzki](https://github.com/alobodzki))
- Eliminate port conflicts in end-to-end tests [\#67](https://github.com/HotelsDotCom/styx/pull/67) ([mikkokar](https://github.com/mikkokar))
- Dynamically allocate port in some e2e tests. [\#65](https://github.com/HotelsDotCom/styx/pull/65) ([mikkokar](https://github.com/mikkokar))
- Removed un-used github pages dependencies Gemfile [\#64](https://github.com/HotelsDotCom/styx/pull/64) ([ClaudioCorridore](https://github.com/ClaudioCorridore))
- Maven version shield add [\#63](https://github.com/HotelsDotCom/styx/pull/63) ([alobodzki](https://github.com/alobodzki))
- Refactor OriginsInventory. [\#62](https://github.com/HotelsDotCom/styx/pull/62) ([mikkokar](https://github.com/mikkokar))
- Moving underlying request send logic from client implementstions to connections. [\#58](https://github.com/HotelsDotCom/styx/pull/58) ([alobodzki](https://github.com/alobodzki))
- Stop NullPointerExceptions in HttpRequestMessageLogger [\#43](https://github.com/HotelsDotCom/styx/pull/43) ([kvosper](https://github.com/kvosper))
- Add cipher suite controls to backend services configuration. [\#36](https://github.com/HotelsDotCom/styx/pull/36) ([mikkokar](https://github.com/mikkokar))
- Merge latest changes from master [\#60](https://github.com/HotelsDotCom/styx/pull/60) ([mikkokar](https://github.com/mikkokar))
- Decouple StateMachine mechanics from the QueueDrainingEventProcessor. [\#54](https://github.com/HotelsDotCom/styx/pull/54) ([mikkokar](https://github.com/mikkokar))
- Refactor styx routing objects [\#53](https://github.com/HotelsDotCom/styx/pull/53) ([mikkokar](https://github.com/mikkokar))
- Remove bodyAs\(Function\) method from FullHttpMessage API. [\#52](https://github.com/HotelsDotCom/styx/pull/52) ([kvosper](https://github.com/kvosper))
- Store Full HTTP message content as byte array. [\#50](https://github.com/HotelsDotCom/styx/pull/50) ([mikkokar](https://github.com/mikkokar))
- Passing a dependency array into service factories [\#49](https://github.com/HotelsDotCom/styx/pull/49) ([alobodzki](https://github.com/alobodzki))
- Extracting OriginsInventory creation outside of StyxHttpClient [\#47](https://github.com/HotelsDotCom/styx/pull/47) ([alobodzki](https://github.com/alobodzki))
- Remove Guava Service from Registry API. [\#45](https://github.com/HotelsDotCom/styx/pull/45) ([mikkokar](https://github.com/mikkokar))
- Add streaming classes [\#44](https://github.com/HotelsDotCom/styx/pull/44) ([kvosper](https://github.com/kvosper))

## [styx-0.7.4](https://github.com/HotelsDotCom/styx/tree/styx-0.7.4) (2018-01-11)
[Full Changelog](https://github.com/HotelsDotCom/styx/compare/styx-0.7.3...styx-0.7.4)

**Implemented enhancements:**

- Choose supported TLS protocol versions for styx client [\#28](https://github.com/HotelsDotCom/styx/issues/28)

**Merged pull requests:**

- Tidy up tests: Use new FullHttpRequest/Response message API [\#42](https://github.com/HotelsDotCom/styx/pull/42) ([mikkokar](https://github.com/mikkokar))
- Add initial changelog file and make rule for changelog generation [\#41](https://github.com/HotelsDotCom/styx/pull/41) ([alobodzki](https://github.com/alobodzki))
- Add FullHttpRequest and FullHttpResponse classes to Styx API [\#40](https://github.com/HotelsDotCom/styx/pull/40) ([kvosper](https://github.com/kvosper))
- Pretty print admin interface pages by default. [\#35](https://github.com/HotelsDotCom/styx/pull/35) ([mikkokar](https://github.com/mikkokar))
- Don't try to parse the inbound request paths [\#15](https://github.com/HotelsDotCom/styx/pull/15) ([taer](https://github.com/taer))

## [styx-0.7.3](https://github.com/HotelsDotCom/styx/tree/styx-0.7.3) (2017-11-27)
[Full Changelog](https://github.com/HotelsDotCom/styx/compare/styx-0.7.1...styx-0.7.3)

**Closed issues:**

- Out of direct memory exception from Netty is sometimes mapped to 502 Bad Gateway [\#24](https://github.com/HotelsDotCom/styx/issues/24)
- Provide a metric for direct memory monitoring [\#17](https://github.com/HotelsDotCom/styx/issues/17)

**Merged pull requests:**

- Added developers section in parent pom [\#39](https://github.com/HotelsDotCom/styx/pull/39) ([alobodzki](https://github.com/alobodzki))
- Version bumped up to 0.7.3-SNAPSHOT [\#38](https://github.com/HotelsDotCom/styx/pull/38) ([alobodzki](https://github.com/alobodzki))
- Maven central publishing profile added [\#37](https://github.com/HotelsDotCom/styx/pull/37) ([alobodzki](https://github.com/alobodzki))
- Change file headers to expected format [\#34](https://github.com/HotelsDotCom/styx/pull/34) ([kvosper](https://github.com/kvosper))
- Issue28: Specify TLS protocol version for backend services [\#33](https://github.com/HotelsDotCom/styx/pull/33) ([mikkokar](https://github.com/mikkokar))
- Create ISSUE\_TEMPLATE.md [\#31](https://github.com/HotelsDotCom/styx/pull/31) ([kainee](https://github.com/kainee))
- Add link to styx-user Google Groups forum. [\#30](https://github.com/HotelsDotCom/styx/pull/30) ([mikkokar](https://github.com/mikkokar))
- Implement Wiremock API on top of Styx/Netty HTTP servers. [\#29](https://github.com/HotelsDotCom/styx/pull/29) ([mikkokar](https://github.com/mikkokar))
- Styx server tls protocol [\#27](https://github.com/HotelsDotCom/styx/pull/27) ([mikkokar](https://github.com/mikkokar))
- Map OutOfDirectMemoryError to 500 Internal Server Error status code. [\#26](https://github.com/HotelsDotCom/styx/pull/26) ([mikkokar](https://github.com/mikkokar))
- Enable Maven quality profile for Travis builds [\#25](https://github.com/HotelsDotCom/styx/pull/25) ([mikkokar](https://github.com/mikkokar))
- Set of metrics that are tracking netty pooled memory allocator [\#23](https://github.com/HotelsDotCom/styx/pull/23) ([alobodzki](https://github.com/alobodzki))
- Added code of conduct [\#22](https://github.com/HotelsDotCom/styx/pull/22) ([massdosage](https://github.com/massdosage))
- Log file changes when file backed registry fails at notifying listene… [\#21](https://github.com/HotelsDotCom/styx/pull/21) ([kvosper](https://github.com/kvosper))
- Change styx version format. [\#20](https://github.com/HotelsDotCom/styx/pull/20) ([mikkokar](https://github.com/mikkokar))
- Add styx load test script [\#18](https://github.com/HotelsDotCom/styx/pull/18) ([mikkokar](https://github.com/mikkokar))
- Add getOptional to Http context [\#16](https://github.com/HotelsDotCom/styx/pull/16) ([taer](https://github.com/taer))
- Make sure we don't end up with 0 threads [\#14](https://github.com/HotelsDotCom/styx/pull/14) ([taer](https://github.com/taer))
- fix documentation [\#13](https://github.com/HotelsDotCom/styx/pull/13) ([ggaeta1](https://github.com/ggaeta1))
- Update pom files [\#12](https://github.com/HotelsDotCom/styx/pull/12) ([mikkokar](https://github.com/mikkokar))

## [styx-0.7.1](https://github.com/HotelsDotCom/styx/tree/styx-0.7.1) (2017-10-19)
**Merged pull requests:**

- Release styx-0.7.1. [\#11](https://github.com/HotelsDotCom/styx/pull/11) ([mikkokar](https://github.com/mikkokar))
- Improve distribution zip file: [\#10](https://github.com/HotelsDotCom/styx/pull/10) ([mikkokar](https://github.com/mikkokar))
- Adding guides to get started with Styx quickly [\#7](https://github.com/HotelsDotCom/styx/pull/7) ([kvosper](https://github.com/kvosper))
- Adding sections of pom requested by central repository [\#6](https://github.com/HotelsDotCom/styx/pull/6) ([alobodzki](https://github.com/alobodzki))
- Reduce test logging [\#5](https://github.com/HotelsDotCom/styx/pull/5) ([mikkokar](https://github.com/mikkokar))
- Apache license shield added [\#4](https://github.com/HotelsDotCom/styx/pull/4) ([alobodzki](https://github.com/alobodzki))
- Add Travis build badge to README.md. [\#3](https://github.com/HotelsDotCom/styx/pull/3) ([mikkokar](https://github.com/mikkokar))
- Add .travis.yml. [\#2](https://github.com/HotelsDotCom/styx/pull/2) ([mikkokar](https://github.com/mikkokar))
- Fixed image reference to Styx overview diagram. [\#1](https://github.com/HotelsDotCom/styx/pull/1) ([iamchrisrice](https://github.com/iamchrisrice))



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*
