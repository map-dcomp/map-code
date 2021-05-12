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
package com.bbn.map.ta2;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;

/**
 * The result of {@link TA2Interface#getRegionTopology()}.
 * 
 * @author jschewe
 *
 */
public class RegionalTopology {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegionalTopology.class);

    private final Graph<RegionIdentifier, RegionalLink> graph;

    private final DijkstraShortestPath<RegionIdentifier, RegionalLink> pathFinder;

    /**
     * @param graph
     *            the graph of regions
     */
    public RegionalTopology(final Graph<RegionIdentifier, RegionalLink> graph) {
        this.graph = graph;
        pathFinder = new DijkstraShortestPath<>(graph);
    }

    /**
     * 
     * @return all regions in the graph
     */
    public Collection<RegionIdentifier> getAllRegions() {
        return graph.getVertices();
    }

    /**
     * Find the regions that are neighbors of {@code region}.
     * 
     * @param region
     *            the region to find the neighbors for
     * @return the neighboring regions
     */
    public @Nonnull Set<RegionIdentifier> getNeighboringRegions(final @Nonnull RegionIdentifier region) {
        if (null == graph) {
            LOGGER.warn("No regional network graph available");
            return Collections.emptySet();
        } else {
            if (!graph.containsVertex(region)) {
                // graph.getPredecessors returns null if region is not a vertex
                // in the graph
                LOGGER.warn("{} is not in the region graph, likely due to missing information");
                return Collections.emptySet();
            } else {
                return Stream.concat(graph.getPredecessors(region).stream(), graph.getSuccessors(region).stream())
                        .collect(Collectors.toSet());
            }
        }

    }

    /**
     * Get the {@link RegionalLink}s to traverse to get from source to dest.
     * 
     * @param source
     *            the source region
     * @param dest
     *            the destination region
     * @return a non-null list, the list is empty if there is no path
     */
    @Nonnull
    public List<RegionalLink> getPath(@Nonnull final RegionIdentifier source, @Nonnull final RegionIdentifier dest) {
        if (null == graph) {
            LOGGER.warn("No regional network graph available");
            return Collections.emptyList();
        }

        if (!graph.containsVertex(source) || !graph.containsVertex(dest)) {
            return Collections.emptyList();
        } else {
            try {
                final List<RegionalLink> path = pathFinder.getPath(source, dest);
                return path;
            } catch (final IllegalArgumentException e) {
                LOGGER.debug("One of the regions is not in the graph", e);
                return Collections.emptyList();
            } catch (final NullPointerException e) {
                LOGGER.debug("The two regions are not connected to each other", e);
                return Collections.emptyList();
            }
        }
    }

}
