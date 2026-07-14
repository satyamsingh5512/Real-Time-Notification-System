# Multi-stage build: compile with the Gradle wrapper + JDK 21, run on a slim JRE 21 image.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY common/build.gradle.kts common/build.gradle.kts
COPY domain/build.gradle.kts domain/build.gradle.kts
COPY application/build.gradle.kts application/build.gradle.kts
COPY infrastructure/build.gradle.kts infrastructure/build.gradle.kts
COPY api/build.gradle.kts api/build.gradle.kts

# Warm the Gradle dependency cache in its own layer so source-only changes don't
# re-download the world.
RUN chmod +x gradlew && ./gradlew --version

COPY common ./common
COPY domain ./domain
COPY application ./application
COPY infrastructure ./infrastructure
COPY api ./api

RUN ./gradlew :api:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S notification && adduser -S notification -G notification
WORKDIR /app

COPY --from=build /workspace/api/build/libs/notification-platform.jar app.jar

USER notification
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
