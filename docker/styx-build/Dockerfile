FROM maven:3.9.9-eclipse-temurin-21

ENV APP_HOME=/build

WORKDIR ${APP_HOME}

RUN apt update \
    && apt install -y bash-completion \
    && apt install -y docker \
    && apt install -y make
