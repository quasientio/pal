#
# Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2029-10-01
# Change License: Apache 2.0
#


# Source this file

# Must for running Pal
export PAL_HOME=`pwd`
export PATH=$PAL_HOME/bin:$PAL_HOME/infra/bin:$PATH

# Optional
export CI_REGISTRY=gitlab.cometera.org:5050
export PAL_JMX_HOST=localhost
export PAL_JMX_PORT=9012
export PAL_PEER_LOGGING_CONFIG=$PAL_HOME/.local/conf/peer-logging.xml
export PAL_CLI_LOGGING_CONFIG=$PAL_HOME/.local/conf/cli-logging.xml
# export NO_COLOR=

# The following properties can be given as args to the `pal` command.
# In production you may want these in a file.

export PAL_DIRECTORY=localhost:2379
export KAFKA_SERVERS=localhost:29092
export KAFKA_JMX_PORT=10121
# export CLASSPATH=
# export PEER_NAME=
# export PEER_UUID=
# export ZMQ_RPC=
# export JSON_RPC=
# export TCP_PUB=
# export LOG=
# export SOURCE_LOG=
# export WAL=
# export LOG_PREFIX=
