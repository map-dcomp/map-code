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
package com.bbn.map.dns;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import com.bbn.map.ServiceConfiguration;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlanBuilder;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionServiceState;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests for {@link PlanTranslator}.
 * 
 * @author jschewe
 *
 */
public class PlanTranslatorTest {

    /**
     * Add test name to logging.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = RuleChain.outerRule(new TestUtils.AddTestNameToLogContext());
    
    private ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations;
    private static final int NUM_TEST_SERVICES = 5;
    private static final int TTL = 100;

    private ApplicationCoordinates generateSerivceName(final int serviceIndex) {
        return new ApplicationCoordinates("test", "service" + serviceIndex, "1");
    }

    private String generateServiceHostname(final ApplicationCoordinates serviceName) {
        return serviceName.getArtifact() + ".test.map";
    }

    /**
     * Default link rate to use for container parameters when testing.
     */
    private static final double DEFAULT_LINK_DATARATE = 100;

    /**
     * Default container capacity to use for container parameters when testing.
     */
    private static final double DEFAULT_CONTAINER_CAPACITY = 1;

    /**
     * Create sample serviceConfigurations and nodeToFqdn.
     */
    @Before
    public void setup() {
        final RegionIdentifier defaultRegion = new StringRegionIdentifier("defaultRegion");

        final ImmutableMap.Builder<NodeAttribute<?>, Double> computeCapacity = ImmutableMap.builder();
        computeCapacity.put(NodeMetricName.TASK_CONTAINERS, DEFAULT_CONTAINER_CAPACITY);

        final ImmutableMap.Builder<LinkAttribute<?>, Double> networkCapacity = ImmutableMap.builder();
        networkCapacity.put(LinkMetricName.DATARATE_TX, DEFAULT_LINK_DATARATE);
        networkCapacity.put(LinkMetricName.DATARATE_RX, DEFAULT_LINK_DATARATE);

        final ContainerParameters containerParameters = new ContainerParameters(computeCapacity.build(),
                networkCapacity.build());

        final ImmutableMap.Builder<ApplicationCoordinates, ServiceConfiguration> serviceBuilder = ImmutableMap
                .builder();
        for (int i = 0; i < NUM_TEST_SERVICES; ++i) {
            final ApplicationCoordinates serviceName = generateSerivceName(i);
            final String hostname = generateServiceHostname(serviceName);
            final String defaultNodeName = "defaultFor" + serviceName;
            final NodeIdentifier defaultNode = new DnsNameIdentifier(defaultNodeName);

            final ServiceConfiguration config = new ServiceConfiguration(serviceName, hostname,
                    ImmutableMap.of(defaultNode, 1), defaultRegion, containerParameters,
                    ApplicationSpecification.DEFAULT_PRIORITY, true, null,
                    ApplicationSpecification.ServiceTrafficType.TX_GREATER);
            serviceBuilder.put(serviceName, config);
        }

        serviceConfigurations = serviceBuilder.build();

        AppMgrUtils.populateApplicationManagerFromServiceConfigurations(serviceConfigurations);
    }

    /**
     * See that the null plans return the default set of DNS entries.
     */
    @Test
    public void testNoPlan() {
        final RegionIdentifier region = new StringRegionIdentifier("test");

        final PlanTranslator translator = new PlanTranslator(TTL);

        final LoadBalancerPlan loadBalancerPlan = LoadBalancerPlan.getNullLoadBalancerPlan(region);

        final RegionServiceState regionServiceState = new RegionServiceState(region, ImmutableSet.of());
        final ImmutableCollection<Pair<DnsRecord, Double>> records = translator.convertToDns(loadBalancerPlan,
                regionServiceState);

        serviceConfigurations.forEach((service, config) -> {
            final DelegateRecord expected = new DelegateRecord(null, TTL, service, config.getDefaultNodeRegion());
            final Optional<Pair<DnsRecord, Double>> found = records.stream()
                    .filter(pair -> pair.getLeft().equals(expected)).findFirst();

            assertTrue("Did not find record for service: " + service, found.isPresent());
        });
    }

