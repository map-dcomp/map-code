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
package com.bbn.map.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.bbn.map.simulator.Simulation;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.ns2.Link;
import com.bbn.protelis.networkresourcemanagement.ns2.NetworkDevice;
import com.bbn.protelis.networkresourcemanagement.ns2.Node;
import com.bbn.protelis.networkresourcemanagement.ns2.Switch;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;

/**
 * Helpful methods for various MAP things.
 * 
 * @author jschewe
 *
 */
public final class MapUtils {

    private MapUtils() {
    }

    /**
     * Node attribute that DCOP and RLG use until they support all node
     * attributes.
     */
    public static final NodeAttribute COMPUTE_ATTRIBUTE = NodeAttribute.TASK_CONTAINERS;

    /**
     * Used by
     * {@link Simulation#parseTopology(Topology, com.bbn.map.utils.MapUtils.GraphFactory)}
     * to create the graph objects.
     * 
     * @author jschewe
     *
     * @param <VERTEX_TYPE>
     *            type used for vertices
     * @param <EDGE_TYPE>
     *            type used for edges
     */
    public interface GraphFactory<VERTEX_TYPE, EDGE_TYPE> {
        /**
         * Create a vertex for the graph.
         * 
         * @param node
         *            the node to base the vertex on
         * @return the vertex
         */
        @Nonnull
        VERTEX_TYPE createVertex(@Nonnull Node node);

        /**
         * Create an edge for the graph.
         * 
         * @param link
         *            the link to base the edge on
         * @param left
         *            the vertex that maps to the left side of the link
         * @param right
         *            the vertex that maps to the right side of the link
         * @return the edge
         */
        @Nonnull
        EDGE_TYPE createEdge(@Nonnull Link link, @Nonnull VERTEX_TYPE left, @Nonnull VERTEX_TYPE right);

    }

    /**
     * If the specified device is an overlay node, then it is returned. If not,
     * it is traversed through and then it's neighbors are returned (working
     * recursively as needed).
     * 
     * @param device
     *            the network device to inspect
     * @param visited
     *            the devices that have been visited to avoid loop issues. This
     *            should initially be set to the device at the other end of the
     *            link being tested.
     * @return the overlay neighbors through device
     */
    public static Set<Node> resolveOverlayNeighbors(final NetworkDevice device, final Set<NetworkDevice> visited) {
        if (device instanceof Switch) {
            final Switch sw = (Switch) device;

            final Set<Node> switchMembers = sw.getNodes().stream().filter(n -> !visited.contains(n))
                    .collect(Collectors.toSet());
            final Set<NetworkDevice> newVisited = new HashSet<>(visited);
            newVisited.add(sw);
            newVisited.addAll(switchMembers);

            // all nodes connected to the switch are neighbors
            // call recursively in case one of the switch nodes is an underlay
            // node
            return switchMembers.stream().map(neighbor -> {
                final Set<Node> ns = resolveOverlayNeighbors(neighbor, newVisited);
                return ns;
            }).flatMap(Collection::stream).collect(Collectors.toSet());

        } else if (device instanceof Node) {
            final Node node = (Node) device;
            if (isUnderlay(node)) {
                final Set<NetworkDevice> newVisited = new HashSet<>(visited);
                newVisited.add(node);

                return node.getLinks().stream().map(link -> {
                    final NetworkDevice other;
                    if (node.equals(link.getLeft())) {
                        other = link.getRight();
                    } else {
                        other = link.getLeft();
                    }

                    final Set<Node> retval;
                    if (!newVisited.contains(other)) {
                        retval = resolveOverlayNeighbors(other, newVisited);
                    } else {
                        retval = Collections.emptySet();
                    }
                    return retval;
                }).flatMap(Collection::stream).collect(Collectors.toSet());
            } else {
                return Collections.singleton(node);
            }
        } else {
            throw new RuntimeException(
                    "Unknown network device type seen: " + (null == device ? "NULL" : device.getClass().getName()));
        }
    }

    /**
     * Parse the topology into a graph of the overlay network.
     * 
     * @param topology
     *            the topology to parse
     * @param factory
     *            used to create the vertices and links in the graph
     * @return graph of the overlay network
     * @param <VERTEX_TYPE>
     *            type for the vertices in the graph
     * @param <EDGE_TYPE>
     *            type for the edges in the graph
     */
    public static <VERTEX_TYPE, EDGE_TYPE> Graph<VERTEX_TYPE, EDGE_TYPE> parseTopology(@Nonnull final Topology topology,
            final GraphFactory<VERTEX_TYPE, EDGE_TYPE> factory) {
        final Graph<VERTEX_TYPE, EDGE_TYPE> graph = new SparseMultigraph<>();

        final Set<Link> links = new HashSet<>();
        final Map<String, VERTEX_TYPE> nodeNameMapping = new HashMap<>();
        topology.getNodes().entrySet().stream().map(Map.Entry::getValue).forEach(node -> {
            final VERTEX_TYPE netNode = factory.createVertex(node);
            graph.addVertex(netNode);

            links.addAll(node.getLinks());
            nodeNameMapping.put(node.getName(), netNode);
        });

        // add links
        final Set<ImmutablePair<VERTEX_TYPE, VERTEX_TYPE>> createdEdges = new HashSet<>();

        links.stream().forEach(link -> {
            final NetworkDevice leftDev = link.getLeft();
            final NetworkDevice rightDev = link.getRight();

            final Set<Node> leftNodes = resolveOverlayNeighbors(leftDev, Collections.singleton(rightDev));
            final Set<Node> rightNodes = resolveOverlayNeighbors(rightDev, Collections.singleton(leftDev));

            leftNodes.forEach(leftNode -> {
                final VERTEX_TYPE left = nodeNameMapping.get(leftNode.getName());

                rightNodes.forEach(rightNode -> {
                    final VERTEX_TYPE right = nodeNameMapping.get(rightNode.getName());
                    if (!left.equals(right)) {
                        // check that we haven't added the edge in either
                        // direction
                        // yet
                        final ImmutablePair<VERTEX_TYPE, VERTEX_TYPE> forwardEdge = ImmutablePair.of(left, right);
                        final ImmutablePair<VERTEX_TYPE, VERTEX_TYPE> backwardEdge = ImmutablePair.of(right, left);
                        if (!createdEdges.contains(forwardEdge) && !createdEdges.contains(backwardEdge)) {
                            final EDGE_TYPE l = factory.createEdge(link, left, right);
                            graph.addEdge(l, left, right);
                            createdEdges.add(forwardEdge);
                        }
                    } // check that link isn't connecting a vertex to itself
                }); // foreach right
            }); // foreach left
        }); // foreach link

        return graph;
    }

    /**
     * Packable visible for testing.
     */
    /* package */ static final String EXTRA_DATA_UNDERLAY = "underlay";

    /**
     * Check if the specified node is an underlay node.
     * 
     * @param n
     *            the node to check
     * @return true if this is an underlay node
     */
    public static boolean isUnderlay(final Node n) {
        final Object v = n.getExtraData().get(EXTRA_DATA_UNDERLAY);
        if (null != v) {
            return Boolean.parseBoolean(v.toString());
        } else {
            return false;
        }
    }

    /**
     * Check if this node is an NCP.
     * 
     * @param node
     *            the node to check
     * @return true if it is an NCP
     */
    public static boolean isNcp(final Node node) {
        return !node.isClient() && !isUnderlay(node);
    }

}
