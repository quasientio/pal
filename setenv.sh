
WORKING_DIR=`pwd`

PEER_CORE_HOME=$WORKING_DIR/peer-core
PEER_RUNNER_HOME=$WORKING_DIR/peer-runner

# Paths needed by some tools in bin/
export JLINE_HOME='/usr/share/java'
export KAFKA_HOME='/usr/local/lib/kafka'
export SJK_PATH=$PEER_CORE_HOME/tools/lib/sjk-plus-0.5.1.jar

# Add bin folders to path
export PATH=$PEER_CORE_HOME/bin:$PEER_RUNNER_HOME/bin:$JAVA_HOME/bin:$PATH

# Some useful aliases to work with peer (in and outside containers)
alias k='pkill -9 -f Concentrator'
alias kd='docker kill peer'
alias dp='docker run --network peers --publish 5671:5671 --rm --name peer peer'
alias da='docker exec -ti $(docker ps -f name=^/peer$ -q) /bin/sh'

# ENV variable needed by peer, runner and integration tests (peer-itt)
export ZOOKEEPER_URL=localhost:2181
