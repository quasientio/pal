#!/bin/sh

if [ -z "$CI_REGISTRY" ]; then
  echo "Error: CI_REGISTRY is undefined"
  exit 1
fi

# build
docker build \
--tag $CI_REGISTRY/quasient/pal/etcd:3.5.7 \
--tag $CI_REGISTRY/quasient/pal/etcd:latest \
--tag etcd:latest .

# login to remote registry and push
docker login $CI_REGISTRY
docker push --all-tags $CI_REGISTRY/quasient/pal/etcd
