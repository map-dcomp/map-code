/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
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
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.annotation.Nonnull;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.data.category.DefaultCategoryDataset;

import com.bbn.map.simulator.Simulation;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.visualizer.ScenarioVisualizer;

/**
 * Display a plot of the number of requests for a region or the amount of load
 * from each source region. The user can choose which region they are interested
 * in.
 * 
 * @author jschewe
 *
 */
public class RegionStatusPlot extends JPanel {

    private static final long serialVersionUID = 1427956934179761922L;

    private final SpiderWebPlot spiderPlot;
    private final JFreeChart chart;
    private final JComboBox<RegionIdentifier> regionChooser;
    private RegionIdentifier destRegion = null;
    private Map<RegionIdentifier, ? extends Number> sourceData = new HashMap<>();
    private final Simulation sim;
    private final PlotType plotType;
    private final Timer updateTimer;

    /**
     * The type of plot to generate.
     * 
     * @author jschewe
     *
     */
    public enum PlotType {
        /** Plot the number of requests from each source region to a region. */
        REQUEST_COUNT,
        /** Plot the percentage of the overall load coming from each region. */
        LOAD_PERCENTAGE
    }

    /**
     * 
     * @param sim
     *            the simulation
     * @param plotType
     *            which type of plot to create
     */
    public RegionStatusPlot(@Nonnull final Simulation sim, final PlotType plotType) {
        super(new BorderLayout());
        this.sim = sim;
        this.plotType = plotType;

        final Vector<RegionIdentifier> regionList = new Vector<>(sim.getAllRegions());
        regionList.sort(RegionComparator.INSTANCE);
        regionChooser = new JComboBox<>(new DefaultComboBoxModel<>(regionList));
        regionChooser.setEditable(false);
        add(regionChooser, BorderLayout.NORTH);
        regionChooser.addActionListener(e -> updateChartData());

        spiderPlot = new SpiderWebPlot();

        chart = new JFreeChart("Unknown", null, spiderPlot, true);

        final ChartPanel chartPanel = new ChartPanel(chart);
        add(chartPanel, BorderLayout.CENTER);

        // initial state
        updateChartData();

        updateTimer = new Timer(ScenarioVisualizer.REFRESH_RATE, e -> updateChartData());
        updateTimer.start();
    }

    private void updateChartData() {
        // combobox is typed and not editable, so the cast is safe
        final RegionIdentifier chosenRegion = (RegionIdentifier) regionChooser.getSelectedItem();
        if (!chosenRegion.equals(destRegion)) {
            destRegion = chosenRegion;
        }

        final NumberFormat format;
        final String series;
        final Map<RegionIdentifier, ? extends Number> newSourceData;
        switch (plotType) {
        case LOAD_PERCENTAGE:
            format = LOAD_NUMBER_FORMAT;
            series = "Source of Load";
            chart.setTitle("Amount of load from each region to region " + destRegion.getName());
            newSourceData = sim.computeRegionLoadPercentageBySource(destRegion, MapUtils.COMPUTE_ATTRIBUTE);
            break;
        case REQUEST_COUNT:
            format = INTEGER_NUMBER_FORMAT;
            series = "Source of Requests";
            chart.setTitle("Number of requests from each region to region " + destRegion.getName());
            newSourceData = sim.getNumRequestsForRegion(destRegion);
            break;
        default:
            throw new RuntimeException("Unknown plot type: " + plotType);
        }

        if (!newSourceData.equals(sourceData)) {
            sourceData = newSourceData;

            final DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            double maxValue = Double.NEGATIVE_INFINITY;
            for (final Map.Entry<RegionIdentifier, ? extends Number> entry : sourceData.entrySet()) {
                final RegionIdentifier srcRegion = entry.getKey();
                final double value = entry.getValue().doubleValue();

                dataset.addValue(value, series, String.format("%s - %s", srcRegion.getName(), format.format(value)));
                maxValue = Math.max(maxValue, value);
            }

            spiderPlot.setMaxValue(maxValue);
            spiderPlot.setDataset(dataset);
            repaint();
        }

    }

    private static final NumberFormat INTEGER_NUMBER_FORMAT = NumberFormat.getIntegerInstance();

    private static final NumberFormat LOAD_NUMBER_FORMAT = NumberFormat.getPercentInstance();

    private static final class RegionComparator implements Comparator<RegionIdentifier> {
        private static final RegionComparator INSTANCE = new RegionComparator();

        @Override
        public int compare(final RegionIdentifier o1, final RegionIdentifier o2) {
            return o1.getName().compareTo(o2.getName());
        }

    }
}
