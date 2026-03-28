#!/usr/bin/env bash
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

# ---------------------------------------------------------------------------
#  run_k8s.sh  –  start / switch / stop a Minikube-backed Kustomize profile
#
#  Usage:  ./run_k8s.sh  <dev|prod|stop>
# ---------------------------------------------------------------------------

set -euo pipefail

##############################################################################
# 0. Globals
##############################################################################
EXPECTED_IP="192.168.49.2"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KUSTOMIZE_ROOT="$(realpath "$SCRIPT_DIR/../k8s/kustomize")"   # infra/ is parent of script

##############################################################################
# 1. Parse CLI argument
##############################################################################
ACTION="${1:-}"
case "$ACTION" in
  dev|prod|stop) ;;                       # OK
  *) echo "Usage: $(basename "$0") <dev|prod|stop>"; exit 1 ;;
esac

##############################################################################
# 2. Verify required binaries
##############################################################################
for bin in minikube kubectl; do
  command -v "$bin" >/dev/null 2>&1 || { echo "❌  '$bin' not in PATH." >&2; exit 1; }
done

##############################################################################
# 3. Ensure Minikube is running (create or start as needed)
##############################################################################
if ! minikube status >/dev/null 2>&1; then
  echo "Creating Minikube cluster ..."
  minikube start --driver=docker
else
  HOST_STATE=$(minikube status --format='{{.Host}}')
  if [[ "$HOST_STATE" != "Running" ]]; then
    echo "Starting existing Minikube cluster ..."
    minikube start
  fi
fi

##############################################################################
# 4. Validate IP
##############################################################################
ACTUAL_IP=$(minikube ip)
if [[ "$ACTUAL_IP" != "$EXPECTED_IP" ]]; then
  echo "Minikube IP '$ACTUAL_IP' ≠ expected '$EXPECTED_IP' – aborting."
  exit 1
fi

##############################################################################
# 5. Helper: which env is deployed right now?
##############################################################################
current_env() {
  for env in dev prod; do
    # -A = --all-namespaces
    if kubectl get deploy,statefulset -A -l env="$env" -o name --no-headers 2>/dev/null | grep -q .; then
      echo "$env"
      return
    fi
  done
  echo ""          # none found
}

##############################################################################
# 6. Handle 'stop'
##############################################################################
if [[ "$ACTION" == "stop" ]]; then
  CUR=$(current_env)
  if [[ -z "$CUR" ]]; then
    echo "Nothing to stop – no dev/prod objects found."
  else
    echo "Deleting '$CUR' overlay ..."
    kubectl delete -k "$KUSTOMIZE_ROOT/overlays/$CUR"
  fi
  exit 0
fi

##############################################################################
# 7. Start or switch to requested env
##############################################################################
CUR=$(current_env)

if [[ "$CUR" == "$ACTION" ]]; then
  echo "'$ACTION' overlay is already running – nothing to do."
  exit 0
fi

if [[ -n "$CUR" ]]; then
  echo "Switching from '$CUR' to '$ACTION' ..."
  kubectl delete -k "$KUSTOMIZE_ROOT/overlays/$CUR"
  echo "Waiting for '$CUR' objects to disappear ..."
  kubectl wait --for=delete deploy,statefulset,svc -l env="$CUR" -A --timeout=60s || true
else
  echo "Starting '$ACTION' overlay ..."
fi

kubectl apply -k "$KUSTOMIZE_ROOT/overlays/$ACTION"

echo "'$ACTION' environment is now active."

