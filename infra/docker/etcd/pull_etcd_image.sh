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
                                                                                                        
IMAGE="$CI_REGISTRY/quasientio/pal/etcd:latest"                                                         
                                                                                                        
if ! docker pull "$IMAGE"; then                                                                         
  echo "Error: Failed to pull $IMAGE"                                                                   
  echo "Run ./build_etcd_image.sh to build and push it first"                                           
  exit 1                                                                                                
fi                                                                                                      
                                                                                                        
docker tag "$IMAGE" etcd:latest                                   
