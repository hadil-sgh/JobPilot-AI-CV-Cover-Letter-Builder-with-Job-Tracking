# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
# Cache mount keeps ~/.m2 between builds, so dependencies download only once.
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 jobpilot && mkdir -p /app/storage && chown jobpilot /app/storage
COPY --from=build /src/target/*.jar app.jar
USER jobpilot
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
