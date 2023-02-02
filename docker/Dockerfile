###################################################################
# Dockerfile
#
# Build:
#                   (run from $PAL_HOME)
#                   docker build --file docker/Dockerfile --tag registry.gitlab.com/cometera/pal/pal .
#    push           docker push registry.gitlab.com/cometera/pal/pal
#    tag            docker tag registry.gitlab.com/cometera/pal/pal:latest pal:latest
# Build Args:       extra_lib
# Run ENV-file:     SEE peer.env file
# Run Cmd Example:  docker run --network=host --name pal --rm --env-file peer.env pal
###################################################################

FROM openjdk:8u151-jre-alpine

ARG extra_lib

WORKDIR /app

COPY bin/wait-for .
COPY bin/pal .
RUN chmod +x pal wait-for
RUN mkdir lib/
COPY tools/target/pal-*.jar lib/

# workaround to copy $extra_lib if present
RUN mkdir libs/
RUN mkdir /throwaway
COPY pom.xml *jar /throwaway/
RUN cp /throwaway/$extra_lib* libs/ || true
RUN rm -rf /throwaway

ENTRYPOINT ["./pal"]
