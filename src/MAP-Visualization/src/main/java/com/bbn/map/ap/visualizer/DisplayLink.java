/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018>, <Raytheon BBN Technologies>
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

import java.text.NumberFormat;

import com.bbn.map.Controller;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.visualizer.DisplayEdge;
import com.bbn.protelis.networkresourcemanagement.visualizer.DisplayNode;
import com.google.common.collect.ImmutableMap;

/**
 * MAP extension of {@link DisplayEdge} for different visualization.
 */
public class DisplayLink extends DisplayEdge {

    /**
     * 
     * @param link
     *            the link
     * @param head
     *            the head
     * @param tail
     *            the tail
     */
    public DisplayLink(final NetworkLink link, final DisplayNode head, final DisplayNode tail) {
        super(link, head, tail);
    }

    @Override
    public String getDisplayText() {
        final StringBuilder text = new StringBuilder();
        text.append("<html>");

        text.append(getLink().getName());

        final NetworkNode head = getHead().getNode();
        final NetworkNode tail = getTail().getNode();

        // check both ways in case it's inconsistent or one end isn't a
        // controller
        addUsagePercentage(head, tail, text);

        addUsagePercentage(tail, head, text);

        text.append("</html>");
        return text.toString();
    }

    private static final NumberFormat USAGE_FORMAT = NumberFormat.getNumberInstance();
    static {
        USAGE_FORMAT.setMinimumFractionDigits(1);
        USAGE_FORMAT.setMaximumFractionDigits(1);
    }

    private void addUsagePercentage(final NetworkNode node, final NetworkNode neighbor, final StringBuilder text) {
        if (node instanceof Controller) {
            final Controller controller = (Controller) node;
            // we don't care about demand right now, so just get the one
            // computed for the short time interval
            final ResourceReport report = controller.getResourceReport(ResourceReport.EstimationWindow.SHORT);

            final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkLoad = report
                    .getAllNetworkLoad();
            final ImmutableMap<LinkAttribute<?>, Double> linkDemand = networkLoad.get(neighbor.getNodeIdentifier());
            if (null != linkDemand) {
                final Double demand = linkDemand.get(LinkMetricName.DATARATE);
                if (null != demand) {
                    final double percentageUsed = demand / getLink().getBandwidth();
                    if (!Double.isNaN(percentageUsed)) {

                        final String color;
                        if (percentageUsed > DisplayController.RED_THRESHOLD) {
                            color = "red";
                        } else if (percentageUsed > DisplayController.ORANGE_THRESHOLD) {
                            color = "orange";
                        } else if (percentageUsed > DisplayController.YELLOW_THRESHOLD) {
                            color = "yellow";
                        } else {
                            color = "black";
                        }

                        text.append(" - ");
                        text.append("<font color='" + color + "'>");
                        text.append(controller.getName());
                        text.append(" : ");
                        text.append(USAGE_FORMAT.format(percentageUsed * 100));
                        text.append("%");
                        text.append("</font>");

                    } // not NaN

                } // have demand
            } // have linkDemand for this neighbor
        } // have a controller
    }
}
