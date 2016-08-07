MVN_REPO=~/.m2/repository
CONF_PATH=/home/libre/code/cometa/aj-impl/trunk/conf
LOG4J_PATH=$MVN_REPO/org/apache/logging/log4j/log4j-core/2.6.2/log4j-core-2.6.2.jar:$MVN_REPO/org/apache/logging/log4j/log4j-api/2.6.2/log4j-api-2.6.2.jar
CLASSPATH=/usr/local/lib/aspectj/lib/aspectjrt.jar:/usr/local/lib/aspectj/lib/aspectjtools.jar:/home/libre/code/cometa/aj-impl/trunk/target/classes:$LOG4J_PATH:$CONF_PATH
export CLASSPATH
