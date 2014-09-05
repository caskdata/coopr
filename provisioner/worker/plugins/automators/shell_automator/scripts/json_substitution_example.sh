#!/bin/bash
#
# Copyright © 2012-2014 Cask Data, Inc.
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

# to use this script, define a service as follows:
#  type: shell
#  script: json_substitution_example.sh
#  data: taskId config/automators [any additional keys from cluster configuration]

echo "--- example json parsing ---"

if [ $# -gt 0 ]; then
  for key in "$@" ; do
    value=`coopr_lookup_key $key 2>&1`
    if [ $? -eq 0 ]; then
      echo "json lookup: $key --> $value"
    fi
  done
else
  echo "no arguments passed, defaulting to taskId"
  value=`coopr_lookup_key taskId 2>&1`
  if [ $? -eq 0 ]; then
    echo "json lookup: task --> $value"
  fi
fi

