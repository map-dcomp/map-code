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
package com.bbn.map.ap.visualizer.region;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;

import javax.annotation.Nonnull;
import javax.swing.Icon;

import com.bbn.protelis.networkresourcemanagement.NetworkClient;

/**
 * Graph node to represent a pool of clients.
 * 
 * @author jschewe
 *
 */
public class ClientDisplayNode extends AbstractRegionDisplayNode {

    private final RegionGraphVisualizer visualizer;

    /**
     * 
     * @param visualizer
     *            the visualizer that the node is drawn in
     * @param client
     *            see {@link #getClient()}
     */
    public ClientDisplayNode(@Nonnull final RegionGraphVisualizer visualizer, @Nonnull final NetworkClient client) {
        this.visualizer = visualizer;
        this.client = client;
    }

    private final NetworkClient client;

    /**
     * @return the client pool that is being represented
     */
    public NetworkClient getClient() {
        return client;
    }

    @Override
    public String getVertexLabel() {
        return client.getName();
    }

    @Override
    public Paint getVertexColor() {
        return Color.BLACK;
    }

    @Override
    public Icon getVertexIcon() {
        return null;
    }

    @Override
    public Paint getVertexFillColor() {
        return Color.BLUE;
    }

    @Override
    public Shape getVertexShape() {
        final double diameter = visualizer.computeClientNodeDiameter(this);

        return new Ellipse2D.Double(-1 * diameter / 2, -1 * diameter / 2, diameter, diameter);
    }

}
