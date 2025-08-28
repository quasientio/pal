#!/usr/bin/env bash
#
# Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2029-10-01
# Change License: Apache 2.0
#


# palind – run “pal run …” inside a Docker container
#
# Supports both class-path (-c / -cp / --classpath) and runnable jars (-jar <file>),
# and automatically maps ports specified via -p/--tcp-pub, -r/--zmq-rpc, -j/--json-rpc.
#
# Example:
#   palind run \
#          --dir etcd:2379 \
#          --kafka-servers kafka:9092 \
#          --name peer_one \
#          --zmq-rpc 6678 \
#          --json-rpc 6679 \
#          -cp "$(pwd)/target/classes" \
#          com.quasient.pal.examples.HelloWorld

###############################################################################
# 0. helper paths / functions
###############################################################################
SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPTS_DIR/docker_utils.sh"        # createPalNetwork, log, …

IMAGE="pal"          # adjust if you pushed the image under another tag/registry
NETWORK="pal"
CONTAINER="peer"

# Predicate: does this path live on the host FS?
needs_mount() { [[ "$1" =~ ^/ || "$1" =~ ^\./ || "$1" =~ .*/ ]]; }

###############################################################################
# 1. scan argv; collect mounts, ports, and rewrite paths for inside the container
###############################################################################
declare -a VOLUMES PORTS NEW_ARGS
cpIndex=0

add_port() {         # avoid duplicate -p <port>:<port> entries
  for entry in "${PORTS[@]}"; do
    [[ "$entry" == "-p" ]] && continue        # skip flags, check only "<host>:<cont>"
    [[ "$entry" == "$1:$1" ]] && return
  done
  PORTS+=("-p" "$1:$1")
}

i=1
while (( i <= $# )); do
  arg="${!i}"
  case "$arg" in
    -c|-cp|--classpath)
      flag="$arg"
      (( i++ )); cpSpec="${!i}"
      IFS=':' read -ra rawParts <<< "$cpSpec"
      mappedParts=()

      for hostPath in "${rawParts[@]}"; do
        if needs_mount "$hostPath"; then
          abs="$(realpath "$hostPath")"
          containerPath="/app/classes${cpIndex}"
          (( ${#rawParts[@]} == 1 )) && containerPath="/app/classes"
          VOLUMES+=("-v" "${abs}:${containerPath}:ro")
          mappedParts+=("$containerPath")
          ((cpIndex++))
        else
          mappedParts+=("$hostPath")
        fi
      done

      NEW_ARGS+=("$flag" "$(IFS=:; echo "${mappedParts[*]}")")
      ;;

    -jar)
      flag="$arg"
      (( i++ )); jarPath="${!i}"
      if needs_mount "$jarPath"; then
        abs="$(realpath "$jarPath")"
        base="$(basename "$abs")"
        containerJar="/app/jars/$base"
        VOLUMES+=("-v" "${abs}:${containerJar}:ro")
        NEW_ARGS+=("$flag" "$containerJar")
      else
        NEW_ARGS+=("$flag" "$jarPath")
      fi
      ;;

    -p|--tcp-pub|-r|--zmq-rpc|-j|--json-rpc)
      flag="$arg"
      (( i++ )); endpoint="${!i}"
      NEW_ARGS+=("$flag" "$endpoint")

      # If endpoint is not 'auto', extract port (HOST:PORT or PORT) and publish it
      if [[ "$endpoint" != auto ]]; then
        port="${endpoint##*:}"
        [[ "$port" =~ ^[0-9]+$ ]] && add_port "$port"
      fi
      ;;

    *)
      NEW_ARGS+=("$arg")
      ;;
  esac
  (( i++ ))
done

###############################################################################
# 2. application logs: ./logs by default (override with PAL_LOGS_DIR)
###############################################################################
PAL_LOGS_DIR="${PAL_LOGS_DIR:-$PWD/logs}"
mkdir -p "$PAL_LOGS_DIR"
VOLUMES+=("-v" "${PAL_LOGS_DIR}:/app/logs")

###############################################################################
# 3. launch container
###############################################################################
if docker ps -q -f "name=^/${CONTAINER}$" | grep -q .; then
  log "Container '${CONTAINER}' already running"
  exit 1
fi

docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
createPalNetwork

docker run --rm \
  --name "$CONTAINER" \
  --network "$NETWORK" \
  "${PORTS[@]}" \
  "${VOLUMES[@]}" \
  "$IMAGE" "${NEW_ARGS[@]}"
