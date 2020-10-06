This repository contains code for executing the MAP system on a single
machine in a low-fidelity test environment. The network simulation is
very basic, but functional enough to allow test of the network
algorithms.

See [docs/simulation_runner.md] for details on running the simulator.

See [docs/] for other documentation of the input files and data structures.

To build
  1. `cd src`
  1. `./gradlew build`
    * you can skip the tests by adding `-x test` to the command
    
