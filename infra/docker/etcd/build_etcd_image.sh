#!/bin/sh
#
# Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2029-10-01
# Change License: Apache 2.0
#


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
