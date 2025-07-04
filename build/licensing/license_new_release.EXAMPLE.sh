#
# Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
#
# Use of this software is governed by the Business Source License 1.1
# included in the file LICENSE and at https://mariadb.com/bsl11
#
# Change Date: 2029-10-01
# Change License: Apache 2.0
#

# create release branch
git switch -c release-1.4
export CDATE=$(date -u -d '+4 years -1 day' '+%Y-%m-%d')
mvn license:format -DchangeDate=$CDATE
git commit -am "Release 1.4 (BUSL, CD $CDATE)"
