#!/bin/bash
#
# Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2029-10-01
# Change License: Apache 2.0
#


if [ -z "$PAL_HOME" ]; then
  echo PAL_HOME is undefined
  exit 1
fi

# Provide default for PAL_DIRECTORY if not set
PAL_DIRECTORY="${PAL_DIRECTORY:-localhost:2379}"

# Provide default for KAFKA_SERVERS if not set
KAFKA_SERVERS="${KAFKA_SERVERS:-localhost:29092}"

# Set ITT_APPS_JAR if it's not already set
: "${ITT_APPS_JAR:=${PAL_HOME}/modules/itt-apps/target/itt-apps-*.jar}"

export PAL_PEER_LOGGING_CONFIG="${PAL_PEER_LOGGING_CONFIG:-"$PAL_HOME/config/peer-logging.xml"}"

pal.sh run \
  --dir $PAL_DIRECTORY \
  --name peer-for-itt \
  --zmq-rpc 5656 \
  --tcp-pub 8876 \
  --json-rpc 7789 \
  --rpc-threads 3 \
  --rpc-allow-nonpublic \
  --log auto \
  --log-prefix itt \
  --kafka-servers $KAFKA_SERVERS \
  --interceptable \
  --classpath $ITT_APPS_JAR
