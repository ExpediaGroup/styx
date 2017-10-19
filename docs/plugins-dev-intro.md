# STYX
## Introduction to developing plugins

This is a quick guide to developing plugins for the Styx platform. It is written with the assumption that you 
are already familiar with running Styx. If not, please consult the [Quick-start guide](quick-start.md).

We have not yet released our modules online as maven dependencies, but this is coming soon!

For now, please build the project locally using one of the following commands:

* either `mvn install -Plinux -Dmaven.test.skip=true`
* or `mvn install -Pmacosx -Dmaven.test.skip=true`

and add the following 
dependency to your `pom.xml` file.

    <dependency>
      <groupId>com.hotels.styx</groupId>
      <artifactId>styx-api</artifactId>
      <version>0.7-SNAPSHOT</version>
    </dependency>


If, for some reason, you are unable to use maven, the modules can be found in the `lib/` folder inside the [Styx release](https://github.com/HotelsDotCom/styx/releases).   

Please refer to this [example plugin code](../plugin-examples/src/main/java/com/hotels/styx/) to familiarise yourself with the plugin SPI.

### Step-by-Step

1. Create a Java project in your IDE of choice.
2. Ensure that you have the required dependencies (see maven instructions above).
3. Copy the classes from [example plugin code](../plugin-examples/src/main/java/com/hotels/styx/) into your project. For more information, consult [the plugins documentation](developer-guide/plugins.md).
4. Modify the example to suit your interests.
4. After compilation, bundle your plugin and any dependencies into a JAR file. Please note the location of the JAR file for use in configuring Styx.
5. Add the following section to your Styx config file:

        plugins:
          active: nameOfYourPlugin
          all:
            - name: nameOfYourPlugin
              factory:
                class: "com.hotels.styx.ExamplePluginFactory"
                classPath: "<path-to-plugin>/yourPluginJar.jar"
              config:
                requestHeaderValue: "example-plugin-modified-request"
                responseHeaderValue: "example-plugin-modified-response"

6. Run Styx (see [Quick-start guide](quick-start.md)).
7. Confirm that the plugin is configured by checking [the admin interface](http://localhost:9000/admin/plugins) and 
finding it in the `enabled` list. 
8. Send a request to [localhost:8080](http://localhost:8080) to test your plugin. In our example, the request should have appended
the header `myRequestHeader=example-plugin-modified-request` and the response should have appended the header `myResponseHeader=example-plugin-modified-response`
9. Note that you can use the `styx-test-api` with a test framework of your choice to write end-to-end tests that run your plugin in a Styx server.

