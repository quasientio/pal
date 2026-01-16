#
# Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2030-10-01
# Change License: Apache 2.0
#

# Instructions to set up Kafka w/ Kraft before 1st run (https://kafka.apache.org/documentation/#quickstart)

if [ -z "$KAFKA_HOME" ]; then
  echo "Error: KAFKA_HOME is undefined"
  exit 1
fi

# Generate a Cluster UUID
KAFKA_CLUSTER_ID="$($KAFKA_HOME/bin/kafka-storage.sh random-uuid)"

# Format Log Directories
$KAFKA_HOME/bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c $PAL_HOME/config/kafka/server.properties
