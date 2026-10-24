# syntax=docker/dockerfile:1
# Build context: repository root (multi-module Maven project)
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY mini-wsa-app/pom.xml mini-wsa-app/
COPY mini-wsa-generator/pom.xml mini-wsa-generator/
COPY mini-wsa-app/src mini-wsa-app/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl mini-wsa-app -am package -DskipTests

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /build/mini-wsa-app/target/mini-wsa-app-*.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
