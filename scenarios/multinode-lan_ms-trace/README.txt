The scenario is the 15 node topology using switches for the regional networks.

The demand profile is based on src/MAP-Agent/src/test/resources/ms_trace.
* The services have been renamed to user-friendly names
* The number of clients for each request has been increased from 1 to 3
* Both TASK_CONTAINERS and CPU are specified as server elements
* The client requests have been spread across the 3 client pools

One can run the scnenario using the script run.sh in the current directory.
The script assumes that all of the code has already been built.
The default DCOP and RLG algorithms are used.
