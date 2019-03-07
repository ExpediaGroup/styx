## Building

To build Styx Docker image, run `make docker-image` from the Styx project root:

    $ make docker-image
    
## Running    

To run the resulting Styx Docker image:

    $ docker container run -d --name myStyx -p 8000:8080 -p 9000:9000 -p 8443:8443 styxcore:latest

## Configuration

Running Styx without configuration is hardly useful. Provide custom configuration using
Docker mounted volumes.

First, create a configuration directory and copy in your Styx and origins configuration:

    $ mkdir myConfig
    $ cp styxconf.yml origins.yml myConfig/

Ensure the the Styx configuration reads the origins from `/styx/config/` directory, like so:

    services:
      factories:
        backendServiceRegistry:
          class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry$Factory"
          config:
            originsFile: "/styx/config/origins.yml"

Finally, start the Styx Docker image using `/styx/config/styxconf.yml` as a configuration file:        

    docker container run -d  \
        -p 8080:8080 -p 9000:9000 -p 8443:8443 \
        -v $(pwd)/myConfig:/styx/config \
        -v $(pwd)/styxLogs:/styx/logs \
        styxcore:latest /styx/config/styxconf.yml
