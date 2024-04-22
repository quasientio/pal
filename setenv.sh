#!/bin/sh

# Paths needed by some tools in bin/
export JLINE_HOME='/usr/share/java'
export KAFKA_HOME='/usr/local/lib/kafka'
export JAVA_HOME='/usr/lib/jvm/default'
export SJK_PATH=$PAL_HOME/peer/tools/lib/sjk-plus-0.5.1.jar

export PATH=$JAVA_HOME/bin:$PATH
