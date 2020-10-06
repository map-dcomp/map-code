This scenario is the same as chain15 with 2 changes.
app1 has low priority and app2 has high priority.

1) Region C is reduced to a single NCP to allow only one service to be in
the same region as the clients.

2) The bandwidth between regions A and B is reduced to 10Mbps to cause
network contention. This shows up in the client latency graph as the lower
priority application taking longer to respond to the clients.

