WORKING_DIR=`pwd`

MVN_REPO=~/.m2/repository

COMMONS_PATH=$MVN_REPO/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar
ASPECTJ_PATH=$MVN_REPO/org/aspectj/aspectjrt/1.8.9/aspectjrt-1.8.9.jar
ASPECTJ_TOOLS=/usr/local/lib/aspectj/lib/aspectjtools.jar
CORE_CLASSES=$MVN_REPO/com/ittera/cometa/peer-core/1.0-SNAPSHOT/peer-core-1.0-SNAPSHOT.jar

CLASSPATH=$CLASSPATH:$ASPECTJ_PATH:$ASPECTJ_TOOLS:$COMMONS_PATH:$CORE_CLASSES

export CLASSPATH
export ASPECTJ_HOME='/usr/local/lib/aspectj'
export PATH=$WORKING_DIR/bin:$PATH
