###################################################################
# Dockerfile for Kafka in Kraft mode, based on openjdk alpine image.
#
# Build:
#    build          docker build \
#                     --build-arg SCALA_VERSION=2.13 \
#                     --build-arg KAFKA_VERSION=3.8.0 \
#                     --tag registry.gitlab.com/cometera/pal/kafka:3.8.0 \
#                     --tag registry.gitlab.com/cometera/pal/kafka:latest \
#                     --tag kafka:latest .
#
#    push           docker login registry.gitlab.com
#                   docker push --all-tags registry.gitlab.com/cometera/pal/kafka
#
# Build Args:
#                   SCALA_VERSION
#                   KAFKA_VERSION
#
# Usage example:
# -------------
# docker run --name kafka --publish 9092:9092 \
#            --mount type=bind,source=$PAL_HOME/config/kafka,target=/etc/kafka \
#            kafka format_start --config /etc/kafka/server.properties
#
###################################################################

# Use a Debian-based OpenJDK 11 image
FROM openjdk:11-jre-slim

# Set build-time variables
ARG SCALA_VERSION="2.13"
ARG KAFKA_VERSION="3.8.0"
ARG VERSION="$SCALA_VERSION-$KAFKA_VERSION"

# Install necessary packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    gnupg \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Download and import the KEYS file
RUN wget https://downloads.apache.org/kafka/KEYS \
    && gpg --import KEYS \
    && rm KEYS

# Set the working directory
WORKDIR /usr/local/lib

# Download Kafka binaries and signature
RUN wget -S https://downloads.apache.org/kafka/$KAFKA_VERSION/kafka_$VERSION.tgz \
    && wget https://downloads.apache.org/kafka/$KAFKA_VERSION/kafka_$VERSION.tgz.asc

# Verify the signature
RUN gpg --verify kafka_$VERSION.tgz.asc kafka_$VERSION.tgz

# Extract Kafka and clean up
RUN tar -xzf kafka_$VERSION.tgz \
    && rm kafka_$VERSION.tgz kafka_$VERSION.tgz.asc \
    && ln -s kafka_$VERSION kafka

# Add Kafka binaries to PATH
ENV PATH="$PATH:/usr/local/lib/kafka/bin"

# Set the working directory to Kafka installation
WORKDIR /usr/local/lib/kafka

# Copy the run script into the image
COPY run.sh .

# Expose Kafka port
EXPOSE 9092

# Set the entrypoint
ENTRYPOINT ["./run.sh"]
