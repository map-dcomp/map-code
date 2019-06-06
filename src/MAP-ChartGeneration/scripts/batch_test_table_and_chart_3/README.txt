Instructions for using the automated test and chart generation scripts
1/24/2019


These scripts must be run next to the MAP-code repository so that the following jar file dependecies can be accessed:
  MAP-code/src/MAP-Agent/build/libs/map-sim-runner-2.6.1-executable
  MAP-code/src/MAP-ChartGeneration/build/libsMAP-ChartGeneration-0.1.1-executable.jar

The versions or locations of these files can be updated in the run_simulation_and_tables.sh script.



Scripts and file dependency hierarchy and descriptions:

run_tests.sh -- example script that runs the automated simulation and table generation with certain parameters
  batch_timeout_run_simulation_and_tables.sh -- runs a sequence of tests a given number of times (iterations), for the given scenarios, using each of the specified algorithms
    rlg_algorithms_to_run.txt -- a file specifying the algorithms to use when running each scenario
    run_simulation_and_tables.sh -- runs the simulation and table generation for iteration of one scenario using one algorithm

batch_generate_tables.sh -- used to regenerate chart tables for each test iteration. This is useful in cases where the chart generator is updated and new chart tables need to be made.
    run_simulation_and_tables.sh -- (see above)

generate_all_charts.sh -- generates charts for each iteration of each scenario for each algorithm that was run
  generate_service_ncp_demand_load_cap_req_plot.sh -- generates charts with Load, Allocated Capacity, Requests Summary information, and overlayed Client Demand. One chart is generated per service.





Steps for running tests:

1. Create a folder that will contain the scenarios to run and place the scenarios in the folder.


2. Prepare a file that specifies the algorithms to use when running tests. Place each algorithm on a new line.
   For "STUB", a choose algorithm can be specified with the format "STUB [choose algorithm]".
   The included file "rlg_algorithms_to_run.txt" can be used as is or modified.


3. Remove all *.log files from the directory containing the scripts. This is to ensure that they are not mixed with new log files for tests.


4. ./batch_generate_tables.sh [scenario folder] [algorithms file] [simulation output folder] [iterations per scenario algorithm pair] [time limit for a scenario run]

   You can also modify and use ./run_tests.sh.
   The script should move log files from the script directory into their appropriate scenario run folder after each run.

   Note the directory structure that the scripts create:
     [simulation output folder]/[algorithm]/[scenario]/[scenario run]/scenario
     [simulation output folder]/[algorithm]/[scenario]/[scenario run]/test_output
     [simulation output folder]/[algorithm]/[scenario]/[scenario run]/test_chart_tables

   [scenario run] -- has the format YYYYMMDD_hhmmss for unfinished tests and YYYYMMDD_hhmmss-finished for finished tests
   scenario -- contains the input scenario configuration copied from the [scenario folder] to clearly identify the exact scenario configuration that was run
   test_output -- contains the output of the simulation
   test_chart_tables -- contains the output of the chart generator, which is based on the contents of "test_output"




Steps for generating charts:

1. Ensure that there exists a set of chart generation input data tables that the MAP-ChartGeneration tool produced within the "test_chart_tables" folder of the following directory structure:
     [simulation output folder]/[algorithm]/[scenario]/[scenario run]/test_chart_tables

   The chart generation scripts expect the directory structure above and depend on the contents of "test_chart_tables"
   but do not depend on "scenario" or "test_output".
   Therefore, if you already have MAP-ChartGeneration output, you can skip the steps for runnning tests and create this directory structure manually. 


2. ./generate_all_charts.sh [simulation output folder] [chart output folder]

   The script adds intermediate gnu and eps files within "test_chart_tables" and outputs to the following directory structure:
     [chart output folder]/[scenario]

   Each [scenario] folder will contain png files with the [scenario], [algorithm], and [scenario run] information in both the file name and the title within the chart image.



Issues/Errors with convert:

Depending on the OS/App config. You could run into policy/security issues with the eps to png convert.

In that case you will need to update your /etc/ImageMagick-6/policy.xml file to allow such an operation.

