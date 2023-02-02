###################################################################
# Dockerfile for Kafka in Kraft mode, based on openjdk alpine image.
#
# Build:
#    build          docker build --tag registry.gitlab.com/cometera/pal/kafka:latest --tag kafka:latest .
#    push           docker login registry.gitlab.com
#                   docker push registry.gitlab.com/cometera/pal/kafka:latest
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

FROM openjdk:8u151-jre-alpine

ARG SCALA_VERSION="2.13"
ARG KAFKA_VERSION="3.4.0"
ARG VERSION="$SCALA_VERSION-$KAFKA_VERSION"

# developer keys downloaded from https://downloads.apache.org/kafka/KEYS)
ADD KEYS .

# install gnupg and import keys so we can verify binary signature
RUN apk add --no-cache gnupg
RUN gpg --import KEYS
RUN rm KEYS

# download & unpack kafka binaries
RUN apk update
RUN apk add wget

WORKDIR /usr/local/lib
RUN wget -S https://downloads.apache.org/kafka/$KAFKA_VERSION/kafka_$VERSION.tgz
RUN wget https://downloads.apache.org/kafka/$KAFKA_VERSION/kafka_$VERSION.tgz.asc
RUN gpg --verify kafka_$VERSION.tgz.asc kafka_$VERSION.tgz
RUN tar zxvf kafka_$VERSION.tgz
RUN rm kafka_$VERSION.tgz kafka_$VERSION.tgz.asc
RUN ln -s kafka_$VERSION kafka

# kafka scripts require bash
RUN apk add bash
RUN echo "export PATH=$PATH:/usr/local/lib/kafka/bin" >> ~/.bashrc

WORKDIR /usr/local/lib/kafka
ADD run.sh .

EXPOSE 9092

ENTRYPOINT [ "./run.sh" ]
