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
                                                                                                        
IMAGE="$CI_REGISTRY/quasientio/pal/etcd:latest"                                                         
                                                                                                        
if ! docker pull "$IMAGE"; then                                                                         
  echo "Error: Failed to pull $IMAGE"                                                                   
  echo "Run ./build_etcd_image.sh to build and push it first"                                           
  exit 1                                                                                                
fi                                                                                                      
                                                                                                        
docker tag "$IMAGE" etcd:latest                                   
