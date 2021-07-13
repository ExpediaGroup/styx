FROM centos:7

ENV APP_HOME=/build

ARG MVN_VERSION=3.6.3
ARG MVN_URL=http://apache.mirrors.nublue.co.uk/maven/maven-3/${MVN_VERSION}/binaries/apache-maven-${MVN_VERSION}-bin.tar.gz
ENV MVN_INSTALL_DIR=/opt/mvn

WORKDIR ${APP_HOME}

RUN yum install -y java-11-openjdk-devel \
    && yum install -y git \
    && yum install -y bash-completion \
    && yum install -y docker \
    && yum install -y wget \
    && yum install -y make \
    && wget ${MVN_URL} \
    && tar -C /opt/ -zxvf apache-maven-${MVN_VERSION}-bin.tar.gz \
    && rm apache-maven-${MVN_VERSION}-bin.tar.gz \
    && source /etc/bash_completion.d/git

ENV PATH=/opt/apache-maven-${MVN_VERSION}/bin:$PATH
ENV JAVA_HOME=/etc/alternatives/java_sdk



