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

import java.util.LinkedList;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.NetworkAvailableServices;
import com.bbn.map.ta2.OverlayTopology;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.NetworkState;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNodeState;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Implementation of an {@link RlgInfoProvider} for testing {@link RLGService}.
 * 
 * @author awald
 *
 */
public class MockRlgInfoProvider implements RlgInfoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(MockRlgInfoProvider.class);

    private final RegionIdentifier region;
    private final NetworkState networkState;
    private final RegionNodeState regionNodeState; // provide ResourceReports

    private final NetworkAvailableServices allNetworkAvailableServices = new NetworkAvailableServices();
    private final Object availableServicesLock = new Object();

    private RlgSharedInformation localRlgSharedInformation = new RlgSharedInformation();

    /**
     * @param region
     *            the region for this RLG test
     */
    public MockRlgInfoProvider(RegionIdentifier region) {
        this.region = region;
        this.networkState = new NetworkState(this.region);
        this.regionNodeState = new RegionNodeState(this.region);
    }
    
    @Override
    public RegionIdentifier getRegion() {
        return region;
    }

    /**
     * Modify the set of reports.
     * 
     * @param reports
     *            the new reports
     */
    public void setResourceReports(@Nonnull final Set<ResourceReport> reports) {
        this.regionNodeState.setResourceReports(ImmutableSet.copyOf(reports));
    }

    /**
     * 
     * @return the {@link ResourceReport}s for all nodes in the region.
     */
    @Nonnull
    public ImmutableSet<ResourceReport> getNodeResourceReports() {
        return this.regionNodeState.getNodeResourceReports();
    }

    @Override
    public ImmutableMap<RegionIdentifier, RlgSharedInformation> getAllRlgSharedInformation() {
        LOGGER.trace("getAllRlgSharedInformation: returning empty Map");
        return ImmutableMap.of();
    }

    @Override
    public void setLocalRlgSharedInformation(RlgSharedInformation v) {
        localRlgSharedInformation = v;
        LOGGER.trace("Set localRlgSharedInformation to {}", localRlgSharedInformation);

    }

    @Override
    @Nonnull
    public ResourceSummary getRegionSummary(@Nonnull ResourceReport.EstimationWindow estimationWindow) {
        return getNetworkState().getRegionSummary(estimationWindow);
    }

    @Override
    public RegionPlan getDcopPlan() {
        return getNetworkState().getRegionPlan();
    }

    /**
     * Sets the DCOP plan for the region.
     * 
     * @param plan
     *            the new DCOP plan
     */
    public void setDcopPlan(RegionPlan plan) {
        getNetworkState().setRegionPlan(plan);
    }

    @Override
    public LoadBalancerPlan getRlgPlan() {
        return getNetworkState().getLoadBalancerPlan();
    }

    private LoadBalancerPlan prevRlgPlan = null;

    @Override
    public void publishRlgPlan(LoadBalancerPlan plan) {
        getNetworkState().setLoadBalancerPlan(plan);

        if (!plan.equals(prevRlgPlan)) {
            // validateRlgPlan(plan);

            // final String message = String.format("%s",
            // EventTypes.RLG_PLAN_PUBLISHED);

            // writeEventLog(message);

            // LOGGER.debug("Published new RLG plan at round {}: {}",
            // getExecutionCount(), plan);

            prevRlgPlan = plan;
        }

    }

    /**
     * @return the current {@link NetworkState} object
     */
    @Nonnull
    public NetworkState getNetworkState() {
        return networkState;
    }

    @Override
    public boolean isServiceAvailable(RegionIdentifier region, ServiceIdentifier<?> service) {
        // TODO: maybe just return true
        synchronized (availableServicesLock) {
            return allNetworkAvailableServices.isServiceAvailable(region, service);
        }
    }

    @Override
    public OverlayTopology getCurrentRegionTopology() {
        return new OverlayTopology(new LinkedList<>(), new LinkedList<>()); // no
                                                                            // topology
    }

}
