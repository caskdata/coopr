#!/usr/bin/env ruby
# encoding: UTF-8
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

# base class for all automator plugins.  This should be extended, not modified
class Automator
  attr_accessor :task, :flavor, :image, :hostname, :providerid, :result
  attr_reader :env
  def initialize(env, task)
    @task = task
    @env = env
    @result = Hash.new { |h, k| h[k] = Hash.new(&h.default_proc) }
  end

  def runTask
    sshauth = @task['config']['ssh-auth']
    hostname = @task['config']['hostname']
    ipaddress = @task['config']['ipaddresses']['access_v4']
    fields = @task['config']['service']['action']['fields'] rescue nil

    case task['taskName'].downcase
    when 'bootstrap'
      bootstrap('hostname' => hostname, 'ipaddress' => ipaddress, 'sshauth' => sshauth)
      return @result
    when "install"
      install({'hostname' => hostname, 'ipaddress' => ipaddress, 'sshauth' => sshauth, 'fields' => fields})
      return @result
    when "configure"
      configure({'hostname' => hostname, 'ipaddress' => ipaddress, 'sshauth' => sshauth, 'fields' => fields})
      return @result
    when "initialize"
      init({'hostname' => hostname, 'ipaddress' => ipaddress, 'sshauth' => sshauth, 'fields' => fields})
      return @result
    when "start"
      start({'hostname' => hostname, 'ipaddress' => ipaddress, 'sshauth' => sshauth, 'fields' => fields})
      return @result
    when "stop"
      stop({'hostname' => hostname, 'ipaddress' => ipaddress, 'sshauth' => sshauth, 'fields' => fields})
      return @result
    when "remove"
      remove({'hostname' => hostname, 'ipaddress' => ipaddress, 'sshauth' => sshauth, 'fields' => fields})
      return @result
    else
      fail "unhandled automator task type: #{task['taskName']}"
    end
  end

  def bootstrap(inputmap)
    @result['status'] = 1
    @result['message'] = "Unimplemented task bootstrap in class #{self.class.name}"
    fail "Unimplemented task bootstrap in class #{self.class.name}"
  end

  def install(inputmap)
    @result['status'] = 1
    @result['message'] = "Unimplemented task install in class #{self.class.name}"
    fail "Unimplemented task install in class #{self.class.name}"
  end

  def configure(inputmap)
    @result['status'] = 1
    @result['message'] = "Unimplemented task configure in class #{self.class.name}"
    fail "Unimplemented task configure in class #{self.class.name}"
  end

  def init(inputmap)
    @result['status'] = 1
    @result['message'] = "Unimplemented task initialize in class #{self.class.name}"
    fail "Unimplemented task initialize in class #{self.class.name}"
  end

  def start(inputmap)
    @result['status'] = 1
    @result['message'] = "Unimplemented task start in class #{self.class.name}"
    fail "Unimplemented task start in class #{self.class.name}"
  end

  def stop(inputmap)
    @result['status'] = 1
    @result['message'] = "Unimplemented task stop in class #{self.class.name}"
    fail "Unimplemented task stop in class #{self.class.name}"
  end

  def remove(inputmap)
    @result['status'] = 1
    @result['message'] = "Unimplemented task remove in class #{self.class.name}"
    fail "Unimplemented task remove in class #{self.class.name}"
  end
end
