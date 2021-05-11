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

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A client request, or requests, for a service over some period of time. The
 * {@link #getNumClients()} property specifies how many clients are to be
 * simulated as executing at the same time.
 *
 */
public class ClientLoad extends BaseNetworkLoad {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLoad.class);

    /**
     * Used by JSON deserialization for construction.
     */
    public ClientLoad() {
    }

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
     * @param clientArguments
     *            {@Link #getClientArguments()}
     */
    public ClientLoad(final long startTime,
            final long serverDuration,
            final long networkDuration,
            final int numClients,
            final ApplicationCoordinates service,
            final ImmutableMap<NodeAttribute, Double> nodeLoad,
            final ImmutableMap<LinkAttribute, Double> networkLoad,
            final ImmutableList<String> clientArguments) {
        super(startTime, networkDuration, service, networkLoad);
        this.serverDuration = serverDuration;
        this.nodeLoad = nodeLoad;
        this.numClients = numClients;
        this.clientArguments = clientArguments;
    }

    private ImmutableList<String> clientArguments;

    /**
     * These arguments can be used by Java clients in the hifi testbed.
     * 
     * @return the client arguments
     */
    public ImmutableList<String> getClientArguments() {
        return clientArguments;
    }

    /**
     * 
     * @param v
     *            {@link #getClientArguments()}
     */
    public void setClientArguments(final ImmutableList<String> v) {
        this.clientArguments = v;
    }

    private long serverDuration;

    /**
     * 
     * @param v
     *            see {@link #getServerDuration()}
     */
    public void setServerDuration(final long v) {
        serverDuration = v;
    }

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

    private ImmutableMap<NodeAttribute, Double> nodeLoad;

    /**
     * 
     * @param v
     *            see {@Link #getNodeLoad()}
     */
    public void setNodeLoad(final ImmutableMap<NodeAttribute, Double> v) {
        nodeLoad = v;
    }

    /**
     * 
     * @return how much load is on the node
     */
    public ImmutableMap<NodeAttribute, Double> getNodeLoad() {
        return nodeLoad;
    }

    /**
     * This is computed from the {@link #getNodeLoad()} and defaults to 0 if the
     * {@link NodeAttribute#TASK_CONTAINERS} attribute is not present.
     * 
     * @return the number of standard sized containers that the request is using
     */
    @JsonIgnore
    public double getNumberOfContainers() {
        return getNodeLoad().getOrDefault(NodeAttribute.TASK_CONTAINERS, 0D);
    }

    private int numClients;

    /**
     * 
     * @param v
     *            see {@link #getNumClients()}
     */
    public void setNumClients(final int v) {
        numClients = v;
    }

    /**
     * The number of clients to simulate. This defaults to 1.
     * 
     * @return the number of clients to simulate
     */
    public int getNumClients() {
        return numClients;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" //
                + " start: " + getStartTime() //
                + " serverDuration: " + getServerDuration() //
                + " networkDuration: " + getNetworkDuration() //
                + " numClients: " + getNumClients() //
                + " service: " + getService() //
                + " nodeLoad: " + getNodeLoad() //
                + " networkLoad: " + getNetworkLoad() //
                + " ]";
    }

    /**
     * Parse the client load requests in the specified file. If the file doesn't
     * exist an empty list is returned without error. If there is an error
     * reading the file, an error is logged and an empty list is returned.
     * 
     * @param clientDemandPath
     *            where to read from
     * @return client demand sorted by start time
     */
    @Nonnull
    public static ImmutableList<ClientLoad> parseClientDemand(final Path clientDemandPath) {
        if (!Files.exists(clientDemandPath)) {
            return ImmutableList.of();
        }

        try {
            try (Reader reader = Files.newBufferedReader(clientDemandPath, StandardCharsets.UTF_8)) {

                final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();

                final List<ClientLoad> demand = mapper.readValue(reader, new TypeReference<LinkedList<ClientLoad>>() {
                });
                demand.sort((one, two) -> Long.compare(one.getStartTime(), two.getStartTime()));

                return ImmutableList.copyOf(demand);
            }
        } catch (final IOException e) {
            LOGGER.error(
                    "Error reading client demand from " + clientDemandPath.toString() + ", ignoring client demand file",
                    e);
            return ImmutableList.of();
        }
    }
}
