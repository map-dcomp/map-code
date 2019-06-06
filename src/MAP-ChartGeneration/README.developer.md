Chart Table Generation Developer Documentation


Build:
./gradlew build



Generating chart tables:
java -jar build/libs/MAP-ChartGeneration-0.0.1-executable.jar [chart type] ...



-----------------------------------------------------------------------------------------------------------------

Adding new chart generation routines


Preliminary Notes:
- Ensure that any JSON files that are required as input can be deserialized by the Jackson JSON library.
	You may need to implement deserializers to deserialize JSON files. 
	The package 'com.bbn.map.ChartGeneration.deserializers' cotains examples of deserializers.
	
	
Main Steps:
1. Create a new class for the new chart generation routine.

2. Add a constructor to the class to prepare any objects to read in simulation files. 
	This may include an ObjectMapper for deserializing JSON files.
	
3. Add a method that executes the routine. 
	The method should accept the inputs that will be supplied to the main class on the command line when running the routine.
	
	Common inputs:
	[scenario configuration folder] is the configuration folder for the scenario
	[demand scenario configuration folder] is the configuration folder for a particular demand scenario within a scenario
	[input folder] is the resulting data outputted from a simulation of the scenario 
	[output folder] is the folder to output the chart tables to
	[data sample interval] is the time interval between consecutive samples of scenario data in milliseconds. This should be the same value that was used when running the scenario.
	
	
	
4. Update the ChartGeneration class to construct an instance of the class for the routine and call the routine method.
	a. Add a constant for the [chart type] parameter, which is used when running the Jar file
	b. Add a line including the [chart type] constant followed by Strings representing additional command line parameters to the String[][] CHART_PARAMETERS constant.
	c. Add a new case to the switch in main for the [chart type] constant.
		In this case statement, read command line arguments, construct an object for your table generation routine, and call the method.
	
	
