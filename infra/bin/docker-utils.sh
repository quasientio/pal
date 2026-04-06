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

# Set VERBOSE to 0 to suppress output, 1 to enable output
VERBOSE=0

# Parse command-line arguments
while getopts "v" opt; do
  case $opt in
    v)
      VERBOSE=1
      ;;
    *)
      ;;
  esac
done

# Function to print messages when VERBOSE is enabled
log() {
  if [ "$VERBOSE" -eq 1 ]; then
    echo "$@"
  fi
}

check_var() {
    local var_name="$1"
    local var_value="${!var_name}"

    if [ -z "$var_value" ]; then
        echo "Error: $var_name is undefined"
        exit 1
    fi
}

# Check if the 'pal' network exists; create it if it doesn't
createPalNetwork() {
	if docker network inspect pal >/dev/null 2>&1; then
		log '`pal` network already exists - skipping creation'
	else
		docker network create pal --driver bridge >/dev/null
		log 'new `pal` network created'
	fi
}

removePalNetwork() {
	docker network rm --force pal
}

get_docker_host_name() {
    docker info --format '{{.Name}}'
}
