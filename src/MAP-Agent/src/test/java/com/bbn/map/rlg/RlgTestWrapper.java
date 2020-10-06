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
package com.bbn.map.rlg;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.RlgAlgorithm;
import com.bbn.map.AgentConfiguration.RlgPriorityPolicy;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.MutableApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * A wrapper for {@link RLGService} and an {@link RlgInfoProvider}
 * implementation {@link MockRlgInfoProvider} and
 * {@link MutableApplicationManagerApi} for convenient testing of an RLG
 * implementation.
 * 
 * @author awald
 *
 */
public class RlgTestWrapper {
    private static final MutableApplicationManagerApi APP_MANAGER = AppMgrUtils.getMutableApplicationManager();
    private static final AgentConfiguration AGENT_CONFIGURATION = AgentConfiguration.getInstance();

    private Map<ServiceIdentifier<?>, ApplicationSpecification> serviceSpecs = new HashMap<>();

    private RegionIdentifier region;
    private MockRlgInfoProvider rlgInfoProvider;

    private RLGService rlg;

    /**
     * @param region
     *            the region to used for this test of RLG
     * @param node
     *            the node on which RLG is running in the test
     * @param serviceSpecs
     *            the service specifications to use for the test
     * @param rlgAlgorithm
     *            the RLG algorithm to test
     * @param rlgPriorityPolicy
     *            the RLG priority policy to use for the test
     */
    public RlgTestWrapper(RegionIdentifier region,
            NodeIdentifier node,
            Set<ApplicationSpecification> serviceSpecs,
            RlgAlgorithm rlgAlgorithm,
            RlgPriorityPolicy rlgPriorityPolicy) {
        APP_MANAGER.clear();

        serviceSpecs.forEach((appSpec) -> {
            APP_MANAGER.save(appSpec);
        });

        AGENT_CONFIGURATION.setRlgAlgorithm(rlgAlgorithm);
        AGENT_CONFIGURATION.setRlgPriorityPolicy(rlgPriorityPolicy);

        this.region = region;

        this.serviceSpecs.clear();
        serviceSpecs.forEach((spec) -> {
            this.serviceSpecs.put(spec.getCoordinates(), spec);
        });

        this.rlgInfoProvider = new MockRlgInfoProvider(this.region);
        this.rlg = new RLGService(node.getName(), region, rlgInfoProvider, APP_MANAGER);
    }

    /**
     * Initializes the DCOP plan based on a set of services to be active in the
     * region.
     * 
     * @param regionServices
     *            the set of services to be active in the region
     */
    public void initializeDcopPlan(Set<ServiceIdentifier<?>> regionServices) {
        Map<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> serviceRegionOverflow = new HashMap<>();

        regionServices.forEach((service) -> {
            // explicitly specify that all traffic for service is to stay in the
            // local region
            serviceRegionOverflow.put(service, ImmutableMap.of(region, 1.0));
        });

        RegionPlan plan = new RegionPlan(region, ImmutableMap.copyOf(serviceRegionOverflow));
        this.rlgInfoProvider.setDcopPlan(plan);
    }

    /**
     * Sets the services and priorities to be used for an RLG test.
     * 
     * @param servicePriorities
     *            Map of services to priority values
     */
    public void setServicesAndPriorities(Map<ServiceIdentifier<?>, Integer> servicePriorities) {
        APP_MANAGER.clear();

        servicePriorities.forEach((service, priority) -> {
            ApplicationSpecification appSpec = new ApplicationSpecification((ApplicationCoordinates) service);
            appSpec.setPriority(priority);
            APP_MANAGER.save(appSpec);
        });
    }

    /**
     * @return the RLG services and priorities currently registered
     */
    public Map<ServiceIdentifier<?>, Integer> getServicePriorities() {
        Map<ServiceIdentifier<?>, Integer> servicePriorities = new HashMap<>();

        APP_MANAGER.getAllApplicationSpecifications().forEach((appSpec) -> {
            servicePriorities.put(appSpec.getCoordinates(), appSpec.getPriority());
        });

        return servicePriorities;
    }

    /**
     * Runs a single round of the RLG implementation.
     * 
     * @param reports
     *            the {@link ResourceReport}s to pass into RLG for this round.
     * @return the resulting RLG plan
     */
    public LoadBalancerPlan executeRound(Set<ResourceReport> reports) {
        rlgInfoProvider.setResourceReports(reports);
        rlg.execute();
        return rlgInfoProvider.getRlgPlan();
    }

    /**
     * @return the DCOP plan that is currently being used for RLG
     */
    public RegionPlan getDcopPlan() {
        return rlgInfoProvider.getDcopPlan();
    }
}
