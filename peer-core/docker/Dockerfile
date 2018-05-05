FROM openjdk:8u151-jre-alpine

MAINTAINER libre

WORKDIR /app

ENV PEER_LOGS_DIR=/app/mylogs
ENV JMX_PORT=9012
ENV JMX_HOST=localhost

COPY \
	bin/peerd \
	target/classes/logback.xml \
	target/classes/peer.properties \
	target/peer-core-1.0-SNAPSHOT-jar-with-dependencies.jar /app/


RUN chmod +x peerd

EXPOSE 5671

CMD ./peerd
