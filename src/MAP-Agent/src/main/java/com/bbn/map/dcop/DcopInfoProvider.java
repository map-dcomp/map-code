package com.bbn.map.dcop;

import javax.annotation.Nonnull;

import com.bbn.map.ap.TotalDemand;
import com.bbn.map.ta2.RegionalTopology;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
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
     * This method is called by DCOP to share new information. This method will
     * ensure that the stored value is disconnected from the value passed in so
     * that it's safe for another to read the data later.
     * 
     * @param v
     *            the current information to share FROM this DCOP node with
     *            other regions
     */
    void setLocalDcopSharedInformation(@Nonnull DcopSharedInformation v);

    /**
     * @return the summary for the region.
     */
    @Nonnull
    ResourceSummary getDcopResourceSummary();

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

    /**
     * Total demand for the specified {@code service} across the topology. This
     * uses the {@link EstimationWindow#LONG} estimation window.
     * 
     * @param service
     *            the service to get the demand for
     * @return demand for the service, {@link TotalDemand#nullTotalDemand()} if
     *         there is no known demand for the service
     */
    TotalDemand getTotalDemandForService(@Nonnull ServiceIdentifier<?> service);

    /**
     * Get the graph of the regions in the network.
     * 
     * @return the resulting graph
     */
    @Nonnull
    RegionalTopology getRegionTopology();
}
