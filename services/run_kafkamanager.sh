#!/bin/sh

PAL_DIRECTORY=127.0.0.1:2181

docker run -it --rm -p 9000:9000 --network=host -e ZK_HOSTS="$PAL_DIRECTORY" -e APPLICATION_SECRET=letmein sheepkiller/kafka-manager
