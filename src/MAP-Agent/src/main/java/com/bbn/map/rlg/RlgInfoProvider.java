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

import javax.annotation.Nonnull;

import com.bbn.map.ta2.OverlayTopology;
import com.bbn.map.ta2.TA2Interface;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.google.common.collect.ImmutableSet;

/**
 * Interface used by RLG to interact with the MAP system.
 */
public interface RlgInfoProvider {

    /**
     * @return the summary for the region.
     */
    @Nonnull
    ResourceSummary getRlgResourceSummary();

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
    ImmutableSet<ResourceReport> getRlgResourceReports();

    /**
     * 
     * @return the region that this RLG is in
     */
    @Nonnull
    RegionIdentifier getRegion();

    /**
     * Get the overlay for the current region.
     * 
     * @return the topology of the region
     * @see TA2Interface#getOverlay(RegionIdentifier)
     */
    @Nonnull
    OverlayTopology getCurrentRegionTopology();

}
