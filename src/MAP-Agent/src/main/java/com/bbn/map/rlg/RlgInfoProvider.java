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
package com.bbn.map.rlg;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNodeState;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.RegionServiceState;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Interface used by RLG to interact with the MAP system.
 */
public interface RlgInfoProvider {

    /**
     * This method should be used by RLG to get the most recent shared state.
     * 
     * @return the RLG shared information
     */
    @Nonnull
    ImmutableMap<RegionIdentifier, RlgSharedInformation> getAllRlgSharedInformation();

    /**
     * This method is called by RLG to share new information.
     * 
     * @param v
     *            the current information to share FROM this RLG node with other
     *            regions
     */
    void setLocalRlgSharedInformation(@Nonnull RlgSharedInformation v);

    /**
     * @param estimationWindow
     *            the estimation window for the demand
     * @return the summary for the region.
     */
    @Nonnull
    ResourceSummary getRegionSummary(@Nonnull ResourceReport.EstimationWindow estimationWindow);

    /**
     * 
     * @return the most recent DCOP plan that this node has seen
     */
    @Nonnull
    RegionPlan getDcopPlan();

    /**
     * 
     * @return the most recent RLG plan that this node has seen
     */
    @Nonnull
    LoadBalancerPlan getRlgPlan();

    /**
     * 
     * @param plan
     *            the new plan to be published
     */
    void publishRlgPlan(@Nonnull LoadBalancerPlan plan);

    /**
     * 
     * @return the current node state
     */
    @Nonnull
    RegionNodeState getRegionNodeState();

    /**
     * 
     * @return the current service state
     */
    @Nonnull
    RegionServiceState getRegionServiceState();

    /**
     * 
     * @param region
     *            the region to check
     * @param service
     *            the service to check for
     * @return check if a service is availahble in a region
     */
    boolean isServiceAvailable(RegionIdentifier region, ServiceIdentifier<?> service);

}
