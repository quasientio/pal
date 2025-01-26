#!/bin/sh

# Get bin directory, where this script is located
SCRIPTS_DIR="$(dirname "$(realpath "${BASH_SOURCE[0]}")")"

# Load common functions
source "$SCRIPTS_DIR/docker_utils.sh"

check_var "PAL_AUTOMATE"

# Load env variables
source "$PAL_AUTOMATE/docker/.env"

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
DOCKER_RUN_OPTS=""

# Check if KAFKA_JMX_PORT is set and is a valid integer
if [ -n "$KAFKA_JMX_PORT" ] && [ "$KAFKA_JMX_PORT" -eq "$KAFKA_JMX_PORT" ] 2>/dev/null; then
  # Valid integer; include JMX options
  DOCKER_RUN_OPTS="$DOCKER_RUN_OPTS --env JMX_PORT=$KAFKA_JMX_PORT --publish $KAFKA_JMX_PORT:$KAFKA_JMX_PORT"
  log "Setting JMX_PORT to $KAFKA_JMX_PORT"
else
  log "KAFKA_JMX_PORT not set or not a valid integer; skipping JMX_PORT configuration"
fi

# If KAFKA_DATA_DIR is set, mount the directory
if [ -n "$KAFKA_DATA_DIR" ]; then
  # ensure the directory exists on the host
  mkdir -p "$KAFKA_DATA_DIR"

  # Add volume mount to Docker run options
  DOCKER_RUN_OPTS="$DOCKER_RUN_OPTS \
    -v $KAFKA_DATA_DIR:/var/lib/kafka/data \
    -e KAFKA_LOG_DIRS=/var/lib/kafka/data \
    -e KAFKA_PROCESS_ROLES=broker,controller \
    -e KAFKA_NODE_ID=1 \
    -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093 \
    -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
    -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
    -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
    -e KAFKA_LOG_DIRS=/var/lib/kafka/data \
    -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
    -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1"
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
  $DOCKER_RUN_OPTS apache/kafka:3.8.0)

log "Kafka container started with ID: $CONTAINER_ID"
