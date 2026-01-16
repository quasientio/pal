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
