#!/usr/bin/env bash
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
DOC_FOLDER_NAME=coopr-docs-v0.9.8

make clean html
cd build
cp -r html/ $DOC_FOLDER_NAME
tar -czf $DOC_FOLDER_NAME.tar.gz $DOC_FOLDER_NAME
rm -r $DOC_FOLDER_NAME
