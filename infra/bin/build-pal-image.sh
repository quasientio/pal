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


PUSH_IMAGE=true

# Check for the '-nopush' argument
if [ "$1" = "-nopush" ]; then
    PUSH_IMAGE="no"
    echo "Build without pushing the Docker image."
else
    echo "Will push the Docker image. Use -nopush to skip pushing it."
    if [ -z "$CI_REGISTRY" ]; then
      echo "Error: CI_REGISTRY is undefined"
      exit 1
    fi
    REGISTRY_URL="$CI_REGISTRY"
    read -p "$REGISTRY_URL Username: " REGISTRY_USERNAME
    read -s -p "$REGISTRY_URL Password: " REGISTRY_PASSWORD
    echo

    # Export the credentials as environment variables
    export REGISTRY_URL
    export REGISTRY_USERNAME
    export REGISTRY_PASSWORD
fi

# Execute the Ansible playbook with the push_image variable
ansible-playbook -i inventory.yml ansible/playbooks/build_and_push_pal_image.yml --extra-vars "push_image=$PUSH_IMAGE"
