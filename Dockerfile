# syntax=docker/dockerfile:1

# ---- build stage ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached unless pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests need a Docker daemon (Testcontainers); CI runs them separately.
RUN mvn -B -q clean package -DskipTests

# Split the fat jar into layers for better runtime image caching.
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ---- runtime stage ------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Non-root user with a fixed uid/gid that the Kubernetes securityContext pins to.
RUN groupadd --gid 1000 app \
 && useradd --uid 1000 --gid app --shell /usr/sbin/nologin --create-home app

# Order matters: least-changing layers first.
COPY --from=build /build/target/extracted/dependencies/ ./
COPY --from=build /build/target/extracted/spring-boot-loader/ ./
COPY --from=build /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build /build/target/extracted/application/ ./

USER app
EXPOSE 8080

# Container-aware heap sizing; k8s sets the memory limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
