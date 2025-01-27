###################################################################
# Dockerfile
#
# Build:
#                   docker build --tag registry.gitlab.com/cometera/pal/pal .
#    push           docker push registry.gitlab.com/cometera/pal/pal
#    tag            docker tag registry.gitlab.com/cometera/pal/pal:latest pal:latest
# Build Args:       NONE
# Run ENV-file:     SEE peer.env file
# Run Cmd Example:  docker run --network=host --name pal --rm --env-file peer.env pal
###################################################################

# Use OpenJDK 17 slim as the base image
FROM openjdk:17-jdk-slim

# Set the working directory in the image
WORKDIR /app

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
