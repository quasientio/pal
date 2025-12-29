#!/bin/bash
#
# Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2029-10-01
# Change License: Apache 2.0
#


if [ -z "$PAL_HOME" ]; then
  echo "PAL_HOME is undefined."
  exit 1
fi

# activate the py venv so we can import javalang
source $PAL_HOME/venv/bin/activate

# colf-linux must be run from the pal-api root path (ie. $PAL_HOME/pal-api)
cd $PAL_HOME/modules/pal-api

# re-compile all schemas
colf-linux -f \
 -b src/main/java \
 -p io.quasient.pal.messages \
 -i io.quasient.pal.messages.Marshallable \
  Java src/main/colfer/*.colf

# add the fromJson() method to all generated classes
bin/generate_fromjson_reset.py
