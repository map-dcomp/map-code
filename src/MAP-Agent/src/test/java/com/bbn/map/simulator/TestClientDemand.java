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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.number.IsCloseTo.closeTo;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.logging.log4j.CloseableThreadContext;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.protelis.lang.datatype.DeviceUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.Controller;
import com.bbn.map.ap.ApplicationManagerUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.ContainerIdentifier;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Some tests for the simulated client demand.
 */
public class TestClientDemand {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestClientDemand.class);

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext())
            .around(new TestUtils.UseMapApplicationManager())
            .around(new TestUtils.Retry(TestUtils.DEFAULT_RETRY_COUNT));

    /**
     * Run ns2/simple with demand from ns2/simple/simple_demand. Check that the
     * {@link ResourceReport} from the controller contains the right
     * information.
     * 
     * @throws IOException
     *             if there is an error reading in the test files
     * @throws URISyntaxException
     *             if the test file paths cannot be properly converted to a URI
     */
    @Test
    public void testSimpleDemand() throws IOException, URISyntaxException {
        // needs to match simple/service-configurations.json
        final ApplicationCoordinates service = new ApplicationCoordinates("test", "test-service", "1");
        final double expectedCpuDemand = 0.5;
        final double demandTolerance = 1E-6;
        final double expectedLinkDemand = 50;
        final String expectedSourceRegionName = "A";
        final RegionIdentifier expectedSourceRegion = new StringRegionIdentifier(expectedSourceRegionName);
        final String expectedSourceNodeName = "clientPoolA";
        final NodeIdentifier expectedSourceNode = new DnsNameIdentifier(expectedSourceNodeName);
        final long pollingInterval = 10;
        final int dnsTtlSeconds = 60;

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/simple");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = baseDirectory.resolve("simple_demand");

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                false, false, false, ApplicationManagerUtils::getContainerParameters)) {

            final int numApRoundsToStabilize = TestUtils.computeRoundsToStabilize(sim);

            sim.startSimulation();
            TestUtils.waitForApRounds(sim, numApRoundsToStabilize);
            sim.stopSimulation();

            final NodeIdentifier nodeRunningServiceId = sim.getRegionalDNS(expectedSourceRegion)
                    .resolveService(expectedSourceRegion, service);
            Assert.assertThat("Expecting a container,  but was: " + nodeRunningServiceId, nodeRunningServiceId,
                    IsInstanceOf.instanceOf(ContainerIdentifier.class));

            final ContainerIdentifier containerRunningServiceId = (ContainerIdentifier) nodeRunningServiceId;
            LOGGER.info("Service is running in container {}", containerRunningServiceId);

            final ContainerSim containerRunningService = sim.getContainerById(containerRunningServiceId);
            Assert.assertThat(containerRunningService.getService(), IsEqual.equalTo(service));

            // check all estimation windows
            for (final ResourceReport.EstimationWindow estimationWindow : ResourceReport.EstimationWindow.values()) {
                try (CloseableThreadContext.Instance context = CloseableThreadContext
                        .push(estimationWindow.toString())) {
                    LOGGER.info("Checking estimation window: " + estimationWindow);

                    containerRunningService.updateResourceReports();
                    final ContainerResourceReport serviceResourceReport = containerRunningService
                            .getContainerResourceReport(estimationWindow);
                    Assert.assertNotNull("Service resource report", serviceResourceReport);

                    // check node demand

                    final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> serviceNodeDemandByNode = serviceResourceReport
                            .getComputeDemand();
                    Assert.assertThat("service node demand by node", serviceNodeDemandByNode, is(notNullValue()));
                    Assert.assertThat("number of regions with demand", serviceNodeDemandByNode.size(), is(1));

                    final ImmutableMap<NodeAttribute<?>, Double> serviceLoad = serviceNodeDemandByNode
                            .get(expectedSourceNode);
                    Assert.assertThat("no service load for the expected source node", serviceLoad, is(notNullValue()));

                    // since the load is still executing the average
                    // processing time
                    // hasn't been computed yet
                    Assert.assertThat("Size of service load", serviceLoad.size(), is(1));
                    final Double cpuDemand = serviceLoad.get(NodeMetricName.TASK_CONTAINERS);
                    Assert.assertThat("Containers load", cpuDemand, is(notNullValue()));
                    Assert.assertThat("Containers load", cpuDemand, closeTo(expectedCpuDemand, demandTolerance));

                    // check link demand
                    // because of the topology all links will have the same
                    // value
                    for (final Map.Entry<DeviceUID, Controller> entry : sim.getScenario().getServers().entrySet()) {
                        final Controller controller = entry.getValue();
                        Assert.assertNotNull("Controller is null?", controller);

                        final SimResourceManager resourceManager = sim.getResourceManager(controller);
                        // just check the short computed, at some point we
                        // may test
                        // both
                        // short
                        // and long by looking at the estimated demand
                        final ResourceReport report = resourceManager.getCurrentResourceReport(estimationWindow);
                        Assert.assertNotNull("Resource report", report);

                        final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkLoad = report
                                .getContainerNetworkLoad();
                        for (final Map.Entry<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> loadEntry : networkLoad
                                .entrySet()) {

                            final Double value = loadEntry.getValue().get(LinkMetricName.DATARATE);
                            Assert.assertThat(loadEntry.getKey() + " is missing datarate on network load", value,
                                    is(notNullValue()));

                            Assert.assertThat("network load datarate", value,
                                    closeTo(expectedLinkDemand, demandTolerance));
                        }

                    } // foreach controller

                    LOGGER.info("Finished checking {}", estimationWindow);
                } // logging context
            } // foreach estimation window
        } // use simulation

    }

    /**
     * Test that we get resource summary information on the DCOP node. Check
     * that the information received is what is expected based on the resource
     * reports retrieved directly from the nodes.
     * 
     * @throws IOException
     *             if there is an error reading in the test files
     * @throws URISyntaxException
     *             if the test file paths cannot be properly converted to a URI
     */
    @Test
    public void testSummaries() throws URISyntaxException, IOException {
        final double demandTolerance = 1E-6;
        final String regionName = "A";
        final RegionIdentifier region = new StringRegionIdentifier(regionName);
        final long pollingInterval = 10;
        final int dnsTtlSeconds = 60;

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/simple");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = baseDirectory.resolve("simple_demand");

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                false, false, false, ApplicationManagerUtils::getContainerParameters)) {
            final int numApRoundsToStabilize = TestUtils.computeRoundsToStabilize(sim);

            sim.startSimulation();
            TestUtils.waitForApRounds(sim, numApRoundsToStabilize);
            sim.stopSimulation();

            // check all estimation windows
            for (final ResourceReport.EstimationWindow estimationWindow : ResourceReport.EstimationWindow.values()) {
                try (CloseableThreadContext.Instance context = CloseableThreadContext
                        .push(estimationWindow.toString())) {
                    LOGGER.info("Checking estimation window: " + estimationWindow);

                    final double regionTaskContainerLoad = sumTaskContainerLoad(sim, region, estimationWindow);

                    boolean foundDcop = false;
                    for (final Map.Entry<DeviceUID, Controller> entry : sim.getScenario().getServers().entrySet()) {
                        final Controller server = entry.getValue();
                        if (server.isRunDCOP()) {
                            LOGGER.info("DCOP is running on node: " + server.getName());
                            foundDcop = true;

                            // only the DCOP node will have the complete region
                            // summary

                            final ResourceSummary summary = server.getNetworkState().getRegionSummary(estimationWindow);
                            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> summaryServerLoad = summary
                                    .getServerLoad();

                            LOGGER.info("summary serverLoad: {} on {}", summaryServerLoad, server.getName());

                            double summaryTaskContainerLoad = 0;
                            for (final Map.Entry<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> serverLoadEntry : summaryServerLoad
                                    .entrySet()) {
                                for (final Map.Entry<RegionIdentifier, ImmutableMap<NodeAttribute<?>, Double>> regionEntry : serverLoadEntry
                                        .getValue().entrySet()) {
                                    final Double v = regionEntry.getValue().get(NodeMetricName.TASK_CONTAINERS);
                                    if (null != v) {
                                        summaryTaskContainerLoad += v.doubleValue();
                                    }
                                }
                            }

                            // check that the server load is the summary of all
                            // resource
                            // reports in the region
                            Assert.assertEquals("Checking estimation window: " + estimationWindow,
                                    regionTaskContainerLoad, summaryTaskContainerLoad, demandTolerance);
                        }

                    } // foreach server

                    Assert.assertTrue("Didn't find DCOP", foundDcop);
                    LOGGER.info("Finished checking {}", estimationWindow);
                } // logging context
            } // foreach estimation window
        } // use simulation
    }

    /**
     * Compute the sum of the task container load across all nodes in the
     * simulation that are in the specified region.
     * 
     * @param sim
     *            where to get all of the node information from
     * @param region
     *            the region to sum the load for
     * @param estimationWindow
     *            the window to look at when computing the sum
     * @return the sum of the task container load for all nodes in the region
     */
    private static double sumTaskContainerLoad(final Simulation sim,
            final RegionIdentifier region,
            final ResourceReport.EstimationWindow estimationWindow) {
        double sum = 0;
        for (final Map.Entry<DeviceUID, Controller> serverEntry : sim.getScenario().getServers().entrySet()) {
            final Controller server = serverEntry.getValue();
            if (region.equals(server.getRegionIdentifier())) {
                final SimResourceManager resourceManager = sim.getResourceManager(server);

                final ResourceReport serviceResourceReport = resourceManager.getCurrentResourceReport(estimationWindow);
                LOGGER.info("ResourceReport compute load: {} on host {}", serviceResourceReport.getComputeLoad(),
                        server.getName());

                final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> serviceClientLoad = serviceResourceReport
                        .getComputeLoad();

                for (final Map.Entry<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> clientLoadEntry : serviceClientLoad
                        .entrySet()) {
                    for (final Map.Entry<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> regionEntry : clientLoadEntry
                            .getValue().entrySet()) {
                        final Double v = regionEntry.getValue().get(NodeMetricName.TASK_CONTAINERS);
                        if (null != v) {
                            sum += v.doubleValue();
                        }
                    }
                }

            } // correct region
        } // foreach server

        return sum;
    }

    /**
     * Test that we get cross-region network capacity to show up in the
     * {@link ResourceSummary}. Runs ns2/two-region with no demand.
     * 
     * @throws IOException
     *             if there is an error reading in the test files
     * @throws URISyntaxException
     *             if the test file paths cannot be properly converted to a URI
     */
    @Test
    public void testRegionalNetworkCapacity() throws URISyntaxException, IOException {
        final double capacityTolerance = 1E-6;
        final String regionAName = "A";
        final RegionIdentifier regionA = new StringRegionIdentifier(regionAName);
        final String regionBName = "B";
        final RegionIdentifier regionB = new StringRegionIdentifier(regionBName);
        final long pollingInterval = 10;
        final int dnsTtlSeconds = 60;
        final LinkMetricName datarateAttribute = Simulation.LINK_BANDWIDTH_ATTRIBUTE;
        final NodeIdentifier nodeA0Id = new DnsNameIdentifier("nodeA0");
        final NodeIdentifier nodeB0Id = new DnsNameIdentifier("nodeB0");

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/two-region");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = null;

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("test network capacity", baseDirectory, demandPath, clock, pollingInterval,
                dnsTtlSeconds, false, false, false, ApplicationManagerUtils::getContainerParameters)) {

            final int numApRoundsToStabilize = TestUtils.computeRoundsToStabilize(sim);

            final Controller nodeA0 = sim.getControllerById(nodeA0Id);
            Assert.assertNotNull("Looking up nodeA0", nodeA0);

            final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> nodeA0NetworkCapacities = nodeA0
                    .getNeighborLinkCapacity(datarateAttribute);
            Assert.assertNotNull("nodeA0Network capacities", nodeA0NetworkCapacities);
            Assert.assertEquals("nodeA0 network capacity size", 1, nodeA0NetworkCapacities.size());
            final ImmutableMap<LinkAttribute<?>, Double> nodeA0B0Capacity = nodeA0NetworkCapacities.get(nodeB0Id);
            Assert.assertNotNull("nodeA0B0Capacity", nodeA0B0Capacity);

            final Double a0B0NetworkCapacity = nodeA0B0Capacity.get(datarateAttribute);
            Assert.assertNotNull("a0B0NetworkCapacity", a0B0NetworkCapacity);

            final double expectedNetworkCapacity = a0B0NetworkCapacity.doubleValue();

            sim.startSimulation();
            TestUtils.waitForApRounds(sim, numApRoundsToStabilize);
            sim.stopSimulation();

            boolean foundDcopA = false;
            boolean foundDcopB = false;
            for (final Map.Entry<DeviceUID, Controller> entry : sim.getScenario().getServers().entrySet()) {
                final Controller server = entry.getValue();
                if (server.isRunDCOP()) {
                    // only the DCOP node will have the complete region summary
                    LOGGER.info("DCOP is running on node: " + server.getName());

                    final RegionIdentifier thisRegion = server.getRegionIdentifier();
                    final RegionIdentifier otherRegion;
                    if (thisRegion.equals(regionA)) {
                        otherRegion = regionB;
                        foundDcopA = true;
                    } else if (thisRegion.equals(regionB)) {
                        otherRegion = regionA;
                        foundDcopB = true;
                    } else {
                        otherRegion = null;
                        Assert.fail("Unknown region: " + thisRegion);
                    }
                    Assert.assertNotNull("Other region not found", otherRegion);

                    for (final ResourceReport.EstimationWindow estimationWindow : ResourceReport.EstimationWindow
                            .values()) {
                        try (CloseableThreadContext.Instance context = CloseableThreadContext
                                .push(estimationWindow.toString())) {
                            LOGGER.info("Checking estimation window: " + estimationWindow);

                            final ResourceSummary summary = server.getNetworkState().getRegionSummary(estimationWindow);
                            final ImmutableMap<RegionIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkCapacity = summary
                                    .getNetworkCapacity();
                            final ImmutableMap<LinkAttribute<?>, Double> networkCapacityToOther = networkCapacity
                                    .get(otherRegion);
                            Assert.assertNotNull("No network capacity to " + otherRegion + " from " + thisRegion, networkCapacityToOther);

                            final Double capacityValue = networkCapacityToOther.get(datarateAttribute);
                            Assert.assertNotNull("No datarate", capacityValue);

                            Assert.assertEquals("Incorrect network capacity", expectedNetworkCapacity, capacityValue,
                                    capacityTolerance);

                            LOGGER.info("Finished checking {}", estimationWindow);
                        } // logging context
                    } // foreach estimation window

                } // DCOP node

            } // foreach server

            Assert.assertTrue("Didn't find DCOP in region A", foundDcopA);
            Assert.assertTrue("Didn't find DCOP in region B", foundDcopB);
        } // use simulation

    }

    /**
     * Run ns2/simple with demand from ns2/simple/short_demand. Check that the
     * average processing time exists and is the right value.
     * 
     * @throws IOException
     *             if there is an error reading in the test files
     * @throws URISyntaxException
     *             if the test file paths cannot be properly converted to a URI
     */
    @Test
    public void testServerProcessingTime() throws IOException, URISyntaxException {
        // needs to match simple/service-configurations.json
        final ApplicationCoordinates service = new ApplicationCoordinates("test", "test-service", "1");
        final double expectedProcessingMs = 20000; // greater than or equal to
                                                   // the duration value in
                                                   // simple/short_demand/clientPoolA.json
                                                   // divided by the number of
                                                   // containers
        final double tolerance = 1E-6;
        final long pollingInterval = 10;
        final int dnsTtlSeconds = 60;
        final String expectedSourceRegionName = "A";
        final RegionIdentifier expectedSourceRegion = new StringRegionIdentifier(expectedSourceRegionName);

        final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/simple");
        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path demandPath = baseDirectory.resolve("short_demand");

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock, pollingInterval, dnsTtlSeconds,
                false, false, false, ApplicationManagerUtils::getContainerParameters)) {

            final int numApRoundsToStabilize = TestUtils.computeRoundsToStabilize(sim);

            sim.startSimulation();
            TestUtils.waitForApRounds(sim, numApRoundsToStabilize);
            sim.stopSimulation();

            final NodeIdentifier nodeRunningServiceId = sim.getRegionalDNS(expectedSourceRegion)
                    .resolveService(expectedSourceRegion, service);
            Assert.assertThat("Expecting a container,  but was: " + nodeRunningServiceId, nodeRunningServiceId,
                    IsInstanceOf.instanceOf(ContainerIdentifier.class));

            final ContainerIdentifier containerRunningServiceId = (ContainerIdentifier) nodeRunningServiceId;
            LOGGER.info("Service is running in container {}", containerRunningServiceId);

            final ContainerSim containerRunningService = sim.getContainerById(containerRunningServiceId);
            Assert.assertThat(containerRunningService.getService(), IsEqual.equalTo(service));

            final NetworkServer parentNode = containerRunningService.getParentNode();
            final SimResourceManager parentNodeMgr = sim.getResourceManager(parentNode);

            // check all estimation windows
            for (final ResourceReport.EstimationWindow estimationWindow : ResourceReport.EstimationWindow.values()) {
                try (CloseableThreadContext.Instance context = CloseableThreadContext
                        .push(estimationWindow.toString())) {
                    LOGGER.info("Checking estimation window: " + estimationWindow);

                    parentNodeMgr.updateResourceReports();
                    final ResourceReport serviceResourceReport = parentNodeMgr
                            .getCurrentResourceReport(estimationWindow);
                    Assert.assertNotNull("Service resource report", serviceResourceReport);

                    // check node demand
                    final ImmutableMap<ServiceIdentifier<?>, Double> serverAvgProcTime = serviceResourceReport
                            .getAverageProcessingTime();
                    Assert.assertThat(estimationWindow + ": Size of avg proc time", serverAvgProcTime.size(), is(1));

                    final Double avgProcTime = serverAvgProcTime.get(service);
                    Assert.assertThat(estimationWindow + ": Average Processing Time", avgProcTime, is(notNullValue()));
                    Assert.assertThat(estimationWindow + ": Average Processing Time", avgProcTime,
                            closeTo(expectedProcessingMs, tolerance));

                    LOGGER.info("Finished checking {}", estimationWindow);
                } // logging context

            } // foreach estimation window
        } // use simulation
    }

}
