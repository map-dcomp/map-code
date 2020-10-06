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

import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringServiceIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Some tests for {@Link DNSSim}.
 * 
 */
public class DNSSimTest {

    /**
     * Add test name to logging and use the application manager.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    private DNSSim dns;
    private Simulation simulation;

    private Simulation createSimulation() throws IOException {
        try {
            final URL baseu = Thread.currentThread().getContextClassLoader().getResource("ns2/simple");
            final Path baseDirectory = Paths.get(baseu.toURI());

            final Path demandPath = baseDirectory.resolve("simple_demand");

            final VirtualClock clock = new SimpleClock();
            final Simulation sim = new Simulation("Simple", baseDirectory, demandPath, clock,
                    TestUtils.POLLING_INTERVAL_MS, TestUtils.DNS_TTL, false, false, false,
                    AppMgrUtils::getContainerParameters);

            return sim;
        } catch (final URISyntaxException e) {
            // this shouldn't happen
            throw new RuntimeException(e);
        }
    }

    /**
     * Setup clock and empty DNS.
     * 
     * @throws IOException
     *             if there is a problem loading the simulation
     */
    @Before
    public void setup() throws IOException {
        simulation = createSimulation();
        dns = new DNSSim(simulation, "test");
    }

    /**
     * Shutdown clock.
     */
    @After
    public void tearDown() {
        if (null != simulation) {
            simulation.stopSimulation();
            simulation = null;
        }
        dns = null;
    }

    /**
     * Test that when adding a record we can get it back out.
     */
    @Test
    public void testAddRecord() {
        final String fqdn = "service1.map";
        final NodeIdentifier node = new DnsNameIdentifier("nodeA1");
        final int ttl = 100;
        final RegionIdentifier sourceRegion = null;
        final ServiceIdentifier<?> service = new StringServiceIdentifier(fqdn);

        final DnsRecord recordBefore = dns.lookup("test-client", service);
        Assert.assertNull(recordBefore);

        final NameRecord record = new NameRecord(sourceRegion, ttl, service, node);
        dns.addRecord(record, 1);

        final DnsRecord recordAfter = dns.lookup("test-client", service);
        Assert.assertEquals(record, recordAfter);

    }

    /**
     * Check basic round robin. See that given 3 entries with equal weight see
     * all 3 with equal weight.
     */
    @Test
    public void testRoundRobinEqual() {
        final String fqdn = "service1.map";
        final NodeIdentifier node1 = new DnsNameIdentifier("nodeA1");
        final NodeIdentifier node2 = new DnsNameIdentifier("nodeA2");
        final NodeIdentifier node3 = new DnsNameIdentifier("nodeA3");
        final ServiceIdentifier<?> service = new StringServiceIdentifier(fqdn);
        final RegionIdentifier sourceRegion = null;
        final int ttl = 100;
        final int numRecords = 3;
        final int numCycles = 100000; // enough times to get a good sampling
        final int numQueries = numRecords * numCycles;
        final String clientName = "test-client";
        final double weightPrecision = 1D / AgentConfiguration.getInstance().getDnsWeightPrecision();
        final double expectedEvenWeight = 1D / numRecords;

        dns.addRecord(new NameRecord(sourceRegion, ttl, service, node1), 1);
        dns.addRecord(new NameRecord(sourceRegion, ttl, service, node2), 1);
        dns.addRecord(new NameRecord(sourceRegion, ttl, service, node3), 1);

        final Map<NodeIdentifier, Integer> counts = new HashMap<>();
        for (int i = 0; i < numQueries; ++i) {
            final NodeIdentifier node = dns.resolveService(clientName, service);
            counts.merge(node, 1, Integer::sum);
        }

        final int countNode1 = counts.getOrDefault(node1, 0);
        final int countNode2 = counts.getOrDefault(node2, 0);
        final int countNode3 = counts.getOrDefault(node3, 0);

        assertThat("Node 1", (double) countNode1 / numQueries, closeTo(expectedEvenWeight, weightPrecision));
        assertThat("Node 2", (double) countNode2 / numQueries, closeTo(expectedEvenWeight, weightPrecision));
        assertThat("Node 3", (double) countNode3 / numQueries, closeTo(expectedEvenWeight, weightPrecision));

    }

    /**
     * Test basic name resolution.
     */
    @Test
    public void testResolveName() {
        final String fqdn = "service1.map";
        final NodeIdentifier node = new DnsNameIdentifier("nodeA1");
        final int ttl = 100;
        final ServiceIdentifier<?> service = new StringServiceIdentifier(fqdn);
        final RegionIdentifier sourceRegion = null;

        final NameRecord record = new NameRecord(sourceRegion, ttl, service, node);
        dns.addRecord(record, 1);

        final NodeIdentifier lookupResult = dns.resolveService("test-client", service);
        Assert.assertEquals(node, lookupResult);
    }

    /**
     * Simple test of delegation. Have regionA delegate to regionB and then have
     * regionB resolve.
     */
    @Test
    public void testDelegation() {
        final String fqdn = "service1.map";
        final NodeIdentifier node = new DnsNameIdentifier("nodeB1");
        final int ttl = 100;
        final RegionIdentifier sourceRegion = null;
        final RegionIdentifier regionA = new StringRegionIdentifier("Region A");
        final RegionIdentifier regionB = new StringRegionIdentifier("Region B");
        final ServiceIdentifier<?> service = new StringServiceIdentifier(fqdn);

        simulation.ensureRegionalDNSExists(regionA);
        simulation.ensureRegionalDNSExists(regionB);

        final DNSSim regionADns = simulation.getRegionalDNS(regionA);
        final DNSSim regionBDns = simulation.getRegionalDNS(regionB);

        final NameRecord recordB = new NameRecord(sourceRegion, ttl, service, node);
        regionBDns.addRecord(recordB, 1);

        final DelegateRecord recordA = new DelegateRecord(sourceRegion, ttl, service, regionB);
        regionADns.addRecord(recordA, 1);

        final NodeIdentifier lookupResult = regionADns.resolveService("test-client", service);
        Assert.assertEquals(node, lookupResult);
    }

}
