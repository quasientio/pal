#
# Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

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
COPY utils/wait-for.sh /app/bin/
COPY lib/pal-*.jar /app/lib/

# Make the pal script executable
RUN chmod +x /app/bin/pal

# Add /app/bin to PATH for easy access to the pal command
ENV PATH="/app/bin:$PATH"

# Set the entrypoint to the pal script
ENTRYPOINT ["pal"]
