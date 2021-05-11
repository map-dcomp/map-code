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

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkClient;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Check that link utilization is properly reported.
 * 
 * @author jschewe
 *
 */
public class LinkUtilizationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkUtilizationTest.class);

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Make sure that the lo-fi simulation produces reports that are consistent
     * with what we will get from the hi-fi testbed.
     * 
     * @throws URISyntaxException
     *             test error
     * @throws IOException
     *             test error
     */
    @Test
    public void testHifiSimulation() throws URISyntaxException, IOException {
        final URL baseu = LinkUtilizationTest.class.getResource("hifi-test");

        final ApplicationCoordinates service1 = new ApplicationCoordinates("test", "service1", "1");
        final NodeIdentifier clientId = new DnsNameIdentifier("client");
        final NodeIdentifier nodeIdA = new DnsNameIdentifier("A");
        final NodeIdentifier nodeIdB = new DnsNameIdentifier("B");
        final NodeIdentifier nodeIdC = new DnsNameIdentifier("C");
        final NodeIdentifier nodeIdD = new DnsNameIdentifier("D");
        final NodeIdentifier nodeIdE = new DnsNameIdentifier("E");
        final RegionIdentifier expectedSourceRegion = new StringRegionIdentifier("A");
        final double tolerance = 8E-4;

        // network load from the server's perspective
        final double rx = 65;
        final double tx = 1.5;
        final double rxFlipped = tx;
        final double txFlipped = rx;
        final ImmutableMap<LinkAttribute, Double> networkLoad = ImmutableMap.of(LinkAttribute.DATARATE_RX, rx,
                LinkAttribute.DATARATE_TX, tx);

        // create the client load request
        final ClientLoad req = new ClientLoad(0, 1000, 1000, 1, service1, ImmutableMap.of(), networkLoad,
                ImmutableList.of());

        final Path baseDirectory = Paths.get(baseu.toURI());

        final Path scenarioPath = baseDirectory.resolve("scenario");

        final VirtualClock clock = new SimpleClock();
        try (Simulation sim = new Simulation("testHifiSimulation", scenarioPath, null, clock,
                TestUtils.POLLING_INTERVAL_MS, TestUtils.DNS_TTL, false, false, false,
                AppMgrUtils::getContainerParameters)) {

            // need to have AP connected so that the network maps have data in
            // them
            sim.startSimulation();
            sim.waitForAllNodesToConnectToNeighbors();
            LOGGER.info("All nodes are connected to AP");

            final NetworkClient client = sim.getClientById(clientId);
            final Controller serverE = sim.getControllerById(nodeIdE);
            assertThat(serverE, notNullValue());

            final NodeIdentifier nodeIdRunningService1 = sim.getRegionalDNS(expectedSourceRegion)
                    .resolveService(clientId.getName(), service1);
            assertThat("Expecting a container,  but was: " + nodeIdRunningService1, nodeIdRunningService1,
                    IsInstanceOf.instanceOf(NodeIdentifier.class));

            final NodeIdentifier containerIdRunningService1 = (NodeIdentifier) nodeIdRunningService1;
            LOGGER.info("Service 1 is running in container {}", containerIdRunningService1);

            final ContainerSim serverEContainer = sim.getContainerById(containerIdRunningService1);

            // apply client request
            final NodeNetworkFlow flowToApply = ClientSim.createNetworkFlow(clientId, serverEContainer.getIdentifier());
            final List<NetworkLink> networkPath = sim.getPath(client, serverE);

            final ImmutableMap<LinkAttribute, Double> networkLoadAsAttribute = req.getNetworkLoadAsAttribute();
            final ImmutableMap<LinkAttribute, Double> networkLoadAsAttributeFlipped = req
                    .getNetworkLoadAsAttributeFlipped();
            final ApplicationCoordinates service = req.getService();
            final long duration = req.getNetworkDuration();

            AbstractClientSimulator.applyNetworkDemand(sim, clientId, clientId, 0, networkLoadAsAttribute,
                    networkLoadAsAttributeFlipped, service, duration, serverEContainer, flowToApply, networkPath);

            final NodeNetworkFlow expectedFlow = new NodeNetworkFlow(containerIdRunningService1, clientId,
                    containerIdRunningService1);
            final NodeNetworkFlow expectedFlowFlipped = new NodeNetworkFlow(expectedFlow.getDestination(),
                    expectedFlow.getSource(), expectedFlow.getServer());

            // check all NCPs starting at the client
            // the client won't show up in the network map because it doesn't
            // participate in AP
            checkNcp(sim, service1, nodeIdA, null, nodeIdB, tolerance, expectedFlow, rx, tx, expectedFlowFlipped,
                    rxFlipped, txFlipped);
            checkNcp(sim, service1, nodeIdB, nodeIdA, nodeIdC, tolerance, expectedFlow, rx, tx, expectedFlowFlipped,
                    rxFlipped, txFlipped);
            checkNcp(sim, service1, nodeIdC, nodeIdB, nodeIdD, tolerance, expectedFlow, rx, tx, expectedFlowFlipped,
                    rxFlipped, txFlipped);
            checkNcp(sim, service1, nodeIdD, nodeIdC, nodeIdE, tolerance, expectedFlow, rx, tx, expectedFlowFlipped,
                    rxFlipped, txFlipped);

            // E is the server, so there is no neighbor to check on the server
            // side
            checkNcp(sim, service1, nodeIdE, nodeIdD, null, tolerance, expectedFlow, rx, tx, expectedFlowFlipped,
                    rxFlipped, txFlipped);

            // check container load
            serverEContainer.updateResourceReports();
            final ContainerResourceReport containerResourceReport = serverEContainer
                    .getContainerResourceReport(EstimationWindow.SHORT);
            assertThat("Container Service resource report", containerResourceReport, notNullValue());

        }
    }

    private void checkNcp(final Simulation sim,
            final ApplicationCoordinates service,
            final NodeIdentifier ncpId,
            final NodeIdentifier clientNeighbor,
            final NodeIdentifier serverNeighbor,
            final double tolerance,
            final NodeNetworkFlow expectedFlow,
            final double rx,
            final double tx,
            final NodeNetworkFlow expectedFlowFlipped,
            final double rxFlipped,
            final double txFlipped) {
        final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> serverANetLoad = getNetworkLoad(
                sim, ncpId);
        int expectedMapSize = 0;

        // even if client neighbor is null we still get network information from
        // the link between the NCP and the client
        ++expectedMapSize;

        if (null != serverNeighbor) {
            ++expectedMapSize;
        }
        assertThat(serverANetLoad, aMapWithSize(expectedMapSize));
        if (null != clientNeighbor) {
            final Optional<ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> load = serverANetLoad
                    .entrySet().stream().filter(entry -> entry.getKey().getNeighbors().contains(clientNeighbor))
                    .map(Map.Entry::getValue).findFirst();
            assertTrue(load.isPresent());
            checkNeighborLoad(service, tolerance, rx, tx, expectedFlow, load.get());
        }
        if (null != serverNeighbor) {
            final Optional<ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> load = serverANetLoad
                    .entrySet().stream().filter(entry -> entry.getKey().getNeighbors().contains(serverNeighbor))
                    .map(Map.Entry::getValue).findFirst();
            assertTrue(load.isPresent());

            checkNeighborLoad(service, tolerance, rxFlipped, txFlipped, expectedFlowFlipped, load.get());
        }
    }

    private ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> getNetworkLoad(
            final Simulation sim,
            final NodeIdentifier id) {
        final Controller ncp = sim.getControllerById(id);
        assertThat(ncp, notNullValue());

        final SimResourceManager manager = sim.getResourceManager(ncp);
        assertThat(manager, notNullValue());

        manager.updateResourceReports();
        final ResourceReport report = manager.getCurrentResourceReport(EstimationWindow.SHORT);
        assertThat(report, notNullValue());

        final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> netLoad = report
                .getNetworkLoad();
        return netLoad;
    }

    private void checkNeighborLoad(final ApplicationCoordinates service1,
            final double tolerance,
            final double expectedRx,
            final double expectedTx,
            final NodeNetworkFlow expectedFlow,
            final ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> neighborNetworkLoad) {
        assertThat(neighborNetworkLoad, aMapWithSize(1));
        assertThat(neighborNetworkLoad, hasKey(expectedFlow));

        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>> ncpServiceNetworkLoad = neighborNetworkLoad
                .get(expectedFlow);
        assertThat(ncpServiceNetworkLoad, aMapWithSize(1));
        assertThat(ncpServiceNetworkLoad, hasKey(service1));

        final ImmutableMap<LinkAttribute, Double> ncpLinkNetworkLoad = ncpServiceNetworkLoad.get(service1);
        assertThat(ncpLinkNetworkLoad, aMapWithSize(2));
        assertThat(ncpLinkNetworkLoad, hasKey(LinkAttribute.DATARATE_RX));
        assertThat(ncpLinkNetworkLoad, hasKey(LinkAttribute.DATARATE_TX));

        final double ncpRx = ncpLinkNetworkLoad.get(LinkAttribute.DATARATE_RX);
        assertThat(ncpRx, closeTo(expectedRx, tolerance));

        final double ncpTx = ncpLinkNetworkLoad.get(LinkAttribute.DATARATE_TX);
        assertThat(ncpTx, closeTo(expectedTx, tolerance));
    }

}
