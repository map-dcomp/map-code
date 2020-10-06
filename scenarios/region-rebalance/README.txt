Successful outcome looks like this:

0. Topology is 15 node chain. Each NCP has capacity for 1 container. Region C is limited to 1 NCP.
1. Scenario has 2 services defined. app1 has low priority and app2 has high priority.
2. The initial demand is for app1.
3. DCOP should push app1 to the client region
4. RLG should start containers as needed for the demand and fills the capacity of the region
5. The same client region starts requesting app2
6. DCOP should push app1 out of the client region and allow app2 to be in the client region
7. RLG should push app1 out of the client region to allow space for app2

