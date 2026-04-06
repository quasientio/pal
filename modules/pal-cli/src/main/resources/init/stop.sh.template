#!/usr/bin/env bash
# Stop and remove PAL infrastructure containers (data is not persisted).
# To keep data across restarts, replace "down" with "stop" below.
# Usage: ./stop.sh [etcd|kafka]   (no argument stops all services)
set -euo pipefail
cd "$(dirname "$0")"
if [ $# -eq 0 ]; then
  docker compose down
  echo "PAL infrastructure stopped."
else
  docker compose down "$1"
  echo "$1 stopped."
fi
