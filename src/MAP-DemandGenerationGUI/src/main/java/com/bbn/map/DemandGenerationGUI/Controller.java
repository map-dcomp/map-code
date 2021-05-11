/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.ServiceConfiguration;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.demand_template.ClientLoadProfile;
import com.bbn.map.demand_template.ClientLoadTemplate;
import com.bbn.map.demand_template.DemandTemplateUtils;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
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
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
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

    private static final ObjectMapper JSON_MAPPER = JsonUtils.getStandardMapObjectMapper();

    private static final double AUTOSCALE_UPPER_Y_FACTOR = 1.1;
    
    //CHECKSTYLE:OFF
    private ClientLoadProfileWrapper[] DEFAULT_LOAD_PROFILES =
    {
            new ClientLoadProfileWrapper( new ClientLoadProfile(
                    ImmutableMap.of(NodeAttribute.TASK_CONTAINERS, 0.1),
                    ImmutableMap.of(LinkAttribute.DATARATE_RX, 0.0, LinkAttribute.DATARATE_TX, 0.0)), null),
            new ClientLoadProfileWrapper( new ClientLoadProfile(
                    ImmutableMap.of(NodeAttribute.TASK_CONTAINERS, 0.1),
                    ImmutableMap.of(LinkAttribute.DATARATE_RX, 0.00001, LinkAttribute.DATARATE_TX, 0.00001)), null),
            new ClientLoadProfileWrapper( new ClientLoadProfile(
                    ImmutableMap.of(NodeAttribute.TASK_CONTAINERS, 0.1),
                    ImmutableMap.of(LinkAttribute.DATARATE_RX, 0.001, LinkAttribute.DATARATE_TX, 0.001)), null),
            
            new ClientLoadProfileWrapper(new ClientLoadProfile(
                    ImmutableMap.of(NodeAttribute.TASK_CONTAINERS, 1.0),
                    ImmutableMap.of(LinkAttribute.DATARATE_RX, 0.0, LinkAttribute.DATARATE_TX, 0.0)), null),
            new ClientLoadProfileWrapper(new ClientLoadProfile(
                    ImmutableMap.of(NodeAttribute.TASK_CONTAINERS, 1.0),
                    ImmutableMap.of(LinkAttribute.DATARATE_RX, 0.00001, LinkAttribute.DATARATE_TX, 0.00001)), null),
            new ClientLoadProfileWrapper(new ClientLoadProfile(
                    ImmutableMap.of(NodeAttribute.TASK_CONTAINERS, 1.0),
                    ImmutableMap.of(LinkAttribute.DATARATE_RX, 0.001, LinkAttribute.DATARATE_TX, 0.001)), null)
    };
    //CHECKSTYLE:ON
    
    
    // stores the directory that the save and open window should start with
    private File saveDirectory = null;

    private Stage stage;
    private MenuBar menuBar;

    // public variables that are bound to UI controls
    // CHECKSTYLE:OFF
    public BorderPane root;

    // axis scaling controls
    public Button rescaleAxesButton;
    public TextField maxTimeTextField;
    public TextField maxValueTextField;

    // curve visualization controls
    public LineChart<Long, Double> curveChart;
    public NumberAxis curveChartXAxis;
    public NumberAxis curveChartYAxis;
    private Tooltip curveChartTooltip = new Tooltip();

    // generation controls
    public Button generatePointsButton;
    public ChoiceBox<PointGenerationMethodIdentifier> generatePointsMethodChoiceBox;
    public TextField generatePointsTextField;
    public TextField generatePointsOffsetTextField;

    // line controls
    public ChoiceBox<LineIdentifier> lineChoiceBox;
    public TextField lineNameTextField;
    public Button lineRenameButton;
    public ChoiceBox<ApplicationCoordinates> lineServiceChoiceBox;
    public Label loadProfileLabel;
    public ChoiceBox<ClientLoadProfileWrapper> loadProfileChoiceBox;
    

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
    
    // status bar
    public Label statusBarText;
    
    

    // CHECKSTYLE:ON

    // point and line fields
    private List<LineIdentifier> lineIds = new ArrayList<>();
    private Map<LineIdentifier, Series<Long, Double>> seriesMap = new HashMap<>();
    private Map<LineIdentifier, Series<Long, Double>> generatedSeriesMap = new HashMap<>();
    private int lineCounter = 0;

    private Data<Long, Double> dragPoint = null;
    
    
    // File configuration
    private File demandTemplateInput = null;
    private File demandTemplateOutput = null;
    
    
    private boolean snapPointValues = true;
    private Map<LineIdentifier, ApplicationCoordinates> lineServiceMap = new HashMap<>();
    private Map<LineIdentifier, ClientLoadProfileWrapper> lineClientLoadProfileMap = new HashMap<>();
    private Map<LineIdentifier, ClientLoadProfileWrapper> lineLoadNodeAttributeMap = new HashMap<>();
    
    private Mode mode = Mode.LINE_VARIES_NUMBER_OF_CLIENTS;
    private ObservableList<ClientLoadProfileWrapper> selectableLoadProfiles = FXCollections.observableArrayList();
    private ObservableList<ClientLoadProfileWrapper> selectableLoadAttributes = FXCollections.observableArrayList();  
    
    
    
    private enum Mode
    {
        LINE_VARIES_NUMBER_OF_CLIENTS,
        LINE_VARIES_NODE_LOAD
    }
    
    
    private void initializeTooltips()
    {
          Map<Control, String> tooltipText = new HashMap<>();
          tooltipText.put(maxValueTextField, "The maximum value (numClients or load unit depending on mode) "
                  + "to display on the chart.");
          tooltipText.put(maxTimeTextField, "The maximum time in milliseconds to display on the chart.");


          tooltipText.put(transformButton, "Apply tranformation only to currently selected line.");
          tooltipText.put(transformAllButton, "Apply transformation to all lines.");
          tooltipText.put(scaleValueTextField, "The factor by which to vertically scale values on the chart.");
          tooltipText.put(translateTimeTextField,
              "The amount by which to horizontally translate points on one or more lines in time.");
          tooltipText.put(translateValueTextField,
              "The amount by which to vertically translate points on one or more lines.");
      
      
          tooltipText.put(generatePointsMethodChoiceBox, "The available methods of "
                  + "generating demand points to export.");
          tooltipText.put(generatePointsTextField, "Depending on the generation method, "
                  + "either the number of points to generate, the time interval in milliseconds between points, or not used.");
          
          
          tooltipText.put(loadSpreadDivisionsTextField, "The number of feathered points in a set per generated "
                  + "point to export to demand JSON.");
          tooltipText.put(loadSpreadHalfRangeTextField, "Half of the range within which a set of feathered "
                  + "points are to span.");
          tooltipText.put(loadSpreadClientsMinTextField, "The minimum number of clients to divde load among for "
                  + "a request. [Lo-fi only]");
          tooltipText.put(loadSpreadClientsMaxTextField, "The maximum number of clients to divde load among for "
                  + "a request. [Lo-fi only]");
          
          
          tooltipText.put(networkLoadRxTextField, "The total DATARATE_RX network load to use for all time. [Lo-fi only]");
          tooltipText.put(networkLoadTxTextField, "The total DATARATE_TX network load to use for all time. [Lo-fi only]");
      
          
          tooltipText.forEach((node, text) ->
          {
              Tooltip t = new Tooltip(text);
              node.setTooltip(t);
          });
      }  
    
    
    private static class ClientLoadProfileWrapper
    {
        private NodeAttribute nodeAttribute;
        
        private ClientLoadProfile clientLoadProfile;
        private File file;
        
        ClientLoadProfileWrapper(NodeAttribute nodeAttribute)
        {
            this.nodeAttribute = nodeAttribute;
            this.clientLoadProfile = null;
            this.file = null;
        }
        
        ClientLoadProfileWrapper(ClientLoadProfile clientLoadProfile, File file)
        {
            this.nodeAttribute = null;
            this.clientLoadProfile = clientLoadProfile;
            this.file = file;
        }
        
        NodeAttribute getNodeAttribute()
        {
            return nodeAttribute;
        }
        
        ClientLoadProfile getClientLoadProfile()
        {
            return clientLoadProfile;
        }

        File getFile()
        {
            return file;
        }
        
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            
            if (nodeAttribute != null)
            {
                sb.append(nodeAttribute.getName());
            } else if (clientLoadProfile != null)
            {
                List<Entry<NodeAttribute, Double>> nodeEntries = new LinkedList<>();
                nodeEntries.addAll(getClientLoadProfile().getNodeLoad().entrySet());
                Collections.sort(nodeEntries, new Comparator<Entry<NodeAttribute, Double>>() {
                    @Override
                    public int compare(Entry<NodeAttribute, Double> a, Entry<NodeAttribute, Double> b)
                    {
                        return a.getKey().getName().compareTo(b.getKey().getName());
                    }
                });
                
                List<Entry<LinkAttribute, Double>> linkEntries = new LinkedList<>();
                linkEntries.addAll(getClientLoadProfile().getNetworkLoad().entrySet());
                Collections.sort(linkEntries, new Comparator<Entry<LinkAttribute, Double>>() {
                    @Override
                    public int compare(Entry<LinkAttribute, Double> a, Entry<LinkAttribute, Double> b)
                    {
                        return a.getKey().getName().compareTo(b.getKey().getName());
                    }
                });
                
                if (getFile() != null)
                {
                    sb.append(getFile().getName().replaceAll(".json\\z", "")).append(" - ");
                }
                
                for (int n = 0; n < nodeEntries.size(); n++)
                {
                    NodeAttribute attr = nodeEntries.get(n).getKey();
                    Double value = nodeEntries.get(n).getValue();
                    
                    if (n > 0)
                    {
                        sb.append(", ");
                    }
                    
                    sb.append(attr.getName()).append("=").append(value);
                }
                
                sb.append(" | ");
                
                for (int n = 0; n < linkEntries.size(); n++)
                {
                    LinkAttribute attr = linkEntries.get(n).getKey();
                    Double value = linkEntries.get(n).getValue();
                    
                    if (n > 0)
                    {
                        sb.append(", ");
                    }
                    
                    sb.append(attr.getName()).append("=").append(value);
                }
            }
            
            return sb.toString();
        }
        
        @Override
        public boolean equals(Object o)
        {
            if (o instanceof ClientLoadProfileWrapper)
            {
                ClientLoadProfileWrapper other = (ClientLoadProfileWrapper) o;
                
                boolean a = (getNodeAttribute() == other.getNodeAttribute() ||
                            getNodeAttribute() != null && getNodeAttribute().equals(other.getNodeAttribute()));
                
                boolean b = (getFile() == other.getFile() || 
                             getFile() != null && getFile().equals(other.getFile())) &&
                            (getClientLoadProfile() == other.getClientLoadProfile() ||
                             getClientLoadProfile() != null && getClientLoadProfile().equals(other.getClientLoadProfile()));
                
                return (a && b);
            }
            
            return false;
        }
        
        @Override
        public int hashCode()
        {
            return Objects.hashCode(nodeAttribute, clientLoadProfile, file);
        }
    }
    
    

    /**
     * @param stage
     *            the stage, which contains the scene for the window
     */
    public Controller(Stage stage)
    {
        this.stage = stage;
    }

    
    private void setMode(Mode mode)
    {
        switch (mode)
        {
            case LINE_VARIES_NUMBER_OF_CLIENTS:
                loadProfileChoiceBox.setItems(selectableLoadProfiles);
                loadProfileLabel.setText("Load Profile");
                
                loadSpreadClientsMinTextField.setDisable(true);
                loadSpreadClientsMaxTextField.setDisable(true);
                
                networkLoadRxTextField.setDisable(true);
                networkLoadTxTextField.setDisable(true);
                break;
                
            case LINE_VARIES_NODE_LOAD:
                loadProfileChoiceBox.setItems(selectableLoadAttributes);
                loadProfileLabel.setText("Load Attribute");
                
                loadSpreadClientsMinTextField.setDisable(false);
                loadSpreadClientsMaxTextField.setDisable(false);
                
                networkLoadRxTextField.setDisable(false);
                networkLoadTxTextField.setDisable(false);
                break;
                
            default:
                LOGGER.error("Unsupported mode: {}", mode);
                return;
        }

        this.mode = mode;
        LOGGER.info("Changed mode to {}.", this.mode);
        
        LineIdentifier lineId = getSelectedLineIdentifier();
        
        if (lineId != null)
        {
            selectLine(lineId);
        }
    }
    
    /**
     * Initializes the controls and state variables.
     */
    public void initialize()
    {
        menuBar = generateMenuBar();
        root.setTop(menuBar);
        
        selectableLoadProfiles.clear();
        selectableLoadProfiles.addAll(DEFAULT_LOAD_PROFILES);
        
        selectableLoadAttributes.clear();
        selectableLoadAttributes.add(new ClientLoadProfileWrapper(NodeAttribute.TASK_CONTAINERS));
        selectableLoadAttributes.add(new ClientLoadProfileWrapper(NodeAttribute.CPU));
        selectableLoadAttributes.add(new ClientLoadProfileWrapper(NodeAttribute.MEMORY));
        
        setMode(mode);

        maxTimeTextField.setText(Long.toString(DEFAULT_MAX_TIME));
        maxValueTextField.setText(Double.toString(DEFAULT_MAX_VALUE));
        
        generatePointsMethodChoiceBox.getItems().clear();
        for (int i = 0; i < PointGenerationMethodIdentifier.values().length; i++)
        {
            PointGenerationMethodIdentifier methodId = PointGenerationMethodIdentifier.values()[i];
            generatePointsMethodChoiceBox.getItems().add(methodId);
            
            if (i == 0)
            {
                generatePointsMethodChoiceBox.setValue(methodId);
            }
        }

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

        lineChoiceBox.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                LineIdentifier currentLineId = lineChoiceBox.getValue();

                if (currentLineId != null)
                {
                    selectLine(currentLineId);
                }
            }
        });
        
        lineServiceChoiceBox.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                LineIdentifier lineId = getSelectedLineIdentifier();
                ApplicationCoordinates app = lineServiceChoiceBox.getSelectionModel().getSelectedItem();
                
                lineServiceMap.put(lineId, app);
                LOGGER.debug("lineServiceMap: {}", lineServiceMap);
            }
        });
        
        loadProfileChoiceBox.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                LineIdentifier lineId = getSelectedLineIdentifier();
                ClientLoadProfileWrapper profile = loadProfileChoiceBox.getSelectionModel().getSelectedItem();
                
                switch (mode)
                { 
                    case LINE_VARIES_NUMBER_OF_CLIENTS:
                        lineClientLoadProfileMap.put(lineId, profile);
                        LOGGER.debug("lineClientLoadProfileMap: {}", lineClientLoadProfileMap);
//                        loadProfileChoiceBox.getSelectionModel().select(lineClientLoadProfileMap.get(lineId));
                        break;
                        
                    case LINE_VARIES_NODE_LOAD:
                        lineLoadNodeAttributeMap.put(lineId, profile);
                        LOGGER.debug("lineLoadNodeAttributeMap: {}", lineLoadNodeAttributeMap);
//                        loadProfileChoiceBox.getSelectionModel().select(lineLoadNodeAttributeMap.get(lineId));
                        break;
                        
                    default:
                        break;
                }
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

        generatePointsButton.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                generatePoints();
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
        
        
        initializeTooltips();
        
        addLine();
    }
    

    private void generatePoints()
    {
        String nString = generatePointsTextField.getText();
        String offsetString = generatePointsOffsetTextField.getText();

        try
        {
            int n = Integer.parseInt(nString);
            long offset = Long.parseLong(offsetString);
            generatePoints(n, offset);
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
                    newUpperY = Math.ceil(point.getYValue() * AUTOSCALE_UPPER_Y_FACTOR);
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

//        if (addStartEndPointsCheckBox.isSelected())
//        {
//            series.getData().add(new Data<Long, Double>(0L, 0.0));
//            series.getData().add(new Data<Long, Double>(getMaxTime(), 0.0));
//        }

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
        lineServiceChoiceBox.getSelectionModel().select(lineServiceMap.get(lineId));
        
        switch (mode)
        {
            case LINE_VARIES_NODE_LOAD:
                loadProfileChoiceBox.getSelectionModel().select(lineLoadNodeAttributeMap.get(lineId));
                break;
                
            case LINE_VARIES_NUMBER_OF_CLIENTS:
                loadProfileChoiceBox.getSelectionModel().select(lineClientLoadProfileMap.get(lineId));
                break;
                
            default:
                break;
        }
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
            
            MenuItem loadServiceConfigurations = new MenuItem("Load Service Configurations...");
            loadServiceConfigurations.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.info("Loading Service Configurations...");
                    loadServiceConfigurations();
                }
            });
            
            MenuItem loadLoadProfiles = new MenuItem("Load load profiles...");
            loadLoadProfiles.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.info("Loading load profiles...");
                    loadLoadProfiles();
                }
            });

            MenuItem exportGeneratedPoints = new MenuItem("Export to client demand JSON file...");
            exportGeneratedPoints.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.info("Exporting generating points...");
                    savePoints(generatedSeriesMap, lineIds, true);
                }
            });
            
            MenuItem setDemandTemplate = new MenuItem("Set demand template...");
            setDemandTemplate.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.info("Set demand template...");
                    setDemandTemplate();
                }
            });
            
            MenuItem setDemandTemplateOutput = new MenuItem("Generate demand using template...");
            setDemandTemplateOutput.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.info("Generate demand using template...");
                    setDemandTemplateOutput();
                }
            });
            
            MenuItem exit = new MenuItem("Exit");
            exit.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.info("Exit...");
                    
                    Alert alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO);
                    ((Button)alert.getDialogPane().lookupButton(ButtonType.YES)).setDefaultButton(false);
                    ((Button)alert.getDialogPane().lookupButton(ButtonType.NO)).setDefaultButton(true);
                    alert.setTitle(stage.getTitle());
                    alert.setHeaderText(null);
                    alert.setContentText("Are you sure you would like to quit?");
                    
                    Optional<ButtonType> response = alert.showAndWait();
                    
                    if (response.isPresent())
                    {
                        if (ButtonType.YES.equals(response.get()))
                        {
                            exit();
                        } else if (ButtonType.NO.equals(response.get()))
                        {
                            LOGGER.info("Exit canceled");
                        }
                    }
                               
                }
            });


            fileMenu.getItems().addAll(newFile, savePoints, loadPoints, loadServiceConfigurations, 
                    loadLoadProfiles, exportGeneratedPoints, setDemandTemplate, setDemandTemplateOutput, exit);
        }

        Menu linesMenu = new Menu("Lines");
        {
            MenuItem newLine = new MenuItem("New Line");
            newLine.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.debug("Lines -> New Line");
                    addLine();
                }
            });

            MenuItem deleteLine = new MenuItem("Delete Line");
            deleteLine.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.debug("Lines -> Delete Line");
                    deleteLine();
                }
            });
            
            CheckMenuItem snapPointValuesMenuItem = new CheckMenuItem("Snap point values");
            snapPointValuesMenuItem.setSelected(snapPointValues);
            snapPointValuesMenuItem.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.debug("Lines -> Snap Point Values: {}", snapPointValuesMenuItem.isSelected());
                    snapPointValues = snapPointValuesMenuItem.isSelected();
                }
            });
            
            MenuItem generatePoints = new MenuItem("Generate Points");
            generatePoints.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    LOGGER.debug("Lines -> Generate Points");
                    generatePoints();
                }
            });
            
            linesMenu.getItems().addAll(newLine, deleteLine, snapPointValuesMenuItem, new SeparatorMenuItem(), generatePoints);
        }
        
        Menu modeMenu = new Menu("Mode");
        {
            ToggleGroup modeGroup = new ToggleGroup();
            
            RadioMenuItem numberOfClientsMenuItem = new RadioMenuItem("Lines specify number of clients (Hi-Fi / Lo-Fi)");
            numberOfClientsMenuItem.setSelected(Mode.LINE_VARIES_NUMBER_OF_CLIENTS.equals(mode));
            numberOfClientsMenuItem.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                   mode = Mode.LINE_VARIES_NUMBER_OF_CLIENTS;
                   setMode(mode);                    
                }
            });
            numberOfClientsMenuItem.setToggleGroup(modeGroup);
            
            RadioMenuItem nodeLoadMenuItem = new RadioMenuItem("Lines specify node load (Lo-Fi)");
            nodeLoadMenuItem.setSelected(Mode.LINE_VARIES_NODE_LOAD.equals(mode));
            nodeLoadMenuItem.setOnAction(new EventHandler<ActionEvent>()
            {
                @Override
                public void handle(ActionEvent event)
                {
                    mode = Mode.LINE_VARIES_NODE_LOAD;
                    setMode(mode);          
                }
            });
            nodeLoadMenuItem.setToggleGroup(modeGroup);
            
            modeMenu.getItems().addAll(numberOfClientsMenuItem, nodeLoadMenuItem);
        }
        
        menu.getMenus().addAll(fileMenu, linesMenu, modeMenu);

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
        
        if (saveDirectory != null)
        {
            fileChooser.setInitialDirectory(saveDirectory);
        }

        if (includeJSONOutputOption)
        {
            fileChooser.getExtensionFilters().add(jsonClientLoadExtensionFilter);
        }

        fileChooser.getExtensionFilters().addAll(pointFileExtensionFilter, allFileExtensionFilter);

        File file = fileChooser.showSaveDialog(stage);  


        if (file != null)
        {
            if (file.getParentFile() != null)
            {
                saveDirectory = file.getParentFile();
            }
            
            LOGGER.info("Save to file: " + file);

            if (fileChooser.getSelectedExtensionFilter() == jsonClientLoadExtensionFilter)
            {
                outputPointsToJSONFile(lines, lineIds, file);
            } else
            {
                outputPointsToCSVFile(lines, lineIds, file, mode);
            }
        }
    }

    private void setDemandTemplate()
    {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Set demand template input...");
        
        if (demandTemplateInput != null && demandTemplateInput.exists())
        {
            dirChooser.setInitialDirectory(demandTemplateInput);
        }

        File file = dirChooser.showDialog(stage);


        if (file != null)
        {
            demandTemplateInput = file;
        }
        
        updateStatusBarText();
    }
    
    private void updateStatusBarText()
    {
        StringBuilder text = new StringBuilder();
        
        if (demandTemplateInput != null)
        {
            text.append("Demand template: ").append(demandTemplateInput.getAbsolutePath());
        }
        
        statusBarText.setText(text.toString());
    }
    
    private void setDemandTemplateOutput()
    {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Generate demand using template...");
        
        if (demandTemplateOutput != null)
        {
            dirChooser.setInitialDirectory(demandTemplateOutput);
        }
        else if (demandTemplateInput != null)
        {
            dirChooser.setInitialDirectory(demandTemplateInput.getParentFile());
        }
        else
        {
            LOGGER.warn("Cannot generate demand from template since no template is currently set.");
            
            Alert alert = new Alert(AlertType.WARNING);
            alert.setTitle(stage.getTitle() + " - WARNING");
            alert.setHeaderText(null);
            alert.setContentText("Cannot generate demand from template since no template is currently set.");
            alert.showAndWait();
            
            return;
        }

        File file = dirChooser.showDialog(stage);

        if (file != null)
        {
            demandTemplateOutput = file;
            
            if (!demandTemplateOutput.equals(demandTemplateInput))
            {
                Alert alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO);
                alert.setTitle(stage.getTitle());
                alert.setHeaderText(null);
                alert.setContentText("Would you like to generate and output demand files to '"
                        + demandTemplateOutput + "' using templates at '" + demandTemplateInput + "'?");
                
                Optional<ButtonType> response = alert.showAndWait();
                
                if (response.isPresent())
                {
                    if (ButtonType.YES.equals(response.get()))
                    {
                        generateDemandUsingTemplate(demandTemplateInput, demandTemplateOutput);
                    } else if (ButtonType.NO.equals(response.get()))
                    {
                        LOGGER.info("Generation of demand using template canceled.");
                    }
                }
            }
            else
            {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle(stage.getTitle() + " - ERROR");
                alert.setHeaderText(null);
                alert.setContentText("The output folder (" + demandTemplateOutput + ") cannot be the same as the demand template input folder (" + demandTemplateInput + ").");
                alert.showAndWait();
            }
        }
    }

    
    private void generateDemandUsingTemplate(File templateInput, File outputDirectory)
    {
        LOGGER.info("Generating demand to '{}' using template directory '{}'", outputDirectory, templateInput);
        
        // load demand templates
        Map<String, Map<NodeIdentifier, ImmutableSet<ClientLoadTemplate>>> clientDemandTemplates = 
                ClientLoadTemplate.parseAllClientDemandTemplates(templateInput.toPath());
        
        // prepare time data values
        Map<Long, Map<String, Number>> symbolValueMaps = new HashMap<>();
        addSeriesToSymbolValueMaps(symbolValueMaps, generatedSeriesMap);
        addConstantsToSymbolValueMaps(symbolValueMaps);
        
        
        clientDemandTemplates.forEach((relativePath, nodeTemplates) ->
        {
            File demandOutputDir = new File(outputDirectory.getAbsolutePath() + relativePath);
            
            if (demandOutputDir.exists() || demandOutputDir.mkdirs())
            {
                nodeTemplates.forEach((node, templates) ->
                {
                    File demandOutputFile = new File(demandOutputDir + File.separator + node.getName() + ".json");
                    
                    LOGGER.debug("generateDemandUsingTemplate: Outputting demand to '{}'", demandOutputFile);
                    
                    List<ClientLoad> clientLoads = DemandTemplateUtils.generateClientLoadFromTemplates(templates,
                            symbolValueMaps);
    
                    // attempt to output the objects to JSON
                    try
                    {
                        JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(demandOutputFile, clientLoads);
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
                    
                });
            }
        });
    }
    


    private Map<Long, Map<LineIdentifier, Double>> seriesToDataTable(
            Map<LineIdentifier, Series<Long, Double>> lines)
    {
        Map<Long, Map<LineIdentifier, Double>> data = new HashMap<>();

        for (Map.Entry<LineIdentifier, Series<Long, Double>> line : lines.entrySet())
        {
            LineIdentifier lineId = line.getKey();
            Series<Long, Double> lineData = line.getValue();
            
            lineData.getData().forEach((point) ->
            {
                long time = point.getXValue();
                double value = point.getYValue();
                
                Map<LineIdentifier, Double> dataAtTime = data.computeIfAbsent(time, k -> new HashMap<>());
                
                dataAtTime.merge(lineId, value, Double::sum);
            });
        }
        
        return data;
    }
    
    
    private Map<Long, Map<LineIdentifier, RequestValue>> seriesToRequestValuesDatatable(
            Map<LineIdentifier, Series<Long, Double>> lines, Mode mode)
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
                NodeAttribute loadAttribute = NodeAttribute.TASK_CONTAINERS;
                ClientLoadProfile loadProfile = DEFAULT_LOAD_PROFILES[0].getClientLoadProfile();
                
                if (lineLoadNodeAttributeMap.get(lineId) != null)
                {
                    loadAttribute = lineLoadNodeAttributeMap.get(lineId).getNodeAttribute();                             
                }
                
                if (lineClientLoadProfileMap.get(lineId) != null)
                {
                    loadProfile = lineClientLoadProfileMap.get(lineId).getClientLoadProfile();
                }
                

                Map<Long, Double> spreadRequests = timeSpreadRequest(currentTime, totalLoadAtCenterTime, halfTimeRange,
                        divisions);
                
                LOGGER.debug("seriesToRequestValuesDatatable: spreadRequests = {}", spreadRequests);

                for (Map.Entry<Long, Double> timeEntry : spreadRequests.entrySet())
                {
                    Long time = timeEntry.getKey();
                    double value = timeEntry.getValue();
                    

                    final int totalNumClients;
                    Map<NodeAttribute, Double> nodeLoad = new HashMap<>();
                    Map<LinkAttribute, Double> networkLoad = new HashMap<>();
                    
                    switch (mode)
                    {     
                        case LINE_VARIES_NUMBER_OF_CLIENTS:
                            totalNumClients = (int)Math.round(value);
                            
                            loadProfile.getNodeLoad().forEach((attr, v) ->
                            {
                                nodeLoad.put(attr, v * totalNumClients);
                            });
                            
                            loadProfile.getNetworkLoad().forEach((attr, v) ->
                            {
                                networkLoad.put(attr, v * totalNumClients);
                            });
                            
                            break;
                            
                        case LINE_VARIES_NODE_LOAD:
                            totalNumClients = RANDOM.nextInt(maxNumClients - minNumClients + 1) + minNumClients;
                            
                            if (lineLoadNodeAttributeMap.get(lineId) != null)
                            {
                                nodeLoad.put(lineLoadNodeAttributeMap.get(lineId).getNodeAttribute(), value);                               
                            }
                            else
                            {
                                nodeLoad.put(NodeAttribute.TASK_CONTAINERS, value);
                            }
                            
                            // total network load
                            networkLoad.put(LinkAttribute.DATARATE_RX, networkLoadRx);
                            networkLoad.put(LinkAttribute.DATARATE_TX, networkLoadTx);
                            break;
                            
                        default:
                            totalNumClients = 1;
                            break;
                    }
                    
                    Map<LineIdentifier, RequestValue> dataForLine = requestValues.computeIfAbsent(time,
                            k -> new HashMap<>());
                    RequestValue requestValue = new RequestValue(duration, totalNumClients, nodeLoad, networkLoad);
                    dataForLine.merge(lineId, requestValue, RequestValue::sum);
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
            long time = centerTime - halfTimeRange + (long) Math.round(n * timeInterval);
            
            if (time >= 0)
            {
                result.merge(time, valuePerDivision, Double::sum);
            }
        }

        LOGGER.debug(
                "timeSpreadRequest: Time spread request centered at '{}' with total value '{}', half range '{}', and {} divisions: {}",
                centerTime, totalValue, halfTimeRange, divisions, result);
        
        return result;
    }

    private void outputPointsToCSVFile(Map<LineIdentifier, Series<Long, Double>> lines, List<LineIdentifier> lineIds,
            File file, Mode mode)
    {
        Map<Long, Map<LineIdentifier, Double>> data = seriesToDataTable(lines); //seriesToRequestValuesDatatable(lines);
        
        LOGGER.debug("outputPointsToCSVFile: data = {}", data);

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
                    
                    Object point = data.get(time).get(lineId);
                    Object value = CSV_MISSING_VALUE;
                    
                    if (point != null)
                    {
                        if (point instanceof RequestValue)
                        {
                            RequestValue p = (RequestValue) point;
                            
                            switch (mode)
                            {
                                case LINE_VARIES_NUMBER_OF_CLIENTS:
                                    value = p.getNumClients();
                                    break;
                                    
                                case LINE_VARIES_NODE_LOAD:
                                    Optional<Entry<NodeAttribute, Double>> attrValue = p.getNodeLoad().entrySet().stream().findFirst();
                                    
                                    if (attrValue.isPresent())
                                    {
                                        value = attrValue.get().getValue();
                                    }
                                    else
                                    {
                                        value = 0.0;
                                    }
                                    break;
                                    
                                default:
                                    break;
                            }
                        }
                        else
                        {
                            value = data.get(time).get(lineId);
                        }
                    }

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
        Map<Long, Map<LineIdentifier, RequestValue>> data = seriesToRequestValuesDatatable(lines, mode);
        LOGGER.debug("outputPointsToJSONFile: Outputting data to JSON: {}", data);

        List<Long> times = new ArrayList<>();
        times.addAll(data.keySet());
        Collections.sort(times);
        
        LOGGER.debug("outputPointsToJSONFile: request start times: {}", times);

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
//                ClientLoadProfile loadProfile = DEFAULT_LOAD_PROFILES[0].getClientLoadProfile();
//                NodeAttribute loadNodeAttribute = NodeAttribute.TASK_CONTAINERS;


                if (lineServiceMap.get(lineId) != null)
                {
                    service = lineServiceMap.get(lineId);
                }

                LOGGER.debug("outputPointsToJSONFile: value.getNumClients(): {}, value.getNodeLoad(): {}, value.getNetworkLoad(): {}",
                        value.getNumClients(), value.getNodeLoad(), value.getNetworkLoad());
                
                final int numClients = value.getNumClients();
                
                Map<NodeAttribute, Double> nodeLoad = new HashMap<>();
                Map<LinkAttribute, Double> networkLoad = new HashMap<>();

                value.getNodeLoad().forEach((attr, v) ->
                {
                    if (numClients > 0)
                    {
                        nodeLoad.put(attr, v / numClients);
                    }
                });
                
                value.getNetworkLoad().forEach((attr, v) ->
                {
                    if (numClients > 0)
                    {
                        networkLoad.put(attr, v / numClients);
                    }
                });

                

                final long serverDuration = value.getDuration();
                final long networkDuration = value.getDuration();
                ClientLoad clientLoad = new ClientLoad(startTime, serverDuration,
                        networkDuration, numClients, service, 
                        ImmutableMap.copyOf(nodeLoad), ImmutableMap.copyOf(networkLoad), ImmutableList.of());
                
                if (numClients > 0 && (nodeLoad.values().stream().anyMatch(v -> v > 0.0) ||
                        networkLoad.values().stream().anyMatch(v -> v > 0.0)))
                {
                    LOGGER.debug("outputPointsToJSONFile: new ClientLoad: {}", clientLoad);
                    clientLoads.add(clientLoad);
                }
                else
                {
                    LOGGER.debug("Skipped adding useless request to export: {}", clientLoad);
                }
            }
        }

        // attempt to output the objects to JSON
        try
        {
            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, clientLoads);
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

    private void loadServiceConfigurations()
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load service configurations from file...");
        fileChooser.getExtensionFilters().addAll(new ExtensionFilter("Service Configurations", "*.json"));
        
        if (saveDirectory != null)
        {
            fileChooser.setInitialDirectory(saveDirectory);
        }
        
        File file = fileChooser.showOpenDialog(stage);
        
        if (file != null)
        {
            if (file.getParentFile() != null)
            {
                saveDirectory = file.getParentFile();
            }
            
            LOGGER.info("Load service configurations from file: {}", file);
            loadServiceConfigurationsFile(file);
        }
    }
    
    private void loadLoadProfiles()
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load load profiles...");
        fileChooser.getExtensionFilters().addAll(new ExtensionFilter("Load Profile", "*.json"));
        
        if (saveDirectory != null)
        {
            fileChooser.setInitialDirectory(saveDirectory);
        }
        
        List<File> files = fileChooser.showOpenMultipleDialog(stage);
        
        if (files != null)
        {            
            LOGGER.info("Load load profile from files: {}", files);
            loadLoadProfiles(files);
        }
    }
    
    private void loadLoadProfiles(List<File> loadProfileFiles)
    {
        for (File loadProfileFile : loadProfileFiles)
        {
            try (BufferedReader reader = Files.newBufferedReader(loadProfileFile.toPath()))
            {
                ClientLoadProfile profile = JSON_MAPPER.readValue(reader, ClientLoadProfile.class);
                loadProfileChoiceBox.getItems().add(new ClientLoadProfileWrapper(profile, loadProfileFile));
            } catch (IOException e) {
                LOGGER.error("Failed to load ClientLoadProfile file {}:", loadProfileFile, e);
            }
        }
    }

    private void loadServiceConfigurationsFile(File serviceConfigurationsFile)
    {           
            Map<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations;
            try {
                serviceConfigurations = ServiceConfiguration.parseServiceConfigurations(serviceConfigurationsFile.toPath());
                
                serviceConfigurations.forEach((app, config) ->
                {
                    lineServiceChoiceBox.getItems().remove(app);
                    lineServiceChoiceBox.getItems().add(app); 
                });
                
                if (!serviceConfigurations.isEmpty())
                {
                    lineServiceChoiceBox.getSelectionModel().select(0);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load service configurations from file {}: {}", serviceConfigurationsFile, e);
            }
    }
    
    private void loadPoints(Map<LineIdentifier, Series<Long, Double>> data)
    {
        LOGGER.info("Loading points...");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load points from file...");
        fileChooser.getExtensionFilters().addAll(pointFileExtensionFilter, allFileExtensionFilter);
        
        if (saveDirectory != null)
        {
            fileChooser.setInitialDirectory(saveDirectory);
        }
        
        File file = fileChooser.showOpenDialog(stage);
        
        if (file != null)
        {
            if (file.getParentFile() != null)
            {
                saveDirectory = file.getParentFile();
            }
            
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

        for (int n = 1; n < header.length; n++)
            stringToLineIdentifierMap.put(header[n], new LineIdentifier(header[n]));
        
        LOGGER.debug("loadPointsFromCSVFile: stringToLineIdentifierMap = {}", stringToLineIdentifierMap);

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
        
        LOGGER.debug("lines: {}", lines);
    }

    private double roundCoordinate(double a, double interval)
    {
        if (interval > 0.0)
        {
            return Math.round(a / interval) * interval;
        }
        else
        {
            return a;
        }
    }
    
    private Data<?, ?> addPointUnderMouse(double mx, double my, long snapTimeInterval, double snapValueInterval)
    {
        Number pointX = curveChart.getXAxis().getValueForDisplay(mx - MOUSE_CURSOR_X_OFFSET);
        Double pointY = curveChart.getYAxis().getValueForDisplay(my - MOUSE_CURSOR_Y_OFFSET);
        pointX = roundCoordinate(pointX.longValue(), snapTimeInterval);
        pointY = roundCoordinate(pointY, snapValueInterval);
            
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

    private void movePoint(Data<Long, Double> point, double mx, double my, boolean boundPoint, long snapTimeInterval, double snapValueInterval)
    {
        Number pointX = curveChart.getXAxis().getValueForDisplay(adjustMouseX(mx));
        Double pointY = curveChart.getYAxis().getValueForDisplay(adjustMouseY(my));

        if (boundPoint)
        {
            pointX = Math.max(curveChartXAxis.getLowerBound(),
                    Math.min(pointX.longValue(), curveChartXAxis.getUpperBound()));
            pointY = Math.max(curveChartYAxis.getLowerBound(), Math.min(pointY, curveChartYAxis.getUpperBound()));
        }

        pointX = roundCoordinate(pointX.longValue(), snapTimeInterval);
        pointY = roundCoordinate(pointY, snapValueInterval);

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

    
    private Task<Map<LineIdentifier, Series<Long, Double>>> pointGenerationTask = null;
    private Thread pointGenerationThread = null;
    
    private void generatePoints(int n, long timeOffset)
    {
        removeGeneratedPoints();
        
        if (pointGenerationTask != null)
        {
            pointGenerationTask.cancel();
            LOGGER.debug("Canceled old point generation task: {}", pointGenerationTask);
            pointGenerationTask = null;
        }
        
        pointGenerationTask = new Task<Map<LineIdentifier, Series<Long, Double>>>()
        {
            @Override
            protected Map<LineIdentifier, Series<Long, Double>> call() throws Exception
            {
                LOGGER.debug("Starting new point generation task: {}", pointGenerationTask);
                
                Map<LineIdentifier, Series<Long, Double>> results = new HashMap<>();

                for (Map.Entry<LineIdentifier, Series<Long, Double>> entry : seriesMap.entrySet())
                {
                    LineIdentifier lineId = entry.getKey();
                    Series<Long, Double> series = entry.getValue();
                    
                    LOGGER.debug("Generating points for line '{}'", lineId);
                    results.put(lineId, generatePoints(generatePointsMethodChoiceBox.getValue(), series, n, timeOffset));
                }    

                LOGGER.debug("generated points: {}", results);
                
                return results;
            }
            
            @Override
            protected void succeeded()
            {
                LOGGER.debug("Point generation task {} succeeded.", pointGenerationTask);
                
                try {
                    generatedSeriesMap.putAll(get());
                    
                    for (Map.Entry<LineIdentifier, Series<Long, Double>> entry : seriesMap.entrySet())
                    {
                        Series<Long, Double> points = entry.getValue();
                        Series<Long, Double> generatedSeries = generatedSeriesMap.get(entry.getKey());

                        int index = curveChart.getData().indexOf(points);
                        curveChart.getData().add(index + 1, generatedSeries);
                        generatedSeries.getNode().setStyle("-fx-stroke-width: 0;");
                    }
                    
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                super.succeeded();
            }
            
            @Override
            protected void cancelled()
            {
                LOGGER.debug("Point generation task {} cenceled.", pointGenerationTask);
                super.cancelled();
            }
            
            @Override
            protected void failed()
            {
                LOGGER.debug("Point generation task {} failed.", pointGenerationTask);
                super.failed();
            }
        };

        pointGenerationThread = new Thread(pointGenerationTask);
        pointGenerationThread.start();
        LOGGER.debug("Started new point generation thread: {}", pointGenerationThread);
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

    private static Series<Long, Double> generatePoints(PointGenerationMethodIdentifier generationMethod, 
            Series<Long, Double> points, int n, long timeOffset)
    {
        if (n < 0)
        {
            return null;
        }
        
        Series<Long, Double> generatedSeries = new Series<>();
        sortPoints(points.getData());

        LOGGER.info("Generating points with method: {}", generationMethod);
        
        switch (generationMethod)
        {
            case POINT_GEN_METHOD_COPY_USER_POINTS:
                
                points.getData().forEach((point) ->
                {
                    Data<Long, Double> copy = new Data<>();
                    copy.setXValue(point.getXValue() + timeOffset);
                    copy.setYValue(point.getYValue());
                    
                    generatedSeries.getData().add(copy);
                });
                break;
                
            case POINT_GEN_METHOD_TIME_DISTANCE:
                generatePointsByDistance(points.getData(), generatedSeries.getData(), 1.0, 0.0, timeOffset, n);
                break;
                
            case POINT_GEN_METHOD_COMMON_TIME_INTERVAL:
                generatePointsAtInterval(points.getData(), generatedSeries.getData(), timeOffset, n);
                break;
                
            case POINT_GEN_METHOD_TIME_VALUE_LINE_DISTANCE:
                double timeRange = getTimeRange(points.getData());
                double valueRange = getValueRange(points.getData());
                double rangeMagnitude = Math.hypot(timeRange, valueRange);
                
                double xFactor, yFactor;
                
                if (timeRange > 0.0 && valueRange > 0.0)
                {
                    xFactor = valueRange / rangeMagnitude;
                    yFactor = timeRange / rangeMagnitude;
                }
                else
                {
                    xFactor = 1.0;
                    yFactor = 1.0;
                }
                
                generatePointsByDistance(points.getData(), generatedSeries.getData(), xFactor, yFactor, timeOffset, n);
                break;
                
            case POINT_GEN_METHOD_VALUE_DISTANCE:
                generatePointsByDistance(points.getData(), generatedSeries.getData(), 0.0, 1.0, timeOffset, n);
                break;
                
            default:
                break;
        }

        generatedSeries.setName("   [" + generatedSeries.getData().size() + "]");
        
        return generatedSeries;
    }
    
    
    


    private static long getTimeRange(List<Data<Long, Double>> inputPoints)
    {
        if (inputPoints.isEmpty())
            return 0;
        
        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;
        
        for (Data<Long, Double> point : inputPoints)
        {
            firstTime = Math.min(firstTime, point.getXValue());
            lastTime = Math.max(lastTime, point.getXValue());
        }
        
        long range = lastTime - firstTime;
        LOGGER.trace("getTimeRange: {}", range);
        return range;
    }
    
    private static double getValueRange(List<Data<Long, Double>> inputPoints)
    {
        if (inputPoints.isEmpty())
            return 0;
        
        double minValue = Double.MAX_VALUE;
        double maxValue = Double.MIN_VALUE;
        
        for (Data<Long, Double> point : inputPoints)
        {
            minValue = Math.min(minValue, point.getYValue());
            maxValue = Math.max(maxValue, point.getYValue());
        }
        
        double range = maxValue - minValue;
        LOGGER.trace("getValueRange: {}", range);
        return range;
    }
    
    
    private static void generatePointsAtInterval(ObservableList<Data<Long, Double>> inputPoints,
            ObservableList<Data<Long, Double>> generatedPoints, final long offset, final long interval)
    {        
        long time = offset;
        
        for (int p = 0; p < inputPoints.size() - 1; p++)
        {
            Data<Long, Double> p1 = inputPoints.get(p);
            Data<Long, Double> p2 = inputPoints.get(p + 1);
            
            double slope = (p2.getYValue() - p1.getYValue()) / (p2.getXValue() - p1.getXValue()); 
            
            while (time < p2.getXValue() || p + 1 == inputPoints.size() - 1 && time == p2.getXValue())
            {
                if (time >= p1.getXValue())
                {
                    double value = (time - p1.getXValue()) * slope + p1.getYValue();
                    generatedPoints.add(new Data<Long, Double>(time, value));
                }
                
                time += interval;
            }
        }
        
    }
    
    
    private static void generatePointsByDistance(List<Data<Long, Double>> inputPoints, List<Data<Long, Double>> generatedPoints, 
            double xFactor, double yFactor, long timeOffset, int n)
    {        
        if (n > 0)
        {
            double totalDistance = 0.0;
    
            for (int p = 0; p < inputPoints.size() - 1; p++)
            {
                Data<Long, Double> p1 = inputPoints.get(p);
                Data<Long, Double> p2 = inputPoints.get(p + 1);
//                double distance = p2.getXValue() - p1.getXValue();
                
                double distance = Math.hypot((p2.getXValue() - p1.getXValue()) * xFactor, (p2.getYValue() - p1.getYValue()) * yFactor);
                LOGGER.debug("generatePointsByDistance: 1 distance({}, {}) = {}", p1, p2, distance);
                totalDistance += distance;
            }
            
            LOGGER.debug("generatePointsByDistance: totalDistance = {}", totalDistance);
    
            double intervalDistance = totalDistance / (n - 1);
            double remainingDistance = 0.0;
            LOGGER.trace("remainingDistance = {}, intervalDistance = {}", remainingDistance, intervalDistance);
    
            int pointsAdded = 0;
            
            for (int p = 0; p < inputPoints.size() - 1 && intervalDistance > 0.0; p++)
            {
                LOGGER.debug("p = {}", p);
                
                Data<Long, Double> p1 = inputPoints.get(p);
                Data<Long, Double> p2 = inputPoints.get(p + 1);
//                double distance = p2.getXValue() - p1.getXValue();
                double distance = Math.hypot((p2.getXValue() - p1.getXValue()) * xFactor, (p2.getYValue() - p1.getYValue()) * yFactor);
                LOGGER.trace("generatePointsByDistance: 2 distance({}, {}) = {}", p1, p2, distance);
                
                while (remainingDistance <= distance || (p == inputPoints.size() - 2 && pointsAdded < n))
                {
                    double x = (remainingDistance / distance) * (p2.getXValue() - p1.getXValue()) + p1.getXValue() + timeOffset;
                    double y = (remainingDistance / distance) * (p2.getYValue() - p1.getYValue()) + p1.getYValue();
                    generatedPoints.add(new Data<Long, Double>(Math.round(x), y));
                    pointsAdded++;
                    
                    remainingDistance += intervalDistance;
                    LOGGER.trace("   remainingDistance: {}", remainingDistance);
                }
    
                remainingDistance -= distance;
                LOGGER.trace("remainingDistance -= distance : {} -= {}", remainingDistance, distance);
            }
            
            LOGGER.trace("remainingDistance: {}", remainingDistance);
        }
    }
    
    

    
    

    private long getMaxTime()
    {
        return (long) curveChartXAxis.getUpperBound();
    }

    private static void sortPoints(List<Data<Long, Double>> series)
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
                    boolean snapValue = isSnapPointValues();
                    
                    Data<?, ?> point = addPointUnderMouse(event.getX(), event.getY(), (snapValue ? 1000 : 0), (snapValue ? 1.0 : 0.0));
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

    private boolean isSnapPointValues()
    {
        return snapPointValues;
    }
    
    private final EventHandler<MouseEvent> movePointEventHandler = new EventHandler<MouseEvent>()
    {
        @Override
        public void handle(MouseEvent event)
        {
            if (dragPoint != null)
            {
                boolean snapValue = isSnapPointValues();
                
                movePoint(dragPoint, event.getX(), event.getY(), true, (snapValue ? 1000 : 0), (snapValue ? 1.0 : 0.0));
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
    
    private void addSeriesToSymbolValueMaps(Map<Long, Map<String, Number>> symbolValueMaps, Map<LineIdentifier, Series<Long, Double>> series)
    {
        series.forEach((line, timeValues) ->
        {            
            for (Data<Long, Double> dataPoint : timeValues.getData())
            {
                Long time = dataPoint.getXValue();
                Double value = dataPoint.getYValue();
                
                symbolValueMaps.computeIfAbsent(time, k -> new HashMap<>()).put("[" + line.getLabel() + "]", value);
            }
        });
    }
    

    private void addConstantsToSymbolValueMaps(Map<Long, Map<String, Number>> symbolValueMaps)
    {
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
        
        for (Map<String, Number> symbolValues : symbolValueMaps.values())
        {
            symbolValues.put("{RX}", networkLoadRx);
            symbolValues.put("{TX}", networkLoadTx);
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
    
    
    
    @SuppressFBWarnings(justification="Feature for intentionally exiting the Demand Generation UI")
    private void exit()
    {
        LOGGER.info("Exiting");
        System.exit(0);
    }
}
