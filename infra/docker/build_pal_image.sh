#!/bin/sh

if [ -z "$PAL_HOME" ]; then
  echo "Error: PAL_HOME is undefined"
  exit 1
fi

# clean up artifacts from previous builds
mkdir -p bin/ lib/
rm -fr bin/* lib/*

# copy artifacts to build context
cp -f ${PAL_HOME}/bin/pal bin/
cp -f ${PAL_HOME}/bin/wait-for bin/
cp -f ${PAL_HOME}/lib/pal-*.jar lib/

docker build --tag registry.gitlab.com/cometera/pal/pal .
docker tag registry.gitlab.com/cometera/pal/pal:latest pal:latest

# login to remote registry and push
docker login registry.gitlab.com
docker push --all-tags registry.gitlab.com/cometera/pal/pal
