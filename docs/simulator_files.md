Simulation definition
=====================

A simulation network is specified as a directory containing the following files.

topology.ns
-----------

This file is an NS2 file that describes the topology.

The link capacity is taken from this file.

`tb-set-hardware` is used to set server capacity

hardware-configurations.json
----------------------------

The file `hardware-configurations.json` maps from the names in
`tb-set-hardware` to the actual values used by MAP.

  * `capacity` - this key specifies a value map that contains keys that are mapped to `NodeMetricName` and values that are the server capacity


Node JSON files
---------------

There is a JSON files for each node. 
The filename matches the node name. This is a JSON map. 

Valid keys are:
  * `region` - this is a string specifying the region name (default value is no region)
  * `DCOP` - this is true or false for the initial state of where DCOP is (default value is false)
  * `RLG` - this is true of false for the initial state of where RLG is (default value is false)
  * `client` - this is true on client nodes. Clients do not have MAP agents running on them (default value is false)
  * `pool` - servers can be pooled, at this point it just effects the display (default value is false)
  * `dns` - true/false - if true, then this node is the one that updates the DNS for the region based on the published DCOP and RLG plans. If there is no node in a region that handles DNS, then no DNS updates will be seen.

Client JSON files
-----------------

There is a JSON file for each client Pool.
They may be named like clientPool<RegionId>json.
The numClient field is primarly used for visualization.
    

service-configurations.json
---------------------------

The file `service-configurations.json` specifies the information to create a list of `ServiceConfiguration` objects.
The `service` is specified as a group/artifact/version tuple. 
The `hostname` is the FQDN that clients interested in this service will lookup in DNS.
The `defaultNode` is the node where the service is running when there has
been no direction from DCOP or RLG to move the service.

The capacities specified are mapped to the class
`ContainerParameters`. This is specifying the size of the `MAPContainer`
that the service runs in. So one would expect the number of `TASK_CONTAINERS`
for a service to be a small number like 2. This would mean that a `MAPContainer` running serviceA can
support load up 2 `TASK_CONTAINERS`.

service-dependencies.json
-------------------------

This file maps a list of `AppMgrUtils.ParsedDependency` objects.

The parsed objects are used to create `Dependency` and
`DependencyDemandFunction` objects which are loaded into the application
manager.

node-failures.json
------------------

This file maps a list of `Simulation.NodeFailure` objects.

The time specifies a a number of milliseconds into the simulation to stop the node or container.
The name is the name of the node or container.

Containers are named based on their parent node. For instance the first
container of nodeA1 is nodeA1_c00.

Nodes do not come back after being shutdown.

Containers will be restarted if directed to start more containers on the
node.

As of 12/6/2018 any node running RLG, DCOP, DNS or the global leader for AP
cannot be stopped.


Demand scenario definition
==========================

A demand scenario is a directory containing files for each client that will create demand.
The files are named <client name>.json.
The file contains a list of `ClientLoad` objects.
See the documentation of the class for the properties.

The client request effects the network load and server load for the
duration of the request. The network load is from the perspective of the
server handling the request. So the following value for the network load
specifies that the server is receiving data at 2Mbps and sending data at
6Mbps.

    { DATARATE_RX=2, DATARATE_TX=6}
    

The node and link attributes are documented in the class `LinkMetricName`.
Look for the constants that match the names in the node and link load
values.
