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

import java.util.HashMap;
import java.util.Map;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stores state information for client simulation.
 * 
 * @author awald
 *
 */
public class ClientState {
    private final String clientName;
    private final RegionIdentifier clientRegion;

    private int numRequestsAttempted = 0;

    /* package */ void incrementRequestsAttempted() {
        ++numRequestsAttempted;
    }

    private int numRequestsSucceeded = 0;

    private int numRequestsFailedForServerLoad = 0;

    /* package */ void incrementRequestsFailedForServerLoad() {
        ++numRequestsFailedForServerLoad;
    }

    private int numRequestsFailedForNetworkLoad = 0;

    /* package */ void incrementRequestsFailedForNetworkLoad() {
        ++numRequestsFailedForNetworkLoad;
    }

    private int numRequestsSlowForServerLoad = 0;

    /* package */ void incrementRequestsSlowForServerLoad() {
        ++numRequestsSlowForServerLoad;
    }

    private int numRequestsSlowForNetworkLoad = 0;

    /* package */ void incrementRequestsSlowForNetworkLoad() {
        ++numRequestsSlowForNetworkLoad;
    }

    private int numRequestsSlowForNetworkAndServerLoad = 0;

    /* package */ void incrementRequestsSlowForNetworkAndServerLoad() {
        ++numRequestsSlowForNetworkAndServerLoad;
    }

    private int numRequestsFailedForDownNode = 0;

    /* package */ void incrementRequestsFailedForDownNode() {
        ++numRequestsFailedForDownNode;
    }

    private final Map<RegionIdentifier, Integer> numRequestsServicedByRegion;

    /* package */ ClientState(ClientSim sim) {
        clientName = sim.getClientName();
        clientRegion = sim.getClientRegion();
        numRequestsServicedByRegion = new HashMap<>();
    }

    /**
     * 
     * @param clientName
     *            the name of the client
     * @param clientRegion
     *            the region of the client
     * @param numRequestsAttempted
     *            the number of client requests that were attempted
     * @param numRequestsSucceeded
     *            the number of client requests that succeeded
     * @param numRequestsFailedForServerLoad
     *            the number of client requests that failed because the server
     *            was overloaded
     * @param numRequestsFailedForNetworkLoad
     *            the number of client requests that failed because the network
     *            path was overloaded
     * @param numRequestsSlowForServerLoad
     *            the number of client requests that are slow because the server
     *            was overloaded
     * @param numRequestsSlowForNetworkLoad
     *            the number of client requests that were slow because the
     *            network path was overloaded
     * @param numRequestsSlowForNetworkAndServerLoad
     *            the number of client requests that were slow because both the
     *            network path and the server were overloaded
     * @param numRequestsServicedByRegion
     *            the number of requests from this client that were serviced by
     *            each region
     * @param numRequestsFailedForDownNode
     *            see {@Link #getNumRequestsFailedForDownNode()}
     */
    public ClientState(@JsonProperty("clientName") final String clientName,
            @JsonProperty("clientRegion") final RegionIdentifier clientRegion,
            @JsonProperty("numRequestsAttempted") final int numRequestsAttempted,
            @JsonProperty("numRequestsSucceeded") final int numRequestsSucceeded,
            @JsonProperty("numRequestsFailedForServerLoad") final int numRequestsFailedForServerLoad,
            @JsonProperty("numRequestsFailedForNetworkLoad") final int numRequestsFailedForNetworkLoad,
            @JsonProperty("numRequestsSlowForServerLoad") final int numRequestsSlowForServerLoad,
            @JsonProperty("numRequestsSlowForNetworkLoad") final int numRequestsSlowForNetworkLoad,
            @JsonProperty("numRequestsSlowForNetworkAndServerLoad") final int numRequestsSlowForNetworkAndServerLoad,
            @JsonProperty("numRequestsServicedByRegion") final Map<RegionIdentifier, Integer> numRequestsServicedByRegion,
            @JsonProperty("numRequestsFailedForDownNode") final int numRequestsFailedForDownNode) {
        this.clientName = clientName;
        this.clientRegion = clientRegion;
        this.numRequestsAttempted = numRequestsAttempted;
        this.numRequestsSucceeded = numRequestsSucceeded;
        this.numRequestsFailedForServerLoad = numRequestsFailedForServerLoad;
        this.numRequestsFailedForNetworkLoad = numRequestsFailedForNetworkLoad;
        this.numRequestsSlowForServerLoad = numRequestsSlowForServerLoad;
        this.numRequestsSlowForNetworkLoad = numRequestsSlowForNetworkLoad;
        this.numRequestsSlowForNetworkAndServerLoad = numRequestsSlowForNetworkAndServerLoad;
        this.numRequestsServicedByRegion = numRequestsServicedByRegion;
        this.numRequestsFailedForDownNode = numRequestsFailedForDownNode;
    }

    /**
     * @return the name of the client
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * @return the region of the client
     */
    public RegionIdentifier getClientRegion() {
        return clientRegion;
    }

    /**
     * The number of client requests attempted is the sum of the
     * {@link ClientRequest#getNumClients()} property of the
     * {@link ClientRequest} objects that have been processed.
     * 
     * @return the number of client requests that were attempted
     */
    public int getNumRequestsAttempted() {
        return numRequestsAttempted;
    }

    /**
     * The number of client requests succeeded.
     * 
     * @return the number of client requests that succeeded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSucceeded() {
        return numRequestsSucceeded;
    }

    /**
     * @return the number of client requests that failed because the server was
     *         overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsFailedForServerLoad() {
        return numRequestsFailedForServerLoad;
    }

    /**
     * @return the number of client requests that failed because the network
     *         path was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsFailedForNetworkLoad() {
        return numRequestsFailedForNetworkLoad;
    }

    /**
     * @return the number of client requests that are slow because the server
     *         was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForServerLoad() {
        return numRequestsSlowForServerLoad;
    }

    /**
     * @return the number of client requests that were slow because the network
     *         path was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForNetworkLoad() {
        return numRequestsSlowForNetworkLoad;
    }

    /**
     * @return the number of client requests that were slow because both the
     *         network path and the server were overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForNetworkAndServerLoad() {
        return numRequestsSlowForNetworkAndServerLoad;
    }

    /**
     * The number of requests from this client that were serviced by each
     * region.
     * 
     * @return key is region, value is count. Unmodifiable map.
     */
    public Map<RegionIdentifier, Integer> getNumRequestsServicedByRegion() {
        return numRequestsServicedByRegion;
    }

    /**
     * 
     * @param region
     *            increment the number of requests serviced by the specified
     *            region
     */
    public void incrementRequestsServicedByRegion(final RegionIdentifier region) {
        numRequestsServicedByRegion.merge(region, 1, (v1, v2) -> v1 + v2);
        ++numRequestsSucceeded;
    }

    /**
     * @return the number of requests that failed due to the node being down
     */
    public int getNumRequestsFailedForDownNode() {
        return numRequestsFailedForDownNode;
    }

    /**
     * 
     * @param v
     *            see {@link #getNumRequestsFailedForDownNode()}
     */
    public void setNumRequestsFailedForDownNode(final int v) {
        numRequestsFailedForDownNode = v;
    }
}