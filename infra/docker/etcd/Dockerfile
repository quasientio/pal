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

###################################################################
# Dockerfile for etcd, based on alpine image.
#
# Build:
#    build          docker build \
#                     --tag $CI_REGISTRY/quasientio/pal/etcd:3.6.7 \
#                     --tag $CI_REGISTRY/quasientio/pal/etcd:latest \
#                     --tag etcd:latest .
#    push           docker login $CI_REGISTRY
#                   docker push --all-tags $CI_REGISTRY/quasientio/pal/etcd
#
# Build Args:
#                   ETCD_VERSION
#
# Usage examples:
# --------------
#
# (run etcd)
# docker run --name etcd --publish 2379:2379 --publish 2380:2380 \
#            --mount type=bind,source=$PAL_HOME/config/etcd,target=/etc/etcd \
#            etcd ./etcd --config-file /etc/etcd/etcd.conf.yml
# 
# (run etcdctl)
# docker run --name etcd --publish 2379:2379 --publish 2380:2380 \
#            --mount type=bind,source=$PAL_HOME/config/etcd,target=/etc/etcd \
#            etcd ./etcdctl
#
###################################################################

FROM alpine:latest

ARG ETCD_VERSION="v3.6.7"
ARG GOOGLE_URL="https://storage.googleapis.com/etcd"

RUN apk update
RUN apk add wget
RUN apk add bash

WORKDIR /usr/local/lib
RUN wget $GOOGLE_URL/${ETCD_VERSION}/etcd-${ETCD_VERSION}-linux-amd64.tar.gz
RUN tar xzvf etcd-${ETCD_VERSION}-linux-amd64.tar.gz && rm etcd-${ETCD_VERSION}-linux-amd64.tar.gz 
RUN ln -s etcd-${ETCD_VERSION}-linux-amd64 etcd
RUN echo "export PATH=$PATH:/usr/local/lib/etcd/bin" >> ~/.bashrc

WORKDIR /usr/local/lib/etcd

EXPOSE 2379
EXPOSE 2380

CMD [ "/bin/bash" ]
