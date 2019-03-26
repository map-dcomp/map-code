/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018>, <Raytheon BBN Technologies>
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

import java.io.Serializable;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

/**
 * A client request for a service over some period of time.
 */
public class ClientRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * @param startTime
     *            see {@link #getStartTime()}
     * @param serverDuration
     *            see {@link #getServerDuration()}
     * @param networkDuration
     *            see {@link #getNetworkDuration()}
     * @param numClients
     *            see {@link #getNumClients()}
     * @param service
     *            see {@link #getService()}
     * @param nodeLoad
     *            see {@link #getNodeLoad()}
     * @param networkLoad
     *            see {@link #getNetworkLoad()}
     */
    public ClientRequest(@JsonProperty("startTime") final long startTime,
            @JsonProperty("serverDuration") final long serverDuration,
            @JsonProperty("networkDuration") final long networkDuration,
            @JsonProperty("numClients") final int numClients,
            @JsonProperty("service") final ApplicationCoordinates service,
            @JsonProperty("nodeLoad") final ImmutableMap<NodeMetricName, Double> nodeLoad,
            @JsonProperty("networkLoad") final ImmutableMap<LinkMetricName, Double> networkLoad) {
        this.startTime = startTime;
        this.serverDuration = serverDuration;
        this.networkDuration = networkDuration;
        this.numClients = numClients;
        this.service = service;
        this.nodeLoad = nodeLoad;
        this.networkLoad = networkLoad;
    }

    private final long startTime;

    /**
     * @return when the request starts
     */
    public long getStartTime() {
        return startTime;
    }

    private final long serverDuration;

    /**
     * This duration is the minimum duration for the request to effect the
     * server. Depending on how busy the server is the actual server processing
     * time may be longer.
     * 
     * @return how long the request is active for
     */
    public long getServerDuration() {
        return serverDuration;
    }

    private final long networkDuration;

    /**
     * This duration is the minimum duration for the request to effect the
     * network. Depending on how busy the network is the actual network
     * processing time may be longer.
     * 
     * @return how long the request is active for
     */
    public long getNetworkDuration() {
        return networkDuration;
    }

    private final int numClients;

    /**
     * The number of clients to simulate. This defaults to 1.
     * 
     * @return the number of clients to simulate
     */
    public int getNumClients() {
        return numClients;
    }

    private final ApplicationCoordinates service;

    /**
     * @return which service is being used
     */
    public ApplicationCoordinates getService() {
        return service;
    }

    private final ImmutableMap<NodeMetricName, Double> nodeLoad;

    /**
     * 
     * @return how much load is on the node
     */
    public ImmutableMap<NodeMetricName, Double> getNodeLoad() {
        return nodeLoad;
    }

    /**
     * @return {@link #getNodeLoad()} with {@link NodeMetricName} objects
     */
    @JsonIgnore
    public ImmutableMap<NodeAttribute<?>, Double> getNodeLoadAsAttribute() {
        final ImmutableMap.Builder<NodeAttribute<?>, Double> builder = ImmutableMap.builder();
        nodeLoad.forEach((k, v) -> builder.put(k, v));
        return builder.build();
    }

    private final ImmutableMap<LinkMetricName, Double> networkLoad;

    /**
     * 
     * @return how much load there is on the network
     */
    public ImmutableMap<LinkMetricName, Double> getNetworkLoad() {
        return networkLoad;
    }

    /**
     * @return {@link #getNetworkLoad()} with {@link LinkMetricName} objects
     */
    @JsonIgnore
    public ImmutableMap<LinkAttribute<?>, Double> getNetworkLoadAsAttribute() {
        final ImmutableMap.Builder<LinkAttribute<?>, Double> builder = ImmutableMap.builder();
        networkLoad.forEach((k, v) -> builder.put(k, v));
        return builder.build();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" //
                + " start: " + startTime //
                + " serverDuration: " + serverDuration //
                + " networkDuration: " + networkDuration //
                + " numClients: " + numClients //
                + " service: " + service //
                + " nodeLoad: " + nodeLoad //
                + " networkLoad: " + networkLoad //
                + " ]";
    }

    /**
     * This is computed from the {@link #getNodeLoad()} and defaults to 0 if the
     * {@link NodeMetricName#TASK_CONTAINERS} attribute is not present.
     * 
     * @return the number of standard sized containers that the request is using
     */
    @JsonIgnore
    public double getNumberOfContainers() {
        return getNodeLoad().getOrDefault(NodeMetricName.TASK_CONTAINERS, 0D);
    }
}
