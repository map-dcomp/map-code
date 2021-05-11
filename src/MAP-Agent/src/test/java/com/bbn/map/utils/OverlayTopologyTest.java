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
package com.bbn.map.utils;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.ns2.Link;
import com.bbn.protelis.networkresourcemanagement.ns2.Node;
import com.bbn.protelis.networkresourcemanagement.ns2.Switch;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test that the right set of neighbors are found for various topology
 * situations.
 * 
 * @author jschewe
 *
 */
public class OverlayTopologyTest {

    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    private static final Map<String, Object> UNDERLAY_EXTRA_DATA = Collections
            .singletonMap(MapUtils.EXTRA_DATA_UNDERLAY, true);

    private static final double DUMMY_BANDWIDTH = 10;
    private static final double DUMMY_DELAY = 0;

    private static Node createNode(final String name) {
        return createNode(name, false);
    }

    private static Node createUnderlayNode(final String name) {
        return createNode(name, true);
    }

    private static Node createNode(final String name, final boolean underlay) {
        final Map<String, Object> extraData = underlay ? UNDERLAY_EXTRA_DATA : Collections.emptyMap();
        return new Node(name, extraData);
    }

    private static int linkCounter = 0;

    private static void createLink(final Node left, final Node right) {
        final int linkIndex = linkCounter++;
        new Link("link" + linkIndex, left, right, DUMMY_BANDWIDTH, DUMMY_DELAY);
    }

    private static int switchCounter = 0;

    private static Switch createSwitch(final Node... nodes) {
        final int switchIndex = switchCounter++;
        return new Switch("sw" + switchIndex, new HashSet<>(Arrays.asList(nodes)), DUMMY_BANDWIDTH, DUMMY_DELAY);
    }

    /**
     * nodeA link nodeB -> nodeB.
     */
    @Test
    public void testSimpleLink() {
        final Node a = createNode("a");
        final Node b = createNode("b");
        createLink(a, b);

        final Set<Node> neighbors = MapUtils.resolveOverlayNeighbors(b, Collections.singleton(a));
        assertThat(neighbors, containsInAnyOrder(b));
    }

    /** nodeA sw nodeB, nodeC -> nodeB, nodeC. */
    @Test
    public void testSimpleSwitch() {
        final Node a = createNode("a");
        final Node b = createNode("b");
        final Node c = createNode("c");
        final Switch sw = createSwitch(a, b, c);

        final Set<Node> neighbors = MapUtils.resolveOverlayNeighbors(sw, Collections.singleton(a));
        assertThat(neighbors, containsInAnyOrder(b, c));
    }

    /** nodeA link nodeB(underlay) nodeC -> nodeC. */
    @Test
    public void testSimpleLinkUnderlay() {
        final Node a = createNode("a");
        final Node b = createUnderlayNode("b");
        final Node c = createNode("c");
        createLink(a, b);
        createLink(b, c);

        final Set<Node> neighbors = MapUtils.resolveOverlayNeighbors(b, Collections.singleton(a));
        assertThat(neighbors, containsInAnyOrder(c));
    }

    /** nodeA link nodeB(underlay) nodeC, nodeD -> nodeC, nodeD. */
    @Test
    public void testUnderlay2() {
        final Node a = createNode("a");
        final Node b = createUnderlayNode("b");
        final Node c = createNode("c");
        final Node d = createNode("d");
        createLink(a, b);
        createLink(b, c);
        createLink(b, d);

        final Set<Node> neighbors = MapUtils.resolveOverlayNeighbors(b, Collections.singleton(a));
        assertThat(neighbors, containsInAnyOrder(c, d));
    }

    /** nodeA sw (nodeB(underlay) link nodeD), nodeC -> nodeD, nodeC. */
    @Test
    public void testSwitchUnderlay1() {
        final Node a = createNode("a");
        final Node b = createUnderlayNode("b");
        final Node c = createNode("c");
        final Node d = createNode("d");

        createSwitch(a, b, c);
        createLink(b, d);

        final Set<Node> neighbors = MapUtils.resolveOverlayNeighbors(b, Collections.singleton(a));
        assertThat(neighbors, containsInAnyOrder(d, c));
    }

    /**
     * nodeA sw (nodeB(underlay) sw nodeD, nodeE), nodeC -> nodeD, nodeE, nodeC.
     */
    public void testSwitchUnderlay2() {
        final Node a = createNode("a");
        final Node b = createUnderlayNode("b");
        final Node c = createNode("c");
        final Node d = createNode("d");
        final Node e = createNode("e");

        createSwitch(a, b, c);
        createLink(b, d);
        createLink(b, e);

        final Set<Node> neighbors = MapUtils.resolveOverlayNeighbors(b, Collections.singleton(a));
        assertThat(neighbors, containsInAnyOrder(d, c, e));
    }

    /** nodeA sw nodeB(underlay) link nodeD, nodeC -> nodeD, nodeC. */
    @Test
    public void testSwitchUnderlay3() {
        final Node a = createNode("a");
        final Node b = createUnderlayNode("b");
        final Node c = createNode("c");
        final Node d = createNode("d");

        createSwitch(a, b);
        createLink(b, c);
        createLink(b, d);

        final Set<Node> neighbors = MapUtils.resolveOverlayNeighbors(b, Collections.singleton(a));
        assertThat(neighbors, containsInAnyOrder(d, c));
    }

    /** nodeA link nodeB link nodeC -> nodeB. */
    @Test
    public void testLimit1() {
        final Node a = createNode("a");
        final Node b = createNode("b");
        final Node c = createNode("c");
        createLink(a, b);
        createLink(b, c);

        final Set<Node> neighbors = MapUtils.resolveOverlayNeighbors(b, Collections.singleton(a));
        assertThat(neighbors, containsInAnyOrder(b));
    }

    /** nodeA sw nodeB link nodeC -> nodeB. */
    @Test
    public void testLimit2() {
        final Node a = createNode("a");
        final Node b = createNode("b");
        final Node c = createNode("c");
        createSwitch(a, b);
        createLink(b, c);

        final Set<Node> neighbors = MapUtils.resolveOverlayNeighbors(b, Collections.singleton(a));
        assertThat(neighbors, containsInAnyOrder(b));
    }

    /** nodeA link nodeB(underlay) link nodeC link nodeD -> nodeC. */
    @Test
    public void testLimit3() {
        final Node a = createNode("a");
        final Node b = createUnderlayNode("b");
        final Node c = createNode("c");
        final Node d = createNode("d");
        createLink(a, b);
        createLink(b, c);
        createLink(c, d);

        final Set<Node> neighbors = MapUtils.resolveOverlayNeighbors(b, Collections.singleton(a));
        assertThat(neighbors, containsInAnyOrder(c));
    }

}
