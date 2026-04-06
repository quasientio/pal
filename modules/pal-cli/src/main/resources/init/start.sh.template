#!/usr/bin/env bash
# Start PAL infrastructure services.
# Usage: ./start.sh [etcd|kafka]   (no argument starts all services)
set -euo pipefail
cd "$(dirname "$0")"
if [ $# -eq 0 ]; then
  docker compose up -d
  echo "PAL infrastructure started."
else
  docker compose up -d "$1"
  echo "$1 started."
fi
