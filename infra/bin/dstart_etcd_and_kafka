#!/bin/sh

# Get bin directory, where this script is located
SCRIPTS_DIR="$(dirname "$(realpath "${BASH_SOURCE[0]}")")"

# Load common functions
source $SCRIPTS_DIR/docker_utils.sh

check_var "PAL_AUTOMATE"

createPalNetwork

compose="docker-compose \
  --project-directory $PAL_AUTOMATE/docker \
	-f $PAL_AUTOMATE/docker/etcd-kafka-compose.yml"

$compose rm -f
$compose up --build --detach
