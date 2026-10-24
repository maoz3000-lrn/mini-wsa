# syntax=docker/dockerfile:1
# Build context: repository root (multi-module Maven project)
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY mini-wsa-app/pom.xml mini-wsa-app/
COPY mini-wsa-generator/pom.xml mini-wsa-generator/
COPY mini-wsa-generator/src mini-wsa-generator/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl mini-wsa-generator -am package -DskipTests

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /build/mini-wsa-generator/target/mini-wsa-generator-*.jar generator.jar
ENV INGEST_URL=http://app:8080
# Extra arguments are appended, so `docker compose run generator --count 500` keeps the URL
ENTRYPOINT ["sh", "-c", "exec java -jar /app/generator.jar --url \"$INGEST_URL\" \"$@\"", "--"]
