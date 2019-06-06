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
package com.bbn.map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.CloseableThreadContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.ap.LeaderElectionTests;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.simulator.SimUtils;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test that {@link NetworkAvailableServices} is properly shared across the
 * network.
 * 
 * @author jschewe
 *
 */
public class TestShareNetworkAvailableServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestShareNetworkAvailableServices.class);

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext())
            .around(new TestUtils.Retry(TestUtils.DEFAULT_RETRY_COUNT));

    /**
     * Reset the {@link AgentConfiguration} object to default values. This is
     * done before and after all tests to ensure that other tests are not
     * effected by using the different algorithms here.
     */
    @Before
    @After
    public void resetAgentConfiguration() {
        AgentConfiguration.resetToDefaults();
    }

    private static final int NUM_ROUNDS_BETWEEN_NEIGHBOR_CONNECT_ATTEMPTS = 1;

    /**
     * Test that dns information is shared with all nodes in the network.
     * 
     * @throws URISyntaxException
     *             internal test error
     * @throws IOException
     *             internal test error
     */
    @Test
    public void test() throws URISyntaxException, IOException {
        final RegionIdentifier regionX = new StringRegionIdentifier("X");
        final RegionIdentifier regionA = new StringRegionIdentifier("A");
        final RegionIdentifier regionB = new StringRegionIdentifier("B");
        final RegionIdentifier regionC = new StringRegionIdentifier("C");
        final String serviceGroup = "com.bbn";
        final String serviceVersion = "1";
        final String serviceArtifact = "image-recognition-high";
        final ServiceIdentifier<?> service = new ApplicationCoordinates(serviceGroup, serviceArtifact, serviceVersion);
        final boolean expectedServicesX = true;
        final boolean expectedServicesA = false;
        final boolean expectedServicesB = false;
        final boolean expectedServicesC = false;

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/test-dns-sharing");
        final Path baseDirectory = Paths.get(baseu.toURI());
        final Path demandPath = null;

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, TestUtils.POLLING_INTERVAL_MS,
                TestUtils.DNS_TTL, true, false, true, AppMgrUtils::getContainerParameters)) {
            sim.startSimulation();
            sim.startClients();

            // wait for for everything to get connected
            LOGGER.info("Waiting for all nodes to be connected to AP");
            // FIXME needs to get code from test-leader-election branch
            // TestUtils
            boolean done = false;
            while (!done) {
                done = sim.getAllControllers().stream().allMatch(c -> c.isApConnectedToAllNeighbors());
                if (!done) {
                    sim.getClock().waitForDuration(NUM_ROUNDS_BETWEEN_NEIGHBOR_CONNECT_ATTEMPTS);
                }
            }
            // end duplicated code
            LOGGER.info("All nodes connected");

            final double networkDiameter = sim.getNetworkDiameter();
            Assert.assertTrue("Cannot find the network diameter", Double.isFinite(networkDiameter));

            final int numRoundsToGatherAndBroadcast = (int) Math
                    .ceil(networkDiameter * LeaderElectionTests.MULTIPLER_FOR_GLOBAL_NETWORK_SHARING);

            LOGGER.info("Waiting {} rounds for information to be shared", numRoundsToGatherAndBroadcast);
            SimUtils.waitForApRounds(sim, numRoundsToGatherAndBroadcast);
            LOGGER.info("Done waiting");

            for (final Controller controller : sim.getAllControllers()) {
                try (CloseableThreadContext.Instance ctc = CloseableThreadContext
                        .push(controller.getNodeIdentifier().getName())) {

                    final NetworkAvailableServices actual = controller.getAllNetworkAvailableServices();

                    final boolean actualA = actual.isServiceAvailable(regionA, service);
                    assertThat("Controller: " + controller.getName() + " region A", actualA, is(expectedServicesA));

                    final boolean actualB = actual.isServiceAvailable(regionB, service);
                    assertThat("Controller: " + controller.getName() + " region B", actualB, is(expectedServicesB));

                    final boolean actualC = actual.isServiceAvailable(regionC, service);
                    assertThat("Controller: " + controller.getName() + " region C", actualC, is(expectedServicesC));

                    final boolean actualX = actual.isServiceAvailable(regionX, service);
                    assertThat("Controller: " + controller.getName() + " region X", actualX, is(expectedServicesX));
                } // logging context
            }

        } // simulation allocation

    }

}
