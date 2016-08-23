MVN_REPO=~/.m2/repository
CONF_PATH=/home/libre/code/cometa/aj-impl/trunk/conf
KAFKA_PATH=$MVN_REPO/org/apache/kafka/kafka-clients/0.10.0.0/kafka-clients-0.10.0.0.jar
SLF4J_PATH=$MVN_REPO/org/slf4j/slf4j-api/1.7.21/slf4j-api-1.7.21.jar
LOG4J_PATH=$MVN_REPO/org/apache/logging/log4j/log4j-core/2.6.2/log4j-core-2.6.2.jar:$MVN_REPO/org/apache/logging/log4j/log4j-api/2.6.2/log4j-api-2.6.2.jar
ASPECTJ_PATH=$MVN_REPO/org/aspectj/aspectjrt/1.8.9/aspectjrt-1.8.9.jar
PROTOBUF_PATH=$MVN_REPO/com/google/protobuf/protobuf-java/2.6.1/protobuf-java-2.6.1.jar
#CLASSPATH=/usr/local/lib/aspectj/lib/aspectjrt.jar:/usr/local/lib/aspectj/lib/aspectjtools.jar:/home/libre/code/cometa/aj-impl/trunk/target/classes:$LOG4J_PATH:$CONF_PATH
CLASSPATH=/home/libre/code/cometa/aj-impl/trunk/target/classes:$ASPECTJ_PATH:$PROTOBUF_PATH:$KAFKA_PATH:$SLF4J_PATH:$LOG4J_PATH:$CONF_PATH
export CLASSPATH
