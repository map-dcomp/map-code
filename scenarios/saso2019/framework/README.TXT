AMP 2/1/19, 2/19/19

Overview:
  
there are lots of notes about the goals and structure and options theses scripts
in the script headers.

broadly, this is a framework for batch testing map scenarios under different
configurations.

Configuration:

As input, a user will need to:

+ configure the variables in the config.sh. This file is sourced in subscripts.
+ configure map scenario(s) (and related demand) inputs in a scenario folder 
+ configure an rlg_algorithms_to_run.txt file to list algorithms under test.

Configuration Notes:

+ each map scenario is defind in the map documentation for the sim runner
+ ***IMPORTANT** these scripts assume each scenario will have a single demand
  folder named 'demand'.  


Usage:

% ./batch_run.sh

Example expected output:

Consider a configuration with 2 scenarios, 2 runs, 2 algos to test.

The results will be stored in the following structure

+ algo1 
++ scenario1 
+++ run1 
+++ run2 
++ scenario2 
+++ run1 
+++ run2 
+ algo2 
++ scenario1
+++ run1 
+++ run2 
++ scenario2 
+++ run1 
+++ run2


