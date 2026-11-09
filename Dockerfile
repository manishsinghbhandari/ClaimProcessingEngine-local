# Stage 1: Build with Maven
FROM eclipse-temurin:21-jdk-jammy AS build

RUN apt-get update && \
    apt-get install -y --no-install-recommends maven && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /usr/src/app

# Copy only the pom.xml to cache dependencies separately
COPY pom.xml .

# Download dependencies using cache mount for .m2 directory
RUN --mount=type=cache,target=/root/.m2 mvn dependency:resolve

# Copy the rest of the project and build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -Dmaven.test.skip=true

# Stage 2: Create the runtime image with the packaged JAR
FROM  eclipse-temurin:21-jre-alpine-3.22
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /usr/src/app/target/claims-1.0.0.jar eclaims.jar

COPY jar/elastic-apm-agent.jar apm-agent.jar

# Set environment variables for JAVA_OPTS (JVM options)
ENV JAVA_OPTS="\
-javaagent:/app/apm-agent.jar \
-Delastic.apm.service_name=EclaimsUat \
-Delastic.apm.environment=beta \
-Delastic.apm.server_url=http://102.37.140.215:8200 \
-Delastic.apm.secret_token=TDFYEexo6RcJE2TWUvumLWYfOdXPcj6JlytW8MWcki8pqQIoX0Oi366weIhGbt2K \
-Delastic.apm.application_packages=io.coredge.health.claims \
-Dlogging.level.io.camunda.zeebe.client.impl.ZeebeCallCredentials=ERROR"

# Expose ports
EXPOSE 8080 8681

# Run the JAR file
CMD ["sh", "-c", "java $JAVA_OPTS -jar eclaims.jar"]




