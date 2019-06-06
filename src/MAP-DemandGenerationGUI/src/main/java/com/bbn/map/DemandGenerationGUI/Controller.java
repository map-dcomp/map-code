/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2019-03-14, with
the exception of the dcop implementation identified below (see notes).

Dispersed Computing (DCOMP)
Mission-oriented Adaptive Placement of Task and Data (MAP) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
BBN_LICENSE_END*/
package com.bbn.map.DemandGenerationGUI;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

/**
 * Implements the functionality of the Demand UI.
 * 
 * @author awald
 *
 */
public class Controller
{
    private static final Logger LOGGER = LogManager.getLogger(Controller.class);

    private static final double FIND_POINT_MAX_DISTANCE_TO_CURSOR = 5.0;

    private static final long DEFAULT_MAX_TIME = 10000;
    private static final double DEFAULT_MAX_VALUE = 100.0;
    private static final double DEFAULT_NETWORK_LOAD_RX = 0.001;
    private static final double DEFAULT_NETWORK_LOAD_TX = 0.001;

    private static final String CSV_HEADER_TIME = "time";
    private static final Object CSV_MISSING_VALUE = "?";

    private static final String APP_COORDINATES_GROUP = "com.bbn.map";
    private static final String APP_COORDINATES_VERSION = "1.0";
    
    private static final double MAX_TIME_PER_TICK_UNIT = 500.0;
    private static final double MOUSE_CURSOR_X_OFFSET = 15.0;
    private static final double MOUSE_CURSOR_Y_OFFSET = 15.0;

    private static final Random RANDOM = new Random(0);

    private static final ObjectWriter JSON_MAPPER = JsonUtils.getStandardMapObjectMapper()
            .writerWithDefaultPrettyPrinter();

    private Stage stage;
    private MenuBar menuBar;

    // public variables that are bound to UI controls
    // CHECKSTYLE:OFF
    public BorderPane root;

    // axis scaling controls
    public Button rescaleAxesButton;
    public TextField maxTimeTextField;
    public TextField maxValueTextField;
    public CheckBox addStartEndPointsCheckBox;

    // curve visualization controls
    public LineChart<Long, Double> curveChart;
    public NumberAxis curveChartXAxis;
    public NumberAxis curveChartYAxis;
    private Tooltip curveChartTooltip = new Tooltip();

    // generation controls
    public Button generatePointsButton;
    public TextField generatePointsTextField;

    // line controls
    public Button newLineButton;
    public ChoiceBox<LineIdentifier> lineChoiceBox;
    public TextField lineNameTextField;
    public Button lineRenameButton;
    public Button lineDeleteButton;

    // load spread controls
    public TextField loadSpreadDivisionsTextField;
    public TextField loadSpreadHalfRangeTextField;
    public TextField loadSpreadClientsMinTextField;
    public TextField loadSpreadClientsMaxTextField;

    // network load controls
    public TextField networkLoadRxTextField;
    public TextField networkLoadTxTextField;
    
    // transform controls
    public Button transformButton;
    public Button transformAllButton;
    public TextField scaleValueTextField;
    public TextField translateTimeTextField;
    public TextField translateValueTextField;
    
    

    // CHECKSTYLE:ON

    // point and line fields
    private List<LineIdentifier> lineIds = new ArrayList<>();
    private Map<LineIdentifier, Series<Long, Double>> seriesMap = new HashMap<>();
    private Map<LineIdentifier, Series<Long, Double>> generatedSeriesMap = new HashMap<>();
    private int lineCounter = 0;

    private Data<Long, Double> dragPoint = null;

    /**
     * @param stage
     *            the stage, which contains the scene for the window
     */
    public Controller(Stage stage)
    {
        this.stage = stage;
    }

