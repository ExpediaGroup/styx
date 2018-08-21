# Admin interface summary

One of main features of the Styx server available for users is an admin interface available "out of the box".
It provides an HTTP interface with functionality that ease quick adaptation of the library. 
Note that this endpoint must be externally secured.

## Endpoints available in the admin module
* POST interface with set of predefined admin commands (see [section below](#postinterface)).

* `Dashboard` - visual dashboard that shows the health of the server and origins.

* `Plugins` - list of plugin extensions and the admin endpoints they expose (if applicable).

* `Metrics` - Styx performance metrics as a JSON document. 

* `JVM` - a subset of metrics specifically related to the underlying JVM usage statistics.

* `Configuration` - Styx server configuration settings. 

* `Origins Configuration` - origin services configuration as a JSON document.

* `Origins Status` - origins status as a JSON document.

* `Log Configuration` - logging configuration.

* `Ping` - simple health-check for the server - returns "pong" if Styx is running.

* `Health Check`, `Status` - health-checks based on HTTP 500 rate from origins.

* `Threads` - a stack trace dump from all threads. 

All endpoints are available from the admin menu:

`http://<STYX_SERVER_URL>/admin/`

##### NOTE:
Endpoints that return `JSON` documents are compressed by default. To display documents prettified, the
`?pretty` option as an additional query parameter should be used: 

* `http://<STYX_SERVER_URL>/admin/metrics?pretty`

## POST interface <a name="postinterface"></a>

The following commands can be sent to the Styx admin interface with HTTP POST requests.

### Reload Origins

This command will cause Styx to reload the origins if the file has meaningfully changed. 
If the file has not changed,or the changes are purely cosmetic (such as adding comments), 
no action will be taken.

#### Syntax:

`http://<STYX_SERVER_URL>/admin/tasks/origins/reload`

#### Parameters:

* `STYX_SERVER_URL`: The host and port of the Styx Admin Interface

#### How to execute command using curl:

`curl -X POST http://<STYX_SERVER_URL>/admin/tasks/origins/reload`

So if Styx was on port 8080 on localhost:

`curl -X POST http://localhost:8080/admin/tasks/origins/reload`

### Origin Toggle

#### Enable Origin

The `enable_origin` command will enable a disabled origin. If health checks are enabled, the origin will initially be
inactive and will be activated upon a successful health check. If health checks are disabled, the origin will be
active immediately.

##### Syntax:

`http://<STYX_SERVER_URL>/admin/tasks/origins?cmd=enable_origin&appId=<APP_ID>&originId=<ORIGIN_ID>`

##### Parameters:

* `STYX_SERVER_URL`: The host and port of the Styx Admin Interface
* `APP_ID`: The backend service the origin belongs to
* `ORIGIN_ID`: An origin to enable

##### How to execute command using curl:

`curl -X POST http://<STYX_SERVER_URL>/admin/tasks/origins?cmd=enable_origin&appId=<APP_ID>&originId=<ORIGIN_ID>`

So if Styx was on port 8080 on localhost and you wanted to enable an origin called "hwa1" for a backend service called "hwa":

`curl -X POST http://localhost:8080/admin/tasks/origins?cmd=enable_origin&appId=hwa&originId=hwa1`

#### Disable Origin

The `disable_origin` command will disable an enabled origin.

##### Syntax:

`http://<STYX_SERVER_URL>/admin/tasks/origins?cmd=disable_origin&appId=<APP_ID>&originId=<ORIGIN_ID>`

##### Parameters:

* `STYX_SERVER_URL`: The host and port of the Styx Admin Interface
* `APP_ID`: The backend service the origin belongs to
* `ORIGIN_ID`: An origin to disable

##### How to execute command using curl:

`curl -X POST http://<STYX_SERVER_URL>/admin/tasks/origins?cmd=disable_origin&appId=<APP_ID>&originId=<ORIGIN_ID>`

So if Styx was on port 8080 on localhost and you wanted to disable an origin called "hwa1" for a backend service called "hwa":

`curl -X POST http://localhost:8080/admin/tasks/origins?cmd=disable_origin&appId=hwa&originId=hwa1`

### Enable/Disable Plugins

Plugins can be enabled and disabled using POST commands.

#### Syntax:

`http://<STYX_SERVER_URL>/admin/tasks/plugin/<PLUGIN_NAME>/enabled`

#### Parameters:

* `PLUGIN_NAME`: The host and port of the Styx Admin Interface
* The requested state should be sent in a request body with value: `true`/`false`

#### How to execute command using curl:

If Styx was on port 8080 on localhost and you wanted to disable a plugin called "rewrite":

`curl -X POST --data "false" http://localhost:8080/admin/tasks/plugin/rewrite/enabled`

## Plugins extensions

#### Syntax

`http://<STYX_SERVER_URL>/admin/plugins`

This endpoint displays all plugins available in Styx with their library versions. Each plugin
can register additional, custom admin extensions which will be visible here. For more details, please
refer to [Plugins](../developer-guide/plugins.md)

## GUI dashboard

#### Syntax
`http://<STYX_SERVER_URL>/admin/dashboard/index.html`

Dashboard provides a visual representation of the origins health. It lists all configured origins providing
information about the status of the underlying connection to each origin. Using this dashboard you can quickly
observe whether the origin is reachable, what is the quality of a connection, etc.

It also provides summarized information about error codes in current server uptime, both for the response
from origins and from Styx.

## Styx performance metrics

#### Syntax

`http://<STYX_SERVER_URL>/admin/metrics`

Returns JSON document with server metrics (from Dropwizard Metrics) gathered in-memory by server instance. 
For more information on the concrete metrics, please refer to [Metrics](metrics.md)
 and [Metrics Reference](metrics-reference.md) documents.

## JVM metrics

#### Syntax

`http://<STYX_SERVER_URL>/admin/jvm`

Subset of metrics exposed by JVM that is running the Styx server instance.

## Server Configuration
#### Syntax

`http://<STYX_SERVER_URL>/admin/configuration`

This endpoint returns a textual representation of the settings configured in the running server instance.
For more details about the content of this document please refer to [Configure overview](configure-overview.md).

## Origin services configuration

#### Syntax

`http://<STYX_SERVER_URL>/admin/configuration/origins`

## Origins status
 
#### Syntax
 
 `http://<STYX_SERVER_URL>/admin/origins/status`
 
This endpoint provides  similar information to the GUI dashboard but in document form. It contains
a list of configured backend services and the status of their underlying servers, stating which one are inactive
and which ones are disabled. 

## Logging configuration
 
#### Syntax
 
`http://<STYX_SERVER_URL>/admin/logging`

The document returned by this endpoint presents the configuration used to setup the logging framework.

## Health checks

#### Syntax

`http://<STYX_SERVER_URL>/admin/ping`

This endpoint returns `pong` when the server is running, without performing any checks regarding the status.

## Running threads

#### Syntax

`http://<STYX_SERVER_URL>/admin/threads`

This endpoint reports threads and their statuses running on the server.
