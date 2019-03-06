FROM openjdk:11-jre-slim

ENV APP_HOME=/styx
ENV BASENAME=styx-1.0.0.beta3
ENV STYX_CONFIG=/styx/default-config/default.yml
ENV STYX_LOG_CONFIG=/styx/styx/conf/logback.xml
ENV STYX_ENV_FILE=/styx/default-config/styx-env.sh
ENV STYX_LOG_OUTPUT=/styx/logs/

WORKDIR ${APP_HOME}

ADD https://github.com/HotelsDotCom/styx/releases/download/${BASENAME}/${BASENAME}-linux-x86_64.zip ${APP_HOME}/styx.zip
ADD default-docker.yml /styx/default-config/default.yml
ADD styx-env.sh /styx/default-config/styx-env.sh
ADD origins.yml /styx/default-config/origins.yml

RUN unzip styx.zip \
    && mv ${BASENAME} styx \
    && rm styx.zip

EXPOSE 8080 8443 9000

CMD ["/styx/default-config/default.yml"]

ENTRYPOINT ["styx/bin/startup"]
