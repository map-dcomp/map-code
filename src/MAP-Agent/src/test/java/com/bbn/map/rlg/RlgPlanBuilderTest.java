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
package com.bbn.map.rlg;

import java.util.HashSet;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.simulator.TestUtils;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan.ContainerInfo;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlanBuilder;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceStatus;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Contains tests for building RLG plans.
 * 
 * @author awald
 *
 */
public class RlgPlanBuilderTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RlgPlanBuilderTest.class);

    /**
     * Unit test rule chain.
     */
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "Used by the JUnit framework")
    @Rule
    public RuleChain chain = TestUtils.getStandardRuleChain();

    /**
     * Tests the {@link LoadBalancerPlanBuilder}'s ability to validate plans.
     */
    @Test
    public void test() {
        RegionIdentifier region = new StringRegionIdentifier("A");
        NodeIdentifier node = new DnsNameIdentifier("nodeA0");

        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = ImmutableMap
                .of();

        final ServiceIdentifier<?> service = new ApplicationCoordinates("com.bbn", "app", "1.0");
        final ServiceIdentifier<?> serviceChanged = new ApplicationCoordinates("com.bbn", "changed", "1.0");

        final ImmutableMap.Builder<NodeIdentifier, ContainerResourceReport> containerReportsBuilder = ImmutableMap
                .builder();

        final NodeIdentifier containerStarting = new DnsNameIdentifier("nodeA0_cSTARTING");
        final ContainerResourceReport containerStartingReport = new ContainerResourceReport(containerStarting, 0,
                service, ServiceStatus.STARTING, EstimationWindow.SHORT, ImmutableMap.of(), ImmutableMap.of(),
                ImmutableMap.of(), 0);
        containerReportsBuilder.put(containerStarting, containerStartingReport);

        final NodeIdentifier containerRunning = new DnsNameIdentifier("nodeA0_cRUNNING");
        final ContainerResourceReport containerRunningReport = new ContainerResourceReport(containerRunning, 0, service,
                ServiceStatus.RUNNING, EstimationWindow.SHORT, ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(),
                0);
        containerReportsBuilder.put(containerRunning, containerRunningReport);

        final NodeIdentifier containerStopping = new DnsNameIdentifier("nodeA0_cSTOPPING");
        final ContainerResourceReport containerStoppingReport = new ContainerResourceReport(containerStopping, 0,
                service, ServiceStatus.STOPPING, EstimationWindow.SHORT, ImmutableMap.of(), ImmutableMap.of(),
                ImmutableMap.of(), 0);
        containerReportsBuilder.put(containerStopping, containerStoppingReport);

        final NodeIdentifier containerStopped = new DnsNameIdentifier("nodeA0_cSTOPPED");
        final ContainerResourceReport containerStoppedReport = new ContainerResourceReport(containerStopped, 0, service,
                ServiceStatus.STOPPED, EstimationWindow.SHORT, ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(),
                0);
        containerReportsBuilder.put(containerStopped, containerStoppedReport);

        final ResourceReport resourceReport = new ResourceReport(node, 0, EstimationWindow.SHORT, //
                ImmutableMap.of(), // nodeComputeCapacity

                ImmutableMap.of(), // networkCapacity
                ImmutableMap.of(), // networkLoad
                ImmutableMap.of(), // networkDemand

                containerReportsBuilder.build(), 0, 0);

        final ImmutableSet<ResourceReport> reports = ImmutableSet.of(resourceReport);

        LoadBalancerPlanBuilder newServicePlan = null;

        // ensure that exception is not thrown when including STARTING and
        // RUNNING containers
        // but excluding STOPPING and STOPPED containers
        try {
            LoadBalancerPlan prevPlan = LoadBalancerPlan.getNullLoadBalancerPlan(region);

            newServicePlan = new LoadBalancerPlanBuilder(prevPlan, reports);
            newServicePlan.getPlan().clear();
            newServicePlan.getPlan().computeIfAbsent(node, k -> new HashSet<>())
                    .add(new ContainerInfo(containerStarting, service, 1.0, false, false));
            newServicePlan.getPlan().computeIfAbsent(node, k -> new HashSet<>())
                    .add(new ContainerInfo(containerRunning, service, 1.0, false, false));

            LOGGER.info("newServicePlan: {}, reports: {}", newServicePlan, reports);
            newServicePlan.toLoadBalancerPlan(reports, overflowPlan);
        } catch (Exception e) {
            Assert.fail("Failed to build plan " + newServicePlan + ": " + e);
        }

        // ensure that exception is thrown when excluding STARTING and RUNNING
        // containers
        try {
            LoadBalancerPlan prevPlan = LoadBalancerPlan.getNullLoadBalancerPlan(region);

            newServicePlan = new LoadBalancerPlanBuilder(prevPlan, reports);
            newServicePlan.getPlan().clear();

            LOGGER.info("newServicePlan: {}, reports: {}", newServicePlan, reports);
            newServicePlan.toLoadBalancerPlan(reports, overflowPlan);

            Assert.fail("Failed to throw exception for ommitting STARTING and RUNNING " + "containers from plan: "
                    + newServicePlan);
        } catch (Exception e) {
            LOGGER.info("Exception successfully thrown for omitting STARTING and RUNNING " + "containers from plan: ",
                    e);
        }

        // ensure that exception is thrown when changing the service
        try {
            LoadBalancerPlan prevPlan = LoadBalancerPlan.getNullLoadBalancerPlan(region);

            newServicePlan = new LoadBalancerPlanBuilder(prevPlan, reports);
            LOGGER.info("newServicePlan: {}, reports: {}", newServicePlan, reports);
            newServicePlan.getPlan().clear();
            newServicePlan.getPlan().computeIfAbsent(node, k -> new HashSet<>())
                    .add(new ContainerInfo(containerStarting, service, 1.0, false, false));
            newServicePlan.getPlan().computeIfAbsent(node, k -> new HashSet<>())
                    .add(new ContainerInfo(containerRunning, serviceChanged, 1.0, false, false));
            newServicePlan.toLoadBalancerPlan(reports, overflowPlan);

            Assert.fail("Failed to throw exception for changing the service " + "of a container in plan: "
                    + newServicePlan);
        } catch (Exception e) {
            LOGGER.info("Exception successfully thrown for changing the service" + "of a container in plan: ", e);
        }
    }
}
