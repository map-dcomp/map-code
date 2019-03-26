package com.bbn.map.dcop;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
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

}
