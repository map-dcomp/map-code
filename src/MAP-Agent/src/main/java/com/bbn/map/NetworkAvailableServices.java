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
package com.bbn.map;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

/**
 * All services available in the network.
 * 
 * @author jschewe
 *
 */
public class NetworkAvailableServices implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The nodes running service.
     */
    private final Map<NodeIdentifier, ServiceIdentifier<?>> nodesRunningServices = new HashMap<>();

    /**
     * 
     * @return the services list so that subclasses can use it for hashing
     */
    protected Map<NodeIdentifier, ServiceIdentifier<?>> getNodesRunningServices() {
        return nodesRunningServices;
    }

    /**
     * Create an object with no known services.
     */
    public NetworkAvailableServices() {
        this.hashCode = 0;
    }

    /**
     * Copy constructor.
     * 
     * @param source
     *            what to copy.
     */
    public NetworkAvailableServices(final NetworkAvailableServices source) {
        nodesRunningServices.putAll(source.nodesRunningServices);
        this.hashCode = Objects.hash(nodesRunningServices);
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
        nodesRunningServices.putAll(a.nodesRunningServices);
        nodesRunningServices.putAll(b.nodesRunningServices);
        this.hashCode = Objects.hash(nodesRunningServices);
    }

    /**
     * 
     * @param node
     *            the node to find the service for
     * @return the service running on the node or null it is not known what
     *         service is running on the node
     */
    public ServiceIdentifier<?> getServiceForNode(final NodeIdentifier node) {
        return nodesRunningServices.get(node);
    }

    /**
     * 
     * @return the null object, used by Protelis
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

    @Override
    public String toString() {
        return nodesRunningServices.toString();
    }

    /**
     * Add an available service.
     * 
     * @param node
     *            the node running the service
     * @param service
     *            the service being run
     */
    protected void internalAddService(@Nonnull final NodeIdentifier node, @Nonnull final ServiceIdentifier<?> service) {
        nodesRunningServices.put(node, service);
    }

    private final int hashCode;

    /**
     * Cached in the constructor since it shouldn't change. Subclasses that are
     * mutable need to override this.
     */
    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (null == o) {
            return false;
        } else if (this == o) {
            return true;
        } else if (this.getClass().equals(o.getClass())) {
            final NetworkAvailableServices other = (NetworkAvailableServices) o;
            return this.nodesRunningServices.equals(other.nodesRunningServices);
        } else {
            return false;
        }
    }

}