    /**
     * Test simple RLG plan. - service1 nodeA1, nodeA2 - all others default
     */
    @Test
    public void testSimpleRLGPlan() {
        final RegionIdentifier region = new StringRegionIdentifier("test");
        final NodeIdentifier nodeA1 = new DnsNameIdentifier("nodeA1");
        final NodeIdentifier nodeA1container0 = new DnsNameIdentifier("nodeA1c0");
        final NodeIdentifier nodeA2 = new DnsNameIdentifier("nodeA2");
        final NodeIdentifier nodeA2container0 = new DnsNameIdentifier("nodeA2c0");
        final ApplicationCoordinates service1 = generateSerivceName(1);

        final PlanTranslator translator = new PlanTranslator(TTL);

        final LoadBalancerPlanBuilder servicePlanBuilder = new LoadBalancerPlanBuilder(region);
        servicePlanBuilder.addService(nodeA1, service1, 1);
        servicePlanBuilder.addService(nodeA2, service1, 1);

        final LoadBalancerPlan loadBalancerPlan = servicePlanBuilder
                .toLoadBalancerPlan(new RegionServiceState(region, ImmutableSet.of()), ImmutableMap.of());

        final ImmutableSet.Builder<ServiceReport> serviceReports = ImmutableSet.builder();

        final ImmutableMap.Builder<NodeIdentifier, ServiceState> nodeA1ServiceState = ImmutableMap.builder();
        nodeA1ServiceState.put(nodeA1container0, new ServiceState(service1, ServiceState.Status.RUNNING));

        final ServiceReport service1NodeA1 = new ServiceReport(nodeA1, nodeA1ServiceState.build());
        serviceReports.add(service1NodeA1);

        final ImmutableMap.Builder<NodeIdentifier, ServiceState> nodeA2ServiceState = ImmutableMap.builder();
        nodeA2ServiceState.put(nodeA2container0, new ServiceState(service1, ServiceState.Status.RUNNING));

        final ServiceReport service1NodeA2 = new ServiceReport(nodeA2, nodeA2ServiceState.build());
        serviceReports.add(service1NodeA2);

        final RegionServiceState regionServiceState = new RegionServiceState(region, serviceReports.build());

        final ImmutableCollection<Pair<DnsRecord, Double>> records = translator.convertToDns(loadBalancerPlan,
                regionServiceState);

        serviceConfigurations.forEach((service, config) -> {

            if (service.equals(service1)) {
                final NameRecord expected1 = new NameRecord(null, TTL, service, nodeA1container0);
                final Optional<Pair<DnsRecord, Double>> found1 = records.stream()
                        .filter(pair -> pair.getLeft().equals(expected1)).findFirst();

                assertTrue("Did not find A1 record for service: " + service, found1.isPresent());

                final NameRecord expected2 = new NameRecord(null, TTL, service, nodeA2container0);
                final Optional<Pair<DnsRecord, Double>> found2 = records.stream()
                        .filter(pair -> pair.getLeft().equals(expected2)).findFirst();
                assertTrue("Did not find A2 record for service: " + service, found2.isPresent());

            } else {
                final DelegateRecord expected = new DelegateRecord(null, TTL, service, config.getDefaultNodeRegion());
                final Optional<Pair<DnsRecord, Double>> found = records.stream()
                        .filter(pair -> pair.getLeft().equals(expected)).findFirst();
                assertTrue("Did not find record for service: " + service, found.isPresent());
            }
        });
    }

    /**
     * Test simple DCOP plan. - service1 50% to regionA - service1 25% to
     * regionB - service1 25% to regionC
     */
    @Test
    public void testSimpleDCOP() {
        final RegionIdentifier region = new StringRegionIdentifier("test");
        final ApplicationCoordinates service1 = generateSerivceName(1);
        final RegionIdentifier regionA = new StringRegionIdentifier("regionA");
        final RegionIdentifier regionB = new StringRegionIdentifier("regionB");
        final RegionIdentifier regionC = new StringRegionIdentifier("regionC");
        final double regionAWeight = 0.5;
        final double regionBWeight = 0.25;
        final double regionCWeight = 0.25;
        final double weightTolerance = 1E-6;

        final PlanTranslator translator = new PlanTranslator(TTL);

        final ImmutableMap.Builder<RegionIdentifier, Double> service1Plan = ImmutableMap.builder();
        service1Plan.put(regionA, regionAWeight);
        service1Plan.put(regionB, regionBWeight);
        service1Plan.put(regionC, regionCWeight);
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = ImmutableMap
                .of(service1, service1Plan.build());
        final LoadBalancerPlan loadBalancerPlan = new LoadBalancerPlan(region, ImmutableMap.of(), overflowPlan);

        final RegionServiceState regionServiceState = new RegionServiceState(region, ImmutableSet.of());
        final ImmutableCollection<Pair<DnsRecord, Double>> records = translator.convertToDns(loadBalancerPlan,
                regionServiceState);

        // check that the other services still just have the default values
        serviceConfigurations.forEach((service, config) -> {
            if (!service.equals(service1)) {
                final DelegateRecord expected = new DelegateRecord(null, TTL, service, config.getDefaultNodeRegion());
                final Optional<Pair<DnsRecord, Double>> found = records.stream()
                        .filter(pair -> pair.getLeft().equals(expected)).findFirst();

                assertTrue("Did not find record for service: " + service, found.isPresent());
            }
        });

        // filter to service1 records
        double regionAactualWeight = 0;
        double regionBactualWeight = 0;
        double regionCactualWeight = 0;
        for (final Pair<DnsRecord, Double> pair : records) {
            final DnsRecord record = pair.getLeft();
            final double weight = pair.getRight();

            final ServiceIdentifier<?> service = record.getService();
            if (service.equals(service1)) {
                assertThat("All records for service1 should be delegate records", record,
                        instanceOf(DelegateRecord.class));

                final DelegateRecord drec = (DelegateRecord) record;
                if (regionA.equals(drec.getDelegateRegion())) {
                    regionAactualWeight += weight;
                } else if (regionB.equals(drec.getDelegateRegion())) {
                    regionBactualWeight += weight;
                } else if (regionC.equals(drec.getDelegateRegion())) {
                    regionCactualWeight += weight;
                } else {
                    fail("Unknown delegate region: " + drec.getDelegateRegion());
                }
            }
        }

        final double totalActualWeight = regionAactualWeight + regionBactualWeight + regionCactualWeight;

        assertThat("region A", regionAactualWeight / totalActualWeight, closeTo(regionAWeight, weightTolerance));
        assertThat("region B", regionBactualWeight / totalActualWeight, closeTo(regionBWeight, weightTolerance));
        assertThat("region C", regionCactualWeight / totalActualWeight, closeTo(regionCWeight, weightTolerance));

    }

}
