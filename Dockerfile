# ---- Build stage: Vaadin production bundle + Spring Boot fat jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Warm the dependency cache first so source changes don't re-download everything.
COPY pom.xml .
RUN mvn -q -B -Pproduction dependency:go-offline || true

COPY src ./src
RUN mvn -q -B -Pproduction -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 raffle \
    && mkdir -p /app/data/images && chown -R raffle /app/data
COPY --from=build /workspace/target/open-raffle-*.jar app.jar
USER raffle
EXPOSE 8080
# Size the heap from the container's memory limit rather than the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
