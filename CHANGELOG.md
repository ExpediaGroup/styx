# Change Log

## [styx-0.7.7](https://github.com/HotelsDotCom/styx/tree/styx-0.7.7) (2018-05-17)
[Full Changelog](https://github.com/HotelsDotCom/styx/compare/styx-0.7.6...styx-0.7.7)

**Implemented enhancements:**

- Config validator: Support for MAP types [\#143](https://github.com/HotelsDotCom/styx/issues/143)
- Config validator: Improve field type detection [\#142](https://github.com/HotelsDotCom/styx/issues/142)
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

- New interceptor and routing API for styx  [\#166](https://github.com/HotelsDotCom/styx/pull/166) ([mikkokar](https://github.com/mikkokar))
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

**Implemented enhancements:**

- Improve FlowControllingHttpContentProducer state machine [\#118](https://github.com/HotelsDotCom/styx/issues/118)

**Fixed bugs:**

- Startup script does not fail if the order of the arguments is incorrect [\#125](https://github.com/HotelsDotCom/styx/issues/125)
- Unable to turn off health checks [\#119](https://github.com/HotelsDotCom/styx/issues/119)
- Styx fails to start with the default configuration file [\#110](https://github.com/HotelsDotCom/styx/issues/110)

**Closed issues:**

- Licence header in incorrect format [\#107](https://github.com/HotelsDotCom/styx/issues/107)
- HTTP messages should use CRLF as the line separator [\#103](https://github.com/HotelsDotCom/styx/issues/103)
- Styx user guide: Overarching backends/origins configuration document is missing  [\#100](https://github.com/HotelsDotCom/styx/issues/100)
- Example plugin pom file should be self-containing [\#8](https://github.com/HotelsDotCom/styx/issues/8)

**Merged pull requests:**

- Call System.exit\(\) when StyxServer.createServer\(\) fails [\#136](https://github.com/HotelsDotCom/styx/pull/136) ([dvlato](https://github.com/dvlato))
- Fix license headers [\#131](https://github.com/HotelsDotCom/styx/pull/131) ([kvosper](https://github.com/kvosper))
- Startup script does not fail if the order of the arguments is incorrect [\#129](https://github.com/HotelsDotCom/styx/pull/129) ([dvlato](https://github.com/dvlato))
- Fix broken startup [\#127](https://github.com/HotelsDotCom/styx/pull/127) ([kvosper](https://github.com/kvosper))
- Change UUID Generator. [\#123](https://github.com/HotelsDotCom/styx/pull/123) ([taer](https://github.com/taer))
- Plugin documentation and example pom.xml for plugins reviewed. [\#121](https://github.com/HotelsDotCom/styx/pull/121) ([dvlato](https://github.com/dvlato))
- Fixes \#118 Improve FlowControllingHttpContentProducer state machine. [\#120](https://github.com/HotelsDotCom/styx/pull/120) ([dvlato](https://github.com/dvlato))
- Additional pom properties for custom gpg2 settings for artifacts signing [\#117](https://github.com/HotelsDotCom/styx/pull/117) ([alobodzki](https://github.com/alobodzki))
- Styx server refactoring [\#116](https://github.com/HotelsDotCom/styx/pull/116) ([kvosper](https://github.com/kvosper))
- Open up SPI interface for Backend Service providers [\#115](https://github.com/HotelsDotCom/styx/pull/115) ([mikkokar](https://github.com/mikkokar))
- changelog update [\#114](https://github.com/HotelsDotCom/styx/pull/114) ([alobodzki](https://github.com/alobodzki))
- Issue with the changes to include a default origins file [\#113](https://github.com/HotelsDotCom/styx/pull/113) ([dvlato](https://github.com/dvlato))
- Update origins-configuration-documentation.yml [\#112](https://github.com/HotelsDotCom/styx/pull/112) ([nitingupta183](https://github.com/nitingupta183))
- Add a minimalistic origins file so the server can be run using the default.yml included in the distribution [\#111](https://github.com/HotelsDotCom/styx/pull/111) ([dvlato](https://github.com/dvlato))
- Decouple configuration parsing [\#109](https://github.com/HotelsDotCom/styx/pull/109) ([kvosper](https://github.com/kvosper))
- Improve origins reload visibility [\#108](https://github.com/HotelsDotCom/styx/pull/108) ([mikkokar](https://github.com/mikkokar))
- Document origin configuration and improve user guide. [\#106](https://github.com/HotelsDotCom/styx/pull/106) ([dvlato](https://github.com/dvlato))
- Styx tests should pass in windows [\#101](https://github.com/HotelsDotCom/styx/pull/101) ([dvlato](https://github.com/dvlato))
- Refactor load balancer APIs [\#96](https://github.com/HotelsDotCom/styx/pull/96) ([mikkokar](https://github.com/mikkokar))



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*