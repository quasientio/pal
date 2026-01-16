#!/bin/sh
#
# Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2030-10-01
# Change License: Apache 2.0
#


# 1) Unset all ETCD_* env vars except those we want to preserve
for v in $(env | grep '^ETCD_' | cut -d= -f1); do
  case "$v" in
    ETCD_CLIENT_PORT|ETCD_PEER_PORT|ETCD_DATA_DIR)
      # preserve user‐provided values
      ;;
    *)
      unset "$v"
      ;;
  esac
done

# 2) Default ports & data‐dir if not already defined
: "${ETCD_CLIENT_PORT:=2379}"
: "${ETCD_PEER_PORT:=2380}"
: "${ETCD_DATA_DIR:=/tmp/etcd/data}"

# 3) Export all etcd settings via env vars
export ETCD_NAME="paldir"
export ETCD_DATA_DIR="$ETCD_DATA_DIR"

export ETCD_CTL_API="3"
export ALLOW_NONE_AUTHENTICATION="yes"
export ETCD_LOG_LEVEL="info"

export ETCD_LISTEN_CLIENT_URLS="http://127.0.0.1:${ETCD_CLIENT_PORT}"
export ETCD_ADVERTISE_CLIENT_URLS="http://127.0.0.1:${ETCD_CLIENT_PORT}"
export ETCD_LISTEN_PEER_URLS="http://127.0.0.1:${ETCD_PEER_PORT}"

# 4) Start etcd with no flags—everything comes from the env
exec etcd
