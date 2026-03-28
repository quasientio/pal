#!/bin/bash
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
