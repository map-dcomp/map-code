Demand Generation GUI

Tool for user to graphically define demand curves, which control load that clients in MAP put on the system over time.


Build Notes
To build, you must have a version of Java with JavaFX installed. Jenkins is configured to use OpenJFX when building this application.
Originally built with jre1.8.0_181

-----------------------------------------------------------------------------------------------------

Using the application


--- Modes ---
The Mode menu contains two overall modes for the demand generation UI:
- Lo-Fi/Hi-Fi: "Lines specify number of clients" allows you to draw lines that define how number of clients varies over time. You can select a Load Profile to use for the unit of load as a set of node and network load attributes for Lo-Fi experiments.
- Lo-Fi: "Lines specify load attributes" allows you to draw lines that directly specify the value for a selected node Load Attribute.


--- Files that can be loaded ---
The file menu allows you to load the following files for use in the UI:
Load Points... - Loads a CSV file with a column per line and a row for each time containing at least 1 point
Load Service Configurations... - Loads a service-configurations.json file to obtain ApplicationCoordinates objects for optionally associating with lines.
Load load profiles... - Loads 1 or more ClientLoadProfile objects from JSON files for use in the "Lines specify number of clients" mode



--- Initializing a new file ---
1. Set the maximum time boundary, which is the highest value displayed in the horizontal time axis of the graph.
2. Set the maximum value boundary, which is the highest value displayed on the vertical axis of the graph.
3. Select File > New.
4. Click OK in confirmation dialog to proceed with the initialization. All lines will be deleted.



--- Rescaling the Axes ---
1. Enter a new maximum time value in the "Maximum Time" field. Leave the field empty for automatic scaling of the X-axis time value.
2. Enter a new maximum value in the "Maximum Value" field. Leave the field empty for automatic scaling of the maximum Y-axis value.
3. Click the "Rescale Axes" button to perform the rescaling.



--- Creating and renaming a line ---
1. Select Lines > New Line to create a new line. It's name will appear in the rename text field.
2. Ensure that the line that you would like to rename is selected from the drop down menu.
3. Modify the name in the text field.
4. Click the "Rename" button to give the line a new name.



--- Deleting a line ---
1. Ensure that the line you would like to delete is selected.
2. Select Line > Delete Line to delete the line and any generated points associated with the line.



--- Editing a line ---
1. Ensure that the line you would like to edit is selected either by choosing it in the drop down or by using the mouse scroll wheel.
2. Snap Point Values - if checked, forces the point being dragged in the chart to snap to the nearest 1.0 second in time and nearest integer value.
3. Make changes, one point at a time:
	a. Left click - place point at center of crosshair
	b. Left click and drag - move point to a new location
	c. Right click - remove point near crosshair
4. Optionally Select a service to associate with the line in Service drop down. You may need to load ApplicationCoordinates object from a service-configurations.json file first.



--- Saving user drawn points to CSV file ---
1. Select File > Save Points...
2. Choose a file name and location and click Save. The file can later be reloaded so that you do not need to redefine the same lines again.



--- Load Spread ---
Feature that prevents demand from being too concentrated at each generated point.
For exporting points, replaces each generated point with a set of points centered about the generated point.
1. Specify number of Divisions, which is the number of points to export centered about the generated point. Each new point will contain a demand equal to the original generated point's value / Divisions so that the total demand remains the same.
2. Specify Half Range (ms), which is half of the span in time within which the set of points is centered.
3. Specify a client range which is a range for a random number of clients to divide load among at each exported point (Lo-fi only mode).



--- Network Load ---
When using the Lo-fi only mode, you can specify constant values for RX and TX network load for each request.



--- Generating points for later export to demand ---
1. In the drop-down below "Generate Points" button, select the method of point generation.
See com.bbn.map.DemandGenerationGUI.PointGenerationMethodIdentifier and below for documentation on generation methods.

Time Value Line Distance - method that evenly spaces points across the line distance
Time Distance - method that evenly spaces points in time
Common Time Interval - method that evenly spaces points in time using a given interval in milliseconds. This method produces points aligned in time across all lines.
Value Distance - method that evenly spaces points across changes in Y value
Copy User Points - method that simply copies the user placed points for the generated points

2. In the text field below the "Generate Points" button, enter a value, which can represent number of points (Time Value Line Distance, Time Distance, Value Distance methods), time interval in milliseconds (Common Time Interval method), or is unused (Copy User Points method) depending on the generation method.
3. Click the "Generate Points" button or Lines > Generate Points to generate a set of equally spaced points for each line. The legend will be modified to show the colors of the generated points.



--- Exporting generated points to a JSON or CSV file ---
1. If you are exporting to JSON ClientLoad objects, enter the number of clients for each object in the "Clients" text field.
2. Select File > Export generated points...
3. Choose a file name and location and either JSON or CSV format.
4. Click Save to export to the file.



--- Transforming lines ---
To quickly translate or scale the value of a line
1. Enter a factor by which to scale the height of the line in Scale Value
2. Enter a horizontal time shift in the Translate Time field
3. Enter a vertical value shift in the Translate Value field
4. Click the Transform button to transform the current line or Transform All to transform all lines.



--- Demand Template Feature ---
File > Set demand template... - used to set a folder containing a set of ClientLoadTemplate object JSON files for later generating a batch of client pool files simultaneously.
File > Generate demand using template... - used to output a folder structure containing filled demand templates in accordance to the selected template folder
