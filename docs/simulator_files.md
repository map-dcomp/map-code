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
    

service-configurations.json
---------------------------

The file `service-configurations.json` specifies the information to create a list of `ServiceConfiguration` objects.
The `service` is specified as a group/artifact/version tuple. 
The `hostname` is the FQDN that clients interested in this service will lookup in DNS.
The `defaultNode` is the node where the service is running when there has
been no direction from DCOP or RLG to move the service.


Demand scenario definition
==========================

A demand scenario is a directory containing files for each client that will create demand.
The files are named <client name>.json.
The file contains a list of `ClientRequest` objects.
See the documentation of the class for the properties.

The client request effects the network load and server load for the
duration of the request.

