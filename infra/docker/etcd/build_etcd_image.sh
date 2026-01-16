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


if [ -z "$CI_REGISTRY" ]; then
  echo "Error: CI_REGISTRY is undefined"
  exit 1
fi

# build
docker build \
--tag $CI_REGISTRY/quasientio/pal/etcd:3.6.7 \
--tag $CI_REGISTRY/quasientio/pal/etcd:latest \
--tag etcd:latest .


# Optional authentication - enable if registry requires auth
# login to remote registry and push
# docker login $CI_REGISTRY

#push it
docker push --all-tags $CI_REGISTRY/quasientio/pal/etcd
