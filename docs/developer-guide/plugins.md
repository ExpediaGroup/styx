# Plugins

Styx has a plugin-based architecture that allows each Styx instance to be deployed 
with a suite of  independently-developed plugins configured as desired by the deployment 
process. Plugins can be used to transform HTTP requests and responses as they are being 
proxied through. This makes it easy to extend Styx with custom business logic 
with familiar Java-based technologies. In this document we describe how to implement 
such business logic in Styx plugins.

Each plugin is contained within its own JAR file, located according to the configuration 
specified in the Styx config files.

## Configuration example

    plugins:
      active: addheaders
      all:
        addheaders:
          factory:
            class: "com.hotels.styx.ExamplePluginFactory"
            classPath: "<path-to-plugin>/plugin-examples-1.0-SNAPSHOT.jar"
          config:
            requestHeaderValue: "requestheader"
            responseHeaderValue: "responseheader"
      
### Configuration example explanation

* **active** contains a comma-separated list of plugin names. It is a convenience feature 
  used to switch a plugin on and off without needing to remove its entire set of configuration.
* **all** contains a list of configuration for all the plugins deployed with the Styx instance. 
  Inside each list item:
    * **addheaders** is the name of the plugin
    * **factory** configures a factory object that can produce the plugin:
        * **class** contains the name of the factory class, which must 
          extend `com.hotels.styx.api.plugins.spi.PluginFactory`                  
        * **classPath** provides the location of the JAR file
    * **config** contains custom configuration for the particular plugin. Its value can be 
      of any type. See below how to write a custom config java class.

When Styx starts, it sets up the HTTP interceptor chain as follows:

1. Looks for all active plugins as specified by plugins.active config setting.
2. For each activated plugin, it loads the jar file configured by factory.classPath attribute.
3. It loads the plugin factory class, specified by factory.class attribute. 
4. It instantiates the plugin by calling the plugin factory create method, passing in the Styx Environment object.



## Developing a plugin
A plugin project can be started by using one of examples in `examples` submodule. All plugins share the same skeleton of a project, containing a:

* main/java/com/hotels/styx/ExamplePlugin.java - The plugin's main class which extends 
  the Plugin interface, and most notably implements the `intercept(LiveHttpRequest, Chain)` method.
* main/java/com/hotels/styx/ExamplePluginConfig.java - A class that represents plugin 
  configuration, as it appears in styx_conf.yml.
* main/java/com/hotels/styx/ExamplePluginFactory.java - A class that implements 
  PluginFactory interface, responsible for instantiating the plugin.

Some additional examples can be found in `system-tests/example-styx-plugin` directory in a project repository. 
There are examples of plugins providing simple examples of how to:
* perform a before action on a request - `loadtest.plugins.AsyncRequestContentDecoderPluginFactory`,
* perform an action response object  - `loadtest.plugins.AsyncResponseContentDecoderPluginFactory`

### Plugin class
A Styx plugin must implement the `Plugin` interface, which extends from `HttpInterceptor`. 
As the name suggests, an HttpInterceptor intercepts, transforms, or performs other actions 
as HTTP traffic is being proxied through. The interceptors are organised linearly in 
a specific order to form a  pipeline. Styx injects the HTTP request to the head of 
the pipeline. Each interceptor then processes the request in turn until the request 
reaches to the tail of the pipeline. After that the request is proxied out to the 
destination origins. Once the response arrives, Styx injects the HTTP response, 
conversely, to the tail of the pipeline. The response therefore propagates through 
the pipeline in reverse order. When it comes out of the pipeline, the response will 
be proxied back to the requestor.

The interceptors have ability to transform proxied HTTP objects either synchronously, or asynchronously.
 
* **intercept** is called every time a request passes through the pipeline, and 
returns an observable of the response. It can be used to modify the request and/or response

* **styxStarting** is called when Styx starts and can be used to perform any initial set-up.

* **styxStopping** is called when Styx stops and can be used to clean up any resources.

* **adminHandlers** allows you to add plugin-specific admin pages to Styx.

#### The intercept() method

The `intercept` method is a callback that exposes a proxied request to an `HttpInterceptor` (plugin). 
Note that:

