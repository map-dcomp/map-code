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
package com.bbn.map.simulator;

import java.util.List;

import javax.annotation.Nonnull;

import com.bbn.map.simulator.ClientSim.RequestResult;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

/**
 * Class for recording client requests and other relevant information.
 * 
 * @author awald
 *
 */
public final class ClientRequestRecord {
    private final NodeIdentifier ncpContacted;
    private final NodeIdentifier containerContacted;
    private final long timeSent;
    private final ClientLoad request;
    private final int linksTraversed;
    private final List<ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> linkLoads;
    private final RequestResult networkRequestResult;
    private final RequestResult serverResult;
    private final boolean downNode;

    /**
     * 
     * @param ncpContacted
     *            see {@link #getNcpContacted()}
     * @param containerContacted
     *            see {@link #getContainerContacted()}
     * @param timeSent
     *            see {@link #getTimeSent()}
     * @param request
     *            see {@link #getRequest()}
     * @param linksTraversed
     *            see {@link #getLinksTraversed()}
     * @param linkLoads
     *            see {@link #getLinkLoads()}
     * @param networkRequestResult
     *            see {@link #getNetworkRequestResult()}
     * @param serverResult
     *            see {@link #getServerResult()}
     * @param downNode
     *            see {@link #getDownNode()}
     */
    public ClientRequestRecord(@JsonProperty("ncpContacted") final NodeIdentifier ncpContacted,
            @JsonProperty("containerContacted") final NodeIdentifier containerContacted,
            @JsonProperty("timeSent") final long timeSent,
            @JsonProperty("request") final ClientLoad request,
            @JsonProperty("linksTraversed") final int linksTraversed,
            @JsonProperty("linkLoads") final List<ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> linkLoads,
            @JsonProperty("networkResult") final RequestResult networkRequestResult,
            @JsonProperty("serverResult") final RequestResult serverResult,
            @JsonProperty("downNode") final boolean downNode) {
        this.ncpContacted = ncpContacted;
        this.containerContacted = containerContacted;
        this.timeSent = timeSent;
        this.request = request;
        this.linksTraversed = linksTraversed;
        this.linkLoads = linkLoads;
        this.networkRequestResult = networkRequestResult;
        this.serverResult = serverResult;
        this.downNode = downNode;
    }

    /**
     * @return the identifier of the NCP that the request was sent to. This
     *         may be null if the request failed.
     */
    public NodeIdentifier getNcpContacted() {
        return ncpContacted;
    }

    /**
     * @return the identifier of the container that the request was sent to.
     *         This may be null if the request failed.
     */
    public NodeIdentifier getContainerContacted() {
        return containerContacted;
    }

    /**
     * @return the time that the request was sent to the container
     */
    public long getTimeSent() {
        return timeSent;
    }

    /**
     * @return the request that was sent
     */
    @Nonnull
    public ClientLoad getRequest() {
        return request;
    }

    /**
     * @return the number of links that the request traversed. May be null.
     */
    public int getLinksTraversed() {
        return linksTraversed;
    }

    /**
     * @return the new load on each link from the path to the NCP
     */
    public List<ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> getLinkLoads() {
        return linkLoads;
    }

    /**
     * @return the status after attempting to send the request over the
     *         network.
     */
    public RequestResult getNetworkRequestResult() {
        return networkRequestResult;
    }

    /**
     * @return the status after attempting to send the request to the
     *         server. Will be null if the network request failed.
     * @see #getNetworkRequestResult()
     */
    @Nonnull
    public RequestResult getServerResult() {
        return serverResult;
    }

    /**
     * 
     * @return true if the node to contact is down or a path to the node
     *         cannot be found
     */
    public boolean getDownNode() {
        return downNode;
    }

}