#!/bin/bash
#
# Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2030-10-01
# Change License: Apache 2.0
#


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
