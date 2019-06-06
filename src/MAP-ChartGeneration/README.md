Chart Table Generation


Build:
./gradlew build



Generating chart tables:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar [chart type] ...


-----------------------------------------------------------------------------------------------------------------
Generating all charts:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar all [scenario configuration folder] [demand scenario configuration folder] [input folder] [output folder] [data sample interval]

see definitions of parameters for each chart type below
-----------------------------------------------------------------------------------------------------------------
Generating node and region compute load tables:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar load [scenario configuration folder] [input folder] [output folder] [data sample interval]

	replace "load" with "load_0" to center the first time bin at time 0 rather than at the first data point in time

	where
	[scenario configuration folder] is the configuration folder for the scenario.
	[input folder] is the resulting data outputted from a simulation of the scenario. 
	[output folder] is the folder to output the chart tables to.
	[data sample interval] is the time interval between consecutive samples of scenario data in milliseconds. This should be the same value that was used when running the scenario.
	
In [output folder], the following files will appear for each compute load attribute:
	nodes-[compute load attribute].csv		- the compute load for each node over time
	regions-[compute load attribute].csv		- the binned compute load for each region over time
-----------------------------------------------------------------------------------------------------------------
Generating planned client pool demand tables:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar client_demand [demand scenario configuration folder] [output folder] [data sample interval]
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar client_demand [demand scenario configuration folder] [output folder]

	replace "client_demand" with "client_demand_0" to start output at time 0

	where
	[demand scenario configuration folder] is the configuration folder for a particular demand scenario within a scenario.
	[output folder] is the folder to output the chart tables to.
	[data sample interval] optionally specifies the time interval between consecutive samples of outputted scenario data in milliseconds. If unspecified, a sample is outputted when load for a service changes.

In [output folder], the following files will appear for each compute load attribute:
	[client pool]-[compute load attribute].csv	- planned client demand that each service in a client pool will place on the MAP system over time
-----------------------------------------------------------------------------------------------------------------
Generating client requests results tables:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar requests_results [input folder] [output folder]

	where
	[input folder] is the resulting data outputted from a simulation of the scenario.
	[output folder] is the folder to output the chart tables to.

In [output folder], the following files will appear for the requests results of each client:
	[client pool]-1.csv			- succeeded, failed
	[client pool]-2.csv			- succeeded, failed_for_server, failed_for_network
	[client pool]-3.csv			- succeeded, failed_for_server, failed_for_network, slow_for_server, slow_for_network
-----------------------------------------------------------------------------------------------------------------
Generating client requests to regions results tables:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar requests_to_regions [scenario configuration folder] [input folder] [output folder]

	where
	[scenario configuration folder] is the configuration folder for the scenario.
	[input folder] is the resulting data outputted from a simulation of the scenario.
	[output folder] is the folder to output the chart tables to.

In [output folder], the following files will appear for the requests results of each client:
	[client pool]-requests_to_regions.csv			- table showing the number of requests sent from [client pool] to each region over time
-----------------------------------------------------------------------------------------------------------------
Generating DNS groupings by region and service:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar dns [scenario configuration folder] [input folder] [output folder]

	where
	[scenario configuration folder] is the configuration folder for the scenario.
	[input folder] is the resulting data outputted from a simulation of the scenario.
	[output folder] is the folder to output the DNS grouping files to.

In [output folder], the following files will appear for the DNS request groupings for each combination of [source region], [service], and [destination region]:
	dns_[source region]--[destination region].csv			- all DNS requests sent from [source region] to [destination region]
	dns_[source region]--[service]--[destination region].csv	- all DNS requests sent from [source region] for [service] to [destination region]
-----------------------------------------------------------------------------------------------------------------
Generating DNS record count tables:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar dns_record_count [input folder] [output folder]

	where
	[input folder] is the resulting data outputted from a simulation of the scenario.
	[output folder] is the folder to output the DNS grouping files to.

In [output folder], the following files will appear for each region-service pair:
	DNS_[region name]-[service name].csv			- table showing the number of records delegating to each region and the number of records pointing to each node
-----------------------------------------------------------------------------------------------------------------
Generating RLG plan update table:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar rlg_plan_updates [input folder] [output folder] [data sample interval]

	where
	[input folder] is the resulting data outputted from a simulation of the scenario.
	[output folder] is the folder to output the chart tables to.
	[data sample interval] specifies the time interval between consecutive samples of outputted scenario data in milliseconds. This is used for binning of load balancer plans in time.

In [output folder], the following files will appear:
	region_[region name]-rlg_leaders.csv				- tables showing which nodes in region with name [region name] are an RLG leader at each bin time. '1' indicates that the node is a leader and '0' inidcates that the node is not a leader 
	region_[region name]-rlg_plan_change_times.csv			- tables showing times when the service plan changed or when the overflow plan changed. '1' indicates that a plan changed and '0' indicates that the plan did not change.
	service_[service name]-rlg_service_plans.csv			- table for each service showing updates to the number of instances of a service to run on each node at each time
	service_[service name]-rlg_overflow_plans.csv			- table for each service showing updates to the amount of overflow for each source-destination region pair at each time
-----------------------------------------------------------------------------------------------------------------
Generating DCOP plan leader and update tables:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar dcop_plan_updates [input folder] [output folder] [data sample interval]

	where
	[input folder] is the resulting data outputted from a simulation of the scenario.
	[output folder] is the folder to output the chart tables to.
	[data sample interval] specifies the time interval between consecutive samples of outputted scenario data in milliseconds. This is used for binning of region plans in time.

In [output folder], the following files will appear:
	region_[region name]-dcop_leaders.csv				- table showing which nodes in region with name [region name] are a DCOP leader at each bin time. '1' indicates that the node is a leader and '0' inidcates that the node is not a leader 
	region_[region name]-dcop_plan_change_times.csv			- a column of times indicating when the DCOP plan in the region with name [region name] changed
	region_[region name]-dcop_plan_changes_by_service.csv		- table showing when the plan for each service in region with name [region name] changes over time. '1' indicates that the plan for a particular service changed and '0' inidcates that the plan did not change
	service_[service name]-dcop_plan_request_distribution.csv	- table for each service showing updates to the distribution of requests at each time
-----------------------------------------------------------------------------------------------------------------













	
	
	