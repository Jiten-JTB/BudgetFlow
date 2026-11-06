# syntax=docker/dockerfile:1.7
# Build context: backend/   ->   docker build -f services/orchestrator-service/Dockerfile -t budgetflow/orchestrator-service backend
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -pl services/orchestrator-service -am package -DskipTests \
 && java -Djarmode=tools -jar services/orchestrator-service/target/orchestrator-service-*.jar extract --layers --launcher --destination /app

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app
# Dependencies change least often: copy them first for layer caching.
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
EXPOSE 8140 8141
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8141/actuator/health/readiness | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
