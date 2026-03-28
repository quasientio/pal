#
# Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Source this file

# Must for running Pal
export PAL_HOME="$(pwd)"
export PATH=$PAL_HOME/bin:$PAL_HOME/infra/bin:$PATH

# Optional
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
export CHRONICLE_BASE_DIR=/tmp/chronicle-logs
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

# Source local-specific vars
[ -f "$PAL_HOME/.env.local" ] && source "$PAL_HOME/.env.local"
