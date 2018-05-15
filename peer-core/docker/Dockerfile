FROM openjdk:8u151-jre-alpine

MAINTAINER libre

ARG zookeeper_url=zookeeper:2181

ENV PEER_LOGS_DIR=/app/logs \
    JMX_PORT=9012 \
    JMX_HOST=localhost \
    ZOOKEEPER_URL=$zookeeper_url

WORKDIR /app

COPY \
	bin/peer-docker \
	target/classes/logback.xml \
	target/classes/peer.properties \
	target/peer-core-1.0-SNAPSHOT-jar-with-dependencies.jar /app/


RUN mv /app/peer-docker /app/peer \
	&& chmod +x peer 

EXPOSE 5671

CMD ./peer
