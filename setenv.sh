WORKING_DIR=`pwd`
MVN_REPO=~/.m2/repository

CONF_PATH=$WORKING_DIR/conf
KAFKA_PATH=$MVN_REPO/org/apache/kafka/kafka-clients/0.11.0.0/kafka-clients-0.11.0.0.jar
SLF4J_PATH=$MVN_REPO/org/slf4j/slf4j-api/1.7.21/slf4j-api-1.7.21.jar:$MVN_REPO/org/apache/logging/log4j/log4j-slf4j-impl/2.0.2/log4j-slf4j-impl-2.0.2.jar
LOG4J_PATH=$MVN_REPO/org/apache/logging/log4j/log4j-core/2.6.2/log4j-core-2.6.2.jar:$MVN_REPO/org/apache/logging/log4j/log4j-api/2.6.2/log4j-api-2.6.2.jar
ASPECTJ_PATH=$MVN_REPO/org/aspectj/aspectjrt/1.8.9/aspectjrt-1.8.9.jar
PROTOBUF_PATH=$MVN_REPO/com/google/protobuf/protobuf-java/3.3.0/protobuf-java-3.3.0.jar
JEROMQ_PATH=$MVN_REPO/org/zeromq/jeromq/0.3.6/jeromq-0.3.6.jar
GUAVA_PATH=$MVN_REPO/com/google/guava/guava/19.0/guava-19.0.jar
GUICE_PATH=$MVN_REPO/com/google/inject/guice/4.1.0/guice-4.1.0-no_aop.jar
JAVAX_INJECT_PATH=$MVN_REPO/javax/inject/javax.inject/1/javax.inject-1.jar
COMMONS_PATH=$MVN_REPO/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar
ZOOKEEPER_PATH=$MVN_REPO/org/apache/zookeeper/zookeeper/3.4.10/zookeeper-3.4.10.jar
COMPILED_CLASSES=$WORKING_DIR/target/classes:$WORKING_DIR/target/test-classes
SJK_PATH=$WORKING_DIR/tools/lib/sjk-plus-0.5.1.jar

CLASSPATH=$COMPILED_CLASSES:$ASPECTJ_PATH:$GUAVA_PATH:$GUICE_PATH:$JAVAX_INJECT_PATH:$PROTOBUF_PATH:$KAFKA_PATH:$JEROMQ_PATH:$SLF4J_PATH:$LOG4J_PATH:$COMMONS_PATH:$ZOOKEEPER_PATH:$CONF_PATH

export CLASSPATH
export SJK_PATH
export PATH=$WORKING_DIR/bin:$PATH
export KAFKA_HOME='/usr/local/lib/kafka'
