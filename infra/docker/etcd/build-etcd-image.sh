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
