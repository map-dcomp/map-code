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

import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.simulator.ClientSim;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.NetworkClient;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * See that getting the overlay topology for a region contains the right nodes.
 * 
 * @author jschewe
 *
 */
public class TestGetOverlay {

    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    private Simulation simulation;

    private Simulation createSimulation() throws IOException {
        try {
            final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/multinode");
            final Path baseDirectory = Paths.get(baseu.toURI());

            final Path demandPath = baseDirectory.resolve("multinode");

            final VirtualClock clock = new SimpleClock();
            final Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock,
                    TestUtils.POLLING_INTERVAL_MS, TestUtils.DNS_TTL, false, false, false,
                    AppMgrUtils::getContainerParameters);

            return sim;
        } catch (final URISyntaxException e) {
            // this shouldn't happen
            throw new RuntimeException(e);
        }
    }

    /**
     * Setup simulation.
     * 
     * @throws IOException
     *             if there is a problem loading the simulation
     */
    @Before
    public void setup() throws IOException {
        simulation = createSimulation();
    }

    /**
     * Shutdown simulation.
     */
    @After
    public void tearDown() {
        if (null != simulation) {
            simulation.stopSimulation();
            simulation = null;
        }
    }

    /**
     * Check that the nodes in a region are in the resulting topology.
     */
    @Test
    public void testNodesInRegion() {
        final RegionIdentifier regionA = new StringRegionIdentifier("A");
        final Stream<NodeIdentifier> expectedServers = simulation.getAllControllers().stream()
                .filter(c -> regionA.equals(c.getRegionIdentifier())).map(Controller::getNodeIdentifier);
        final Stream<NodeIdentifier> expectedClients = simulation.getClientSimulators().stream()
                .map(ClientSim::getClient).filter(c -> regionA.equals(c.getRegionIdentifier()))
                .map(NetworkClient::getNodeIdentifier);

        final Set<NodeIdentifier> expectedNodes = Stream.concat(expectedServers, expectedClients)
                .collect(Collectors.toSet());

        final OverlayTopology overlay = simulation.getOverlay(regionA);
        final Set<NodeIdentifier> allOverlayNodes = new HashSet<>(overlay.getAllNodes());
        assertThat(allOverlayNodes, hasItems(expectedNodes.toArray(new NodeIdentifier[0])));
    }
}
