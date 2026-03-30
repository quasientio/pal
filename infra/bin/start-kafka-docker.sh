#!/bin/bash
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


# Get bin directory, where this script is located
SCRIPTS_DIR="$(dirname "$(realpath "${BASH_SOURCE[0]}")")"

# Load common functions
source "$SCRIPTS_DIR/docker-utils.sh"

# Change to infra/ dir
cd "$SCRIPTS_DIR/.."

# Load env variables
source "docker/.env"

# Check all required variables are defined
check_var "KAFKA_PORT"
check_var "KAFKA_HOST_PORT"
check_var "KAFKA_CONTROLLER_PORT"

# Check if a container named "kafka" is running
if [ "$(docker ps -q -f name=kafka)" ]; then
  log "kafka container already running"
  exit 1
fi

# Remove existing Kafka container if it exists (stopped container)
if [ "$(docker ps -a -q -f name=kafka)" ]; then
  docker container rm --volumes kafka >/dev/null 2>&1 || true
fi

createPalNetwork

# Initialize Docker run options
DOCKER_RUN_OPTS="\
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:${KAFKA_CONTROLLER_PORT} \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:${KAFKA_PORT},PLAINTEXT_HOST://0.0.0.0:${KAFKA_HOST_PORT},CONTROLLER://0.0.0.0:${KAFKA_CONTROLLER_PORT} \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:${KAFKA_PORT},PLAINTEXT_HOST://localhost:${KAFKA_HOST_PORT} \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,CONTROLLER:PLAINTEXT \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  -e KAFKA_NUM_PARTITIONS=1"

# Check if KAFKA_JMX_PORT is set and is a valid integer
if [ -n "$KAFKA_JMX_PORT" ] && [ "$KAFKA_JMX_PORT" -eq "$KAFKA_JMX_PORT" ] 2>/dev/null; then
  # Valid integer; include JMX options
  DOCKER_RUN_OPTS+=" \
     -e JMX_PORT=$KAFKA_JMX_PORT \
     --publish $KAFKA_JMX_PORT:$KAFKA_JMX_PORT"
  log "Setting JMX_PORT to $KAFKA_JMX_PORT"
else
  log "KAFKA_JMX_PORT not set or not a valid integer; skipping JMX_PORT configuration"
fi

# If KAFKA_DATA_DIR is set, mount the directory
if [ -n "$KAFKA_DATA_DIR" ]; then
  # ensure the directory exists on the host
  mkdir -p "$KAFKA_DATA_DIR"

  # Add volume mount to Docker run options
  DOCKER_RUN_OPTS+=" \
    -v $KAFKA_DATA_DIR:/var/lib/kafka/data \
    -e KAFKA_LOG_DIRS=/var/lib/kafka/data"
  log "Kafka data will be persisted in $KAFKA_DATA_DIR"
else
  log "KAFKA_DATA_DIR not set; running Kafka with ephemeral container storage"
fi

# Run the Kafka container with the specified options
CONTAINER_ID=$(docker run \
  --name kafka \
  --ulimit nofile=1024:1024 \
  --detach \
  --network pal \
  --publish "$KAFKA_PORT":"$KAFKA_PORT" \
  --publish "$KAFKA_HOST_PORT":"$KAFKA_HOST_PORT" \
  $DOCKER_RUN_OPTS apache/kafka:3.8.0)

log "Kafka container started with ID: $CONTAINER_ID"
