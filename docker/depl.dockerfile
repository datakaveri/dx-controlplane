ARG VERSION="0.0.1-SNAPSHOT"

# Using maven base image in builder stage to build Java code.
FROM maven:3-eclipse-temurin-21 as builder

WORKDIR /usr/share/app
COPY pom.xml .

# Downloads all packages defined in pom.xml
RUN mvn clean package
COPY src src

# Copying openapi docs
COPY docs docs
COPY configs/config-depl.json ./config.json

# Build the source code to generate the fatjar
RUN mvn clean package -Dmaven.test.skip=true

# Install jq and replace BASE_URL in OpenAPI spec
RUN apt-get update && apt-get install -y jq && \
    BASE_URL=$(jq -r '.commonOptions.baseUrl // empty' config.json) && \
    BASE_URL=${BASE_URL:-example.com} && \
    APD_URL=$(jq -r '.commonOptions.apdURL // empty' config.json) && \
    APD_URL=${APD_URL:-example.com} && \
    sed -i "s|{{BASE_URL}}|${BASE_URL}|g" docs/openapi.yaml && \
    sed -i "s|{{BASE_URL}}|${APD_URL}|g" docs/openapi2.yaml && \
    rm -f config.json \

# Java Runtime as the base for final image
FROM eclipse-temurin:21-jre

ARG VERSION
ENV JAR="iudx.aaa.server-cluster-${VERSION}-fat.jar"

WORKDIR /usr/share/app

# Copying cluster fatjar from builder stage to final image
COPY --from=builder /usr/share/app/docs ./docs
COPY --from=builder /usr/share/app/target/${JAR} ./fatjar.jar

# Expose ports
EXPOSE 8080 8443

# Creating a non-root user
RUN useradd -r -u 1001 -g root aaa-user

# Setting non-root user to use when container starts
USER aaa-user
