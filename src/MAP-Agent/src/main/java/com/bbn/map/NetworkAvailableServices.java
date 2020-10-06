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
package com.bbn.map;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * All services available in the network.
 * 
 * @author jschewe
 *
 */
public class NetworkAvailableServices implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<RegionIdentifier, RegionAvailableServices> data = new HashMap<>();

    /**
     * Create an object with no known services.
     */
    public NetworkAvailableServices() {
    }

    /**
     * @param regionAvailableServices
     *            the know services for a region
     */
    public NetworkAvailableServices(@Nonnull final RegionAvailableServices regionAvailableServices) {
        data.put(regionAvailableServices.getRegion(), regionAvailableServices);
    }

    /**
     * Merge 2 objects.
     * 
     * @param a
     *            the first object
     * @param b
     *            the second object
     */
    public NetworkAvailableServices(@Nonnull final NetworkAvailableServices a,
            @Nonnull final NetworkAvailableServices b) {
        data.putAll(a.data);
        b.data.forEach((region, regionAvailableServices) -> {
            final RegionAvailableServices existing = data.get(region);
            if (null == existing) {
                data.put(region, regionAvailableServices);
            } else {
                final RegionAvailableServices merged = new RegionAvailableServices(regionAvailableServices, existing);
                data.put(region, merged);
            }
        });
    }

    /**
     * @param region
     *            the region to check
     * @param service
     *            the service to find
     * @return if a service is available in the specified region
     */
    public boolean isServiceAvailable(final RegionIdentifier region, final ServiceIdentifier<?> service) {
        if (data.containsKey(region)) {
            return data.get(region).isServiceAvailable(service);
        } else {
            return false;
        }
    }

    /**
     * 
     * @param node
     *            the node to find the service for
     * @return the service running on the node or null it is not known what
     *         service is running on the node
     */
    public ServiceIdentifier<?> getServiceForNode(final NodeIdentifier node) {
        return data.entrySet().stream().map(e -> e.getValue().getServiceForNode(node)).filter(s -> null != s).findAny()
                .orElse(null);
    }

    /**
     * 
     * @return the null object, used byProtelis
     */
    public static NetworkAvailableServices nullNetworkAvailableServices() {
        return new NetworkAvailableServices();
    }

    /**
     * Used by Protelis to merge objects.
     * 
     * @param a
     *            the first object to merge
     * @param b
     *            the second object to merge
     * @return a new object that is the merge of a and b
     */
    public static NetworkAvailableServices mergeNetworkAvailableServices(@Nonnull final NetworkAvailableServices a,
            @Nonnull final NetworkAvailableServices b) {
        return new NetworkAvailableServices(a, b);
    }

    /**
     * 
     * @param regionAvailableServices
     *            the object to convert
     * @return a new network available services object
     */
    public static NetworkAvailableServices convertToNetworkAvailableServices(
            @Nonnull final RegionAvailableServices regionAvailableServices) {
        return new NetworkAvailableServices(regionAvailableServices);
    }

    @Override
    public String toString() {
        return data.toString();
    }

}
