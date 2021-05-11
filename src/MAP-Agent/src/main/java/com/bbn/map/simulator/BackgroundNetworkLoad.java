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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Represents a network request for a service that is not managed by MAP.
 *
 * @author jschewe
 *
 */
public class BackgroundNetworkLoad extends BaseNetworkLoad {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundNetworkLoad.class);

    private static final long serialVersionUID = 1L;

    /**
     * Used by JSON deserialization for construction.
     */
    public BackgroundNetworkLoad() {
    }

    /**
     * @param startTime
     *            see {@link #getStartTime()}
     * @param networkDuration
     *            see {@link #getNetworkDuration()}
     * @param networkLoad
     *            see {@link #getNetworkLoad()}
     * @param client
     *            see {@link #getClient()}
     * @param server
     *            see {@link #getServer()}
     */
    public BackgroundNetworkLoad(final String client,
            final String server,
            final long startTime,
            final long networkDuration,
            final ImmutableMap<LinkAttribute, Double> networkLoad) {
        super(startTime, networkDuration, ApplicationCoordinates.UNMANAGED, networkLoad);
        this.client = client;
        this.server = server;
    }

    private String client;

    /**
     * 
     * @param v
     *            see {@link #getClient()}
     */
    public void setClient(final String v) {
        client = v;
    }

    /**
     * 
     * @return the node at the client end of the network flow
     */
    public String getClient() {
        return client;
    }

    private String server;

    /**
     * 
     * @param v
     *            see {@link #getServer()}
     */
    public void setServer(final String v) {
        server = v;
    }

    /**
     * 
     * @return the node at the server end of the network flow
     */
    public String getServer() {
        return server;
    }

    private int rxPort = -1;

    /**
     * The port used in a real environment to simulate the receive portion of
     * the traffic.
     * 
     * @return a value less than zero is considered unset, default is unset
     */
    public int getRxPort() {
        return rxPort;
    }

    /**
     * 
     * @param v
     *            see {@link #getRxPort()}
     */
    public void setRxPort(final int v) {
        rxPort = v;
    }

    private int txPort = -1;

    /**
     * The port used in a real environment to simulate the send portion of the
     * traffic.
     * 
     * @return a value less than zero is considered unset, default is unset
     */
    public int getTxPort() {
        return txPort;
    }

    /**
     * 
     * @param v
     *            see {@link #getTxPort()}
     */
    public void setTxPort(final int v) {
        txPort = v;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" //
                + " client: " + getClient() //
                + " server: " + getServer() //
                + " start: " + getStartTime() //
                + " networkDuration: " + getNetworkDuration() //
                + " networkLoad: " + getNetworkLoad() //
                + " txPort: " + txPort //
                + " rxPort: " + rxPort //
                + " ]";
    }

    /**
     * Parse the background traffic requests in the specified file. If the file
     * doesn't exist an empty list is returned without error. If there is an
     * error reading the file, an error is logged and an empty list is returned.
     * 
     * @param clientDemandPath
     *            where to read from
     * @return requests sorted by start time
     */
    @Nonnull
    public static ImmutableList<BackgroundNetworkLoad> parseBackgroundTraffic(final Path clientDemandPath) {
        if (!Files.exists(clientDemandPath)) {
            return ImmutableList.of();
        }

        try {
            try (Reader reader = Files.newBufferedReader(clientDemandPath, StandardCharsets.UTF_8)) {

                final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();

                final List<BackgroundNetworkLoad> demand = mapper.readValue(reader,
                        new TypeReference<LinkedList<BackgroundNetworkLoad>>() {
                        });
                demand.sort((one, two) -> Long.compare(one.getStartTime(), two.getStartTime()));

                return ImmutableList.copyOf(demand);
            }
        } catch (final IOException e) {
            LOGGER.error("Error reading background traffic from " + clientDemandPath.toString() + ", ignoring file", e);
            return ImmutableList.of();
        }
    }
}