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
package com.bbn.map.simulator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.Controller;
import com.bbn.map.ap.ApplicationManagerUtils;
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
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext())
            .around(new TestUtils.UseMapApplicationManager())
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
        final long pollingInterval = 10;
        final int dnsTtlSeconds = 60;
        final int numApRoundsToInitialize = 5;

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/test-dcop-sharing");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = null;

        final VirtualClock clock = new SimpleClock();

        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                false, false, false, ApplicationManagerUtils::getContainerParameters)) {

            final int numApRoundsToFinishSharing = TestUtils.computeRoundsToStabilize(sim);

            sim.startSimulation();
            TestUtils.waitForApRounds(sim, numApRoundsToInitialize);

            // set shared information on the 2 DCOP nodes
            final Controller nodeA0 = sim.getControllerById(new DnsNameIdentifier("nodeA0"));
            final TestDcopShared regionAShared = new TestDcopShared();
            regionAShared.setMessage("RegionA");
            nodeA0.setLocalDcopSharedInformation(regionAShared);

            final Controller nodeB0 = sim.getControllerById(new DnsNameIdentifier("nodeB0"));
            final TestDcopShared regionBShared = new TestDcopShared();
            regionBShared.setMessage("RegionB");
            nodeB0.setLocalDcopSharedInformation(regionBShared);

            TestUtils.waitForApRounds(sim, numApRoundsToFinishSharing);
            sim.stopSimulation();

            // check that the data is consistent

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionAResult = nodeA0
                    .getAllDcopSharedInformation();
            // ClassCastException is an error
            final TestDcopShared regionASharedResultA = (TestDcopShared) regionAResult.get(regionA);
            Assert.assertEquals(regionAShared, regionASharedResultA);

            final TestDcopShared regionASharedResultB = (TestDcopShared) regionAResult.get(regionB);
            Assert.assertEquals(regionBShared, regionASharedResultB);

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionBResult = nodeB0
                    .getAllDcopSharedInformation();
            // ClassCastException is an error
            final TestDcopShared regionBSharedResultA = (TestDcopShared) regionBResult.get(regionA);
            Assert.assertEquals(regionAShared, regionBSharedResultA);

            final TestDcopShared regionBSharedResultB = (TestDcopShared) regionBResult.get(regionB);
            Assert.assertEquals(regionBShared, regionBSharedResultB);
        } // use simulation

    }

    /**
     * Example of how to share point to point information.
     * 
     * The topology looks like this.
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

        final long pollingInterval = 10;
        final int dnsTtlSeconds = 60;

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/test-dcop-sharing-p2p");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = null;

        final VirtualClock clock = new SimpleClock();
        final int numApRoundsToInitialize = 5;

        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                false, false, false, ApplicationManagerUtils::getContainerParameters)) {

            final int numApRoundsToFinishSharing = TestUtils.computeRoundsToStabilize(sim);

            sim.startSimulation();
            TestUtils.waitForApRounds(sim, numApRoundsToInitialize);

            // set shared information on the 3 DCOP nodes
            final Controller nodeA0 = sim.getControllerById(new DnsNameIdentifier("nodeA0"));
            final TestDcopSharedP2P regionAShared = new TestDcopSharedP2P(regionA);
            final String msgAB = "Message from A to B";
            regionAShared.addMessage(regionB, msgAB);
            final String msgAC = "Message from A to C";
            regionAShared.addMessage(regionC, msgAC);
            nodeA0.setLocalDcopSharedInformation(regionAShared);

            final Controller nodeB0 = sim.getControllerById(new DnsNameIdentifier("nodeB0"));
            final TestDcopSharedP2P regionBShared = new TestDcopSharedP2P(regionB);
            final String msgBA = "Message from B to A";
            regionBShared.addMessage(regionA, msgBA);
            final String msgBC = "Message from B to C";
            regionBShared.addMessage(regionC, msgBC);
            nodeB0.setLocalDcopSharedInformation(regionBShared);

            final Controller nodeC0 = sim.getControllerById(new DnsNameIdentifier("nodeC0"));
            final TestDcopSharedP2P regionCShared = new TestDcopSharedP2P(regionC);
            final String msgCA = "Message from C to A";
            regionCShared.addMessage(regionA, msgCA);
            final String msgCB = "Message from C to B";
            regionCShared.addMessage(regionB, msgCB);
            nodeC0.setLocalDcopSharedInformation(regionCShared);

            TestUtils.waitForApRounds(sim, numApRoundsToFinishSharing);

            LOGGER.info("Stopping simulation");
            sim.stopSimulation();
            LOGGER.info("Simulation stopped");

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

            // ClassCastException is an error

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionAResult = nodeA0
                    .getAllDcopSharedInformation();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("regionAresult: {}", regionAResult);
            }

            final TestDcopSharedP2P regionASharedResultB = (TestDcopSharedP2P) regionAResult.get(regionB);
            Assert.assertEquals(regionB, regionASharedResultB.getSourceRegion());
            final String resultMsgBA = regionASharedResultB.getMessage(regionA);
            Assert.assertEquals(msgBA, resultMsgBA);

            final TestDcopSharedP2P regionASharedResultC = (TestDcopSharedP2P) regionAResult.get(regionC);
            Assert.assertEquals(regionC, regionASharedResultC.getSourceRegion());
            final String resultMsgCA = regionASharedResultC.getMessage(regionA);
            Assert.assertEquals(msgCA, resultMsgCA);

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionBResult = nodeB0
                    .getAllDcopSharedInformation();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("regionBresult: {}", regionBResult);
            }

            final TestDcopSharedP2P regionBSharedResultA = (TestDcopSharedP2P) regionBResult.get(regionA);
            Assert.assertEquals(regionA, regionBSharedResultA.getSourceRegion());
            final String resultMsgAB = regionBSharedResultA.getMessage(regionB);
            Assert.assertEquals(msgAB, resultMsgAB);

            final ImmutableMap<RegionIdentifier, DcopSharedInformation> regionCResult = nodeC0
                    .getAllDcopSharedInformation();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("regionCresult: {}", regionCResult);
            }

            final TestDcopSharedP2P regionCSharedResultA = (TestDcopSharedP2P) regionCResult.get(regionA);
            Assert.assertEquals(regionA, regionCSharedResultA.getSourceRegion());
            final String resultMsgAC = regionCSharedResultA.getMessage(regionC);
            Assert.assertEquals(msgAC, resultMsgAC);

            LOGGER.info("End of tests");
        } // use simulation

    }

    private static final class TestDcopShared extends DcopSharedInformation {

        private static final long serialVersionUID = 1L;

        private String message;

        /**
         * 
         * @param str
         *            the message to share
         */
        public void setMessage(final String str) {
            message = str;
        }

        /**
         * 
         * @return the message that was shared
         */
        public String getMessage() {
            return message;
        }

        @Override
        public int hashCode() {
            return message.hashCode();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            } else if (o instanceof TestDcopShared) {
                final TestDcopShared other = (TestDcopShared) o;
                return other.getMessage().equals(getMessage());
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " [" + " message: " + message + " ]";

        }
    }

    private static final class TestDcopSharedP2P extends DcopSharedInformation {

        private static final long serialVersionUID = 1L;

        private final RegionIdentifier sourceRegion;

        private Map<RegionIdentifier, String> messages = new HashMap<RegionIdentifier, String>();

        /**
         * 
         * @param sourceRegion
         *            the region that the message originated from
         */
        /* package */ TestDcopSharedP2P(final RegionIdentifier sourceRegion) {
            this.sourceRegion = sourceRegion;
        }

        /**
         * @return get the source region
         */
        public RegionIdentifier getSourceRegion() {
            return sourceRegion;
        }

        /**
         * @param destination
         *            which region should be looking at the message
         * @param message
         *            the message to send.
         */
        public void addMessage(final RegionIdentifier destination, final String message) {
            messages.put(destination, message);
        }

        /**
         * 
         * @return the message that was shared for the specified region
         */
        public String getMessage(final RegionIdentifier destination) {
            return messages.get(destination);
        }

        @Override
        public int hashCode() {
            return messages.hashCode();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            } else if (o instanceof TestDcopSharedP2P) {
                final TestDcopSharedP2P other = (TestDcopSharedP2P) o;
                return other.messages.equals(messages);
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " [" + " sourceRegion: " + sourceRegion + " messages: " + messages
                    + " ]";

        }
    }
}
