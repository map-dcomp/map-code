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
package com.bbn.map.simulator;

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Class for storing information in the node load.
 * 
 * @author jschewe
 *
 */
/* package */ final class NodeLoadEntry implements LoadEntry {

    /**
     * 
     * @param startTime
     *            see {@Link #getStartTime()}
     * @param client
     *            see {@link #getClient()}
     * @param request
     *            see {@link #getRequest()}
     */
    /* package */ NodeLoadEntry(final long startTime,
            @Nonnull final NodeIdentifier client,
            @Nonnull final ClientLoad request,
            final long duration) {
        this.startTime = startTime;
        this.client = client;
        this.request = request;
        this.duration = duration;
    }

    private final NodeIdentifier client;

    /**
     * @return the client that is causing the load.
     */
    @Nonnull
    public NodeIdentifier getClient() {
        return client;
    }

    private final ClientLoad request;

    /**
     * @return the request that is creating the load
     */
    @Nonnull
    public ClientLoad getRequest() {
        return request;
    }

    private final long duration;

    /**
     * @return The duration that the load takes, this is based on the load of
     *         the system at the time that the client requested the service
     */
    public long getDuration() {
        return duration;
    }

    private final long startTime;

    /**
     * This may not be the start time of the {@link BaseNetworkLoad} object if
     * the request is processed after it has been scheduled.
     * 
     * @return when the entry started
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * @return the number of standard sized containers that the request is using
     */
    @JsonIgnore
    public double getNumberOfContainers() {
        return getRequest().getNumberOfContainers();
    }

}