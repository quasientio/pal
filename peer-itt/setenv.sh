WORKING_DIR=`pwd`

MVN_REPO=~/.m2/repository

COMMONS_PATH=$MVN_REPO/org/apache/commons/commons-lang3/3.9/commons-lang3-3.9.jar
ASPECTJ_PATH=$MVN_REPO/org/aspectj/aspectjrt/1.9.4/aspectjrt-1.9.4.jar
ASPECTJ_TOOLS=/usr/local/lib/aspectj/lib/aspectjtools.jar
CORE_CLASSES=$MVN_REPO/com/ittera/cometa/peer-core/1.0-SNAPSHOT/peer-core-1.0-SNAPSHOT.jar
COMMON_CLASSES=$MVN_REPO/com/ittera/cometa/peer-common/1.0-SNAPSHOT/peer-common-1.0-SNAPSHOT.jar

CLASSPATH=$CLASSPATH:$ASPECTJ_PATH:$ASPECTJ_TOOLS:$COMMONS_PATH:$CORE_CLASSES:$COMMON_CLASSES

export CLASSPATH
export ASPECTJ_HOME='/usr/local/lib/aspectj'
export PATH=$WORKING_DIR/bin:$JAVA_HOME/bin:$PATH

export ZOOKEEPER_URL=localhost:2181
