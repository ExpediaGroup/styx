ARG TAG=11-jdk
FROM openjdk:${TAG}

ARG STYX_VERSION=""
ARG STYX_IMAGE=https://github.com/HotelsDotCom/styx/releases/download/${STYX_VERSION}/${STYX_VERSION}-linux-x86_64.zip

ENV APP_HOME=/styx

ENV STYX_CONFIG=/styx/default-config/default.yml
ENV STYX_LOG_CONFIG=/styx/conf/logback.xml
ENV STYX_ENV_FILE=/styx/default-config/styx-env.sh
ENV STYX_LOG_OUTPUT=/styx/logs/

RUN addgroup styx && useradd -d /home/styx -g styx -s /bin/bash styx

# Remove overriding ulimits
RUN rm -f /etc/security/limits.d/*

ADD ${STYX_IMAGE} /styx.zip
RUN unzip /styx.zip \
    && rm /styx.zip

WORKDIR ${APP_HOME}

ADD default-docker.yml /styx/default-config/default.yml
ADD styx-env.sh /styx/default-config/styx-env.sh
ADD origins.yml /styx/default-config/origins.yml

RUN mkdir -p ${STYX_LOG_OUTPUT}
RUN chown styx:styx ${STYX_LOG_OUTPUT}

EXPOSE 8080 8443 9000

USER styx

CMD ["/styx/default-config/default.yml"]

ENTRYPOINT ["bin/startup"]
