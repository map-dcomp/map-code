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

import java.util.Map;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stores state information for client simulation.
 * 
 * @author awald
 *
 */
public class ClientState extends BaseClientState {
    private final String clientName;
    private final RegionIdentifier clientRegion;

    /* package */ ClientState(final ClientSim sim) {
        clientName = sim.getSimName();
        clientRegion = sim.getClientRegion();
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
        super(numRequestsAttempted, numRequestsSucceeded, numRequestsFailedForServerLoad,
                numRequestsFailedForNetworkLoad, numRequestsSlowForServerLoad, numRequestsSlowForNetworkLoad,
                numRequestsSlowForNetworkAndServerLoad, numRequestsServicedByRegion, numRequestsFailedForDownNode);
        this.clientName = clientName;
        this.clientRegion = clientRegion;
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

}