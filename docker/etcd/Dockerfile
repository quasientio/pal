###################################################################
# Dockerfile for etcd, based on alpine image.
#
# Build:
#    build          docker build \
#                     --tag registry.gitlab.com/cometera/pal/etcd:3.5.7 \
#                     --tag registry.gitlab.com/cometera/pal/etcd:latest \
#                     --tag etcd:latest .
#    push           docker login registry.gitlab.com
#                   docker push --all-tags registry.gitlab.com/cometera/pal/etcd
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

ARG ETCD_VERSION="v3.5.16"
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
