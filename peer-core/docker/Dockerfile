###################################################################
# Dockerfile
#
# Build Cmd:        docker build --pull --tag peer .
# Build Args:       (None)
# Run ENV-file:     SEE peer.env file
# Run Cmd Example:  docker run --network=host --name peer --rm --env-file peer.env peer
###################################################################

FROM openjdk:8u151-jre-alpine

MAINTAINER libre

WORKDIR /app

COPY bin/wait-for .
COPY bin/peer4docker peer
COPY target/peer-core-*.jar .

RUN chmod +x peer wait-for

CMD ./peer
