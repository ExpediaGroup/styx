# STYX
## Quick-Start Plugin-Development Guide

### Introduction

This is a quick guide to developing plugins for the Styx platform. It is written with the assumption that you 
are already familiar with running Styx. If not, please consult the [Quick start guide](./docs/quick-start.md).

We have not yet released our modules online as maven dependencies, but this is coming soon!

Until then, you can find the JAR files instead the Styx release (ZIP file).   

Please look at this [example plugin code](plugin-examples/src/main/java/com/hotels/styx/).

### Step-by-Step

1. Create a Java project in your IDE of choice.
2. Add `styx-api` as a dependency/library.
3. Create your plugin code as per [the plugins documentation](docs/developer-guide/plugins.md).
4. After compilation, bundle your plugin and any dependencies into a JAR file.
5. Add the following section to your Styx config file:

        plugins:
          active: nameOfYourPlugin
          all:
            - name: nameOfYourPlugin
              factory:
                class: "name.of.your.PluginFactoryClass"
                classPath: "<path-to-plugin>/yourPluginJar.jar"
              config:
                yourConfig: "configForYourPlugin"

6. Run Styx
7. Confirm that the plugin is configured by checking [the admin interface](http://localhost:9000/admin/plugins) and 
finding it in the `enabled` list. 
7. Send a request to [localhost:8080](http://localhost:8080) to test your plugin.


