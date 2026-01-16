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


KAFKA_CONFIG="$PAL_HOME/config/kafka/server.properties"

if [ -z "$KAFKA_HOME" ]; then
  echo "Error: KAFKA_HOME is undefined"
  exit 1
fi

# Start Kafka
$KAFKA_HOME/bin/kafka-server-start.sh $KAFKA_CONFIG
