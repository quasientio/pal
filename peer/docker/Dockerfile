###################################################################
# Dockerfile
#
# Build:
#    build          docker build --pull --tag registry.gitlab.com/cometera/pal/peer .
#    push           docker push registry.gitlab.com/cometera/pal/peer
#    tag            docker tag registry.gitlab.com/cometera/pal/peer:latest peer:latest
# Build Args:       extra_lib
# Run ENV-file:     SEE peer.env file
# Run Cmd Example:  docker run --network=host --name peer --rm --env-file peer.env peer
###################################################################

FROM openjdk:8u151-jre-alpine

ARG extra_lib

WORKDIR /app

COPY bin/wait-for .
COPY bin/peer .
RUN chmod +x peer wait-for
COPY target/peer-*.jar .

# workaround to copy $extra_lib if present
RUN mkdir libs/
RUN mkdir /throwaway
COPY pom.xml *jar /throwaway/
RUN cp /throwaway/$extra_lib* libs/ || true
RUN rm -rf /throwaway

ENTRYPOINT ["./peer"]
