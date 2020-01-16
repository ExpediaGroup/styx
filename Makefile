SHELL := /bin/bash

.PHONY: all test clean
ALL_PROFILES = release,quality
STACK = development

CURRENT_DIR := $(shell pwd)

# VERSION UPDATE
VERSION=0.6-SNAPSHOT
set-version:
	mvn versions:set -DnewVersion=${VERSION}

## Removes all files built by build process
clean:
	mvn clean -P$(ALL_PROFILES)
	mvn -f distribution/pom.xml clean
	rm -rf ${DOCKER_CONTEXT}

## Run unit tests and checkstyle
unit-test:
	mvn clean test -Pquality

PLATFORM=macosx
## Compile, run unit tests, checkstyle and integration tests
e2e:
	mvn clean verify -Pquality,release,$(PLATFORM)

## Compile code and tests but do not run
# Note: Pre-integration test phase is necessary to produce styx.properties file
# needed by AdminSpec.scala tests.
e2e-compile:
	mvn clean pre-integration-test -P$(PLATFORM)

## Run system tests
e2e-test:
	mvn -f system-tests/e2e-suite/pom.xml -P$(PLATFORM) scalatest:test

## Execute a single end-to-end (scala) test
# Alternatively, it should be possible to run an individual e2e test
# with following Maven command:
#
#  mvn -f system-tests/e2e-suite/pom.xml scalatest:test -Dsuites='*ProxyPropertySpec'
#
e2e-test-single:
	mvn -f system-tests/e2e-suite/pom.xml scalatest:test -Dsuites='${TEST}'

## Compile, test and create styx.zip
release: clean
	mvn install -Prelease

## Compile and create styx.zip without running tests
release-no-tests: clean
	mvn install -Prelease,$(PLATFORM) -Dmaven.test.skip=true

GIT_BRANCH=$(shell basename $(shell git symbolic-ref --short HEAD))
LOAD_TEST_DIR=$(CURRENT_DIR)/logs/load-test-$(GIT_BRANCH)-$(shell date "+%Y_%m_%d_%H:%M:%S")
RATE=3000
TIMES=3
DURATION=30
CONNECTIONS=200
ENDPOINT=http://localhost:8080/landing/demo
SSL_ENDPOINT=https://localhost:8443/landing/demo
LOAD_TEST_TOOL=scripts/load-test-tool/load_test.py
PERF_DIR=system-tests/performance

wrk2:
	git clone https://github.com/giltene/wrk2.git

wrk2/wrk: wrk2
	# Assumes openssl has been installed using homebrew, *AND* the homebrew
	# installation directory is /usr/local
	(cd wrk2/; LIBRARY_PATH=/usr/local/opt/openssl/lib C_INCLUDE_PATH=/usr/local/opt/openssl/include make)

## Run a load test against Styx - launch styx with: make start STACK=perf-local
load-test: wrk2/wrk
	(cd $(PERF_DIR); python $(LOAD_TEST_TOOL) -o'$(LOAD_TEST_DIR)' -d $(DURATION) -c $(CONNECTIONS) --times $(TIMES) -R $(RATE) $(ENDPOINT))

## Run a load test against Styx's HTTPS endpoint - launch styx with: make start STACK=perf-local
load-test-https:
	(cd $(PERF_DIR); python $(LOAD_TEST_TOOL) -o'$(LOAD_TEST_DIR)' -d $(DURATION) -c $(CONNECTIONS) --times $(TIMES) -R $(RATE) $(SSL_ENDPOINT))

## A more primitive load-test - do we need this?
load-simple:
	(cd $(PERF_DIR)/tools/wrk; ./wrk -H 'Host: localhost' -H 'Connection: keep-alive' -t 2 -c 200 -d 30s -R3000 --latency $(ENDPOINT))

## Run a build with tests and checkstyle
quality: clean
	mvn install -Pquality

## Run a build with checkstyle but no tests
quality-no-tests:
	mvn -Dmaven.test.skip=true clean install -Pquality -Dmaven.test.skip=true

STYX_HOME = $(CURRENT_DIR)/distribution/target/styx/styx
DOCKER_CONTEXT = $(CURRENT_DIR)/distribution/target/styx/docker
CONFIG_ROOT := $(STYX_HOME)/conf/env-$(STACK)

## Compile and create styx.zip then unzip into a directory defined by STYX_HOME
release-styx: release-no-tests
	unzip -oq `find  distribution/target -maxdepth 1 -name "styx*.zip"` -d $(dir ${STYX_HOME})

## Build site-docs
docs:
	mvn clean site:site

## Run site-docs locally
docs-run:
	mvn site:run

## Test Styx's resilence against denial-of-service attack
ddos:
	slowhttptest -c 4000 -B -g -o my_body_stats -i 110 -r 200 -s 8192 -t POST -u http://localhost:8080/demo

## Generates change log between two github tags. If TAG2 value is not provided it will generate changes until HEAD.
# Requires authentication token set either in environment variable CHANGELOG_GITHUB_TOKEN or make argument with a same name.
# example: make changelog TAG2=styx-0.7.3 CHANGELOG_GITHUB_TOKEN=xxx
TAG1 = 'styx-0.7.1'
CHANGELOG_GITHUB_TOKEN ?= $(shell $CHANGELOG_GITHUB_TOKEN)
changelog:
	docker run --rm --interactive --tty --net "host" -v "$(CURRENT_DIR):/tmp/" -w "/tmp/" \
	-it muccg/github-changelog-generator --between-tags $(TAG1),$(TAG2) -u HotelsDotCom -p 'styx' --token '$(CHANGELOG_GITHUB_TOKEN)'

#
# To run the styx docker image with custom configuration:
#
#     docker container run -d --name mystyx \
#                          -p 8080:8080 -p 9000:9000 -p 8443:8443 \
#                          -v $(pwd)/docker-config:/styx/config \
#                          styxcore:latest /styx/config/styxconf.yml
#
# Assuming that styxconf.yml exists in "./docker-config/" directory.
# Default configuration file: /styx/default-config/default.yml
#
docker-image: clean
	mvn install -Prelease,linux,docker -DskipTests=true -Dmaven.test.skip=true
