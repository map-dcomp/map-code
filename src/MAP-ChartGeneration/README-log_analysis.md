MAP-ChartGeneration Log Analysis


Description:
The log analysis feature of MAP-ChartGeneration scans a set of log files from an experiment or Hifi run node and categorizes log statements according to a given set of regular expressions.
Each regular expression defines a category of log statements. Each category contains the log statements that match its given pattern.

The analysis tracks certain statistics about the statements found within each category such as count and time intervals between consecutive statements.
If you expect a certain density of a certain type of log statement over time, or certain time intervals based on the rate at which a certain aspect
of MAP runs, you can use the output statisitcs on count or time intervals to help determine if the system is functioning properly.

-----------------------------------------------------------------------------------------------------------------

Performing a log analysis:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar log_analysis [matchers file] [input folder] [output folder]

	where
	[matchers file] is the files that defines the categories available for processing the input log statements. Each non-empty line in the file defines
					a category in [regular expression] -> [category label] format. The string " : |" can be appended to the line after the label
					to have analysis output a file containing all mathes in the category.
	[input folder] is the folder that contains log files for processing. All map-*.log files found directly in this folder or within /agent, /client, /[node name]/agent, or /[node name]/client
					will be aggregated together. In hifi, you can do a full analysis for all nodes in a run by specifying the overall run's folder, or you can run on a specific node folder. 
					In lofi, the log file for the entire run is used by specifying the run folder.
	[output folder] is the folder to which to output the resulting analysis files.


-----------------------------------------------------------------------------------------------------------------

Example [matchers file]. DCOP Iteration statements and Exceptions will be output to separate match files in addition to the summary files because of " : |" being specified on their lines : 


	.* -> All
	.*FATAL.* -> Level FATAL
	.*ERROR.* -> Level ERROR
	.*INFO.* -> Level INFO
	.*WARN.* -> Level WARN
	.*DEBUG.* -> Level DEBUG
	.*TRACE.* -> Level TRACE


	.*DCOPService.* -> DCOP
	.*DCOPService.*Iteration \d* Region .* -> DCOP Iteration Region : | 
	.*RLGService.* -> RLG

	.*ap\.program.* -> AP Program
	.*Exiting thread.* -> Exiting thread
	.*Exiting Protelis.* -> Exiting Protelis

	.*NetworkServer.* -> NetworkServer
	.*NodeNetworkManager.* -> NodeNetworkManager
	.*NetworkNeighbor.* -> NetworkNeighbor



	.*Exception.* -> Exceptions : |
	.*SocketException.* -> SocketException
	.*InterruptedException.* -> InterruptedException
	.*EOFException.* -> EOFException
	.*OptionalDataException.* -> OptionalDataException
	.*NullPointerException.* -> NullPointerException


-----------------------------------------------------------------------------------------------------------------
	
In [output folder], the following analysis results files will appear:	
	
	results_summary.txt - contains a summery of the results for the analysis. The summary contains a "Run result", which is a word SUCCESS or FAIL. Currently, this word is based on whether there were no exceptions in the run and whether the DCOP intervals are acceptable. However, this aspect of the summary is not up to date and can be ignored.
	The run duration in seconds is also listed and "Run finished", which is only valid in lofi is shown. The summary also containers a Requirement satisfaction section, which lists a set of criteria for SUCCESS and whether each component has been met. There is also an errors section about why certain criteria has not been met.
	
	The most informative part of the summary are the sections for each match category. Each category section shows the label and regex used to identify log statements from the category.
	The section shows number of matches found, the time of the first and last match, and lists off significant time intervals between consecutive statements separared by orders of magnitude 
	and the number of statements within each time interval in ( ).
	
	Below is an example of output. Looking at RLG time intervals, we can see
		0.01-0.74 (9496), which is likely due to statements within an RLG interation
		1.66-5.49 (2631), which is likely due to the interval between RLG rounds, and we can see how this interval deviates above and below the expected 3 seconds
		13.6-16.67 (2), which is likely showing that there were some anomalous intervals between RLG rounds in the run
	
					FAIL

					Summary
					   Run result: FAIL
					   Run duration (s): 9217.188
					   Run finished: false

					Requirement satisfaction
					   No bad exceptions: false
					   DCOP working: false

					Errors
					   Found large time interval between DCOP messages (s): 59.03-72.93 (132)
					   Found NullPointerException
					

					
					RLG [.*RLGService.*]
					   matches: 339867
					   first match time (s): 1256.221
					   last match time (s): 9214.886
					   significant time intervals between matches (s): [0.01-0.74 (9496), 1.66-5.49 (2631), 13.6-16.67 (2)]
				
					Exceptions [.*Exception.*]
					   matches: 113
					   first match time (s): 1257.987
					   last match time (s): 9217.096
					   significant time intervals between matches (s): [0.01 (19), 0.03-0.98 (30), 2.74 (1), 83.53 (1), 172.06 (1), 358.52-439.01 (3), 2244.71-4232.11 (2)]
		   
		   
	binned_category_match_counts.csv - Time binned counts of category matches. Each column is for a particular category, and each row is a run time bin containing a number of matches.
	binned_match_time_interval_counts.csv - Time binned counts of category match intervals. Each column is for a particular category, and each row is an interval time bin containing a number of pairs of 
		consecutive matches with time intervals that fall into that bin.
	category_match_occurrences.csv - Times of category matches. Each column is for a particular category, and each row is a time at which at least one match across all categories occurred.
		At a particular row for a category column, either no data is present, or an arbitrary number, which places the categories in a particular order for timeline charting purposes is present to indicate a match at that time for that category.
		
	[category label].txt -  A file containing statistics at the top and category matches is output for categories with " : |" specified in the matchers file.

-----------------------------------------------------------------------------------------------------------------


