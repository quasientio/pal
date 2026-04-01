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
# All PAL-specific env vars use the PAL_ prefix (except CLASSPATH which is standard Java).
# In production you may want these in a file.

export PAL_DIRECTORY=localhost:2379
export PAL_KAFKA_SERVERS=localhost:29092
export KAFKA_JMX_PORT=10121
export PAL_CHRONICLE_BASE_DIR=/tmp/chronicle-logs
# export CLASSPATH=
# export PAL_PEER_NAME=
# export PAL_PEER_UUID=
# export PAL_ZMQ_RPC=
# export PAL_JSON_RPC=
# export PAL_TCP_PUB=
# export PAL_LOG=
# export PAL_SOURCE_LOG=
# export PAL_WAL=
# export PAL_LOG_PREFIX=

# Source local-specific vars
[ -f "$PAL_HOME/.env.local" ] && source "$PAL_HOME/.env.local"
