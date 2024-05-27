FROM eclipse-temurin:21-alpine as build

WORKDIR /application

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .
COPY src src

RUN chmod +x ./gradlew
# RUN apk add --no-cache bash
RUN ./gradlew bootJar --no-daemon


FROM eclipse-temurin:21-alpine
LABEL maintainer="mashirot <shiina@sakurasou.io>"
WORKDIR /application

COPY --from=build /application/build/libs/*.jar application.jar

VOLUME /application/config

ENV JVM_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["java", "-jar", "application.jar"]