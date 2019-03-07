FROM openjdk:11-jre-slim

ARG STYX_VERSION=""
ARG STYX_IMAGE=https://github.com/HotelsDotCom/styx/releases/download/${STYX_VERSION}/${STYX_VERSION}-linux-x86_64.zip

ENV APP_HOME=/styx

ENV STYX_CONFIG=/styx/default-config/default.yml
ENV STYX_LOG_CONFIG=/styx/styx/conf/logback.xml
ENV STYX_ENV_FILE=/styx/default-config/styx-env.sh
ENV STYX_LOG_OUTPUT=/styx/logs/

WORKDIR ${APP_HOME}

ADD ${STYX_IMAGE} ${APP_HOME}/styx.zip
ADD default-docker.yml /styx/default-config/default.yml
ADD styx-env.sh /styx/default-config/styx-env.sh
ADD origins.yml /styx/default-config/origins.yml

RUN unzip styx.zip \
    && rm styx.zip

EXPOSE 8080 8443 9000

CMD ["/styx/default-config/default.yml"]

ENTRYPOINT ["styx/bin/startup"]
