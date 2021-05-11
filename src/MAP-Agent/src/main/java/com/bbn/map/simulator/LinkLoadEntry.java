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
package com.bbn.map.simulator;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Class for storing information about the link load.
 * 
 * @author jschewe
 *
 */
/* package */ final class LinkLoadEntry implements LoadEntry {
    /**
     * 
     * @param parent
     *            see {@link #getParent()}
     * @param client
     *            see {@Link #getClient()}
     */
    /* package */ LinkLoadEntry(@Nonnull final LinkResourceManager parent,
            @Nonnull final NodeNetworkFlow flow,
            final long startTime,
            final long duration,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final ImmutableMap<LinkAttribute, Double> networkLoad) {
        this.parent = parent;
        this.flow = flow;
        this.startTime = startTime;
        this.duration = duration;
        this.service = service;
        this.networkLoad = networkLoad;
    }

    private final LinkResourceManager parent;

    /**
     * 
     * @return the manager that this entry belongs to
     */
    @Nonnull
    public LinkResourceManager getParent() {
        return parent;
    }

    private final NodeNetworkFlow flow;

    /**
     * 
     * @return the flow that is creating the load
     */
    public NodeNetworkFlow getFlow() {
        return flow;
    }

    private final ServiceIdentifier<?> service;

    /**
     * 
     * @return the service that the load is for
     */
    public ServiceIdentifier<?> getService() {
        return service;
    }

    private final ImmutableMap<LinkAttribute, Double> networkLoad;

    /**
     * 
     * @return the load that is applied to the network
     */
    public ImmutableMap<LinkAttribute, Double> getNetworkLoad() {
        return networkLoad;
    }

    private final long startTime;

    /**
     * 
     * @return the start of the request
     * @see BaseNetworkLoad#getStartTime()
     */
    public long getStartTime() {
        return startTime;
    }

    private final long duration;

    /**
     * 
     * @return the amount of time that the request is active on the network
     * @see BaseNetworkLoad#getNetworkDuration()
     */
    public long getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return String.format("flow: %s start: %s duration: %s load: %s", getFlow(), getStartTime(), getDuration(),
                getNetworkLoad());
    }
}