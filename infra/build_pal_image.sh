#!/bin/sh

REGISTRY_URL="registry.gitlab.com"
read -p "Registry Username:" REGISTRY_USERNAME

# Prompt for Docker Registry Password securely
read -s -p "Registry Password:" REGISTRY_PASSWORD

# Export the credentials as environment variables
export REGISTRY_URL
export REGISTRY_USERNAME
export REGISTRY_PASSWORD

ansible-playbook -i inventory.yml ansible/playbooks/build_and_push_pal_image.yml
