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

import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.Test;
import org.protelis.lang.datatype.DeviceUID;

import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableMap;

/**
 * Some tests for {@link ResourceSummary} objects.
 * 
 * @author jschewe
 *
 */
public class ResourceSummaryTest {

    /**
     * Check that we see resource summaries in each region have the proper
     * source and neighbor for the test service.
     * 
     * @throws URISyntaxException
     *             test error
     * @throws IOException
     *             test error
     */
    @Test
    public void testSymetricNetworkLoadAndDemand() throws URISyntaxException, IOException {
        final String regionAName = "A";
        final RegionIdentifier regionA = new StringRegionIdentifier(regionAName);
        final String regionBName = "B";
        final RegionIdentifier regionB = new StringRegionIdentifier(regionBName);
        final String regionCName = "C";
        final RegionIdentifier regionC = new StringRegionIdentifier(regionCName);
        final String serviceGroup = "test";
        final String serviceArtifact = "service1";
        final String serviceVersion = "1";
        final ApplicationCoordinates service = new ApplicationCoordinates(serviceGroup, serviceArtifact,
                serviceVersion);
        final EstimationWindow estimationWindow = EstimationWindow.LONG;

        final URL baseu = ResourceSummaryTest.class.getResource("SymmetricSummary");
        final Path basePath = Paths.get(baseu.toURI());
        final Path scenarioPath = basePath.resolve("scenario");
        final Path demandPath = basePath.resolve("demand");

        final VirtualClock clock = new SimpleClock();

        try (Simulation sim = new Simulation("test", scenarioPath, demandPath, clock, TestUtils.POLLING_INTERVAL_MS,
                TestUtils.DNS_TTL, false, false, false, AppMgrUtils::getContainerParameters)) {

            Controller dcopA = null;
            Controller dcopB = null;
            Controller dcopC = null;

            // find all of the DCOP nodes
            for (final Map.Entry<DeviceUID, Controller> entry : sim.getScenario().getServers().entrySet()) {
                final Controller server = entry.getValue();
                if (server.isRunDCOP()) {
                    if (regionA.equals(server.getRegionIdentifier())) {
                        assertNull(dcopA);
                        dcopA = server;
                    } else if (regionB.equals(server.getRegionIdentifier())) {
                        assertNull(dcopB);
                        dcopB = server;
                    } else if (regionC.equals(server.getRegionIdentifier())) {
                        assertNull(dcopC);
                        dcopC = server;
                    } else {
                        fail("DCOP found in unknown region: " + server.getRegionIdentifier());
                    }
                }
            }
            assertNotNull(dcopA);
            assertNotNull(dcopB);
            assertNotNull(dcopC);

            final int numApRoundsToStabilize = SimUtils.computeRoundsToStabilize(sim);

            // make sure AP is UP
            sim.startSimulation();
            SimUtils.waitForApRounds(sim, numApRoundsToStabilize);

            // start the clients and give them time to share some information
            sim.startClients();
            SimUtils.waitForApRounds(sim, numApRoundsToStabilize);
            clock.stopClock();

            // check the values

            // region A
            // neighbor B, src A
            final ResourceSummary summaryA = dcopA.getNetworkState().getRegionSummary(estimationWindow);
            final ImmutableMap<RegionIdentifier, ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> netLoadA = summaryA
                    .getNetworkLoad();
            assertThat(netLoadA, aMapWithSize(1));
            assertThat(netLoadA, hasKey(regionB));

            final ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> netLoadANeighborB = netLoadA
                    .get(regionB);
            assertThat(netLoadANeighborB, aMapWithSize(1));
            assertThat(netLoadANeighborB, hasKey(regionA));

            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> netLoadANeighborBSrcA = netLoadANeighborB
                    .get(regionA);
            assertThat(netLoadANeighborBSrcA, aMapWithSize(1));
            assertThat(netLoadANeighborBSrcA, hasKey(service));

            // region B
            // neighbor A, src A && neighbor C, src A
            final ResourceSummary summaryB = dcopB.getNetworkState().getRegionSummary(estimationWindow);
            final ImmutableMap<RegionIdentifier, ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> netLoadB = summaryB
                    .getNetworkLoad();
            assertThat(netLoadB, aMapWithSize(2));
            assertThat(netLoadB, hasKey(regionA));

            final ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> netLoadBNeighborA = netLoadB
                    .get(regionA);
            assertThat(netLoadBNeighborA, aMapWithSize(1));

            assertThat(netLoadBNeighborA, hasKey(regionA));
            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> netLoadBNeighborASrcA = netLoadBNeighborA
                    .get(regionA);
            assertThat(netLoadBNeighborASrcA, aMapWithSize(1));
            assertThat(netLoadBNeighborASrcA, hasKey(service));

            assertThat(netLoadB, hasKey(regionC));
            final ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> netLoadBNeighborC = netLoadB
                    .get(regionC);
            assertThat(netLoadBNeighborC, aMapWithSize(1));
           
            assertThat(netLoadBNeighborA, hasKey(regionA));
            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> netLoadBNeighborCSrcA = netLoadBNeighborC
                    .get(regionA);
            assertThat(netLoadBNeighborCSrcA, aMapWithSize(1));
            assertThat(netLoadBNeighborCSrcA, hasKey(service));

            // region C
            // neighbor B, src A
            final ResourceSummary summaryC = dcopC.getNetworkState().getRegionSummary(estimationWindow);
            final ImmutableMap<RegionIdentifier, ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> netLoadC = summaryC
                    .getNetworkLoad();
            assertThat(netLoadC, aMapWithSize(1));
            assertThat(netLoadC, hasKey(regionB));

            final ImmutableMap<RegionIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> netLoadCNeighborB = netLoadC
                    .get(regionB);
            assertThat(netLoadCNeighborB, aMapWithSize(1));
            assertThat(netLoadCNeighborB, hasKey(regionA));

            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> netLoadCNeighborBSrcA = netLoadCNeighborB
                    .get(regionA);
            assertThat(netLoadCNeighborBSrcA, aMapWithSize(1));
            assertThat(netLoadCNeighborBSrcA, hasKey(service));

        } // use simulation
    }

}
