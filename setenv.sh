
WORKING_DIR=`pwd`

#############
# PATH exports
#
export PEER_CORE_HOME=$WORKING_DIR/core
export PEER_TOOLS_HOME=$WORKING_DIR/tools

# Paths needed by some tools in bin/
export JLINE_HOME='/usr/share/java'
export KAFKA_HOME='/usr/local/lib/kafka'
export SJK_PATH=$PEER_CORE_HOME/tools/lib/sjk-plus-0.5.1.jar

# Add bin folder to path
export PATH=$PEER_CORE_HOME/bin:$PEER_TOOLS_HOME/bin:$JAVA_HOME/bin:$PATH

#############
# Aliases to work with peer (in and outside containers)
#
# kills peer process when running as jar (peer script)
alias k='pkill -9 -f core'

# kills docker instance running peer process 
alias kd='docker kill peer'

alias dp='docker run --network peers --publish 5671:5671 --rm --name peer peer'
alias da='docker exec -ti $(docker ps -f name=^/peer$ -q) /bin/sh'
