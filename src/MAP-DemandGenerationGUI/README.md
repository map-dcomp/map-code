Demand Generation GUI

Tool for user to graphically define demand curves, which control load that clients in MAP put on the system over time.


Build Notes
To build, you must have a version of Java with JavaFX installed. Jenkins is configured to use OpenJFX when building this application.
Originally built with jre1.8.0_181

-----------------------------------------------------------------------------------------------------

Using the application


--- Initializing a new file ---
1. Set the maximum time boundary, which is the highest value displayed in the horizontal time axis of the graph.
2. Set the maximum value boundary, which is the highest value displayed on the vertical axis of the graph.
3. Select File > New.
4. Click OK in confirmation dialog to procede with the initialization. All lines will be deleted.



--- Rescaling the Axes ---
1. Enter a new maximum time value in the "Maximum Time" field. Leave the field empty for automatic scaling of the X-axis time value.
2. Enter a new maximum value in the "Maximum Value" field. Leave the field empty for automatic scaling of the maximum Y-axis value.
3. Click the "Rescale Axes" button to perform the rescaling.



--- Creating and renaming a line ---
1. If you would like the new line to include a point at the start and end of time with value 0, ensure "Add start/end points" is checked.
2. Click the "New Line" button to create a new line. It's name will appear in the rename text field.
3. Ensure that the line that you would like to rename is selected from the drop down menu.
4. Modify the name in the text field.
5. Click the "Rename" button to give the line a new name.



--- Deleting a line ---
1. Ensure that the line you would like to delete is selected.
2. Click the "Delete" button to delete the line and any generated points associated with the line.



--- Editing a line ---
1. Ensure that the line you would like to edit is selected either by choosing it in the drop down or by using the mouse scroll wheel.
2. Make changes, one point at a time:
	a. Left click - place point at center of crosshair
	b. Left click and drag - move point to a new location
	c. Right click - remove point near crosshair



--- Generating points that occur at a fixed interval ---
1. In the text field next to the "Generate Points" button, enter the number of points per line.
2. Click the "Generate Points" button to generate a set of equally spaced points for each line. The legend will be modified to show the colors of the generated points.



--- Saving a file ---
1. Select File > Save Points...
2. Choose a file name and location and click Save.

--- Loading a file ---
1. Select File > Load points...
2. Choose a file and click Open. After the points are loaded, the axes will be scaled to fit the new set of points.

--- Exporting generated points to a JSON or CSV file ---
1. If you are exporting to JSON ClientLoad objects, enter the number of clients for each object in the "Clients" text field.
2. Select File > Export generated points...
3. Choose a file name and location and either JSON or CSV format.
4. Click Save to export to the file.
