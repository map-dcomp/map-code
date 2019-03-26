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
 
