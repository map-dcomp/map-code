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

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A client request, or requests, for a service over some period of time. The
 * {@link #getNumClients()} property specifies how many clients are to be
 * simulated as executing at the same time.
 *
 */
public class ClientLoad implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLoad.class);

    private static final long serialVersionUID = 1L;

    private static final LinkMetricName OLD_DATARATE = new LinkMetricName("DATARATE");

    /**
     * Take a network capacity map and make sure that if the old datarate is
     * there convert it to rx and tx. Don't overwrite rx and tx values if they
     * are present.
     * 
     * @param networkLoad
     *            the value to be migrated
     * @return the new value with {@link LinkMetricName#DATARATE_RX} and
     *         {@link LinkMetricName#DATARATE_TX} and without DATARATE. May be
     *         the same as the original value.
     */
    public static ImmutableMap<LinkMetricName, Double> migrateDatarate(
            final ImmutableMap<LinkMetricName, Double> networkLoad) {
        if (networkLoad.containsKey(OLD_DATARATE)) {
            final double datarate = networkLoad.get(OLD_DATARATE);
            final Map<LinkMetricName, Double> newMap = new HashMap<>(networkLoad);

            if (!newMap.containsKey(LinkMetricName.DATARATE_RX)) {
                newMap.put(LinkMetricName.DATARATE_RX, datarate);
            }

            if (!newMap.containsKey(LinkMetricName.DATARATE_TX)) {
                newMap.put(LinkMetricName.DATARATE_TX, datarate);
            }

            newMap.remove(OLD_DATARATE);

            return ImmutableMap.copyOf(newMap);
        } else {
            return networkLoad;
        }
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
     */
    public ClientLoad(@JsonProperty("startTime") final long startTime,
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
        this.networkLoad = migrateDatarate(networkLoad);
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

    private final ImmutableMap<LinkMetricName, Double> networkLoad;

    /**
     * 
     * @return how much load there is on the network
     */
    public ImmutableMap<LinkMetricName, Double> getNetworkLoad() {
        return networkLoad;
    }

    private transient ImmutableMap<LinkAttribute<?>, Double> netLoadAsAttribute = null;

    /**
     * This is cached so subsequent calls are fast.
     * 
     * @return the network load as {@link LinkAttribute}
     * @see #getNetworkLoad()
     */
    @JsonIgnore
    public ImmutableMap<LinkAttribute<?>, Double> getNetworkLoadAsAttribute() {
        if (null == netLoadAsAttribute) {
            final ImmutableMap.Builder<LinkAttribute<?>, Double> networkLoad = ImmutableMap.builder();

            getNetworkLoad().forEach((attr, value) -> {
                networkLoad.put(attr, value);
            });

            netLoadAsAttribute = networkLoad.build();
        }
        return netLoadAsAttribute;
    }

    private transient ImmutableMap<LinkAttribute<?>, Double> netLoadAsAttributeFlipped = null;

    /**
     * This is cached so subsequent calls are fast.
     * 
     * @return the network load as {@link LinkAttribute} with TX and RX flipped
     * @see #getNetworkLoad()
     */
    @JsonIgnore
    public ImmutableMap<LinkAttribute<?>, Double> getNetworkLoadAsAttributeFlipped() {
        if (null == netLoadAsAttributeFlipped) {
            final ImmutableMap.Builder<LinkAttribute<?>, Double> networkLoad = ImmutableMap.builder();

            getNetworkLoad().forEach((attr, value) -> {
                final LinkMetricName toInsert;
                if (LinkMetricName.DATARATE_RX.equals(attr)) {
                    toInsert = LinkMetricName.DATARATE_TX;
                } else if (LinkMetricName.DATARATE_TX.equals(attr)) {
                    toInsert = LinkMetricName.DATARATE_RX;
                } else {
                    toInsert = attr;
                }
                networkLoad.put(toInsert, value);
            });

            netLoadAsAttributeFlipped = networkLoad.build();
        }
        return netLoadAsAttributeFlipped;
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

                ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper().registerModule(new GuavaModule());

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
