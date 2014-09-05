..
   Copyright © 2012-2014 Cask Data, Inc.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
 
       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

:orphan:

.. _plugin-reference:


.. index::
   single: Chef Solo Automator Plugin

==========================
Chef Solo Automator Plugin
==========================

.. include:: /guide/admin/admin-links.rst

This section describes an automator that uses chef-solo. Basic knowledge of Chef and its primitives is assumed.

Overview
========

The Chef Solo Automator plugin, like all automator plugins, is responsible for performing the installation and
operation of services on remote hosts. The Chef Solo Automator plugin achieves this by running chef-solo on the remote host
with a custom run-list and set of JSON attributes. The attributes provided to each chef-solo run will be a combination
of cluster-wide configuration attributes, as well as service-specific attributes definable for each action. Each
chef-solo run is self-contained and intended to perform a specific task such as starting a service. This differs from
the typical usage of Chef where one builds up a large run-list managing all resources on a box in one run.

To illustrate this, consider the following example which shows how we can manage the apache web server on Coopr cluster
nodes using the "apache2" community cookbook. We define a Coopr service "apache-httpd" as follows:
::

    {
        "dependson": [
            "hosts"
        ],
        "description": "Apache HTTP Server",
        "name": "apache-httpd",
        "provisioner": {
            "actions": {
                "install": {
                    "type": "chef-solo"
                    "fields": {
                        "run_list": "recipe[apache2::default]",
                    }
                },
                "configure": {
                    "type": "chef-solo"
                    "fields": {
                        "run_list": "recipe[apache2::default]",
                    }
                },
                "start": {
                    "type": "chef-solo"
                    "fields": {
                        "json_attributes": "{\"coopr\": { \"node\": { \"services\": { \"apache2\": \"start\" } } } }",
                        "run_list": "recipe[apache2::default],recipe[coopr_service_runner::default]",
                    }
                },
                "stop": {
                    "type": "chef-solo"
                    "fields": {
                        "json_attributes": "{\"coopr\": { \"node\": { \"services\": { \"apache2\": \"stop\" } } } }",
                        "run_list": "recipe[apache2::default],recipe[coopr_service_runner::default]",
                    }
                }
            }
        }
    }

For each action, we define the ``type``, and the custom fields ``run_list`` and ``json_attributes``. (defaults to empty string if not specified). The ``type``
field indicates to the provisioner to use the Chef Solo Automator plugin to manage this action. The ``run_list`` field specifies
the run-list to use. The ``json_attributes`` field is any additional JSON data we wish to include in the Chef run (more on this
later). When the Chef Solo Automator plugin executes any of these actions for the apache-httpd service, it performs
the following actions:

        1. generate a task-specific JSON file containing any attributes defined in the json_attributes field, as well as base cluster attributes defined elsewhere in Coopr.
        2. invoke chef-solo using the ``run_list`` field as the run-list as follows:  ``chef-solo -o [run_list] -j [task-specific json]``


In this example, to execute an "install" task for the apache-httpd service, the provisioner will simply run the default
recipe from the apache2 cookbook as a single chef-solo run. No additional JSON attributes are provided beyond the base
cluster configuration attributes.

For a "configure" task, the provisioner will also run the default recipe from the apache2 cookbook. For this community
cookbook, the installation and configuration are done in the same recipe, which is common but not always the case. So
one may wonder why we need both 'install' and 'configure' when they perform identical actions. It is best practice to
keep them both, since configure may be run many times throughout the lifecycle of the cluster, and install is needed
to satisfy dependencies.

The "start" and "stop" tasks introduce a couple of features. They make use of the ``json_attributes`` field to specify custom JSON
attributes. Note that the format is an escaped JSON string. The ``run_list`` field also contains an additional recipe,
``coopr_service_runner::default``. More on this later, but essentially this is a helper cookbook that can operate on
any Chef service resource. It looks for any service names listed in node['coopr']['node']['services'], finds the
corresponding Chef service resource, and invokes the specified action.


JSON Attributes
================

Coopr maintains significant JSON data for a cluster, and makes it available for each task. This JSON data includes:
    * cluster-wide configuration defined in cluster templates (Catalog -> cluster template -> defaults -> config)
    * node data for each node of the cluster: hostname, ip, etc
    * service data, specified in the actions for each service

