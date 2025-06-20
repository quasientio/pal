#!/bin/sh

PUSH_IMAGE=true

# Check for the '-nopush' argument
if [ "$1" = "-nopush" ]; then
    PUSH_IMAGE="no"
    echo "Build without pushing the Docker image."
else
    echo "Will push the Docker image. Use -nopush to skip pushing it."
    REGISTRY_URL="registry.gitlab.com"
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
