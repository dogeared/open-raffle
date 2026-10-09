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
# Fit a 512 MB container: the heap is only part of the process (metaspace, symbols, code
# cache and thread stacks add ~230 MB outside it), so the heap gets 35% of RAM (~180 MB;
# the live heap after a collection is ~55 MB, so that is ample),
# the serial collector keeps GC overhead small, stacks and the code cache are trimmed, and
# the JVM exits (to be restarted) rather than limp on if it ever does run out of heap.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=35.0 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=16m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
