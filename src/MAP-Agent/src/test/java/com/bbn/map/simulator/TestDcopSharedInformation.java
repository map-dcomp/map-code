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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.dcop.DcopReceiverMessage;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests for {@link DcopSharedInformation}.
 * 
 * @author jschewe
 *
 */
public class TestDcopSharedInformation {

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain()
            .around(new TestUtils.Retry(TestUtils.DEFAULT_RETRY_COUNT));

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDcopSharedInformation.class);

    /**
     * Make sure that shared information is properly traversing the network.
     * 
     * @throws IOException
     *             if there is an error reading in the test files
     * 
     * @throws URISyntaxException
     *             if the test file paths cannot be properly converted to a URI
     * 
     */
    @Test
    public void testBasicSharing() throws URISyntaxException, IOException {
        final String regionAName = "A";
        final RegionIdentifier regionA = new StringRegionIdentifier(regionAName);
        final String regionBName = "B";
        final RegionIdentifier regionB = new StringRegionIdentifier(regionBName);
        final int numApRoundsToInitialize = 5;

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/test-dcop-sharing");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = null;

        final VirtualClock clock = new SimpleClock();

        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, TestUtils.POLLING_INTERVAL_MS,
                TestUtils.DNS_TTL, false, false, false, AppMgrUtils::getContainerParameters)) {

            final int numApRoundsToFinishSharing = SimUtils.computeRoundsToStabilize(sim) + 10;

            sim.startSimulation();
            sim.startClients();

            SimUtils.waitForApRounds(sim, numApRoundsToInitialize);

            // set shared information on the 2 DCOP nodes
            final Controller nodeA0 = sim.getControllerById(new DnsNameIdentifier("nodeA0"));
            final DcopSharedInformation regionAShared = new DcopSharedInformation();
            final int regionAiteration = 1;
            regionAShared.putMessageAtIteration(regionAiteration, new DcopReceiverMessage());
            nodeA0.setLocalDcopSharedInformation(regionAShared);
            LOGGER.info("Running dcop on nodeA0: {}", nodeA0.isRunDCOP());

            final Controller nodeB0 = sim.getControllerById(new DnsNameIdentifier("nodeB0"));
            final DcopSharedInformation regionBShared = new DcopSharedInformation();
            final int regionBiteration = 2;
            regionBShared.putMessageAtIteration(regionBiteration, new DcopReceiverMessage());
            nodeB0.setLocalDcopSharedInformation(regionBShared);
            LOGGER.info("Running dcop on nodeB0: {}", nodeB0.isRunDCOP());

            SimUtils.waitForApRounds(sim, numApRoundsToFinishSharing);
            clock.stopClock();

            // check that the data is consistent

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionAResult = nodeA0
                    .getAllDcopSharedInformation();
            // ClassCastException is an error
            final DcopSharedInformation regionASharedResultA = regionAResult.get(regionA);
            assertThat(regionASharedResultA, equalTo(regionAShared));

            final DcopSharedInformation regionASharedResultB = regionAResult.get(regionB);
            assertThat(regionASharedResultB, equalTo(regionBShared));

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionBResult = nodeB0
                    .getAllDcopSharedInformation();
            // ClassCastException is an error
            final DcopSharedInformation regionBSharedResultA = regionBResult.get(regionA);
            assertThat(regionBSharedResultA, equalTo(regionAShared));

            final DcopSharedInformation regionBSharedResultB = regionBResult.get(regionB);
            assertThat(regionBSharedResultB, equalTo(regionBShared));

        } // use simulation

    }

    /**
     * Run {@link #testBasicSharing()} with direct DCOP communication.
     * 
     * @throws URISyntaxException
     *             test error
     * @throws IOException
     *             test error
     */
    @Ignore("Sharing of initial state and retry isn't implemented, see ticket 532")
    @Test
    public void testBasicSharingDirect() throws IOException, URISyntaxException {
        AgentConfiguration.getInstance().setDcopShareDirect(true);
        testBasicSharing();
    }

    /**
     * Example of how to share point to point information.
     * 
     * @throws IOException
     *             if there is an error reading in the test files
     * 
     * @throws URISyntaxException
     *             if the test file paths cannot be properly converted to a URI
     * 
     */
    @Test
    public void testP2PSharing() throws URISyntaxException, IOException {
        final String regionAName = "A";
        final RegionIdentifier regionA = new StringRegionIdentifier(regionAName);

        final String regionBName = "B";
        final RegionIdentifier regionB = new StringRegionIdentifier(regionBName);

        final String regionCName = "C";
        final RegionIdentifier regionC = new StringRegionIdentifier(regionCName);

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/test-dcop-sharing-p2p");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = null;

        final VirtualClock clock = new SimpleClock();
        final int numApRoundsToInitialize = 5;

        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, TestUtils.POLLING_INTERVAL_MS,
                TestUtils.DNS_TTL, false, false, false, AppMgrUtils::getContainerParameters)) {

            final int numApRoundsToFinishSharing = SimUtils.computeRoundsToStabilize(sim);

            sim.startSimulation();
            sim.startClients();
            SimUtils.waitForApRounds(sim, numApRoundsToInitialize);

            // set shared information on the 3 DCOP nodes
            final Controller nodeA0 = sim.getControllerById(new DnsNameIdentifier("nodeA0"));
            final DcopSharedInformation regionAShared = new DcopSharedInformation();
            final int regionAIteration = 1;
            regionAShared.putMessageAtIteration(regionAIteration, new DcopReceiverMessage());
            nodeA0.setLocalDcopSharedInformation(regionAShared);

            final Controller nodeB0 = sim.getControllerById(new DnsNameIdentifier("nodeB0"));
            final DcopSharedInformation regionBShared = new DcopSharedInformation();
            final int regionBIteration = 5;
            regionBShared.putMessageAtIteration(regionBIteration, new DcopReceiverMessage());
            nodeB0.setLocalDcopSharedInformation(regionBShared);

            final Controller nodeC0 = sim.getControllerById(new DnsNameIdentifier("nodeC0"));
            final DcopSharedInformation regionCShared = new DcopSharedInformation();
            final int regionCIteration = 10;
            regionCShared.putMessageAtIteration(regionCIteration, new DcopReceiverMessage());
            nodeC0.setLocalDcopSharedInformation(regionCShared);

            SimUtils.waitForApRounds(sim, numApRoundsToFinishSharing);

            LOGGER.info("Stopping clock");
            clock.stopClock();
            LOGGER.info("Clock stopped");

            // check that the data is correct

            // the *- is to keep eclipse from reformatting the comment.
            /*-
             * The topology looks like this.
             * 
             * A0 - A1 - B1 - B0 
             *      | 
             *      C1 - C0
             *      
             * A will see the message from itself, regionB and regionC. 
             * However what one really cares about is did A see a message from regionB and regionC
             * with the appropriate message. 
             * 
             * B will see messages from itself and A, but not from C because C is not a neighbor of B.
             * 
             * C will see messages from itself and A, but not from B because B is not a neighbor of C.
             */

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionAResult = nodeA0
                    .getAllDcopSharedInformation();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("regionAresult: {}", regionAResult);
            }

            assertThat(regionAResult, hasKey(regionB));
            final DcopSharedInformation regionASharedResultB = regionAResult.get(regionB);
            assertThat(regionASharedResultB, equalTo(regionBShared));

            // Assert.assertEquals(regionB,
            // regionASharedResultB.getSourceRegion());
            // final String resultMsgBA =
            // regionASharedResultB.getMessage(regionA);
            // Assert.assertEquals(msgBA, resultMsgBA);

            assertThat(regionAResult, hasKey(regionC));
            final DcopSharedInformation regionASharedResultC = regionAResult.get(regionC);
            assertThat(regionASharedResultC, equalTo(regionCShared));

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionBResult = nodeB0
                    .getAllDcopSharedInformation();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("regionBresult: {}", regionBResult);
            }

            assertThat(regionBResult, hasKey(regionA));
            final DcopSharedInformation regionBSharedResultA = regionBResult.get(regionA);
            assertThat(regionBSharedResultA, equalTo(regionAShared));

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionCResult = nodeC0
                    .getAllDcopSharedInformation();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("regionCresult: {}", regionCResult);
            }

            assertThat(regionCResult, hasKey(regionA));
            final DcopSharedInformation regionCSharedResultA = regionCResult.get(regionA);
            assertThat(regionCSharedResultA, equalTo(regionAShared));

            LOGGER.info("End of tests");
        } // use simulation

    }

    /**
     * Execute {@link #testP2PSharing()} using direct DCOP communication.
     * 
     * @throws IOException
     *             test error
     * @throws URISyntaxException
     *             test error
     */
    @Ignore("Sharing of initial state and retry isn't implemented, see ticket 532")
    @Test
    public void testP2PSharingDirect() throws URISyntaxException, IOException {
        AgentConfiguration.getInstance().setDcopShareDirect(true);
        testP2PSharing();
    }

    /**
     * Make sure that a second DCOP message is properly traversing the network.
     * 
     * @throws IOException
     *             if there is an error reading in the test files
     * 
     * @throws URISyntaxException
     *             if the test file paths cannot be properly converted to a URI
     * 
     */
    @Test
    public void test2MessageSharing() throws URISyntaxException, IOException {
        final String regionAName = "A";
        final RegionIdentifier regionA = new StringRegionIdentifier(regionAName);
        final String regionBName = "B";
        final RegionIdentifier regionB = new StringRegionIdentifier(regionBName);
        final String regionCName = "C";
        final RegionIdentifier regionC = new StringRegionIdentifier(regionCName);
        final int numApRoundsToInitialize = 5;

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/test-dcop-sharing-multiple");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = null;

        final VirtualClock clock = new SimpleClock();

        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, TestUtils.POLLING_INTERVAL_MS,
                TestUtils.DNS_TTL, false, false, false, AppMgrUtils::getContainerParameters)) {

            final int numApRoundsToFinishSharing = SimUtils.computeRoundsToStabilize(sim) + 10;

            sim.startSimulation();
            sim.startClients();

            SimUtils.waitForApRounds(sim, numApRoundsToInitialize);

            final Controller nodeA0 = sim.getControllerById(new DnsNameIdentifier("nodeA0"));
            final Controller nodeB0 = sim.getControllerById(new DnsNameIdentifier("nodeB0"));
            final Controller nodeC0 = sim.getControllerById(new DnsNameIdentifier("nodeC0"));
            LOGGER.info("Running dcop on nodeA0: {}", nodeA0.isRunDCOP());
            LOGGER.info("Running dcop on nodeB0: {}", nodeB0.isRunDCOP());
            LOGGER.info("Running dcop on nodeC0: {}", nodeC0.isRunDCOP());

            // set shared information on the 2 DCOP nodes
            final DcopSharedInformation regionAShared1 = new DcopSharedInformation();
            final int regionAiteration1 = 10;
            regionAShared1.putMessageAtIteration(regionAiteration1, new DcopReceiverMessage());
            nodeA0.setLocalDcopSharedInformation(regionAShared1);

            final DcopSharedInformation regionBShared1 = new DcopSharedInformation();
            final int regionBiteration1 = 20;
            regionBShared1.putMessageAtIteration(regionBiteration1, new DcopReceiverMessage());
            nodeB0.setLocalDcopSharedInformation(regionBShared1);

            final DcopSharedInformation regionCShared1 = new DcopSharedInformation();
            final int regionCiteration1 = 30;
            regionCShared1.putMessageAtIteration(regionCiteration1, new DcopReceiverMessage());
            nodeC0.setLocalDcopSharedInformation(regionCShared1);

            SimUtils.waitForApRounds(sim, numApRoundsToFinishSharing);
            
            // set second shared information on the 2 DCOP nodes
            final DcopSharedInformation regionAShared2 = new DcopSharedInformation();
            final int regionAiteration2 = 11;
            regionAShared2.putMessageAtIteration(regionAiteration2, new DcopReceiverMessage());
            nodeA0.setLocalDcopSharedInformation(regionAShared2);

            final DcopSharedInformation regionBShared2 = new DcopSharedInformation();
            final int regionBiteration2 = 21;
            regionBShared2.putMessageAtIteration(regionBiteration2, new DcopReceiverMessage());
            nodeB0.setLocalDcopSharedInformation(regionBShared2);
            
            final DcopSharedInformation regionCShared2 = new DcopSharedInformation();
            final int regionCiteration2 = 31;
            regionCShared2.putMessageAtIteration(regionCiteration2, new DcopReceiverMessage());
            nodeC0.setLocalDcopSharedInformation(regionCShared2);

            SimUtils.waitForApRounds(sim, numApRoundsToFinishSharing);
            
            clock.stopClock();

            // check that the data is consistent

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionAResult = nodeA0
                    .getAllDcopSharedInformation();            
            final DcopSharedInformation regionASharedResultA = regionAResult.get(regionA);
            assertThat(regionASharedResultA, equalTo(regionAShared2));
            final DcopSharedInformation regionASharedResultB = regionAResult.get(regionB);
            assertThat(regionASharedResultB, equalTo(regionBShared2));

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionBResult = nodeB0
                    .getAllDcopSharedInformation();
            final DcopSharedInformation regionBSharedResultA = regionBResult.get(regionA);
            assertThat(regionBSharedResultA, equalTo(regionAShared2));
            final DcopSharedInformation regionBSharedResultB = regionBResult.get(regionB);
            assertThat(regionBSharedResultB, equalTo(regionBShared2));
            final DcopSharedInformation regionBSharedResultC = regionBResult.get(regionC);
            assertThat(regionBSharedResultC, equalTo(regionCShared2));

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionCResult = nodeC0
                    .getAllDcopSharedInformation();
            final DcopSharedInformation regionCSharedResultB = regionCResult.get(regionB);
            assertThat(regionCSharedResultB, equalTo(regionBShared2));
            final DcopSharedInformation regionCSharedResultC = regionCResult.get(regionC);
            assertThat(regionCSharedResultC, equalTo(regionCShared2));

        } // use simulation

    }
}
