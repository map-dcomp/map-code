Data that is sent via AP and where it is sent.

# resource reports 
The class `ResourceReport` is produced by all nodes.
All reports in a region are collected in a list and sent to the RLG node.

# resource summary
An instance of `ResourceSummary` is created from all `ResourceReport` objects in a region.
This information is stored on the DCOP leader for the region.

# DCOP shared information
Each DCOP leader stores information in `DcopSharedInformation`.
This information is shared with all neighboring regions.

# RLG shared information
Each RLG leader stores information in `RlgSharedInformation`.
This information is shared with all neighboring regions.

# DCOP plan
This is an instance of `RegionPlan`. 
This is shared with all nodes in the region that created it.

# RLG plan
This is an instance of `LoadBalancerPlan`.
This is shared with all nodes in the region that created it.

# service reports
This is an instance of `ServiceReport`.
This information is sent to all nodes in the region.
Ideally this would be limited to the RLG leader and the DNS leader.

# available services
This is used to let RLG in other regions know when a service is running in the region so that it can overflow the service to the other region.
The is an instance of NetworkAvailableServices.
This information is sent to all nodes in the network.
Ideally this would just be sent to all RLG leaders in the network.

