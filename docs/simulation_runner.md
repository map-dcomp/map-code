Overview
======

The simulation runner is executed by running the following after a build.

    cd src/MAP-Agent
    java -jar build/libs/map-sim-runner-0.1-executable.jar --scenario src/test/resources/ns2/rlg-example/ -d src/test/resources/ns2/rlg-example/rlg_overload_1/ -r PT1M --dumpInterval 10 -o test-output
    
This tells the system to run the topology `rlg-example` with the simulated
demand from `rlg_overload`. The simulation will run for 1 minute. Every 10
seconds a directory will be created in `test-output` that contains the
current state of the simulation.  If you run with `--help` you can see all of the options.

You can find documentation on how the duration parameters are formatted
here:
https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-

Client Requests
===========

Client requests are categorized based on the current state of the network:
failed due to network, failed due to server, success.  Those that succeed
can also be counted as slow due to network and/or slow due to server.

When simulating the client request, if the a link in the network path is
loaded more than 100%, then the client request fails due to network.  If the
network passes and the server is more than 100% loaded, then the client
request fails due to server.  Otherwise the request succeeds.

The number of requests attempted equals the number of requests failed due
to network, plus the number failed due to server, plus success.

Of the requests that succeed they can also be counted as slow. If any link
in the network path that has a load above
the `slow network threshold`, the number of requests slow due to
network is incremented.  If the server has any attribute above
`slow server threshold`, then the number of requests slow due to
server is incremented.

A client request is attempted up to max client connection attempts
 (configurable).  The number of connection attempts is always greater than
 or equal to the number of request attempts.
 

Outputs
======

When the simulation runner is run scenarios will produce (i) an agent
configuration file, (ii) a collection of NCP data directories and (iii) a
single simulation directory. The directories will contain JSON data files
that capture state(s) of the simulation.

Each JSON file maps to a Java class. This documentation is a snapshot as of
5/16/2018. If there are any questions about what the outputs are, go to the
Java class and check it's documentation.

Agent configuration file
------------------------

A file named agent-configuration.json will be placed in the top-level of the
output directory. This file will list the configuration options used as test
inputs.

NCP data directories
--------------------

NCP data directories are named after a node name and contain a collection of
millisecond timestamped sub-directories. These sub-directories contain variable
JSON files and data, depending upon the configuration of the NCP at a given time
step.

An example structure of a data sub-directory for an NCP that only runs a DCOP
leader, in this case nodeA0 at timestep 320187:

a3dev@a3dev:/tmp$ tree nodeA0/320187/
nodeA0/320187/
├── regionPlan.json
├── resourceReport-LONG.json
├── resourceReport-SHORT.json
├── resourceSummary-LONG.json
├── dns-records.json
└── state.json

Generally, data directories will contain some combination of the following types
of files and will be logged as JSON outputs depending upon the role of a given
node:

* configuration state (state.json) - maps to the class Controller.NodeState
  * Flags defining the node's role (is DCOP, RLG, DNS, ect.)
  * AP debugging level - the current value of the Protelis program on the
    specified node
  * Region and name for the NCP.
  
* region plan (regionPlan.json) - maps to the class RegionPlan
  * only output on nodes running DCOP and RLG
  * This is the current instance of the region plan that this node has
    received from AP
  * Outputs produced by DCOP. Shared state that is consumed by an RLG.
  * The output on the node running DCOP is the latest that DCOP has
    produced. The output on the node running RLG is the latest version that
    AP has propagated to that RLG node. These 2 versions may not match up
    while AP is propagating the object.
  
* load balancing plans (loadBalancerPlan.json) - maps to the class LoadBalancerPlan
  * only output on nodes running RLG
  * Outputs produced by RLG.
  * Includes service plan, overflow plan and what to do with the currently
    executing containers (stop traffic to them to allow them to catch up or
    shutdown the container)
  * This state is acted upon by DNS translators/servers.
  
* resource reports (resourceReport-SHORT.json, resourceReport-LONG.json) -
  maps to the class ResourceReport
  * Current resource report produced by the node.
  * Can come in SHORT or LONG moving average forms. 
  
* resource summaries (resourceSummary-SHORT.json,
  resourceSummary-LONG.json) - maps to the class ResourceSummary
  * Current regional aggregate summary of all regional NCP states.
  * Can come in SHORT or LONG moving average forms. 
  * Usually used by DCOP.
  
* region node state (regionNodeState.json) - maps to class RegionNodeState
  * Only output on nodes running RLG
  * A list of resource reports for all NCPs in a region.

* dns-records.json - collection of DnsRecord objects
  * only output if the node is handling DNS change
  
simulation data directory
-------------------------

A single simulation data directory is produced for each scenario. That directory
contains client summaries for the scenario and a collection of millisecond
timestamped data directories.

The client summaries are found at the top-level of the directory for each
'client pool'. For example, the file client-clientPoolA-final-state.json might
contain a summary of the total number of requests, failures, slowed-resources
and general description of how requests were serviced by region. That may look
like the following:

a3dev@a3dev:/tmp/simulation$ cat client-clientPoolA-final-state.json 
{
  "numRequestsAttempted" : 300,
  "numRequestsSucceeded" : 42,
  "numRequestsFailedForServerLoad" : 95,
  "numRequestsFailedForNetworkLoad" : 0,
  "numRequestsSlowForNetworkLoad" : 0,
  "numRequestsSlowForNetworkAndServerLoad" : 0,
  "numRequestsSlowForServerLoad" : 10,
  "numRequestsServicedByRegion" : {
    "B" : 4,
    "X" : 38
  },
  "clientName" : "clientPoolA",
  "clientRegion" : {
    "name" : "A"
  }
}

Success, failure and 'slow' designations are governed by inputs to the
simulator. See the Client Requests section above for more details about this.

Simulation data directories contain output files similar to the following:

a3dev@a3dev:/tmp/simulation/330350$ ls
client-clientPoolA.json  client-clientPoolC.json  dns-B.json  dns-GLOBAL.json
client-clientPoolB.json  dns-A.json               dns-C.json  dns-X.json

The client pool files are snapshots of the success/failures during a run.
These files map to the class ClientSim.

The dns files capture the delegation structure (e.g., how requests are
routed for individual regions). These files map to the class
Simulation.DnsState.




