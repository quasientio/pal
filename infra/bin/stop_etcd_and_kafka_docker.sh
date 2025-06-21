#!/bin/sh

# Get bin directory, where this script is located
SCRIPTS_DIR="$(dirname "$(realpath "${BASH_SOURCE[0]}")")"

# Load common functions
source $SCRIPTS_DIR/docker_utils.sh

# Change to infra/ dir
cd "$SCRIPTS_DIR/.."

docker-compose \
	--project-directory docker \
	--file docker/compose/etcd-kafka-compose.yml \
	stop -t 2

removePalNetwork
