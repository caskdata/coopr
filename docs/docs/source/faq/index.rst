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

.. _faq_toplevel:

.. index::
   single: Frequently Asked Questions

============================
FAQs
============================

General
=======

#. :doc:`What are the differences between Coopr and Ambari/Savanna?<general>`
#. :doc:`Does Coopr work with Ambari?<general>`
#. :doc:`What are the differences between Coopr and Amazon EMR?<general>`
#. :doc:`Will Coopr support docker based clusters ?<general>`
#. :doc:`Does Coopr support bare metal ?<general>`
#. :doc:`What providers are supported by Coopr ?<general>`
#. :doc:`Does Coopr make it easy for me to move from one cloud to another ?<general>`
#. :doc:`Can Coopr work on my laptop ?<general>`
#. :doc:`How long has Coopr been used in a production enviroment and where is it being used?<general>`
#. :doc:`Is Coopr designed only for provisioning compute and storage?<general>`
#. :doc:`What is the recommended setup for Coopr in terms of hardware and configuration?<general>`
#. :doc:`Does Coopr support monitoring and alerting of services deployed ?<general>`
#. :doc:`Does Coopr support metering ?<general>`
#. :doc:`I use puppet will I be able to use puppet with Coopr ?<general>`
#. :doc:`Can Coopr support approval workflows or ability to pause provisioning for approval ?<general>`

Coopr Server
============

#. :doc:`How many concurrent provisioning jobs can Coopr handle?<server>`
#. :doc:`Can I scale-up or scale-down a cluster?<server>`
#. :doc:`Do I have the ability to import and export configurations from one cluster to another?<server>`
#. :doc:`Where are the configurations of cluster template and it's metadata stored?<server>`
#. :doc:`How do I setup a database for Coopr to use it?<server>`
#. :doc:`Is node pooling supported?<server>`
#. :doc:`What is node pooling?<server>`
#. :doc:`Can I run multiple servers concurrently for HA?<server>`
#. :doc:`Can I look at the plan before the cluster is being provisioned?<server>`
#. :doc:`Is there a way to plugin my own planner or layout solver?<server>`
#. :doc:`Is there anyway to inspect the plan for cluster being provisioned?<server>`


Coopr Provisioner
=================

#. :doc:`When something goes wrong, how can I look at the logs?<provisioner>`
#. :doc:`How many provisioners should I run?<provisioner>`
#. :doc:`Can I increase the number of provisioners on the fly?<provisioner>`
#. :doc:`How many resources does each provisioner need?<provisioner>`
#. :doc:`Is it possible for multiple provisioners to perform operations on the same node at the same time?<provisioner>`
#. :doc:`Can I run different types of provisioners at the same time?<provisioner>`
#. :doc:`Can I customize provisioners?<provisioner>`
#. :doc:`What happens when I stop a provisioner while it is performing a task?<provisioner>`
#. :doc:`Can the Chef Solo Automator plugin use a chef server ?<provisioner>`

Coopr Administration
====================

#. :doc:`What operations are only available to the admin versus other users?<admin>`
#. :doc:`What happens to existing clusters when the template used to create them changes?<admin>`
#. :doc:`How can I write configuration settings that reference hostnames of other nodes in the cluster?<admin>`
#. :doc:`Can I configure clusters to delete themselves after some amount of time?<admin>`
#. :doc:`What is the admin password?<admin>`
#. :doc:`Any user password works. What are the user passwords for?<admin>`

Security
========
#. :doc:`Does Coopr support authentication?<security>`
#. :doc:`Are all the communication between Coopr Server and Coopr Provisioners secure?<security>`
#. :doc:`Can Coopr integrate with any authentication system?<security>`
#. :doc:`Will Coopr support authorization and granular control in the future?<security>`

Licensing and Open Source
=========================

#. :doc:`What type of license is Coopr open sourced under?<oss>`
#. :doc:`How can I contribute?<oss>`
