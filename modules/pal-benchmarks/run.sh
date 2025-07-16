#!/bin/sh
#
# Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2029-10-01
# Change License: Apache 2.0
#



#VARIANT=NOOP
#VARIANT=INTERCEPTS
VARIANT=PUB
#VARIANT=WAL
#VARIANT=PUB_WAL
#VARIANT=INTERCEPTS_PUB
#VARIANT=INTERCEPTS_WAL
#VARIANT=INTERCEPTS_PUB_WAL

PUB_QUEUE_TYPE=CHUNKED
#PUB_QUEUE_TYPE=FIXED
#PUB_QUEUE_TYPE=GROWABLE
#PUB_QUEUE_TYPE=UNBOUNDED
#PUB_QUEUE_TYPE=ZMQ_REP


#  -p pubQueueType="FIXED,CHUNKED,GROWABLE,UNBOUNDED" \

# full hot path
java -Dpeer.logging=$PAL_HOME/config/peer-logging.xml \
  -jar target/pal-benchmarks-1.0.0-SNAPSHOT.jar \
  SendExecMessageUsingMPSCBenchmark \
  -p variant="${VARIANT}" \
  -p pubQueueType="ZMQ_REP,FIXED,CHUNKED,GROWABLE,UNBOUNDED" \
  -t 8
