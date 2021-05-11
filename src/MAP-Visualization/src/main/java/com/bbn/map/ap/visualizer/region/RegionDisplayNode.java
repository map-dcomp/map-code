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
package com.bbn.map.ap.visualizer.region;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

import javax.annotation.Nonnull;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.Controller;
import com.bbn.map.ap.visualizer.DisplayController;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

/**
 * Graph node to represent a region and display it's load percentage.
 * 
 * @author jschewe
 *
 */
public class RegionDisplayNode extends AbstractRegionDisplayNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegionDisplayNode.class);

    private final RegionGraphVisualizer visualizer;

    private final double regionCapacity;

    /**
     * @return the capacity of this region
     * @see MapUtils#COMPUTE_ATTRIBUTE
     */
    public double getRegionCapacity() {
        return regionCapacity;
    }

    private final RegionIdentifier region;

    private final Simulation sim;

    /**
     * Creates a node with no {@link Controller}s in it. They should be added
     * with {@link #addController(Controller)}.
     * 
     * @param visualizer
     *            the visualizer that the node belongs to, used to get size
     *            information
     * @param region
     *            the region that is being represented
     * @param sim
     *            the simulation to get information from
     */
    public RegionDisplayNode(@Nonnull final RegionGraphVisualizer visualizer,
            @Nonnull final RegionIdentifier region,
            @Nonnull final Simulation sim) {
        this.visualizer = visualizer;
        this.region = region;
        this.sim = sim;
        this.regionCapacity = sim.getRegionCapacity(region, MapUtils.COMPUTE_ATTRIBUTE);
    }

    @Override
    public String getVertexLabel() {
        return region.getName();
    }

    @Override
    public Paint getVertexColor() {
        return Color.BLACK;
    }

    @Override
    public Paint getVertexFillColor() {
        // don't want JUNG to fill the shape, that will be done with the icon
        return null;
    }

    @Override
    public Icon getVertexIcon() {
        final double diameter = visualizer.computeRegionNodeDiameter(this);
        final int diameterInt = (int) Math.floor(diameter);

        final BufferedImage image = new BufferedImage(diameterInt, diameterInt, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();

        final double loadPercentage = getLoadPercentage(MapUtils.COMPUTE_ATTRIBUTE);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Computed load percentage {} on node {}", loadPercentage, getVertexLabel());
        }

        final Color c;
        if (loadPercentage > DisplayController.RED_THRESHOLD) {
            c = Color.RED;
        } else if (loadPercentage > DisplayController.ORANGE_THRESHOLD) {
            c = Color.ORANGE;
        } else if (loadPercentage > DisplayController.YELLOW_THRESHOLD) {
            c = Color.YELLOW;
        } else {
            c = Color.GREEN;
        }
        graphics.setPaint(c);

        // start at 90 so that we start straight up
        final double startAngle = 90;

        // fill goes in a counter clockwise fashion
        final double arcLength = Math.min(360, loadPercentage * 360);
        final Arc2D.Double arc = new Arc2D.Double(0, 0, diameter, diameter, startAngle, arcLength, Arc2D.PIE);
        graphics.fill(arc);

        return new ImageIcon(image);
    }

    @Override
    public Shape getVertexShape() {
        final double diameter = visualizer.computeRegionNodeDiameter(this);

        return new Ellipse2D.Double(-1 * diameter / 2, -1 * diameter / 2, diameter, diameter);
    }

    /**
     * Compute the load percentage in this region for the specified attribute.
     * 
     * @param attribute
     *            the attribute to get the load percentage for
     * @return a value between 0 and 1 representing the load percentage
     */
    private double getLoadPercentage(@Nonnull final NodeAttribute attribute) {
        if (regionCapacity > 0) {
            final double load = sim.computeRegionLoad(region, attribute);
            if (load > 0) {
                final double percent = load / regionCapacity;
                return percent;
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

}
