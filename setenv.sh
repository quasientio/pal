#!/bin/sh

WORKING_DIR=`pwd`

#############
# PATH exports
#
export PAL_HOME=$WORKING_DIR

# Paths needed by some tools in bin/
export JLINE_HOME='/usr/share/java'
export KAFKA_HOME='/usr/local/lib/kafka'
export SJK_PATH=$PAL_HOME/peer/tools/lib/sjk-plus-0.5.1.jar

# Add bin folders to path
export PATH=bin:$PAL_HOME/peer/bin:$JAVA_HOME/bin:$PATH
