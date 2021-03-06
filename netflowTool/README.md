# netflowTool
This is a web based tool designed to visualize network flows of a simulated network. It runs on javascript and the d3 library. The tool works offline but needs to be hosted on a server. A simple python server is included in the main directory named server.py. Python3.6 is required for the server and the other python scripts. For the best experience use Google Chrome to connect to the server, but it should work with other webbrowsers.

# Dependencies
* Python3.6
* pip3
* nfdump - installed locally as part of setup.sh
  * requires the packages: libbz2-dev flex bison doxygen
* d3.v5.js, jquery-3.5.1.js, jquery-ui.js (which are in the git repository)

# Running the Simulation
After setup.sh is finished, copy config.json.template to config.json and edit it. Then the data for the visualization needs to be created by just running masterScript.py. If everything ran correctly you should see the 4-5 files mentioned below in webdata. Use python to run server.py and it will start a server on localhost:3000. If any of the .json files are changed the server will need to be restarted to ensure that the new files are used.

# Data Required by the Website
The website needs 4 jsons to visualize the flows. The website reads these files from the webdata directory:
	1. netconfig.json: This json contains the information about how many nodes there are and what links are between them. This file is created from a .ns file and the topologyReader.py script. The file from topologyReader.py doesn't have xy information for the nodes. This can be loaded into the website and the nodes can be dragged around into the optimal layout. This layout can be saved by the "Save Layout" button and then replacing the netconfig.json in the webdata directory.
	2. routingTable.json: This file contains a quagga-like table showing the next step from one node to another. Every node get's it's own table that has the next step based on any destination ip. A path can be generated by consulting the table of the next step until the destination is reached. This file can be created by routingTable.py which creates an estimated routing table. The simpler the network, the more accurate routingTable.py will be. The better option would be a quagga file from the network simulation.
	3. portNames.json: This file contains the names of services that typically run on a port. This file is static and doesn't need to be changed.
	4. flowData.json: This file contains the flow records that were recorded. This file is created from nfcapd files with merger.py.
	5. nodeData.json(optional): This file contains timestamped entries with most the node's cpu usage data. This is optional as the visualization will work without this file.

# Scripts
There are a handful of python and bash scripts that help create the data used for the website. They require Python3.6, newer versions may also work.
	1. setup.sh: This program installs python3.6, pip3, and nfdump.
	2. topologyReader.py: This script reads through topology.ns to create a json containing all the nodes, their ip, and what links connect them. It does this by looking for key words in the .ns files like "tb-set-ip-lan" that specify what data is in the command. The output is netconfig.json which is required by some of the other scripts so it shouldn't be moved into the webdata directory until all other scripts have been run. This script should be run with the first command-line arguments as the path to the topology.ns file, or the topology.ns file should be in the main directory.
		Inputs: config.json, *.ns
		Outputs: netconfig.json
	3. routingTable.py: This script creates a table of next steps for paths to other nodes. It creates this table by reading in the configuration from netconfig.json and doing a breadth-first search between all the nodes to find a path between them. If there is only one possible path between nodes, then the table will be correct. If there is more than one way, then it will try to choose the best route between nodes but this will not be 100% accurate to what the flows are actually doing. The best route finding is not very robust as getting a routing table from the simulation will be a much better option in most cases. If the routing table changes during the simulation then routingTable.json will need to upscaled in some way. This script is ran with no command-line arguments.
		Inputs: netconfig.json
		Outputs: routingTable.json
	4. merger.py: This script finds all the recorded flows and merges them into one file with all the flows. The first command-line argument should be the path to the directory with the node directories containing nfcapd files. The script loops through the node directories looking for nodes with nfcapd files in them. It uses nfdump to read the nfcapd files into the mergeFiles directory as a json-like text file. It then converts it into a json file and merges the files from all the different nodes. During the merge, it checks for duplicate flows recorded from multiple sources and removes any duplicates. This process may not be 100% accurate and hasn't been tested with any simulated network latency on the links.
		Inputs: config.json, netconfig.json, routingTable.json
		Outputs: flowData.json
	5. flowSplitter.py: This script will split flowData.json in the specified number of sections based on time. This only needs to be run if the original flowData.json is too big to be read in by the website. Problematic sizes are around over 1GB.
	6. gatherNFCAPDFiles.sh: Run from merger.py to run the command to read the nfcapd files to jsons.
    7. compare-app-load.py:  This script is in MAP-code/src/MAP-ChartGeneration/scripts and needs to be run before nodeDataReader.py. The output folder of this script then needs to be put in the config.json
	8. nodeDataReader.py: Reads the outputs of compare-app-load.py to get the cpu load information of the nodes and put them in "ready to be graphed in D3" formats.
		Inputs: config.json,
		Outputs: nodeData.json
	9. server.py: Runs a simple python server so the website can be accessed by localhost in a webbroswer.
	10. masterScript.py: This script runs topologyReader.py, routingTable.py, and merger.py and moves the outputs into webdata.
	11. export.sh: After you have run all the scripts to make the data, this script groups only the necessary files to run the simulation and tars and zips them. This tar can be sent to other people and all they have to do is run server.py to see the visualization. This exported version will work on Windows, Linux, and Mac.

# Using the website
	Buttons: (Most buttons are accessed through right clicking on the simulation)
		"Save Layout": This button saves the xy positions of the network layout into the downloaded file. Replace netconfig.json with the downloaded file to update the positions.
		"Load Flows": This loads flowData.json and will attempts to load nodeData.json if it exists. You can tell it worked it the time slider shows up. If it fails to load flowData.json it will attempt to load flowData0.json then flowData1.json and so on.
		"Replay: This asks for Window Size which is the window of seconds that is displayed in the graphs. It also asks for how many seconds you want the window to move by and where to start the end of the window.
		"Pause": This allows you to pause and play the simulation when it is in replay mode.
		"Set Detail": Controls the amount of data points on the graphs. Defaults to 1000. Turning the detail up too much can cause the replay to run at a slower pace.
		"Swap": This swaps out the main network graph for a chord diagram.
		"Show/Hide Times": This will overlay a vertical black line over key times of the scenario like node failures
		"Green button on bottom righthand side of graphs": These allow you to disable a graph from updating. This is useful because some of the graphs take a bit of time to compute and if you aren't interested in them they don't need to be updated.
		 