* `intercept` methods are called serially, in order they are configured in the pipeline, 
  starting from the first interceptor.
* an interceptor "owns" the request until it passes it to the next interceptor, by
  calling `chain.proceed`. At this point Styx calls `intercept` of the next interceptor.
* If a plugin chooses to respond to the request, the request will not 
  get propagated any further down the pipeline.

The `intercept` is called with two arguments: HTTP request, and a `Chain`. 
The `Chain` represents the section of the Styx interceptor pipeline further
upstream from this plugin, and
it is used to pass the request on in the pipeline. Chain also stores request
context attributes and exposes them to interceptors.

Implementations of `intercept` operate in this order:

1. Transform HTTP request.
2. Pass the request on by calling `chain.proceed`. 
   This returns a `Eventual<LiveHttpResponse>`.
3. Apply response transformations on the response `Eventual`.    
4. Return an `Eventual` of the transformed response.

Alternatively, when the interceptor responds to the request:

1. Consume request body.
2. Construct a response.
3. Return an `Eventual` of the response.

### Plugin factory class

A plugin factory class instantiates the plugin. 
There is only one method to implement: `Plugin create(Environment environment);`

The `create` method performs any necessary initialisation before starting 
the plugin. Styx passes in an `Environment` object that contains the plugin's 
configuration, a view of Styx configuration, and also a metrics registry.

Styx will not start forwarding traffic until the call to `create` returns successfully
for all active plugins. Therefore it is acceptable to perform blocking operations 
on `create` until the plugin is ready to start. This may include reading local 
files or querying remote servers. However, use this capability 
judiciously. Plugins are loaded serially, and initialisation time for the full 
plugin chain adds up. Future versions of Styx may offer more sophisticated 
lifecycle management.
 

### Running a plugin

To build a single jar with dependencies, please execute maven command `mvn -Pstyx clean package`. 
This single jar can be referenced in a styx configuration file. All the details regarding 
running styx server with additional plugins locally can be found in [User Guide](user-guide.md) section. 

### Custom config

The custom config can be any type. It could be a simple type such as `java.lang.String` 
or a custom class developed as part of the plugin. It could also be a 
list or map of simple of custom classes.

Here is an example of how a custom config class can be written in java. Note the 
use of `com.fasterxml.jackson.annotation.JsonProperty` to tell the YAML parser 
how to construct your class. The properties of your custom classes can be instances 
of custom classes themselves.

    import com.fasterxml.jackson.annotation.JsonProperty;

        public class RewritePluginConfig {
        private final String oldUri;
        private final String newUri;

        public RewritePluginConfig(
                @JsonProperty("oldUri") String oldUri,
                @JsonProperty("newUri") String newUri) {
            this.oldUri = oldUri;
            this.newUri = newUri;
        }

        public String oldUri() {
            return oldUri;
        }

        public String newUri() {
            return newUri;
        }
    }

Note that the custom class does not have to be named in the YAML at all. It is 
simply accessed via the method `com.hotels.styx.api.plugins.spi.PluginFactory.Environment.pluginConfig`
As long as the class passed to that method has properties matching the YAML, it can be loaded.
For details of styx configuration file please refer to [User Guide](user-guide.md) section.

## Development best practices

To ensure that plugins are correct, perform well and are maintainable, best practices must be followed. 

### Performance

The most important thing to remember is that Styx uses non-blocking I/O. This 
means that each worker thread is shared between multiple requests.
As such, any delay during a request will affect all requests on the 
same thread. In particular:

* Never block the current thread. This will block **all requests** using that thread.
* If it is necessary to do I/O to an external service during a request, a separate thread pool should be used.
* Avoid logging at runtime. 
 
You should also avoid loading lots of data into memory when your plugin starts as 
this will make Styx take longer to start up. 

### Toggling plugins on-and-off

Plugins can be toggled on-and-off by sending a PUT request to `/admin/tasks/plugin/<PLUGIN_NAME>/enabled` 
with a body of `true` to enable or `false` to disable.

To find out whether a plugin has been enabled or disabled through this mechanism, 
send a GET request to `/admin/tasks/plugin/<PLUGIN_NAME>/enabled`.
The result will be `true` if enabled, or `false` if disabled.

