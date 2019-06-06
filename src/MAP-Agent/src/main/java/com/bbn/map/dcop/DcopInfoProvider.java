package com.bbn.map.dcop;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.google.common.collect.ImmutableMap;

/**
 * Interface for access the {@link DcopSharedInformation} object.
 */
public interface DcopInfoProvider {

    /**
     * This method should be used by DCOP to get the most recent shared state.
     * 
     * @return the DCOP shared information
     */
    @Nonnull
    ImmutableMap<RegionIdentifier, DcopSharedInformation> getAllDcopSharedInformation();

    /**
     * This method is called by DCOP to share new information.
     * 
     * @param v
     *            the current information to share FROM this DCOP node with
     *            other regions
     */
    void setLocalDcopSharedInformation(@Nonnull DcopSharedInformation v);

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
     * @param plan
     *            the new plan to be published
     */
    void publishDcopPlan(@Nonnull RegionPlan plan);

}
