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
package com.bbn.map;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.BasicResourceManager;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionLookupService;
import com.bbn.protelis.networkresourcemanagement.RegionNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceStatus;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringServiceIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test cases for {@link ResourceSummary}.
 * 
 * @author jschewe
 *
 */
public class ResourceSummaryTest {

    /**
     * Rules for running tests.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Test that converting a {@link ResourceReport} to an
     * {@link ResourceSummary} is sane.
     */
    @Test
    public void testConvert() {
        final NodeIdentifier nodeName = new DnsNameIdentifier("testNode");
        final NodeIdentifier neighborNodeName = new DnsNameIdentifier("testNeighborNode");
        final long timestamp = 0;
        final EstimationWindow estimationWindow = EstimationWindow.SHORT;

        final NodeAttribute nodeAttribute = NodeAttribute.TASK_CONTAINERS;
        final double serverCapacityValue = 10;
        final double serverLoadValue = 5;
        final double serverDemandValue = 3;
        final ImmutableMap<NodeAttribute, Double> serverCapacity = ImmutableMap.of(nodeAttribute, serverCapacityValue);
        final ServiceIdentifier<?> service = new StringServiceIdentifier("testService");
        final RegionIdentifier region = new StringRegionIdentifier("A");
        final RegionIdentifier neighborRegion = new StringRegionIdentifier("B");

        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> serverLoad = ImmutableMap.of(nodeName,
                ImmutableMap.of(nodeAttribute, serverLoadValue));
        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> serverDemand = ImmutableMap.of(nodeName,
                ImmutableMap.of(nodeAttribute, serverDemandValue));
        final double serverAverageProcessingTime = 30000D;

        final InterfaceIdentifier neighborInterface = BasicResourceManager
                .createInterfaceIdentifierForNeighbor(neighborNodeName);

        final LinkAttribute linkAttribute = LinkAttribute.DATARATE_TX;
        final double networkCapacityValue = 20;
        final double networkLoadValue = 15;
        final double networkDemandValue = 13;
        final ImmutableMap<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> networkCapacity = ImmutableMap
                .of(neighborInterface, ImmutableMap.of(linkAttribute, networkCapacityValue));

        // flow between neighborNode and node
        final NodeNetworkFlow nodeFlow = new NodeNetworkFlow(neighborNodeName, nodeName, nodeName);
        final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> networkLoad = ImmutableMap
                .of(neighborInterface, ImmutableMap.of(nodeFlow,
                        ImmutableMap.of(service, ImmutableMap.of(linkAttribute, networkLoadValue))));

        final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> networkDemand = ImmutableMap
                .of(neighborInterface, ImmutableMap.of(nodeFlow,
                        ImmutableMap.of(service, ImmutableMap.of(linkAttribute, networkDemandValue))));

        // all traffic is going to the container through the node so both have
        // the same network load and demand values
        final NodeIdentifier containerId = new DnsNameIdentifier("container0");
        final ContainerResourceReport containerReport = new ContainerResourceReport(containerId, timestamp, service,
                ServiceStatus.RUNNING, estimationWindow, serverCapacity, serverLoad, serverDemand,
                serverAverageProcessingTime);

        final ResourceReport report = null;
        if(true) {
        throw new RuntimeException("HACK for testing");
        }
        //new ResourceReport(nodeName, timestamp, estimationWindow, serverCapacity,
          //      networkCapacity, networkLoad, networkDemand, ImmutableMap.of(containerId, containerReport), 1, 1);

        final TestRegionLookup regionLookup = new TestRegionLookup();
        regionLookup.addMapping(nodeName, region);
        regionLookup.addMapping(neighborNodeName, neighborRegion);

        final ResourceSummary summary = Controller.computeResourceSummary(region, regionLookup,
                ImmutableSet.of(report));

        Assert.assertEquals(region, summary.getRegion());
        Assert.assertEquals(timestamp, summary.getMinTimestamp());
        Assert.assertEquals(timestamp, summary.getMaxTimestamp());
        Assert.assertEquals(estimationWindow, summary.getDemandEstimationWindow());

        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> expectedServerLoad = ImmutableMap
                .of(service, ImmutableMap.of(region, ImmutableMap.of(nodeAttribute, serverLoadValue)));

        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> expectedServerDemand = ImmutableMap
                .of(service, ImmutableMap.of(region, ImmutableMap.of(nodeAttribute, serverDemandValue)));

        Assert.assertEquals(serverCapacity, summary.getServerCapacity());
        Assert.assertEquals(expectedServerLoad, summary.getServerLoad());
        Assert.assertEquals(expectedServerDemand, summary.getServerDemand());
        Assert.assertEquals(ImmutableMap.of(service, serverAverageProcessingTime),
                summary.getServerAverageProcessingTime());

        final ImmutableMap<RegionIdentifier, ImmutableMap<LinkAttribute, Double>> expectedNetworkCapacity = ImmutableMap
                .of(neighborRegion, ImmutableMap.of(linkAttribute, networkCapacityValue));

        final RegionNetworkFlow regionFlow = new RegionNetworkFlow(neighborRegion, region, region);
        final ImmutableMap<RegionIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> expectedNetworkLoad = ImmutableMap
                .of(neighborRegion, ImmutableMap.of(regionFlow,
                        ImmutableMap.of(service, ImmutableMap.of(linkAttribute, networkLoadValue))));
        final ImmutableMap<RegionIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> expectedNetworkDemand = ImmutableMap
                .of(neighborRegion, ImmutableMap.of(regionFlow,
                        ImmutableMap.of(service, ImmutableMap.of(linkAttribute, networkDemandValue))));

        Assert.assertEquals(expectedNetworkCapacity, summary.getNetworkCapacity());
        Assert.assertEquals(expectedNetworkLoad, summary.getNetworkLoad());
        Assert.assertEquals(expectedNetworkDemand, summary.getNetworkDemand());
        Assert.assertEquals(expectedNetworkDemand, summary.getNetworkDemand());
    }

    private static final class TestRegionLookup implements RegionLookupService {

        private final Map<NodeIdentifier, RegionIdentifier> data = new HashMap<>();

        public void addMapping(@Nonnull final NodeIdentifier node, @Nonnull final RegionIdentifier region) {
            data.put(node, region);
        }

        @Override
        public RegionIdentifier getRegionForNode(final NodeIdentifier nodeId) {
            return data.get(nodeId);
        }

    }

}