The Chef Solo Automator plugin automatically merges this data into a single JSON file, which is then passed to chef-solo via
the ``--json-attributes argument``. Any custom cookbooks that want to make use of this Coopr data need to be familiar
with the JSON layout of the Coopr data. In brief, cluster-wide configuration defined in cluster templates and
service-level action data are merged together, and preserved at the top-level. Coopr data is then also merged in under
``coopr/*``. For example:
::

    {
        // cluster config attributes defined in clustertemplates are preserved here at top-level
        // service-level action data string converted to json and merged here at top-level
        "coopr": {
            "clusterId": "00000001",
            "cluster": {
                //cluster config here as well
                "nodes": {
                    // node data
                }
            }
            "services": [
              // list of coopr services on this node
            ]
        }
    }


Consider the following two rules of thumb:
	* When using community cookbooks, attributes can be specified in Coopr templates exactly as the cookbook expects (at the top-level).
	* When writing cookbooks specifically utilizing Coopr metadata (cluster node data for example), recipes can access the metadata at ``node['coopr']['cluster']...``

Bootstrap
=========

Each Coopr Automator plugin is responsible for implementing a bootstrap method in which it performs any actions it needs to be able to carry out further tasks. The Chef Solo Automator plugin performs the following actions for a bootstrap task:
	1. Bundle its local copy of the cookbooks/roles/data_bags directories into tarballs, ``cookbooks.tar.gz``, ``roles.tar.gz``, ``data_bags.tar.gz``.
		* Unless the tarballs exist already and were created in the last 10 minutes.
	2. Logs into the remote box and installs chef via the Opscode Omnibus installer (``curl -L https://www.opscode.com/chef/install.sh | bash``).
	3. Creates the remote coopr cache directory ``/var/cache/coopr``.
	4. SCP the local tarballs to the remote Coopr cache directory.
        5. Extracts the tarballs on the remote host to the default chef directory ``/var/chef``.

The most important things to note are that:
	* Upon adding any new cookbooks, roles, or data_bags to the local directories, the tarballs will be regenerated within 10 minutes and used by all running provisioners.
	* This implementation requires internet access to install Chef (and also required by the cookbooks used within).


Adding your own Cookbooks
=========================
**Cookbook requirements**

Since the Chef Solo Automator plugin is implemented using chef-solo, the following restrictions apply:

	* No Chef search capability
	* No persistent attributes

Cookbooks should be fully attribute-driven. At this time the Chef Solo Automator does not support the chef-solo "environment" primitive. 
Attributes normally specified in an environment can instead be populated in Coopr primitives such as cluster templates or service action data.

In order to add cookbooks, roles, or data-bags for use by the provisioners, simply add them to the local chef directories for the Chef Solo Automator
plugin. If using the default package install, these directories are currently:
::

    /opt/coopr/provisioner/daemon/plugins/automators/chef_solo_automator/chef_solo_automator/cookbooks
    /opt/coopr/provisioner/daemon/plugins/automators/chef_solo_automator/chef_solo_automator/roles
    /opt/coopr/provisioner/daemon/plugins/automators/chef_solo_automator/chef_solo_automator/data_bags

Your cookbook should be readable by the 'coopr-provisioner' user (default: 'coopr'). The next provisioner which runs a
bootstrap task will regenerate the local tarballs
(for example ``/opt/coopr/provisioner/daemon/plugins/automators/chef_solo_automator/chef_solo_automator/cookbooks.tar.gz``) and it will be
available for use when chef-solo runs on the remote box.

In order to actually invoke your cookbook or role as part of a cluster provision, you will need to define a Coopr service
definition with the following parameters:

	* Category: any action (install, configure, start, stop, etc)
	* Type: chef-solo
	* run_list: a run-list containing your cookbook's recipe(s) or roles. If your recipe depends on resources defined in other cookbooks which aren't declared dependencies in your cookbook's metadata, make sure to also add them to the run-list.
	* json_attributes: (optional), any additional custom attributes you want to specify, unique to this action

Then simply add your service to a cluster template.


Helper Cookbooks
================

Coopr ships with several helper cookbooks.


**coopr_base**
--------------
This is a convenience cookbook which is intended to provide base functionality for all hosts provisioned by coopr.  It currently 
does the following:

	* run ``apt-get update`` (on Ubuntu hosts)
	* include ``coopr_hosts::default`` (discussed below)
	* include ``coopr_firewall::default`` (discussed below)
	* include ``ulimit::default`` to enable user-defined ulimits

**coopr_hosts**
---------------

This simple cookbook's only purpose is to populate ``/etc/hosts`` with the hostnames and IP addresses of the cluster.
It achieves this by accessing the ``coopr-populated`` attributes at ``node['coopr']['cluster']['nodes']`` to get a list of
all the nodes in the cluster. It then simply utilizes the community "hostsfile" cookbook's LWRP to write entries for
each node.

The example coopr service definition invoking this cookbook is called "base". It simply sets up a "configure" service
action of type "chef-solo" and run_list ``recipe[coopr_base::default]`` (which includes ``recipe[coopr_hosts::default]``).
Note that the community "hostsfile" cookbook is not needed in the run-list since it is declared in coopr_hosts's metadata.

**coopr_service_runner**
------------------------

This cookbook comes in handy as a simple way to isolate the starting and stopping of various services within your
cluster. It allows you to simply specify the name of a Chef service resource and an action within a Coopr service
definition. When run, it will simply lookup the Chef service resource of the given name, regardless of which cookbook
it is defined in, and run the given action. In the example apache-httpd service definition above, it is simply included
in the run-list to start or stop the apache2 service defined in the apache2 community cookbook. All that is needed is
to set the following attribute to "start" or "stop":
::

    node['coopr']['node']['services']['apache2'] = "start"


**coopr_firewall**
------------------

This cookbook is a simple iptables firewall manager, with the added functionality of automatically whitelisting all
nodes of a cluster. To use, simply set any of the following attributes:
::

    node['coopr_firewall']['INPUT_policy']  = (string)
    node['coopr_firewall']['FORWARD_policy'] = (string)
    node['coopr_firewall']['OUTPUT_policy'] = (string)
    node['coopr_firewall']['notrack_ports'] = [ array ]
    node['coopr_firewall']['open_tcp_ports'] = [ array ]
    node['coopr_firewall']['open_udp_ports'] = [ array ]

If this recipe is included in the run-list and no attributes specified, the default behavior is to disable the firewall.


Best Practices
==============

* Coopr is designed to use attribute-driven cookbooks. All user-defined attributes are specified in Coopr primitives. Recipes that use Chef server capabilities like discovery and such do not operate well with Coopr.
* Separate the install, configuration, initialization, starting/stopping, and deletion logic of your cookbooks into granular recipes. This way Coopr services can often be defined with a 1:1 mapping to recipes. Remember that Coopr will need to install, configure, initialize, start, stop, and remove your services, each independently through a combination of run-list and attributes.
* Use wrapper cookbooks in order to customize community cookbooks to suit your needs.
* Remember to declare cookbook dependencies in metadata.
