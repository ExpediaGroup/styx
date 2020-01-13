
### styx-build

This directory contains a centos:7 based Styx build environment. It
has all necessary build tools (git, maven, make, docker) for building
images and running tests in a Linux environment. 

To build the docker image:

    docker build -t styxbuild:latest docker/styx-build/.
 
To run the image:

    docker run -it -v /var/run/docker.sock:/var/run/docker.sock styxbuild
    docker run -it -v /var/run/docker.sock:/var/run/docker.sock  -v /Users/$USER/.m2:/root/.m2 -v `pwd`:/build styxbuild:latest
    
Note, mounting a `docker.sock` is only necessary for building a Styx Docker
image inside the build container.


This image is handy for:
 
 * Confirming the build system works equally in Linux as well as in Mac OSX.
 * Hardening the system tests. Some intermittent errors only tend to occur in
   build containers.   

### styx-image

This is a Styx docker image. To build this image, use `make docker-image` command
from styx project root.

Use the test enviromnent in `system-tests/docker-test-env` as an example how to
run this image.
