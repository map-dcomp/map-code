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
package com.bbn.map;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * Keep track of the services that are available in a region.
 * 
 * @author jschewe
 *
 */
public class RegionAvailableServices implements Serializable {

    private static final long serialVersionUID = 1L;

    private final RegionIdentifier region;

    /**
     * 
     * @return the region that the information is for
     */
    public RegionIdentifier getRegion() {
        return region;
    }

    /**
     * Create an object that is the union of the two parameters.
     * 
     * @param one
     *            one object to merge
     * @param two
     *            the second object to merge
     * @throws IllegalArgumentException
     *             if the two objects do not have the same region
     */
    public RegionAvailableServices(@Nonnull final RegionAvailableServices one,
            @Nonnull final RegionAvailableServices two) throws IllegalArgumentException {
        this.region = one.getRegion();

        runningServices.addAll(one.runningServices);
        nodesRunningServices.putAll(one.nodesRunningServices);

        runningServices.addAll(two.runningServices);
        nodesRunningServices.putAll(two.nodesRunningServices);
    }

    /**
     * The nodes running service.
     */
    private final Map<NodeIdentifier, ServiceIdentifier<?>> nodesRunningServices = new HashMap<>();

    /**
     * The services running in the region.
     */
    private final Set<ServiceIdentifier<?>> runningServices = new HashSet<>();

    /**
     * 
     * @param region
     *            see {@link #getRegion()}
     */
    public RegionAvailableServices(@Nonnull final RegionIdentifier region) {
        this.region = region;
    }

    /**
     * @param service
     *            the service to look for
     * @return if the service is available
     */
    public boolean isServiceAvailable(final ServiceIdentifier<?> service) {
        return runningServices.contains(service);
    }

    /**
     * Add an available service.
     * 
     * @param node the node running the service
     * @param service the service being run
     */
    protected void internalAddService(@Nonnull final NodeIdentifier node, @Nonnull final ServiceIdentifier<?> service) {
        nodesRunningServices.put(node, service);
        runningServices.add(service);
    }

    /**
     * 
     * @param node
     *            the node to find the service for
     * @return the service running on a node, null if the service is not running
     *         on a node in the region
     */
    public ServiceIdentifier<?> getServiceForNode(final NodeIdentifier node) {
        return nodesRunningServices.get(node);
    }

    @Override
    public String toString() {
        return "Region: " + getRegion() + " services: " + nodesRunningServices.toString();
    }

}
