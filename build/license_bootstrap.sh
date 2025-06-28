#
# Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2029-10-01
# Change License: Apache 2.0
#

# one-off to set the change date in the BSL

export CDATE="2029-10-01"
# OR pick "today + 4y - 1d"
# export CDATE=$(date -u -d '+4 years -1 day' '+%Y-%m-%d')
export YEAR_NOW=$(date '+%Y')
mvn license:format -DchangeDate=$CDATE -DcopyrightYear=$YEAR_NOW
#git commit -am "Init BSL (Change Date $CDATE)"
