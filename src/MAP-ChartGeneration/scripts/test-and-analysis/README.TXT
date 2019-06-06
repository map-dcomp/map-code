Overview:

These are partial instructions to run automated developer integration tests and
to semi-automate graphical analysis with gnuplot.

Warning: You may need to edit/debug these scripts in a new environment.

Background:

These scripts are specific to the 3-region multi-node topology. These scripts
will probably not work if there are more than 3 regions. You can probably edit
the link capacity or number of nodes without issue. No promises.

Dependencies:

Ubuntu/Bash, Awk, Gnuplot, dot/graphviz. Probably others....

Instructions to run simulation and generate intermediate results: 

1. Embed (move/copy) the .sh files and template directory inside of your target
scenario folder 
2. Edit config.sh to  point to the correct simulation and chart gen jar
files 
3. setup your demand folder inside the scenario folder 
  3.a. E.g., create my_demand folder. add inputs
  3.b. Suggestion: add README.TXT here to describe the test and software.
4.execute with run_all script
  4.a. % ./run_all.sh my_demand 
5. drink coffee. laugh with friends.

Immediate output directories (based upon an input <my_demand>): 

my_demand-test: location of the simulator output. contains map log file too.
my_demand-analysis: output of the chart gen tool, i.e., a tabular summary of
the json sim outputs
my_demand-results: a set of tools and data points to generate charts with dot
and gnuplot

Instructions to build plots and plot booklet: 

6. cd my_demand_results - or to your demand test results folder 
7. (optional) update dcop windows  
  (NYI / XXX / ToDo - automate these steps) 
  7.a. cd 3-dcop 
  7.b. for each region, cat/append the dcop-<service-coords>_<region>-gnu.txt
       into the dcop-region<region>.gnu 
       e.g., % cat >> dcop_AppCoordinates_webservice_A-gnu.txt >> dcop-regionA.gnu 
  7.c. edit the dcop-region<region>.gnu file to place the "windows" above the plot
       line.  
8. ./build_plots.sh 
  8.a. this will generate a lot of charts as pngs and convert them to a pdf booklet.





