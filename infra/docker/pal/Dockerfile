###################################################################
# Dockerfile
#
# Build:
#                   docker build --tag $CI_REGISTRY/quasient/pal/pal .
#    push           docker push $CI_REGISTRY/quasient/pal/pal
#    tag            docker tag $CI_REGISTRY/quasient/pal/pal:latest pal:latest
# Build Args:       NONE
# Run ENV-file:     SEE peer.env file
# Run Cmd Example:  docker run --network=host --name pal --rm --env-file peer.env pal
###################################################################

# Use eclipse-temurin slim as the base image
FROM eclipse-temurin:17-jdk-jammy

# Set the working directory in the image
WORKDIR /app

# Install ping and netcat, then clean up apt caches
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      iputils-ping \
      netcat-openbsd \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

# Create necessary directories in the image
RUN mkdir -p /app/bin /app/lib

# Copy files from the build environment's PAL_HOME to the image
COPY bin/pal /app/bin/
COPY bin/wait-for /app/bin/
COPY lib/pal-*.jar /app/lib/

# Make the pal script executable
RUN chmod +x /app/bin/pal

# Add /app/bin to PATH for easy access to the pal command
ENV PATH="/app/bin:$PATH"

# Set the entrypoint to the pal script
ENTRYPOINT ["pal"]
