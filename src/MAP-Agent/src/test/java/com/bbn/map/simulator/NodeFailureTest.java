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
package com.bbn.map.simulator;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for simulating node failures.
 * 
 * @author jschewe
 *
 */
public class NodeFailureTest {

    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Specify that the most loaded node should be marked as a failure. See that
     * this node goes down.
     * 
     * NodeA3 is the default node for the service. So it is always the most
     * loaded node. Check that when given a choice of this node and another one,
     * nodeA3 is the one that is shutdown.
     * 
     * @throws URISyntaxException
     *             internal test failure
     * @throws IOException
     *             internal test failure
     */
    @Test
    public void testMostLoadedFail() throws URISyntaxException, IOException {
        final NodeIdentifier nodeA3Id = new DnsNameIdentifier("nodeA3");

        final URL baseu = NodeFailureTest.class.getResource("fail-most-loaded");
        final Path basePath = Paths.get(baseu.toURI());
        final Path scenarioPath = basePath.resolve("scenario");
        final Path demandPath = basePath.resolve("demand");

        // failure is at time 41000
        final long timeToWaitForFailure = 50000;
        final VirtualClock clock = new SimpleClock();

        try (Simulation sim = new Simulation("test", scenarioPath, demandPath, clock, TestUtils.POLLING_INTERVAL_MS,
                TestUtils.DNS_TTL, false, false, false, AppMgrUtils::getContainerParameters)) {

            final int numApRoundsToStabilize = SimUtils.computeRoundsToStabilize(sim);

            // make sure AP is UP
            sim.startSimulation();
            SimUtils.waitForApRounds(sim, numApRoundsToStabilize);

            // start the clients and give them time to share some information
            sim.startClients();

            clock.waitUntilTime(timeToWaitForFailure);

            // stop the world to check on things
            clock.stopClock();

            // check that nodeA3 is not in the simulation
            assertThat(sim.getControllerById(nodeA3Id), nullValue());

        }
    }
}
