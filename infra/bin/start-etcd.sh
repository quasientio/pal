#!/bin/sh
#
# Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
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
