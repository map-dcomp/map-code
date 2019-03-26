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
package com.bbn.map.ap.visualizer.region;

import java.awt.Color;
import java.awt.Paint;
import java.util.Objects;

import javax.annotation.Nonnull;

/**
 * Graph object to display an edge between 2 nodes in
 * {@link RegionGraphVisualizer}.
 * 
 * @author jschewe
 *
 */
public class RegionDisplayEdge {

    /**
     * 
     * @param left
     *            see {@link #getLeft()}
     * @param right
     *            see {@link #getRight()}
     */
    public RegionDisplayEdge(@Nonnull final AbstractRegionDisplayNode left,
            @Nonnull final AbstractRegionDisplayNode right) {
        this.left = left;
        this.right = right;
    }

    private final AbstractRegionDisplayNode left;

    /**
     * 
     * @return one end of the edge
     */
    public AbstractRegionDisplayNode getLeft() {
        return left;
    }

    private final AbstractRegionDisplayNode right;

    /**
     * 
     * @return the other end of the edge
     */
    public AbstractRegionDisplayNode getRight() {
        return right;
    }

    @Override
    public int hashCode() {
        // make sure that edge(one, two) is equal to edge(two, one)
        // Objects.hash returns different values depending on the order
        if (left.hashCode() < right.hashCode()) {
            return Objects.hash(left, right);
        } else {
            return Objects.hash(right, left);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (null == o) {
            return false;
        } else if (getClass() == o.getClass()) {
            // edges are bidirectional, so left and right don't matter for
            // equality
            final RegionDisplayEdge other = (RegionDisplayEdge) o;
            return (left.equals(other.getLeft()) && right.equals(other.getRight()))
                    || (left.equals(other.getRight()) && right.equals(other.getLeft()));
        } else {
            return false;
        }
    }

    /**
     * 
     * @return the color of the edge
     */
    public Paint getEdgeColor() {
        return Color.BLACK;
    }

    /**
     * 
     * @return the text to display on the edge, null to not display anything
     */
    public String getDisplayText() {
        return null;
    }
}
