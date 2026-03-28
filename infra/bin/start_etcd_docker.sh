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
source "$SCRIPTS_DIR/docker_utils.sh"

# Change to infra/ dir
cd "$SCRIPTS_DIR/.."

# Load env variables
source "docker/.env"

# Check all required variables are defined
check_var "ETCD_CLIENT_PORT"
check_var "ETCD_PEER_PORT"

# Check if a container named "etcd" is running
if [ "$(docker ps -q -f name=etcd)" ]; then
  log "etcd container already running"
  exit 1
fi

# Remove existing etcd container if it exists (stopped container)
if [ "$(docker ps -a -q -f name=etcd)" ]; then
  docker container rm --volumes etcd >/dev/null 2>&1 || true
fi

createPalNetwork

# Initialize extra run options
DOCKER_RUN_OPTS=""
ETCD_COMMAND="./etcd"  # default command inside the container image

# If user specifies a host directory for data, mount it and tell etcd to use it
if [ -n "$ETCD_DATA_DIR" ]; then
  mkdir -p "$ETCD_DATA_DIR"
  # Mount the host directory to /etcd-data in the container
  DOCKER_RUN_OPTS="$DOCKER_RUN_OPTS -v $ETCD_DATA_DIR:/etcd-data"
  # Use environment variable override so etcd writes to /etcd-data
  DOCKER_RUN_OPTS="$DOCKER_RUN_OPTS -e ETCD_DATA_DIR=/etcd-data"
  log "etcd data will be persisted in $ETCD_DATA_DIR on the host"
else
  log "ETCD_DATA_DIR not set; running etcd with ephemeral container storage"
fi

CONTAINER_ID=$(docker run \
  --name etcd \
  --detach \
  --network pal \
  --env NAME=default \
  --env ETCD_INITIAL_ADVERTISE_PEER_URLS="http://etcd:${ETCD_PEER_PORT}" \
  --env ETCD_LISTEN_PEER_URLS="http://0.0.0.0:${ETCD_PEER_PORT}" \
  --env ETCD_ADVERTISE_CLIENT_URLS="http://etcd:${ETCD_CLIENT_PORT}" \
  --env ETCD_LISTEN_CLIENT_URLS="http://0.0.0.0:${ETCD_CLIENT_PORT}" \
  --env ETCD_INITIAL_CLUSTER="default=http://etcd:${ETCD_PEER_PORT}" \
  --publish "${ETCD_CLIENT_PORT}:${ETCD_CLIENT_PORT}" \
  --publish "${ETCD_PEER_PORT}:${ETCD_PEER_PORT}" \
  $DOCKER_RUN_OPTS \
  etcd \
  $ETCD_COMMAND)

log "Etcd container started with ID: $CONTAINER_ID"
