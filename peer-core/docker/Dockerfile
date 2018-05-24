FROM openjdk:8u151-jre-alpine

MAINTAINER libre

ARG zookeeper_url=zookeeper:2181
ARG jmx_host
ARG jmx_port
ARG logs_dir=/app/logs

ENV PEER_LOGS_DIR=$logs_dir \
    JMX_HOST=$jmx_host \
    JMX_PORT=$jmx_port \
    ZOOKEEPER_URL=$zookeeper_url

WORKDIR /app

COPY \
	bin/peer4docker \
	bin/wait-for \
	target/classes/logback.xml \
	target/peer-core-1.0-SNAPSHOT-jar-with-dependencies.jar /app/


RUN mv /app/peer4docker /app/peer \
	&& chmod +x peer 

EXPOSE 5671

CMD ./peer
