FROM openjdk:11-jre-slim


ENV STYX_HOME=/styx
ENV CONFIG_PATH=/conf
WORKDIR ${STYX_HOME}


COPY styx-1.0-SNAPSHOT-linux-x86_64.zip ${STYX_HOME}/styx.zip


RUN unzip styx.zip \
    && mv styx-1.0-SNAPSHOT styx \
    && rm styx.zip \
    && mkdir -p default-config \
    && cp styx/conf/styx-env.sh default-config/. \
    && cp styx/conf/default-docker.yml default-config/default.yml \
    && cp styx/conf/origins.yml default-config/. \
    && mkdir -p config

EXPOSE 8080 8443 9000

CMD ["default-config/default.yml"]

ENTRYPOINT ["styx/bin/startup"]
