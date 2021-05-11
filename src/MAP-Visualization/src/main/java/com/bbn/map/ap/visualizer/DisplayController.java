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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.swing.ImageIcon;

import com.bbn.map.Controller;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.visualizer.DisplayNode;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Special support for displaying {@link Controller} objects.
 */
public class DisplayController extends DisplayNode {

    /**
     * 
     * @param node
     *            the object to display
     */
    public DisplayController(final NetworkNode node) {
        super(node);
    }

    /**
     * Decimal value above which items should be red. Assumed range is [0,1].
     */
    public static final double RED_THRESHOLD = 1;
    /**
     * Decimal value above which items should be orange. Assumed range is [0,1].
     */
    public static final double ORANGE_THRESHOLD = 0.75;
    /**
     * Decimal value above which items should be yellow. Assumed range is [0,1].
     */
    public static final double YELLOW_THRESHOLD = 0.5;

    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "Bug in FindBugs 3.0.4")
    @Override
    public ImageIcon getIcon() {
        final ImageIcon icon = super.getIcon();

        final NetworkNode node = getNode();
        if (node instanceof Controller) {
            final Controller controller = (Controller) node;
            // we don't care about demand right now, so just get the one
            // computed for the short time interval
            final ResourceReport report = controller.getResourceReport(ResourceReport.EstimationWindow.SHORT);

            final ImmutableMap<NodeAttribute, Double> serverCapacity = report.getAllocatedComputeCapacity();
            final Map<NodeAttribute, Double> serverLoad = new HashMap<>();
            final ImmutableMap<?, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>> allServerLoad = report
                    .getComputeLoad();
            for (final Map.Entry<?, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>> entry : allServerLoad
                    .entrySet()) {
                for (final Map.Entry<?, ImmutableMap<NodeAttribute, Double>> innerEntry : entry.getValue()
                        .entrySet()) {
                    final ImmutableMap<NodeAttribute, Double> serviceServerLoad = innerEntry.getValue();
                    serviceServerLoad.forEach((k, v) -> serverLoad.merge(k, v, Double::sum));
                }
            }

            final BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            final Graphics graphics = image.getGraphics();

            graphics.drawImage(icon.getImage(), 0, 0, null);

            final int imageHeight = image.getHeight();
            final int imageWidth = image.getWidth();
            final int borderWidth = 1;
            final int numAttributes = serverLoad.size();
            final int columnWidth = numAttributes > 0 ? (imageWidth - 2 * borderWidth) / numAttributes : 0;
            final int maxColumnHeight = imageHeight - 2 * borderWidth;
            if (columnWidth > 0) {

                int x = borderWidth;
                for (final Map.Entry<NodeAttribute, Double> serverLoadEntry : serverLoad.entrySet()) {
                    final NodeAttribute attr = serverLoadEntry.getKey();
                    final double load = serverLoadEntry.getValue();
                    if (serverCapacity.containsKey(attr) && serverLoad.containsKey(attr)) {
                        final double capacity = serverCapacity.get(attr);
                        final double percentageUsed = load / capacity;

                        final int columnHeight = Math.min(maxColumnHeight,
                                (int) Math.floor(percentageUsed * maxColumnHeight));

                        final Color c;
                        if (percentageUsed > RED_THRESHOLD) {
                            c = Color.RED;
                        } else if (percentageUsed > ORANGE_THRESHOLD) {
                            c = Color.ORANGE;
                        } else if (percentageUsed > YELLOW_THRESHOLD) {
                            c = Color.YELLOW;
                        } else {
                            c = Color.GREEN;
                        }

                        graphics.setColor(c);
                        final int topY = imageHeight - borderWidth - columnHeight;
                        graphics.fillRect(x, topY, columnWidth, columnHeight);
                    }
                } // can compute percentage used

                x += columnWidth;
            }
            // else should do something, but don't know what

            return new ImageIcon(image);
        } else {
            return icon;
        }
    }

    /**
     * Add DNS display information beyond what is normally there.
     */
    @Override
    public String getVertexLabel() {
        final NetworkNode node = getNode();

        final StringBuilder builder = new StringBuilder();
        builder.append("<html>");

        builder.append("<b>" + node.getNodeIdentifier().getName() + "</b>");
        if (node instanceof Controller) {
            final Controller controller = (Controller) node;
            if (controller.isHandleDnsChanges()) {
                builder.append(" - handles DNS changes");
            }
        }

        if (node instanceof NetworkServer) {
            final NetworkServer server = (NetworkServer) node;
            final String valueStr = Objects.toString(server.getVM().getCurrentValue());
            builder.append("<br><hr>" + valueStr);
            
            final String debugValue = String.valueOf(server.getEnvironment().get("debug"));
            builder.append("<br><hr>" + debugValue);
        }

        builder.append("</html>");

        return builder.toString();
    }

}
