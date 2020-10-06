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
package com.bbn.map.ta2;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;

/**
 * The result of the TA2 interface function get_overlay converted to Java
 * syntax.
 * 
 * @author jschewe
 *
 */
public class OverlayTopology {

    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayTopology.class);

    private final Graph<NodeIdentifier, OverlayLink> graph = new SparseMultigraph<>();

    private final DijkstraShortestPath<NodeIdentifier, OverlayLink> pathFinder;

    /**
     * This constructor matches the information that comes in from the TA2
     * interface. The actual interface uses strings, but converting them to
     * {@link NodeIdentifier} objects can be done trivially with
     * {@Link DnsNameIdentifier#DnsNameIdentifier(String)}.
     * 
     * @param nodes
     *            the nodes from the TA2 interface
     * @param links
     *            the links from the TA2 interface
     */
    public OverlayTopology(final Collection<NodeIdentifier> nodes,
            final Collection<ImmutablePair<NodeIdentifier, NodeIdentifier>> links) {
        nodes.forEach(name -> {
            if (!graph.containsVertex(name)) {
                graph.addVertex(name);
            }
        });

        links.forEach(linkPair -> {
            final OverlayLink link = new OverlayLink(linkPair.getLeft(), linkPair.getRight());
            if (!graph.containsEdge(link)) {
                graph.addEdge(link, linkPair.getLeft(), linkPair.getRight());
            }
        });

        pathFinder = new DijkstraShortestPath<>(graph);
    }

    /**
     * 
     * @return all nodes in the graph
     */
    public Collection<NodeIdentifier> getAllNodes() {
        return graph.getVertices();
    }

    /**
     * Get the {@link OverlayLink}s to traverse to get from source to dest.
     * 
     * @param source
     *            the source node
     * @param dest
     *            the destination node
     * @return a non-null list, the list is empty if there is no path
     */
    @Nonnull
    public List<OverlayLink> getPath(@Nonnull final NodeIdentifier source, @Nonnull final NodeIdentifier dest) {
        synchronized (graph) {
            if (!graph.containsVertex(source) || !graph.containsVertex(dest)) {
                return Collections.emptyList();
            } else {
                try {
                    final List<OverlayLink> path = pathFinder.getPath(source, dest);
                    return path;
                } catch (final IllegalArgumentException e) {
                    LOGGER.debug("One of the nodes is not in the graph", e);
                    return Collections.emptyList();
                } catch (final NullPointerException e) {
                    LOGGER.debug("The two nodes are not connected to each other", e);
                    return Collections.emptyList();
                }
            }
        }
    }

}
