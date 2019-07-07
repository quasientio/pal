
# Set up classpath
WORKING_DIR=`pwd`
MVN_REPO=~/.m2/repository

CONF_PATH=$WORKING_DIR/conf
KAFKA_PATH=$MVN_REPO/org/apache/kafka/kafka-clients/2.3.0/kafka-clients-2.3.0.jar
SLF4J_PATH=$MVN_REPO/org/slf4j/slf4j-api/1.7.21/slf4j-api-1.7.21.jar
LOGBACK_PATH=$MVN_REPO/ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.jar:$MVN_REPO/ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3.jar
ASPECTJ_PATH=$MVN_REPO/org/aspectj/aspectjrt/1.9.4/aspectjrt-1.9.4.jar
PROTOBUF_PATH=$MVN_REPO/com/google/protobuf/protobuf-java/3.8.0/protobuf-java-3.8.0.jar
JEROMQ_PATH=$MVN_REPO/org/zeromq/jeromq/0.5.1/jeromq-0.5.1.jar
GUAVA_PATH=$MVN_REPO/com/google/guava/guava/19.0/guava-19.0.jar
GUICE_PATH=$MVN_REPO/com/google/inject/guice/4.1.0/guice-4.1.0-no_aop.jar
ORGJSON_PATH=$MVN_REPO/org/json/json/20180813/json-20180813.jar
JAVAX_INJECT_PATH=$MVN_REPO/javax/inject/javax.inject/1/javax.inject-1.jar
COMMONS_PATH=$MVN_REPO/org/apache/commons/commons-lang3/3.9/commons-lang3-3.9.jar:$MVN_REPO/commons-cli/commons-cli/1.4/commons-cli-1.4.jar
ZOOKEEPER_PATH=$MVN_REPO/org/apache/zookeeper/zookeeper/3.4.14/zookeeper-3.4.14.jar
COMPILED_CLASSES=$WORKING_DIR/target/classes
SJK_PATH=$WORKING_DIR/tools/lib/sjk-plus-0.5.1.jar

PEER_COMMON_PATH=$MVN_REPO/com/ittera/cometa/peer-common/1.0-SNAPSHOT/peer-common-1.0-SNAPSHOT.jar
PEER_SERDES_PATH=$MVN_REPO/com/ittera/cometa/peer-serdes/1.0-SNAPSHOT/peer-serdes-1.0-SNAPSHOT.jar
PEER_CXN_PATH=$MVN_REPO/com/ittera/cometa/peer-cxn/1.0-SNAPSHOT/peer-cxn-1.0-SNAPSHOT.jar
PEER_ITT_APPS_PATH=$MVN_REPO/com/ittera/cometa/peer-itt-apps/1.0-SNAPSHOT/peer-itt-apps-1.0-SNAPSHOT.jar
PEER_AJ_PATH=$MVN_REPO/com/ittera/cometa/peer-aj/1.0-SNAPSHOT/peer-aj-1.0-SNAPSHOT.jar

CLASSPATH=$PEER_ITT_APPS_PATH:$COMPILED_CLASSES:$ASPECTJ_PATH:$GUAVA_PATH:$ORGJSON_PATH:$GUICE_PATH:$JAVAX_INJECT_PATH:$PROTOBUF_PATH:$KAFKA_PATH:$JEROMQ_PATH:$SLF4J_PATH:$LOGBACK_PATH:$COMMONS_PATH:$ZOOKEEPER_PATH:$PEER_COMMON_PATH:$PEER_SERDES_PATH:$PEER_CXN_PATH:$PEER_AJ_PATH

# Export classpath and other path variables
export CLASSPATH
export SJK_PATH
export PATH=$WORKING_DIR/bin:$JAVA_HOME/bin:$PATH
export KAFKA_HOME='/usr/local/lib/kafka'
export JLINE_HOME='/usr/share/java'

# Some usefull aliases to work with peer (in and outside containers)
alias k='pkill -9 -f Concentrator'
alias kd='docker kill peer'
alias dp='docker run --network peers --publish 5671:5671 --rm --name peer peer'
alias da='docker exec -ti $(docker ps -f name=^/peer$ -q) /bin/sh'
