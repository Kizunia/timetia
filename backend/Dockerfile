# Multi-stage build of the Spring Boot application (ADR 0047, ADR 0048): Maven build on a JDK, then a slim JRE running
# the layered jar as a non-root user. Build context: this directory (backend/).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
# A Windows checkout can turn the wrapper's line endings into CRLF, which Linux cannot run.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
# Tests run in CI. The image build only packages, so it needs no Docker daemon.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/backend-*.jar extract --layers --launcher --destination /build/layers

FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system --gid 10001 kibootia && useradd --system --uid 10001 --gid kibootia --no-create-home kibootia
WORKDIR /app
# Least-changing layers first, so a code-only change rebuilds only the last one.
COPY --from=build /build/layers/dependencies/ ./
COPY --from=build /build/layers/spring-boot-loader/ ./
COPY --from=build /build/layers/snapshot-dependencies/ ./
COPY --from=build /build/layers/application/ ./
USER 10001:10001
# 8080 is the API; the management port (8090) is bound to loopback inside the container and never published.
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