    /**
     * Initializes the controls and state variables.
     */
    public void initialize()
    {
        menuBar = generateMenuBar();
        root.setTop(menuBar);

        maxTimeTextField.setText(Long.toString(DEFAULT_MAX_TIME));
        maxValueTextField.setText(Double.toString(DEFAULT_MAX_VALUE));
        addStartEndPointsCheckBox.setSelected(true);

        curveChart.legendVisibleProperty().set(true);
        curveChart.legendSideProperty().set(Side.RIGHT);

        curveChartXAxis.setLowerBound(0);
        curveChartXAxis.setForceZeroInRange(true);
        curveChartXAxis.setAutoRanging(false);
        curveChartXAxis.setTickUnit(1000);
        curveChartXAxis.setMinorTickCount(10);

        curveChartYAxis.setLowerBound(0.0);
        curveChartYAxis.setForceZeroInRange(true);
        curveChartYAxis.setAutoRanging(false);

        curveChart.animatedProperty().set(false);
        curveChart.setCursor(Cursor.CROSSHAIR);

        reinitializeChart();

        curveChart.addEventHandler(MouseEvent.MOUSE_RELEASED, clickToAddPointEventHandler);
        curveChart.addEventHandler(MouseEvent.MOUSE_PRESSED, grabPointEventHandler);
        curveChart.addEventHandler(MouseEvent.MOUSE_DRAGGED, movePointEventHandler);
        curveChart.addEventHandler(MouseEvent.MOUSE_MOVED, mouseHoverEventHandler);

        stage.addEventHandler(ScrollEvent.SCROLL, scrollLineSelectEventHandler);

        rescaleAxesButton.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                Long upperX = null;
                Double upperY = null;

                try
                {
                    upperX = Long.parseLong(maxTimeTextField.getText());
                } catch (NumberFormatException e)
                {
                }

                try
                {
                    upperY = Double.parseDouble(maxValueTextField.getText());
                } catch (NumberFormatException e)
                {
                }

                autoScaleAxes(upperX, upperY);
            }
        });

        newLineButton.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                addLine();
            }
        });

        lineChoiceBox.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                LineIdentifier currentLineId = lineChoiceBox.getValue();

                if (currentLineId != null)
                    selectLine(currentLineId);
            }
        });

        lineRenameButton.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                renameLine();
            }
        });

        lineDeleteButton.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                deleteLine();
            }
        });

        generatePointsButton.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                String nString = generatePointsTextField.getText();

                try
                {
                    int n = Integer.parseInt(nString);
                    generatePoints(n);
                    Set<LineIdentifier> linesWithZeros = checkForZeros(generatedSeriesMap);
                    
                    if (!linesWithZeros.isEmpty())
                    {
                        LOGGER.warn("The point generation resulted in points with zero compute load for the following lines: {}", linesWithZeros);
                        
                        Alert alert = new Alert(AlertType.WARNING);
                        alert.setTitle(stage.getTitle() + " - WARNING");
                        alert.setHeaderText(null);
                        alert.setContentText("The following lines contain generated points with zero load:\n" + linesWithZeros + "\n\nTo gaurantee that there are no zero points, move the specified line points above Y=0.");
                        alert.showAndWait();
                    }
                } catch (NumberFormatException e)
                {
                    LOGGER.error("'{}' is not a valid number of points.", nString);
                    removeGeneratedPoints();
                }
            }
        });
        
        
        transformButton.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                transformLines(getSelectedLineIdentifier());
                
            }
        });
        
        transformAllButton.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                transformLines(seriesMap.keySet());
            }
        });
    }
    


    private void autoScaleAxes(Long optionalNewUpperX, Double optionalNewUpperY)
    {
        long newUpperX = 0;
        double newUpperY = 0.0;

        for (Series<Long, Double> series : seriesMap.values())
        {
            for (Data<Long, Double> point : series.getData())
            {
                if (point.getXValue() > newUpperX)
                {
                    newUpperX = point.getXValue();
                }

                if (point.getYValue() > newUpperY)
                {
                    newUpperY = point.getYValue();
                }
            }
        }

        if (optionalNewUpperX != null)
        {
            newUpperX = optionalNewUpperX;
        }

        if (optionalNewUpperY != null)
        {
            newUpperY = optionalNewUpperY;
        }

        scaleAxes(newUpperX, newUpperY);
    }

    private void scaleAxes(long newUpperX, double newUpperY)
    {
        curveChartXAxis.setUpperBound(newUpperX);
        curveChartYAxis.setUpperBound(newUpperY);

        autoSetTickUnit();

        maxTimeTextField.setText(Long.toString(newUpperX));
        maxValueTextField.setText(Double.toString(newUpperY));
    }

    private void addLine()
    {
        LineIdentifier li = new LineIdentifier("Line " + lineCounter);
        lineCounter++;

        Series<Long, Double> series = new Series<Long, Double>();

        if (addStartEndPointsCheckBox.isSelected())
        {
            series.getData().add(new Data<Long, Double>(0L, 0.0));
            series.getData().add(new Data<Long, Double>(getMaxTime(), 0.0));
        }

        series.setName(li.getLabel());
        seriesMap.put(li, series);
        curveChart.getData().add(series);

        LOGGER.debug("seriesMap: {}", seriesMap);

        lineIds.add(li);
        lineChoiceBox.getItems().add(li);
        lineChoiceBox.setValue(li);
    }

    private void selectLine(LineIdentifier lineId)
    {
        lineNameTextField.setText(lineId.getLabel());
    }

    private void renameLine()
    {
        LineIdentifier currentLineId = lineChoiceBox.getValue();

        if (currentLineId != null)
        {
            int index = lineChoiceBox.getItems().indexOf(currentLineId);
            currentLineId.setLabel(lineNameTextField.getText());

            if (index > -1)
            {
                // set the name in the drop down
                lineChoiceBox.getItems().remove(index);
                lineChoiceBox.getItems().add(index, currentLineId);
                lineChoiceBox.setValue(currentLineId);

                // set the name of the series
                seriesMap.get(currentLineId).setName(currentLineId.getLabel());
            }
        }
    }

    private void deleteLine()
    {
        LineIdentifier currentLineId = lineChoiceBox.getValue();
        lineNameTextField.clear();

        deleteLine(currentLineId);
    }

    private void deleteLines()
    {
        List<LineIdentifier> lineIds = new ArrayList<>();
        lineIds.addAll(seriesMap.keySet());

        for (LineIdentifier lineId : lineIds)
            deleteLine(lineId);
    }

    private void deleteLine(LineIdentifier lineId)
    {
        LOGGER.debug("Delete line: {}", lineId);

        lineChoiceBox.getItems().remove(lineId);
        curveChart.getData().remove(seriesMap.get(lineId));
        curveChart.getData().remove(generatedSeriesMap.get(lineId));
        seriesMap.remove(lineId);
        generatedSeriesMap.remove(lineId);
        lineIds.remove(lineId);
    }
    
    
    private void transformLines(LineIdentifier line)
    {
        Set<LineIdentifier> lines = new HashSet<>();
        lines.add(line);
        transformLines(lines);
    }
    
    private void transformLines(Set<LineIdentifier> lines)
    {
        String scaleValueFactorStr = scaleValueTextField.getText();
        String translateTimeDeltaStr = translateTimeTextField.getText();
        String translateValueDeltaStr = translateValueTextField.getText();
        
        try
        {
            double scaleValueFactor = Double.parseDouble(scaleValueFactorStr);
            long translateTimeDelta = Long.parseLong(translateTimeDeltaStr);
            double translateValueDelta = Double.parseDouble(translateValueDeltaStr);
            
            lines.forEach((lineId) ->
            {
                if (lineId != null)
                {
                    scaleLine(lineId, scaleValueFactor);
                    translateLine(lineId, translateTimeDelta, translateValueDelta);
                }
            });            
            
        } catch (NumberFormatException e)
        {
            LOGGER.error("One or more of the transform values are not valid numbers: {}, {}, {}", scaleValueFactorStr, translateTimeDeltaStr, translateValueDeltaStr);
        
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle(stage.getTitle() + " - ERROR");
            alert.setHeaderText(null);
            alert.setContentText("One or more of the transform values are not valid numbers:\n" + scaleValueFactorStr + "\n" + translateTimeDeltaStr + "\n" + translateValueDeltaStr);
            alert.showAndWait();
        }
    }
    
    private void translateLine(LineIdentifier lineId, long deltaTime, double deltaValue)
    {
        Series<Long, Double> series = seriesMap.get(lineId);
        
        series.getData().forEach((point) ->
        {
            long x = point.getXValue();
            double y = point.getYValue();
            
            point.setXValue(x + deltaTime);
            point.setYValue(y + deltaValue);
        });
    }
    
    private void scaleLine(LineIdentifier lineId, double valueFactor)
    {
        Series<Long, Double> series = seriesMap.get(lineId);
        
        series.getData().forEach((point) ->
        {
            double y = point.getYValue();
            point.setYValue(y * valueFactor);
        });
    }

    private MenuBar generateMenuBar()
    {
        MenuBar menu = new MenuBar();

        Menu fileMenu = new Menu("File");
        {
            MenuItem newFile = new MenuItem("New");
            newFile.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    Alert alert = new Alert(AlertType.CONFIRMATION);
                    alert.setTitle("New file confirmation");
                    alert.setHeaderText(null);
                    alert.setContentText(
                            "Are you sure you would like to discard all unsaved changes and start a new file?");

                    double x = stage.getX() + stage.getWidth() / 2;
                    double y = stage.getY() + stage.getHeight() / 2;

                    alert.setX(x);
                    alert.setY(y);

                    Optional<ButtonType> result = alert.showAndWait();

                    if (result.get() == ButtonType.OK)
                    {
                        LOGGER.info("Starting new file by reinitializing");
                        reinitializeChart();
                    }
                }
            });

            MenuItem savePoints = new MenuItem("Save points...");
            savePoints.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.info("Saving points...");
                    savePoints(seriesMap, lineIds, false);
                }
            });

            MenuItem loadPoints = new MenuItem("Load points...");
            loadPoints.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.info("Loading points...");
                    loadPoints(seriesMap);
                    autoScaleAxes(null, null);
                }
            });

            MenuItem exportGeneratedPoints = new MenuItem("Export generated points...");
            exportGeneratedPoints.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.info("Exporting generating points...");
                    savePoints(generatedSeriesMap, lineIds, true);
                }
            });

            fileMenu.getItems().addAll(newFile, savePoints, loadPoints, exportGeneratedPoints);
        }

        menu.getMenus().addAll(fileMenu);

        return menu;
    }

    private void reinitializeChart()
    {
        String maxTimeString = maxTimeTextField.getText();
        String maxValueString = maxValueTextField.getText();

        Long maxTime = null;
        Double maxValue = null;

        try
        {
            maxTime = Long.parseLong(maxTimeString);
        } catch (NumberFormatException e)
        {
            LOGGER.error("'{}' is not a valid max time.", maxTimeString);
        }

        try
        {
            maxValue = Double.parseDouble(maxValueString);
        } catch (NumberFormatException e)
        {
            LOGGER.error("'{}' is not a valid max value.", maxValueString);
        }

        deleteLines();
        lineNameTextField.clear();

        seriesMap = new HashMap<>();
        lineIds = new ArrayList<>();

        autoScaleAxes(maxTime, maxValue);
    }

    private void autoSetTickUnit()
    {
        double tickUnit = 1000;
        long maxTime = (long) curveChartXAxis.getUpperBound();

        while (maxTime / tickUnit > MAX_TIME_PER_TICK_UNIT)
            tickUnit *= 10.0;

        curveChartXAxis.setTickUnit(tickUnit);
    }

    private void savePoints(Map<LineIdentifier, Series<Long, Double>> lines, List<LineIdentifier> lineIds,
            boolean includeJSONOutputOption)
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save points to file...");

        if (includeJSONOutputOption)
        {
            fileChooser.getExtensionFilters().add(jsonClientLoadExtensionFilter);
        }

        fileChooser.getExtensionFilters().addAll(pointFileExtensionFilter, allFileExtensionFilter);

        File file = fileChooser.showSaveDialog(stage);

        if (file != null)
        {
            LOGGER.info("Save to file: " + file);

            if (fileChooser.getSelectedExtensionFilter() == jsonClientLoadExtensionFilter)
            {
                outputPointsToJSONFile(lines, lineIds, file);
            } else
            {
                outputPointsToCSVFile(lines, lineIds, file);
            }
        }
    }


    private Map<Long, Map<LineIdentifier, RequestValue>> seriesToDataTable(
            Map<LineIdentifier, Series<Long, Double>> lines)
    {
        Map<LineIdentifier, Map<Long, Double>> data = new HashMap<>();

        for (Map.Entry<LineIdentifier, Series<Long, Double>> line : lines.entrySet())
        {
            LineIdentifier lineId = line.getKey();
            Map<Long, Double> dataForLine = data.computeIfAbsent(lineId, k -> new HashMap<>());

            for (Data<Long, Double> point : line.getValue().getData())
            {
                long time = point.getXValue();
                double load = point.getYValue();

                dataForLine.merge(time, load, Double::sum);
            }
        }


        Map<Long, Map<LineIdentifier, RequestValue>> requestValues = new HashMap<>();

        String halfTimeRangeStr = loadSpreadHalfRangeTextField.getText();
        String divisionsStr = loadSpreadDivisionsTextField.getText();
        String loadSpreadClientsMinStr = loadSpreadClientsMinTextField.getText();
        String loadSpreadClientsMaxStr = loadSpreadClientsMaxTextField.getText();

        long halfTimeRange = 0;
        int divisions = 1;

        try
        {
            halfTimeRange = Long.parseLong(halfTimeRangeStr);
        } catch (NumberFormatException e)
        {
            LOGGER.error("Invalid load spread half range '{}'. Resetting to {}.", halfTimeRangeStr, halfTimeRange);
            loadSpreadHalfRangeTextField.setText(Long.toString(halfTimeRange));
        }

        try
        {
            divisions = Integer.parseInt(divisionsStr);
        } catch (NumberFormatException e)
        {
            LOGGER.error("Invalid load spread number of divisions '{}'. Resetting to {}.", divisionsStr, divisions);
            loadSpreadDivisionsTextField.setText(Long.toString(divisions));
        }

        int minNumClients = 1;
        int maxNumClients = 1;

        if (loadSpreadClientsMinStr.isEmpty())
        {
            loadSpreadClientsMinStr = loadSpreadClientsMaxStr;
        }
        
        if (loadSpreadClientsMaxStr.isEmpty())
        {
            loadSpreadClientsMaxStr = loadSpreadClientsMinStr;
        }
        
        try
        {
            minNumClients = Integer.parseInt(loadSpreadClientsMinStr);
        } catch (NumberFormatException e)
        {
            LOGGER.error("Invalid min number of clients '{}'. Resetting to {}.", loadSpreadClientsMinStr,
                    minNumClients);
        }
        
        if (!loadSpreadClientsMaxStr.isEmpty())
        {
            try
            {
                maxNumClients = Integer.parseInt(loadSpreadClientsMaxStr);
            } catch (NumberFormatException e)
            {
                LOGGER.error("Invalid max number of clients '{}'. Resetting to {}.", loadSpreadClientsMaxStr,
                        maxNumClients);
            }
        }
        
        
        int a = Math.max(minNumClients, 1);
        int b = Math.max(maxNumClients, 1);
        minNumClients = Math.min(a, b);
        maxNumClients = Math.max(a, b);
        
        loadSpreadClientsMinTextField.setText(Long.toString(minNumClients));
        loadSpreadClientsMaxTextField.setText(Long.toString(maxNumClients));
        

        double networkLoadRx = DEFAULT_NETWORK_LOAD_RX;
        double networkLoadTx = DEFAULT_NETWORK_LOAD_TX;

        try
        {
            networkLoadRx = Double.parseDouble(networkLoadRxTextField.getText());
        } catch (NumberFormatException e)
        {
            networkLoadRxTextField.setText(Double.toString(networkLoadRx));
        }

        try
        {
            networkLoadTx = Double.parseDouble(networkLoadTxTextField.getText());
        } catch (NumberFormatException e)
        {
            networkLoadTxTextField.setText(Double.toString(networkLoadTx));
        }

        for (Map.Entry<LineIdentifier, Map<Long, Double>> lineDataEntry : data.entrySet())
        {
            LineIdentifier lineId = lineDataEntry.getKey();
            Map<Long, Double> lineData = lineDataEntry.getValue();

            List<Long> lineDataTimes = new ArrayList<>();
            lineDataTimes.addAll(lineData.keySet());
            Collections.sort(lineDataTimes);

            for (int t = 0; t < lineDataTimes.size() - 1; t++)
            {
                long currentTime = lineDataTimes.get(t);
                long nextTime = lineDataTimes.get(t + 1);
                long duration = nextTime - currentTime;

                double totalLoadAtCenterTime = data.get(lineId).get(currentTime);

                Map<Long, Double> spreadRequests = timeSpreadRequest(currentTime, totalLoadAtCenterTime, halfTimeRange,
                        divisions);

                for (Map.Entry<Long, Double> timeEntry : spreadRequests.entrySet())
                {
                    Long time = timeEntry.getKey();
                    Double computeLoad = timeEntry.getValue();

                    Map<LineIdentifier, RequestValue> dataForLine = requestValues.computeIfAbsent(time,
                            k -> new HashMap<>());
                    int totalNumClients = RANDOM.nextInt(maxNumClients - minNumClients + 1) + minNumClients;
                    RequestValue value = new RequestValue(duration, computeLoad, networkLoadRx, networkLoadTx,
                            totalNumClients / divisions);
                    dataForLine.merge(lineId, value, RequestValue::sum);
                }
            }
        }

        return requestValues;
    }

    private Map<Long, Double> timeSpreadRequest(long centerTime, double totalValue, long halfTimeRange, int divisions)
    {
        Map<Long, Double> result = new HashMap<>();

        double valuePerDivision = totalValue / divisions;
        double timeInterval;

        if (divisions != 1)
        {
            timeInterval = halfTimeRange * 2.0 / (divisions - 1);
        } else
        {
            halfTimeRange = 0;
            timeInterval = 0;
        }

        for (int n = 0; n < divisions; n++)
        {
            long time = centerTime - halfTimeRange + (long) (n * timeInterval);
            result.merge(time, valuePerDivision, Double::sum);
        }

        LOGGER.debug(
                "Time spread request centered at '{}' with total value '{}', half range '{}', and {} divisions: {}",
                centerTime, totalValue, halfTimeRange, divisions, result);
        return result;
    }

    private void outputPointsToCSVFile(Map<LineIdentifier, Series<Long, Double>> lines, List<LineIdentifier> lineIds,
            File file)
    {
        Map<Long, Map<LineIdentifier, RequestValue>> data = seriesToDataTable(lines);

        List<Long> times = new ArrayList<>();
        times.addAll(data.keySet());
        Collections.sort(times);

        String[] header = new String[1 + lineIds.size()];
        header[0] = CSV_HEADER_TIME;

        for (int l = 0; l < lineIds.size(); l++)
            header[1 + l] = lineIds.get(l).toString();

        try (Writer writer = Files.newBufferedWriter(file.toPath(), Charset.defaultCharset());
                CSVPrinter printer = new CSVPrinter(writer, CSVFormat.EXCEL.withHeader(header)))
        {

            for (int n = 0; n < times.size(); n++)
            {
                Long time = times.get(n);

                List<Object> record = new ArrayList<>();
                record.add(time);

                for (int l = 0; l < lineIds.size(); l++)
                {
                    LineIdentifier lineId = lineIds.get(l);
                    Object value = data.get(time).get(lineId).getTotalComputeLoad();

                    record.add(value);
                    LOGGER.debug("Adding point for line {}: {}, {}", lineId, time, value);
                }

                printer.printRecord(record);
            }

            printer.flush();
        } catch (IOException e)
        {
            LOGGER.error("Failed to output points to file: {}\n{}", file, e);
        }
    }

    private void outputPointsToJSONFile(Map<LineIdentifier, Series<Long, Double>> lines, List<LineIdentifier> lineIds,
            File file)
    {
        Map<Long, Map<LineIdentifier, RequestValue>> data = seriesToDataTable(lines);
        LOGGER.debug("Outputting data to JSON: {}", data);

        List<Long> times = new ArrayList<>();
        times.addAll(data.keySet());
        Collections.sort(times);

        List<ClientLoad> clientLoads = new ArrayList<>();

        // create ClientLoad objects
        for (int n = 0; n < times.size(); n++)
        {
            long startTime = times.get(n);

            Map<LineIdentifier, RequestValue> lineValues = data.get(startTime);

            for (Entry<LineIdentifier, RequestValue> entry : lineValues.entrySet())
            {
                LineIdentifier lineId = entry.getKey();
                RequestValue value = entry.getValue();

                ApplicationCoordinates service = new ApplicationCoordinates(APP_COORDINATES_GROUP, lineId.getLabel(), APP_COORDINATES_VERSION);

                Map<NodeMetricName, Double> nodeLoad = new HashMap<>();
                nodeLoad.put(NodeMetricName.TASK_CONTAINERS, value.getTotalComputeLoad() / value.getNumClients());

                Map<LinkMetricName, Double> networkLoad = new HashMap<>();
                networkLoad.put(LinkMetricName.DATARATE_RX, value.getNetworkLoadRx());
                networkLoad.put(LinkMetricName.DATARATE_TX, value.getNetworkLoadTx());

                long serverDuration = value.getDuration();
                long networkDuration = value.getDuration();
                ClientLoad clientLoad = new ClientLoad(startTime, serverDuration, networkDuration,
                        value.getNumClients(), service, ImmutableMap.copyOf(nodeLoad),
                        ImmutableMap.copyOf(networkLoad));

                clientLoads.add(clientLoad);
            }
        }

        // attempt to output the objects to JSON
        try
        {
            JSON_MAPPER.writeValue(file, clientLoads);
        } catch (JsonGenerationException e)
        {
            LOGGER.error("Failed to generate JSON\n", e);
        } catch (JsonMappingException e)
        {
            LOGGER.error("Failed to map JSON\n", e);
        } catch (IOException e)
        {
            LOGGER.error("Failed to generate JSON\n", e);
        }
    }

    private void loadPoints(Map<LineIdentifier, Series<Long, Double>> data)
    {
        LOGGER.info("Loading points...");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load points from file...");
        fileChooser.getExtensionFilters().addAll(pointFileExtensionFilter, allFileExtensionFilter);

        File file = fileChooser.showOpenDialog(stage);

        if (file != null)
        {
            LOGGER.info("Load from file: " + file);
            loadPointsFromCSVFile(seriesMap, file, false);
        }
    }

    private void loadPointsFromCSVFile(Map<LineIdentifier, Series<Long, Double>> lines, File file,
            boolean clearOldPoints)
    {
        String[] header = {CSV_HEADER_TIME};

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), Charset.defaultCharset()))
        {
            String headerLine = reader.readLine();

            if (headerLine != null)
                header = headerLine.split(",");

        } catch (FileNotFoundException e)
        {
            LOGGER.error("File not found: {}\n{}", file.getAbsolutePath(), e);
        } catch (IOException e)
        {
            LOGGER.error("{}", e);
        }

        LOGGER.debug("Loaded CSV header: {}", Arrays.toString(header));

        Map<String, LineIdentifier> stringToLineIdentifierMap = new HashMap<>();

        for (int n = 0; n < header.length; n++)
            stringToLineIdentifierMap.put(header[n], new LineIdentifier(header[n]));

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), Charset.defaultCharset());
                CSVParser parser = new CSVParser(reader, CSVFormat.EXCEL.withHeader(header).withSkipHeaderRecord()))
        {
            List<CSVRecord> records = parser.getRecords();

            if (clearOldPoints)
                lines.clear();

            for (CSVRecord record : records)
            {
                Long time = Long.parseLong(record.get(CSV_HEADER_TIME));

                for (int l = 1; l < header.length; l++)
                {
                    LineIdentifier lineId = stringToLineIdentifierMap.get(header[l]);

                    String valueString = record.get(lineId.getLabel());

                    if (!valueString.equals(CSV_MISSING_VALUE) && !valueString.equals(""))
                    {
                        Double value = Double.parseDouble(valueString);

                        if (!lines.containsKey(lineId))
                        {
                            Series<Long, Double> series = lines.computeIfAbsent(lineId, (line) -> new Series<>());
                            lineCounter++;

                            curveChart.getData().add(series);

                            LOGGER.debug("seriesMap: {}", seriesMap);

                            lineIds.add(lineId);
                            lineChoiceBox.getItems().add(lineId);
                            lineChoiceBox.setValue(lineId);
                        }

                        Series<Long, Double> series = lines.get(lineId);
                        series.setName(lineId.getLabel());
                        series.getData().add(new Data<Long, Double>(time, value));
                    }
                }
            }
        } catch (FileNotFoundException e)
        {
            LOGGER.error("{}", e);
        } catch (IOException e)
        {
            LOGGER.error("{}", e);
        }
    }

    private Data<?, ?> addPointUnderMouse(double mx, double my)
    {
        Number pointX = curveChart.getXAxis().getValueForDisplay(mx - MOUSE_CURSOR_X_OFFSET);
        Double pointY = curveChart.getYAxis().getValueForDisplay(my - MOUSE_CURSOR_Y_OFFSET);

        if (curveChartXAxis.getLowerBound() <= pointX.longValue()
                && pointX.longValue() <= curveChartXAxis.getUpperBound() && curveChartYAxis.getLowerBound() <= pointY
                && pointY <= curveChartYAxis.getUpperBound())
        {
            LOGGER.info("Add Point:  Mouse: (" + mx + ", " + my + ")" + "     Point: (" + pointX + ", " + pointY + ")");

            Series<Long, Double> points = getSelectedLine();

            if (points != null)
            {
                int n = 0;

                points.getData();
                while (n < points.getData().size() && points.getData().get(n).getXValue() < pointX.longValue())
                    n++;

                Data<Long, Double> point = new Data<Long, Double>(pointX.longValue(), pointY);
                points.getData().add(n, point);

                return point;
            }
        }

        return null;
    }

    private void removePoint(double mx, double my)
    {
        Data<?, ?> point = findClosestPoint(mx, my);

        if (point != null)
            getSelectedLine().getData().remove(point);
    }

    private void movePoint(Data<Long, Double> point, double mx, double my, boolean boundPoint)
    {
        Number pointX = curveChart.getXAxis().getValueForDisplay(adjustMouseX(mx));
        Double pointY = curveChart.getYAxis().getValueForDisplay(adjustMouseY(my));

        if (boundPoint)
        {
            pointX = Math.max(curveChartXAxis.getLowerBound(),
                    Math.min(pointX.longValue(), curveChartXAxis.getUpperBound()));
            pointY = Math.max(curveChartYAxis.getLowerBound(), Math.min(pointY, curveChartYAxis.getUpperBound()));
        }

        point.setXValue(pointX.longValue());
        point.setYValue(pointY);
    }

    private Data<Long, Double> findClosestPoint(double mx, double my)
    {
        Data<Long, Double> bestPoint = null;
        double bestDistance = FIND_POINT_MAX_DISTANCE_TO_CURSOR;

        Series<Long, Double> points = getSelectedLine();

        if (points != null)
        {
            for (int p = 0; p < points.getData().size(); p++)
            {
                Data<Long, Double> point = points.getData().get(p);
                double x = curveChartXAxis.getDisplayPosition(point.getXValue());
                double y = curveChartYAxis.getDisplayPosition(point.getYValue());

                double dist = Math.sqrt(Math.pow(x - adjustMouseX(mx), 2) + Math.pow(y - adjustMouseY(my), 2));

                if (dist <= bestDistance)
                {
                    bestPoint = point;
                    bestDistance = dist;
                }
            }
        }

        LOGGER.trace("Find point close to (" + mx + ", " + my + "): " + bestPoint);
        return bestPoint;
    }

    private void removeGeneratedPoints()
    {
        generatedSeriesMap.forEach((lineId, line) ->
        {
            curveChart.getData().remove(line);
        });

        generatedSeriesMap.clear();
    }

    private void generatePoints(int n)
    {
        generatedSeriesMap.forEach((lineId, line) ->
        {
            curveChart.getData().remove(line);
        });

        generatedSeriesMap.clear();

        seriesMap.forEach((lineId, line) ->
        {
            generatePoints(lineId, n);
        });
    }
    
    private Set<LineIdentifier> checkForZeros(Map<LineIdentifier, Series<Long, Double>> generatedSeriesMap)
    {
        Set<LineIdentifier> linesWithZeros = new HashSet<>();
        
        generatedSeriesMap.forEach((lineId, series) ->
        {
            series.getData().forEach((point) ->
            {
                if (point.getYValue() == 0.0)
                {
                    linesWithZeros.add(lineId);
                }
            });
        });
        
        return linesWithZeros;
    }

    private void generatePoints(LineIdentifier lineId, int n)
    {
        if (n < 0)
            return;

        Series<Long, Double> points = seriesMap.get(lineId);
        Series<Long, Double> generatedSeries = generatedSeriesMap.computeIfAbsent(lineId, (line) -> new Series<>());
        generatedSeries.setName("   [" + n + "]");

        int index = curveChart.getData().indexOf(points);
        curveChart.getData().add(index + 1, generatedSeries);

        sortPoints(points.getData());

        if (n > 0)
        {
            double totalDistance = 0.0;

            for (int p = 0; p < points.getData().size() - 1; p++)
            {
                Data<Long, Double> p1 = points.getData().get(p);
                Data<Long, Double> p2 = points.getData().get(p + 1);
                double distance = p2.getXValue() - p1.getXValue();
                totalDistance += distance;
            }

            double intervalDistance = totalDistance / (n - 1);
            double remainingDistance = 0.0;

            for (int p = 0; p < points.getData().size() - 1; p++)
            {
                Data<Long, Double> p1 = points.getData().get(p);
                Data<Long, Double> p2 = points.getData().get(p + 1);
                double distance = p2.getXValue() - p1.getXValue();

                while (remainingDistance <= distance)
                {
                    double x = (remainingDistance / distance) * (p2.getXValue() - p1.getXValue()) + p1.getXValue();
                    double y = (remainingDistance / distance) * (p2.getYValue() - p1.getYValue()) + p1.getYValue();
                    generatedSeries.getData().add(new Data<Long, Double>(Math.round(x), y));
                    remainingDistance += intervalDistance;
                }

                remainingDistance -= distance;
            }
        }

        generatedSeries.getNode().setStyle("-fx-stroke-width: 0;");
    }

    private long getMaxTime()
    {
        return (long) curveChartXAxis.getUpperBound();
    }

    private void sortPoints(List<Data<Long, Double>> series)
    {
        series.sort(new Comparator<Data<Long, ?>>()
        {
            @Override
            public int compare(Data<Long, ?> a, Data<Long, ?> b)
            {
                return (int) Math.signum(a.getXValue() - b.getXValue());
            }
        });
    }

    private double adjustMouseX(double mx)
    {
        return (mx - MOUSE_CURSOR_X_OFFSET);
    }

    private double adjustMouseY(double my)
    {
        return (my - MOUSE_CURSOR_Y_OFFSET);
    }

    private final EventHandler<MouseEvent> clickToAddPointEventHandler = new EventHandler<MouseEvent>()
    {
        @Override
        public void handle(MouseEvent event)
        {

            if (dragPoint == null)
            {
                if (event.getButton() == MouseButton.PRIMARY)
                {
                    Data<?, ?> point = addPointUnderMouse(event.getX(), event.getY());
                    updateHoverTooltip(point, event.getX(), event.getY());
                } else if (event.getButton() == MouseButton.SECONDARY)
                {
                    removePoint(event.getX(), event.getY());
                }
            } else
            {
                LOGGER.info("Drop point: " + dragPoint);
                dragPoint = null;
            }
        }
    };

    private final EventHandler<MouseEvent> grabPointEventHandler = new EventHandler<MouseEvent>()
    {
        @Override
        public void handle(MouseEvent event)
        {
            if (event.getButton() == MouseButton.PRIMARY)
            {
                dragPoint = findClosestPoint(event.getX(), event.getY());
            }
        }
    };

    private final EventHandler<MouseEvent> movePointEventHandler = new EventHandler<MouseEvent>()
    {
        @Override
        public void handle(MouseEvent event)
        {
            if (dragPoint != null)
            {
                movePoint(dragPoint, event.getX(), event.getY(), true);
                updateHoverTooltip(dragPoint, event.getX(), event.getY());
            }
        }
    };

    private final EventHandler<MouseEvent> mouseHoverEventHandler = new EventHandler<MouseEvent>()
    {
        @Override
        public void handle(MouseEvent event)
        {
            Data<?, ?> point = findClosestPoint(event.getX(), event.getY());
            updateHoverTooltip(point, event.getX(), event.getY());
        }
    };

    private final EventHandler<ScrollEvent> scrollLineSelectEventHandler = new EventHandler<ScrollEvent>()
    {
        @Override
        public void handle(ScrollEvent event)
        {
            relativeSelectLine((int) Math.signum(event.getTextDeltaY()));
        }
    };

    private void relativeSelectLine(int delta)
    {
        int size = lineChoiceBox.getItems().size();

        int newIndex = 0;

        if (size > 0)
        {
            newIndex = lineChoiceBox.getSelectionModel().getSelectedIndex() + delta;

            while (newIndex < 0)
            {
                newIndex += size;
            }

            newIndex = newIndex % lineChoiceBox.getItems().size();
        }

        lineChoiceBox.getSelectionModel().select(newIndex);
    }

    private void updateHoverTooltip(Data<?, ?> point, double mx, double my)
    {
        if (point != null)
        {
            curveChartTooltip.setText(String.format("(%d, %.3f)", point.getXValue(), point.getYValue()));
            curveChartTooltip.show(stage, stage.getX() + curveChart.getLayoutX() + mx,
                    stage.getY() + curveChart.getLayoutY() + my);
        } else
        {
            curveChartTooltip.hide();
        }
    }

    private Series<Long, Double> getSelectedLine()
    {
        Series<Long, Double> selectedLine = seriesMap.get(getSelectedLineIdentifier());
        LOGGER.trace("getSelectedLine: " + selectedLine);

        return selectedLine;
    }

    private LineIdentifier getSelectedLineIdentifier()
    {
        LineIdentifier lineId = lineChoiceBox.getValue();
        LOGGER.trace("getSelectedLine: " + lineId);
        return lineId;
    }

    private final ExtensionFilter jsonClientLoadExtensionFilter = new ExtensionFilter("JSON Client Load files",
            "*.json");
    private final ExtensionFilter pointFileExtensionFilter = new ExtensionFilter("Point files", "*.csv");
    private final ExtensionFilter allFileExtensionFilter = new ExtensionFilter("All files", "*.*");
}
