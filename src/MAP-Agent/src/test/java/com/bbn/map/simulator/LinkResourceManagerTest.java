/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
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

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test cases for {@link LinkResourceManager}.
 * 
 * @author jschewe
 *
 */
public class LinkResourceManagerTest {

    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Test that the computed load is properly returned when the datarate should
     * not be flipped.
     */
    @Test
    public void computedLoadNoFlipDatarate() {
        final double datarateCapacity = 100;
        final double rxLoad = 1;
        final double txLoad = 5;
        final ApplicationCoordinates service = new ApplicationCoordinates("test", "service1", "1");
        final long currentTime = 10;
        final NodeIdentifier client = new DnsNameIdentifier("client");
        final double tolerance = 1E-5;
        final NodeIdentifier server1 = new DnsNameIdentifier("one");
        final NodeIdentifier server2 = new DnsNameIdentifier("two");

        final ImmutableMap<LinkAttribute, Double> capacity = ImmutableMap.of(LinkAttribute.DATARATE_RX,
                datarateCapacity, LinkAttribute.DATARATE_TX, datarateCapacity);

        final LinkResourceManager manager = new LinkResourceManager(server1, server2, capacity);

        final ImmutableMap<LinkAttribute, Double> networkLoad = ImmutableMap.of(LinkAttribute.DATARATE_RX, rxLoad,
                LinkAttribute.DATARATE_TX, txLoad);

        final NodeNetworkFlow flow = new NodeNetworkFlow(client, server1, NodeIdentifier.UNKNOWN);

        final ClientLoad req = new ClientLoad(0, Long.MAX_VALUE, Long.MAX_VALUE, 1, service, ImmutableMap.of(),
                networkLoad);
        manager.addLinkLoad(req.getStartTime(), req, flow, manager.getTransmitter());

        final ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> computedLoad = manager
                .computeCurrentLinkLoad(currentTime, manager.getReceiver());
        assertThat("Computed load size should be 1", computedLoad.size(), is(1));

        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>> clientLoad = computedLoad
                .get(flow);
        assertThat("Can't find load for expected network flow", clientLoad, is(notNullValue()));

        final ImmutableMap<LinkAttribute, Double> serviceLoad = clientLoad.get(service);
        assertThat("Can't find load for test service", serviceLoad, is(notNullValue()));

        assertThat(serviceLoad.get(LinkAttribute.DATARATE_RX), closeTo(rxLoad, tolerance));
        assertThat(serviceLoad.get(LinkAttribute.DATARATE_TX), closeTo(txLoad, tolerance));
    }

    /**
     * Test that the computed load is properly returned when the datarate should
     * be flipped.
     */
    @Test
    public void computedLoadFlipDatarate() {
        final double rxLoad = 1;
        final double datarateCapacity = 100;
        final double txLoad = 5;
        final ApplicationCoordinates service = new ApplicationCoordinates("test", "service1", "1");
        final long currentTime = 10;
        final NodeIdentifier client = new DnsNameIdentifier("client");
        final double tolerance = 1E-5;
        final NodeIdentifier server1 = new DnsNameIdentifier("one");
        final NodeIdentifier server2 = new DnsNameIdentifier("two");

        final ImmutableMap<LinkAttribute, Double> capacity = ImmutableMap.of(LinkAttribute.DATARATE_RX,
                datarateCapacity, LinkAttribute.DATARATE_TX, datarateCapacity);

        final LinkResourceManager manager = new LinkResourceManager(server1, server2, capacity);

        final NodeNetworkFlow flow = new NodeNetworkFlow(client, server1, NodeIdentifier.UNKNOWN);

        final ImmutableMap<LinkAttribute, Double> networkLoad = ImmutableMap.of(LinkAttribute.DATARATE_RX, rxLoad,
                LinkAttribute.DATARATE_TX, txLoad);

        final ClientLoad req = new ClientLoad(0, Long.MAX_VALUE, Long.MAX_VALUE, 1, service, ImmutableMap.of(),
                networkLoad);
        manager.addLinkLoad(req.getStartTime(), req, flow, manager.getTransmitter());

        final NodeNetworkFlow flowFlipped = new NodeNetworkFlow(flow.getDestination(), flow.getSource(),
                flow.getServer());

        final ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> computedLoad = manager
                .computeCurrentLinkLoad(currentTime, manager.getTransmitter());
        assertThat("Computed load size should be 1", computedLoad, aMapWithSize(1));
        assertThat("Can't find load for expected network flow", computedLoad, hasKey(flowFlipped));

        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>> clientLoad = computedLoad
                .get(flowFlipped);
        assertThat(clientLoad, aMapWithSize(1));
        assertThat("Can't find load for test service", clientLoad, hasKey(service));

        final ImmutableMap<LinkAttribute, Double> serviceLoad = clientLoad.get(service);

        // checking TX against rxLoad since the values should be flipped
        assertThat(serviceLoad.get(LinkAttribute.DATARATE_TX), closeTo(rxLoad, tolerance));
        assertThat(serviceLoad.get(LinkAttribute.DATARATE_RX), closeTo(txLoad, tolerance));
    }

    /**
     * Check that async capacity fails.
     */
    @Test(expected = IllegalArgumentException.class)
    public void asyncCapacity() {
        final double rxCapacity = 10;
        final double txCapacity = 100;

        final ImmutableMap<LinkAttribute, Double> capacity = ImmutableMap.of(LinkAttribute.DATARATE_RX, rxCapacity,
                LinkAttribute.DATARATE_TX, txCapacity);

        new LinkResourceManager(new DnsNameIdentifier("one"), new DnsNameIdentifier("two"), capacity);
    }
}
