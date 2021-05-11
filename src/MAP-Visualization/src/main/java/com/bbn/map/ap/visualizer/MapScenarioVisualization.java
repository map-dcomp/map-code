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
package com.bbn.map.ap.visualizer;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Timer;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.MaskFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.ap.visualizer.region.RegionGraphVisualizer;
import com.bbn.map.ap.visualizer.utils.VisualizationViewerControlPanel;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.simulator.DNSSim;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.utils.LogExceptionHandler;
import com.bbn.map.utils.MapLoggingConfigurationFactory;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.visualizer.ScenarioVisualizer;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

import edu.uci.ics.jung.visualization.control.GraphMouseListener;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tool to visualize a MAP network scenario.
 */
@SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "This class is not to be serialized")
public final class MapScenarioVisualization extends JFrame {
    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapLoggingConfigurationFactory.class.getName());
    }

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(MapScenarioVisualization.class);

    // not a parameter in AgentConfiguration because this is specific to the
    // simulation
    // if this value is at 10 ms, then things start falling behind and the CPU
    // usage ramps way up
    private static final long POLLING_INTERVAL_MS = 500;

    /**
     * DNS TTL in seconds.
     */
    private static final int TTL = 60;

    /**
     * Start the visualization.
     * 
     * @param args
     *            directory to where the network information is
     */
    public static void main(final String[] args) {
        LogExceptionHandler.registerExceptionHandler();

        Path scenarioPathArg = (args.length >= 1) ? Paths.get(args[0]) : null;
        Path demandPathArg = (args.length >= 2) ? Paths.get(args[1]) : null;
        LOGGER.info("Scenario: " + Objects.toString(scenarioPathArg));
        LOGGER.info("Demand: " + Objects.toString(demandPathArg));

        final MapScenarioVisualization vis = new MapScenarioVisualization(scenarioPathArg, demandPathArg);
        vis.pack();
        vis.setVisible(true);
    }

    private transient VirtualClock clock = null;
    private final JButton topologyButton;
    private final JButton demandButton;
    private final JButton startButton;
    private final JButton stopButton;

    /**
     * How long between calls to update the displayed time.
     */
    private static final int TIMER_UPDATE_INTERVAL = (int) Duration.ofSeconds(1).toMillis();
    private final JLabel time;
    private final NumberFormat timeFormatter = NumberFormat.getIntegerInstance();

    private final NodeInformation nodePanel;
    private final Box dnsPanel;
    private final List<DnsInformation> dnsInfoPanels = new LinkedList<>();
    private final Box clientPanel;

    private Simulation sim = null;
    private Path scenarioPath = null;
    private Path demandPath = null;
    private ScenarioVisualizer<DisplayController, DisplayLink, NetworkLink, NetworkNode> visualizer = null;
    private final JPanel visualizerPanel;

    private final JFormattedTextField apDurationField;
    private final JFormattedTextField dcopDurationField;
    private final JFormattedTextField rlgDurationField;

    private final JButton regionRequestPlots;
    private final JButton regionLoadPlots;

    private final RegionGraphVisualizer regionVisualizer;

    private final VisualizationViewerControlPanel viewerControl;

    // track the plots so that they can be closed when the simulation changes
    private final List<JDialog> requestStatusDialogs = new LinkedList<>();

    private MapScenarioVisualization(final Path scenarioPath, final Path demandPath) {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        this.scenarioPath = scenarioPath;
        this.demandPath = demandPath;

        // The the display that it should kill the remote nodes on window
        // close.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(final WindowEvent e) {
                stopScenario();
            }
        });

        final Container cpane = getContentPane();
        cpane.setLayout(new BorderLayout());

        // --- control panel
        final JComponent controlPanel = Box.createVerticalBox();

        final Box controlPanelTop = Box.createHorizontalBox();
        controlPanel.add(controlPanelTop);

        controlPanelTop.add(new JLabel("Topology: "));

        topologyButton = new JButton("Load");
        controlPanelTop.add(topologyButton);
        topologyButton.addActionListener(e -> chooseTopology());

        controlPanelTop.add(new JLabel("Demand: "));

        demandButton = new JButton("Load");
        controlPanelTop.add(demandButton);
        demandButton.addActionListener(e -> chooseDemand());

        final Box durationPanel = Box.createHorizontalBox();
        durationPanel.setBorder(BorderFactory.createTitledBorder("Round Durations"));
        controlPanelTop.add(durationPanel);

        durationPanel.add(new JLabel("AP: "));
        apDurationField = createDurationField();
        durationPanel.add(apDurationField);
        apDurationField.setToolTipText("Time between runs of AP minutes:seconds");

        durationPanel.add(new JLabel("DCOP: "));
        dcopDurationField = createDurationField();
        durationPanel.add(dcopDurationField);
        dcopDurationField.setToolTipText("Time between runs of DCOP minutes:seconds");

        durationPanel.add(new JLabel("RLG: "));
        rlgDurationField = createDurationField();
        durationPanel.add(rlgDurationField);
        rlgDurationField.setToolTipText("Time between runs of RLG minutes:seconds");

        final JButton resetAgentConfig = new JButton("Reset");
        durationPanel.add(resetAgentConfig);
        resetAgentConfig.addActionListener(e -> {
            AgentConfiguration.resetToDefaults();
            updateDurationFieldsFromAgentConfig();
        });

        startButton = new JButton("Start");
        controlPanelTop.add(startButton);
        startButton.addActionListener(e -> startScenario());

        stopButton = new JButton("Stop");
        controlPanelTop.add(stopButton);
        stopButton.addActionListener(e -> stopScenario());

        controlPanelTop.add(Box.createHorizontalGlue());

        final Box controlPanelBottom = Box.createHorizontalBox();
        controlPanel.add(controlPanelBottom);

        final JLabel timeLabel = new JLabel("Time: ");
        controlPanelBottom.add(timeLabel);

        time = new JLabel("        ");
        controlPanelBottom.add(time);
        final Timer timeUpdateTimer = new Timer(TIMER_UPDATE_INTERVAL, e -> {
            if (null == sim) {
                time.setText(null);
            } else {
                final long now = sim.getClock().getCurrentTime();
                final String timeText = timeFormatter.format(now);
                time.setText(timeText);
            }
        });
        timeUpdateTimer.start();

        // add some space between the time and the control buttons
        // CHECKSTYLE:OFF - dealing with UI, can't have a constant for
        // everything
        controlPanelBottom.add(Box.createHorizontalStrut(5));
        // CHECKSTYLE:ON

        viewerControl = new VisualizationViewerControlPanel();
        controlPanelBottom.add(viewerControl);

        regionRequestPlots = new JButton("Region Request Plot");
        controlPanelBottom.add(regionRequestPlots);
        regionRequestPlots.addActionListener(e -> {
            if (null != sim) {
                final RegionStatusPlot plot = new RegionStatusPlot(sim, RegionStatusPlot.PlotType.REQUEST_COUNT);
                final JDialog dialog = new JDialog(this, "Source Region Request Plot");
                dialog.getContentPane().setLayout(new BorderLayout());
                dialog.getContentPane().add(plot, BorderLayout.CENTER);
                dialog.pack();
                dialog.setVisible(true);
                requestStatusDialogs.add(dialog);
            }
        });

        regionLoadPlots = new JButton("Region Load Plot");
        controlPanelBottom.add(regionLoadPlots);
        regionLoadPlots.addActionListener(e -> {
            if (null != sim) {
                final RegionStatusPlot plot = new RegionStatusPlot(sim, RegionStatusPlot.PlotType.LOAD_PERCENTAGE);
                final JDialog dialog = new JDialog(this, "Source Region Load Plot");
                dialog.getContentPane().setLayout(new BorderLayout());
                dialog.getContentPane().add(plot, BorderLayout.CENTER);
                dialog.pack();
                dialog.setVisible(true);
                requestStatusDialogs.add(dialog);
            }
        });

        regionVisualizer = new RegionGraphVisualizer();
        final JDialog regionVisualizerDialog = new JDialog(this);
        regionVisualizerDialog.getContentPane().setLayout(new BorderLayout());
        regionVisualizerDialog.getContentPane().add(regionVisualizer, BorderLayout.CENTER);
        regionVisualizerDialog.pack();
        final JButton regionGraphButton = new JButton("Region Load Graph");
        controlPanelBottom.add(regionGraphButton);
        regionGraphButton.addActionListener(e -> {
            regionVisualizerDialog.setVisible(true);
        });

        controlPanelBottom.add(Box.createHorizontalGlue());
        // --- end control panel

        // -- right panel
        final Box rightPanel = Box.createVerticalBox();

        nodePanel = new NodeInformation();
        rightPanel.add(nodePanel);
        nodePanel.setBorder(BorderFactory.createTitledBorder("Node Information"));

        dnsPanel = Box.createVerticalBox();
        final JScrollPane dnsScroller = new JScrollPane(dnsPanel);
        rightPanel.add(dnsScroller);
        dnsPanel.setBorder(BorderFactory.createTitledBorder("DNS Information"));

        // filler
        rightPanel.add(Box.createVerticalGlue());

        // -- end right panel

        // -- left panel
        final Box leftPanel = Box.createVerticalBox();
        final JScrollPane leftScroller = new JScrollPane(leftPanel);

        clientPanel = Box.createVerticalBox();
        leftPanel.add(clientPanel);

        // filler
        leftPanel.add(Box.createVerticalGlue());

        // -- end left panel

        // build up the layout
        cpane.add(controlPanel, BorderLayout.NORTH);
        visualizerPanel = new JPanel(new BorderLayout());
        final JSplitPane centerRightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, visualizerPanel, rightPanel);
        // give all extra space to thevisualizerPanel
        centerRightSplit.setResizeWeight(1);
        final JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroller, centerRightSplit);
        cpane.add(leftSplit, BorderLayout.CENTER);

        // initial state
        stopButton.setEnabled(false);
        demandButton.setEnabled(true);
        topologyButton.setEnabled(true);
        startButton.setEnabled(scenarioPath != null && demandPath != null);
        regionRequestPlots.setEnabled(false);
        regionLoadPlots.setEnabled(false);
        updateDurationFieldsFromAgentConfig();
    }

    @Nonnull
    private static JFormattedTextField createDurationField() {
        try {
            final MaskFormatter durationFormatter = new MaskFormatter("###:##");
            durationFormatter.setPlaceholderCharacter('0');
            durationFormatter.setValueClass(String.class);
            final DefaultFormatterFactory dff = new DefaultFormatterFactory(durationFormatter);
            final JFormattedTextField field = new JFormattedTextField(dff);
            field.setMaximumSize(field.getPreferredSize());
            return field;
        } catch (final ParseException e) {
            throw new RuntimeException("Internal error, bad mask for input fields", e);
        }
    }

    private static void updateDurationField(final JFormattedTextField field, final Duration duration) {
        final long minutes = duration.toMinutes();
        final long seconds = duration.getSeconds() - (60 * minutes);
        final String str = String.format("%03d:%02d", minutes, seconds);
        field.setText(str);
    }

    private void updateDurationFieldsFromAgentConfig() {
        final Duration apDuration = AgentConfiguration.getInstance().getApRoundDuration();
        updateDurationField(apDurationField, apDuration);

        final Duration dcopDuration = AgentConfiguration.getInstance().getDcopRoundDuration();
        updateDurationField(dcopDurationField, dcopDuration);

        final Duration rlgDuration = AgentConfiguration.getInstance().getRlgRoundDuration();
        updateDurationField(rlgDurationField, rlgDuration);
    }

    private File lastDirectoryPicked = new File("."); // default to current
                                                      // directory

    private void chooseTopology() {
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a directory containing the scenario topology");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setCurrentDirectory(lastDirectoryPicked);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            lastDirectoryPicked = chooser.getSelectedFile();
            setScenarioPath(chooser.getSelectedFile().toPath());
        } else {
            setScenarioPath(null);
        }

    }

    private void setScenarioPath(final Path path) {
        scenarioPath = path;
        if (null == scenarioPath) {
            startButton.setEnabled(false);
            topologyButton.setText("Load");
        } else {
            startButton.setEnabled(true);
            final Path filename = scenarioPath.getFileName();
            final String lastElement;
            if (null != filename) {
                lastElement = filename.toString();
            } else {
                lastElement = scenarioPath.toString();
            }
            topologyButton.setText(lastElement);

            // load the scenario just so it's visible
            try {
                loadScenario();
            } catch (final IOException e) {
                LOGGER.error("Error loading scenario", e);
                JOptionPane.showMessageDialog(this, "Error loading scenario: " + e.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
    }

    private void chooseDemand() {
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a directory containing the demand, canceling unsets the demand");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setCurrentDirectory(lastDirectoryPicked);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setDemandPath(chooser.getSelectedFile().toPath());
            LOGGER.info("Chose demand path: " + demandPath.toString());
            lastDirectoryPicked = chooser.getSelectedFile();
        } else {
            setDemandPath(null);
        }
    }

    private void setDemandPath(final Path path) {
        demandPath = path;
        if (null == demandPath) {
            demandButton.setText("Load");
        } else {
            final Path filename = demandPath.getFileName();

            final String lastElement;
            if (null != filename) {
                lastElement = filename.toString();
            } else {
                lastElement = demandPath.toString();
            }

            demandButton.setText(lastElement);
        }
    }

    private void loadScenario() throws IOException {
        if (null != sim) {
            stopScenario();
        }

        if (null != visualizer) {
            visualizerPanel.remove(visualizer);
            visualizer = null;
        }

        if (null != clock) {
            clock.shutdown();
            clock = null;
        }

        closeAllRequestPlots();

        clock = new SimpleClock();
        sim = new Simulation(scenarioPath.toString(), scenarioPath, demandPath, clock, POLLING_INTERVAL_MS, TTL,
                AppMgrUtils::getContainerParameters);

        final MapNetworkVisualizerFactory visFactory = new MapNetworkVisualizerFactory();
        visualizer = new ScenarioVisualizer<>(visFactory);
        visualizer.setScenario(sim.getGraph());

        viewerControl.setViewer(visualizer.getViewer());

        // make sure that we know when something is selected in any mode
        visualizer.getViewer().addGraphMouseListener(new GraphMouseListener<DisplayController>() {
            @Override
            public void graphClicked(final DisplayController v, final MouseEvent me) {
                if (me.getButton() == MouseEvent.BUTTON1) {
                    nodePanel.setNode(v.getNode());
                    me.consume();
                }
            }

            @Override
            public void graphPressed(final DisplayController v, final MouseEvent me) {
            }

            @Override
            public void graphReleased(final DisplayController v, final MouseEvent me) {
            }
        });

        visualizerPanel.add(visualizer, BorderLayout.CENTER);

        // setup dns panels
        sim.getAllRegions().forEach(region -> {
            final DNSSim dns = sim.getRegionalDNS(region);
            final DnsInformation panel = new DnsInformation(region, dns);
            dnsInfoPanels.add(panel);
            dnsPanel.add(panel);
        });

        sim.getClientSimulators().forEach(client -> {
            final ClientInformation panel = new ClientInformation(client);
            clientPanel.add(panel);
        });

        regionRequestPlots.setEnabled(true);
        regionLoadPlots.setEnabled(true);

        regionVisualizer.setSimulation(sim);
    }

    /**
     * Assumes that the string comes from a {@link JFormattedTextField} that is
     * setup to have numbers a colon and then some numbers representing minutes
     * and seconds.
     */
    @Nonnull
    private static Duration parseDurationFromField(@Nonnull final String str) {
        final int colonIndex = str.indexOf(':');
        if (colonIndex < 0) {
            throw new RuntimeException("Internal error, cannot find colon in duration string: " + str);
        }
        try {
            final String minutesStr = str.substring(0, colonIndex).trim();
            final long minutes = Long.parseLong(minutesStr);

            final String secondsStr = str.substring(colonIndex + 1).trim();
            final long seconds = Long.parseLong(secondsStr);

            final Duration duration = Duration.ofMinutes(minutes).plusSeconds(seconds);
            return duration;
        } catch (final NumberFormatException e) {
            throw new RuntimeException(
                    "Internal error, should not have an error parsing a duration from a formatted text field", e);
        }
    }

    private void startScenario() {
        if (null == scenarioPath) {
            JOptionPane.showMessageDialog(this, "You must specify a scenario path", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // set AgentConfiguration based on the duration fields
        final Duration apDuration = parseDurationFromField(apDurationField.getText());
        AgentConfiguration.getInstance().setApRoundDuration(apDuration);

        final Duration dcopDuration = parseDurationFromField(dcopDurationField.getText());
        AgentConfiguration.getInstance().setDcopRoundDuration(dcopDuration);

        final Duration rlgDuration = parseDurationFromField(rlgDurationField.getText());
        AgentConfiguration.getInstance().setRlgRoundDuration(rlgDuration);

        // load scenario here to ensure we have the topology and the demand
        try {
            loadScenario();
        } catch (final IOException e) {
            LOGGER.error("Error loading scenario", e);
            JOptionPane.showMessageDialog(this, "Error loading scenario: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        visualizer.start(false);

        stopButton.setEnabled(true);
        demandButton.setEnabled(false);
        topologyButton.setEnabled(false);
        startButton.setEnabled(false);

        sim.startSimulation();
    }

    private void closeAllRequestPlots() {
        requestStatusDialogs.forEach(plot -> {
            plot.setVisible(false);
            plot.dispose();
        });
        requestStatusDialogs.clear();
    }

    private void stopScenario() {
        closeAllRequestPlots();

        stopButton.setEnabled(false);
        demandButton.setEnabled(true);
        topologyButton.setEnabled(true);
        startButton.setEnabled(true);

        visualizer.stop();

        if (null != sim) {
            dnsInfoPanels.forEach(panel -> panel.shutdown());
            dnsPanel.removeAll();
            clientPanel.removeAll();

            sim.stopSimulation();

            sim = null;

            regionRequestPlots.setEnabled(false);
            regionLoadPlots.setEnabled(false);
        }
    }

}
