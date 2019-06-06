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

import java.awt.Paint;
import java.awt.Shape;

import javax.annotation.Nonnull;
import javax.swing.Icon;

/**
 * Base class for nodes displayed in {@link RegionGraphVisualizer}.
 * 
 * @author jschewe
 *
 */
public abstract class AbstractRegionDisplayNode {

    /**
     * 
     * @return the label to be displayed for the node
     */
    public abstract String getVertexLabel();

    /**
     * 
     * @return the color to draw the outline of the shape of the node, null to
     *         not draw an outline
     */
    public abstract Paint getVertexColor();

    /**
     * 
     * @return the color to fill the shape of hte node, null to not fill the
     *         shape
     */
    public abstract Paint getVertexFillColor();

    /**
     * 
     * @return the icon to display on the node, null to not display an icon
     */
    public abstract Icon getVertexIcon();

    /**
     * 
     * @return the shape of the node to draw
     */
    @Nonnull
    public abstract Shape getVertexShape();

}
