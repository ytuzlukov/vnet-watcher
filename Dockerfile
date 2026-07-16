FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode clean package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S watcher \
    && adduser -S watcher -G watcher
WORKDIR /app

COPY --from=build /build/target/vnet-watcher.jar /app/vnet-watcher.jar

USER watcher
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "/app/vnet-watcher.jar"]
