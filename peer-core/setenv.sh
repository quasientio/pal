
WORKING_DIR=`pwd`

# Paths needed by some tools in bin/
export JLINE_HOME='/usr/share/java'
export SJK_PATH=$WORKING_DIR/tools/lib/sjk-plus-0.5.1.jar
export KAFKA_HOME='/usr/local/lib/kafka'

# Add bin folders to path
export PATH=$WORKING_DIR/bin:$WORKING_DIR/../peer-runner/bin:$JAVA_HOME/bin:$PATH

# Some usefull aliases to work with peer (in and outside containers)
alias k='pkill -9 -f Concentrator'
alias kd='docker kill peer'
alias dp='docker run --network peers --publish 5671:5671 --rm --name peer peer'
alias da='docker exec -ti $(docker ps -f name=^/peer$ -q) /bin/sh'

# For convenience; at least needed for running integration tests (peer-itt)
export ZOOKEEPER_URL=localhost:2181
