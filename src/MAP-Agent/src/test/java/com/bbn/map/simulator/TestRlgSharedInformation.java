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
package com.bbn.map.simulator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.rlg.RlgSharedInformation;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests for {@link RlgSharedInformation}.
 * 
 * @author jschewe
 *
 */
public class TestRlgSharedInformation {

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext())
            .around(new TestUtils.Retry(TestUtils.DEFAULT_RETRY_COUNT));

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

            final int numApRoundsToFinishSharing = SimUtils.computeRoundsToStabilize(sim);

            sim.startSimulation();
            sim.startClients();

            SimUtils.waitForApRounds(sim, numApRoundsToInitialize);

            // set shared information on the 2 RLG nodes
            final Controller nodeA0 = sim.getControllerById(new DnsNameIdentifier("nodeA0"));
            final TestRlgShared regionAShared = new TestRlgShared();
            regionAShared.setMessage("RegionA");
            nodeA0.setLocalRlgSharedInformation(regionAShared);

            final Controller nodeB0 = sim.getControllerById(new DnsNameIdentifier("nodeB0"));
            final TestRlgShared regionBShared = new TestRlgShared();
            regionBShared.setMessage("RegionB");
            nodeB0.setLocalRlgSharedInformation(regionBShared);

            SimUtils.waitForApRounds(sim, numApRoundsToFinishSharing);
            clock.stopClock();

            // check that the data is consistent

            final ImmutableMap<RegionIdentifier, RlgSharedInformation> regionAResult = nodeA0
                    .getAllRlgSharedInformation();
            // ClassCastException is an error
            final TestRlgShared regionASharedResultA = (TestRlgShared) regionAResult.get(regionA);
            Assert.assertEquals(regionAShared, regionASharedResultA);

            final TestRlgShared regionASharedResultB = (TestRlgShared) regionAResult.get(regionB);
            Assert.assertEquals(regionBShared, regionASharedResultB);

            final ImmutableMap<RegionIdentifier, RlgSharedInformation> regionBResult = nodeB0
                    .getAllRlgSharedInformation();
            // ClassCastException is an error
            final TestRlgShared regionBSharedResultA = (TestRlgShared) regionBResult.get(regionA);
            Assert.assertEquals(regionAShared, regionBSharedResultA);

            final TestRlgShared regionBSharedResultB = (TestRlgShared) regionBResult.get(regionB);
            Assert.assertEquals(regionBShared, regionBSharedResultB);
        } // use simulation

    }

    private static final class TestRlgShared extends RlgSharedInformation {

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
            } else if (o instanceof TestRlgShared) {
                final TestRlgShared other = (TestRlgShared) o;
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
}
