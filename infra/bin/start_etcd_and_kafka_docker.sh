#!/bin/sh

# Get bin directory, where this script is located
SCRIPTS_DIR="$(dirname "$(realpath "${BASH_SOURCE[0]}")")"

# Load common functions
source $SCRIPTS_DIR/docker_utils.sh

check_var "PAL_AUTOMATE"

# Load env variables
source "$PAL_AUTOMATE/docker/.env"

# Check all required variables are defined
check_var "ETCD_CLIENT_PORT"
check_var "ETCD_PEER_PORT"
check_var "KAFKA_PORT"
check_var "KAFKA_HOST_PORT"
check_var "KAFKA_CONTROLLER_PORT"

# Check if a container named "etcd" is running
etcd_running_ids=$(docker ps -q -f name=etcd)
if [ -n "$etcd_running_ids" ]; then
  log "A etcd container is already running: $etcd_running_ids"
  exit 1
fi

# Remove existing etcd container if it exists (stopped container)
etcd_ids=$(docker ps -a -q -f name=etcd)
if [ -n "$etcd_ids" ]; then
  log "Removing existing etcd container(s): $etcd_ids"
  docker container rm --volumes $etcd_ids >/dev/null 2>&1 || true
fi

# Check if a container named "kafka" is running
kafka_running_ids=$(docker ps -q -f name=kafka)
if [ -n "$kafka_running_ids" ]; then
  log "A kafka container is already running: $kafka_running_ids"
  exit 1
fi

# Remove existing Kafka container if it exists (stopped container)
kafka_ids=$(docker ps -a -q -f name=kafka)
if [ -n "$kafka_ids" ]; then
  log "Removing existing kafka container(s): $kafka_ids"
  docker container rm --volumes $kafka_ids >/dev/null 2>&1 || true
fi


createPalNetwork

compose="docker-compose \
  --project-directory $PAL_AUTOMATE/docker \
  -f $PAL_AUTOMATE/docker/compose/etcd-kafka-compose.yml"

# Add JMX override if requested
if [ -n "${KAFKA_JMX_PORT:-}" ]; then
  compose+=" -f $PAL_AUTOMATE/docker/compose/overrides/etcd-kafka-jmx.yml"
fi

# Add host-data override if requested
if [ -n "${KAFKA_DATA_DIR:-}" ]; then
  compose+=" -f $PAL_AUTOMATE/docker/compose/overrides/etcd-kafka-data.yml"
fi


$compose rm -f
$compose up --build --detach
