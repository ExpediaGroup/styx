# Toxiproxy Test Network

This is a small Docker container network with Styx and one Apache httpd origin. 
They are connected via Toxiproxy server that can simulate various network error
conditions (aka toxics).

## Installation

Local Toxiproxy installation is not necessary. It is deployed into this container 
network from Docker Hub. However you may want to use `toxiproxy-cli` for configuration
management. In that case just install it locally:

On Mac OSX:

```bash
$ brew tap shopify/shopify
$ brew install toxiproxy
```

## Startup

First, build a development Docker image for Styx:

```bash
$ mvn install -Prelease -Dmaven.test.skip=true
$ make docker-image
``` 

Ensure the image was built:

```bash
$ docker images |grep styxcore
styxcore         latest      9b65640de45b        31 minutes ago      343MB
```

Bootstrap the network:

```bash
$ docker-compose -f system-tests/docker-test-env/docker-compose.yml up
```


## Applying Toxics

### HTTP API

Inspecting configuration:

```bash
$ curl http://localhost:8474/proxies | jq
```

Applying toxics:

```bash
$ curl -v -X POST -H "Content-Type: application/json" -d @system-tests/docker-test-env/toxiproxy/origin-latency.json http://localhost:8474/proxies/origin-01/toxics
```

Removing toxics:
```bash
$ curl -v -X DELETE http://localhost:8474/proxies/httpd-01/toxics/latency_downstream
```


### Command Line

Inspecting configuration:

```bash
$ toxiproxy-cli list
```

```bash
$ toxiproxy-cli inspect httpd-01
```

Applying toxics:

```bash
$ toxiproxy-cli toxic add -t latency -tox 1 -d -a latency=5000 -a jitter=2000 httpd-01
```

Removing toxics:

```bash
$ toxiproxy-cli toxic r -n latency_downstream httpd-01
```

To enable/disable origins:
```bash
$ toxiproxy-cli toggle httpd-01
```


## Debugging

The docker environment exposes `localhost:8000` for remote debugging.

Remote debugging server options are configured in the docker compose file.
You can tweak them, for example, to suspend Styx at start.
