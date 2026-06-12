# Multi-stage build:
# Stage 1 compiles the kb-app jar, and Stage 2 only runs that jar.
# The final image does not contain source code or Maven, which keeps it smaller and cleaner.

FROM maven:3.9-eclipse-temurin-17 AS build

# Set the Maven build workspace inside the container.
WORKDIR /build

# Copy the parent POM first so Docker can cache dependency resolution when source files change.
COPY pom.xml .

# Copy module POM files before source code to maximize Docker layer cache reuse.
COPY kb-common/pom.xml kb-common/pom.xml
COPY kb-app/pom.xml kb-app/pom.xml
COPY kb-gateway/pom.xml kb-gateway/pom.xml

# Download Maven dependencies ahead of the real build to speed up later Docker builds.
RUN mvn dependency:go-offline

# Copy all project source after dependency caching layers are prepared.
COPY . .

# Build only kb-app and modules it depends on.
# -pl selects kb-app, -am also builds required dependent modules such as kb-common, so kb-gateway is not built.
# -DskipTests skips unit tests in the container image build stage to keep image builds fast.
RUN mvn -pl kb-app -am clean package -DskipTests

# Runtime uses jre-alpine instead of jdk because no compiler tools are needed after packaging.
# jre + alpine keeps the runtime image as small as possible.
FROM eclipse-temurin:17-jre-alpine AS runtime

# Set the application runtime workspace.
WORKDIR /app

# Set the container timezone; wrong timezone can make MySQL DATETIME values display incorrectly.
ENV TZ=Asia/Shanghai

# Copy the packaged application jar from the build stage into the runtime image.
COPY --from=build /build/kb-app/target/*.jar app.jar

# Create a dedicated non-root user and group for the Spring Boot process.
# Running as non-root limits the impact of a container escape or application compromise.
RUN addgroup -S spring && adduser -S spring -G spring

# Switch the application process to the non-root user.
USER spring

# Expose the internal Spring Boot application port.
EXPOSE 8081

# Docker healthcheck lets docker-compose use depends_on with condition: service_healthy,
# so downstream services can wait until this service is truly ready.
# start-period=60s avoids marking the container unhealthy while Spring Boot is still starting.
# Confirm spring-boot-starter-actuator is included in pom.xml so /actuator/health is available.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

# Start the Spring Boot application jar.
ENTRYPOINT ["java", "-jar", "app.jar"]
