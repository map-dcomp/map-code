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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import org.apache.logging.log4j.CloseableThreadContext;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for induced demand.
 * 
 * @author jschewe
 *
 */
public class InducedDemandTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(InducedDemandTests.class);

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext());

    /**
     * Run induced-demand-simple relative to this class. Check that the
     * dependent service node sees load.
     * 
     * @throws IOException
     *             if there is an error reading in the test files
     * @throws URISyntaxException
     *             if the test file paths cannot be properly converted to a URI
     */
    @Test
    public void testSimpleDemand() throws IOException, URISyntaxException {
        // needs to match simple/service-configurations.json
        final ApplicationCoordinates service1 = new ApplicationCoordinates("test", "service1", "1");
        final ApplicationCoordinates service2 = new ApplicationCoordinates("test", "service2", "1");

        final NodeIdentifier server2NcpId = new DnsNameIdentifier("nodeService2");
        final double expectedTaskContainersLoad = 0.05;
        final double loadTolerance = 8E-4;
        final double expectedTxLinkLoad = 3;
        final double expectedRxLinkLoad = 0.25;
        final String expectedSourceRegionName = "A";
        final RegionIdentifier expectedSourceRegion = new StringRegionIdentifier(expectedSourceRegionName);

        final URL baseu = InducedDemandTests.class.getResource("induced-demand-simple");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path scenarioPath = baseDirectory.resolve("scenario");
        final Path demandPath = baseDirectory.resolve("demand");

        // the initial requests take 120 seconds, 180 seconds should get us into
        // the dependent requests
        final Duration runtime = Duration.ofSeconds(180);

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", scenarioPath, demandPath, clock, TestUtils.POLLING_INTERVAL_MS,
                TestUtils.DNS_TTL, false, false, false, AppMgrUtils::getContainerParameters)) {

            sim.startSimulation();
            sim.waitForAllNodesToConnectToNeighbors();

            sim.startClients();

            // wait for the dependent load to show up
            clock.waitForDuration(runtime.toMillis());

            clock.stopClock();

            final NodeIdentifier nodeIdRunningService1 = sim.getRegionalDNS(expectedSourceRegion)
                    .resolveService("test-client", service1);
            assertThat("Expecting a container,  but was: " + nodeIdRunningService1, nodeIdRunningService1,
                    IsInstanceOf.instanceOf(NodeIdentifier.class));

            final NodeIdentifier containerIdRunningService1 = (NodeIdentifier) nodeIdRunningService1;
            LOGGER.info("Service 1 is running in container {}", containerIdRunningService1);

            final ContainerSim containerRunningService1 = sim.getContainerById(containerIdRunningService1);
            assertThat(containerRunningService1, notNullValue());
            assertThat(containerRunningService1.getService(), equalTo(service1));

            final NodeIdentifier nodeIdRunningService2 = sim.getRegionalDNS(expectedSourceRegion)
                    .resolveService("test-client", service2);
            assertThat("Expecting a container,  but was: " + nodeIdRunningService2, nodeIdRunningService2,
                    IsInstanceOf.instanceOf(NodeIdentifier.class));

            final NodeIdentifier containerIdRunningService2 = (NodeIdentifier) nodeIdRunningService2;
            LOGGER.info("Service 2 is running in container {}", containerIdRunningService2);

            final ContainerSim containerRunningService2 = sim.getContainerById(containerIdRunningService2);
            assertThat(containerRunningService2.getService(), IsEqual.equalTo(service2));

            // check all estimation windows
            for (final ResourceReport.EstimationWindow estimationWindow : ResourceReport.EstimationWindow.values()) {
                try (CloseableThreadContext.Instance context = CloseableThreadContext
                        .push(estimationWindow.toString())) {
                    LOGGER.info("Checking estimation window: " + estimationWindow);

                    containerRunningService2.updateResourceReports();
                    final ContainerResourceReport containerResourceReport = containerRunningService2
                            .getContainerResourceReport(estimationWindow);
                    assertThat("Service resource report", containerResourceReport, notNullValue());

                    // check node load
                    final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> serviceNodeLoadByNode = containerResourceReport
                            .getComputeLoad();
                    assertThat("service node load by node", serviceNodeLoadByNode, is(notNullValue()));
                    assertThat("number of regions with load", serviceNodeLoadByNode.size(), is(1));

                    final ImmutableMap<NodeAttribute<?>, Double> serviceLoad = serviceNodeLoadByNode
                            .get(containerRunningService1.getIdentifier());
                    assertThat("no service load for the expected source node in " + serviceNodeLoadByNode, serviceLoad,
                            is(notNullValue()));

                    assertThat("Size of service load", serviceLoad.size(), is(1));
                    final Double taskContainersLoad = serviceLoad.get(NodeMetricName.TASK_CONTAINERS);
                    assertThat("task containers not found in " + serviceLoad, taskContainersLoad, is(notNullValue()));
                    assertThat("Containers load", taskContainersLoad,
                            closeTo(expectedTaskContainersLoad, loadTolerance));

                    // check link load
                    final ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> networkLoad = containerResourceReport
                            .getNetworkLoad();
                    assertThat(networkLoad, notNullValue());

                    final ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> neighborNetworkLoad = networkLoad
                            .get(server2NcpId);
                    assertThat("missing neighbor network load in " + networkLoad, neighborNetworkLoad, notNullValue());

                    final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>> sourceNetworkLoad = neighborNetworkLoad
                            .get(containerIdRunningService1);
                    assertThat("missing source network load in " + neighborNetworkLoad, neighborNetworkLoad,
                            notNullValue());

                    final ImmutableMap<LinkAttribute<?>, Double> serviceNetworkLoad = sourceNetworkLoad.get(service2);
                    assertThat(sourceNetworkLoad, notNullValue());

                    final Double actualTx = serviceNetworkLoad.get(LinkMetricName.DATARATE_TX);
                    assertThat(actualTx, notNullValue());
                    assertThat(actualTx, closeTo(expectedTxLinkLoad, loadTolerance));

                    final Double actualRx = serviceNetworkLoad.get(LinkMetricName.DATARATE_RX);
                    assertThat(actualRx, notNullValue());
                    assertThat(actualRx, closeTo(expectedRxLinkLoad, loadTolerance));

                    LOGGER.info("Finished checking {}", estimationWindow);
                } // logging context
            } // foreach estimation window
        } // use simulation

    }

}
