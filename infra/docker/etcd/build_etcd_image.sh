#!/bin/sh


# build
docker build \
--tag registry.gitlab.com/cometera/pal/etcd:3.5.7 \
--tag registry.gitlab.com/cometera/pal/etcd:latest \
--tag etcd:latest .

# login to remote registry and push
docker login registry.gitlab.com
docker push --all-tags registry.gitlab.com/cometera/pal/etcd
