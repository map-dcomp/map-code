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
package com.bbn.map;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nonnull;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Specifies the configuration of a service in a network.
 * 
 */
public class ServiceConfiguration implements Serializable {

    private static final long serialVersionUID = 1356276032114705011L;

    /**
     * 
     * @param service
     *            see {@link #getService()}
     * @param hostname
     *            see {@link #getHostname()}
     * @param defaultNode
     *            see {@link #getDefaultNode()}
     * @param initialInstances
     *            see {@link #getInitialInstances()}
     * @param defaultNodeRegion
     *            see {@link #getDefaultNodeRegion()}
     * @param containerParameters
     *            see {@link #getContainerParameters()}
     */
    public ServiceConfiguration(@Nonnull final ApplicationCoordinates service,
            @Nonnull final String hostname,
            @Nonnull final NodeIdentifier defaultNode,
            @Nonnull final RegionIdentifier defaultNodeRegion,
            final int initialInstances,
            final ContainerParameters containerParameters) {
        this.service = service;
        this.hostname = hostname;
        this.defaultNode = defaultNode;
        this.defaultNodeRegion = defaultNodeRegion;
        this.initialInstances = initialInstances;
        this.containerParameters = containerParameters;
    }

    private final ApplicationCoordinates service;

    /**
     * 
     * @return the service identifier
     */
    @Nonnull
    public ApplicationCoordinates getService() {
        return service;
    }

    private final String hostname;

    /**
     * 
     * @return the hostname used to find instances of this service
     */
    @Nonnull
    public String getHostname() {
        return hostname;
    }

    private final NodeIdentifier defaultNode;

    /**
     * 
     * @return the node that this service runs on by default
     */
    @Nonnull
    public NodeIdentifier getDefaultNode() {
        return defaultNode;
    }

    private final RegionIdentifier defaultNodeRegion;

    /**
     * @return the region that {@link #getDefaultNode()} is in
     */
    @Nonnull
    public RegionIdentifier getDefaultNodeRegion() {
        return defaultNodeRegion;
    }

    private final int initialInstances;

    /**
     * The number of instances of this service to be started on
     * {@link #getDefaultNode()} when the system starts up.
     * 
     * @return the number of instances
     */
    public int getInitialInstances() {
        return initialInstances;
    }

    private final ContainerParameters containerParameters;

    /**
     * 
     * @return the parameters for a container that will run this service
     */
    public ContainerParameters getContainerParameters() {
        return containerParameters;
    }
    

    /**
     * Read in the service configurations and populate the application manager
     * with the information about those services.
     * 
     * @param path
     *            the path to the file to read
     * @return the data read from the file, empty of the file doesn't exist
     * @throws IOException
     *             when there is an error reading the file
     */
    public static ImmutableMap<ApplicationCoordinates, ServiceConfiguration> parseServiceConfigurations(
            @Nonnull final Path path) throws IOException {
        if (!Files.exists(path)) {
            // no hardware configs
            return ImmutableMap.of();
        }

        final ObjectMapper mapper = new ObjectMapper().registerModule(new GuavaModule());

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            final ImmutableList<ParsedServiceConfiguration> list = mapper.readValue(reader,
                    new TypeReference<ImmutableList<ParsedServiceConfiguration>>() {
                    });

            final ImmutableMap.Builder<ApplicationCoordinates, ServiceConfiguration> map = ImmutableMap.builder();
            list.forEach(config -> {
                final ApplicationCoordinates service = config.getService();

                final NodeIdentifier defaultNode = new DnsNameIdentifier(config.getDefaultNode());
                final RegionIdentifier defaultNodeRegion = new StringRegionIdentifier(config.getDefaultNodeRegion());
                final ContainerParameters containerParams = new ContainerParameters(
                        ImmutableMap.copyOf(config.getComputeCapacity()),
                        ImmutableMap.copyOf(config.getNetworkCapacity()));

                final ServiceConfiguration sconfig = new ServiceConfiguration(service, config.getHostname(),
                        defaultNode, defaultNodeRegion, config.getInitialInstances(), containerParams);
                map.put(sconfig.getService(), sconfig);
            });

            return map.build();
        }

    }


    private static final class ParsedServiceConfiguration {

        /**
         * 
         * @param service
         *            see {@link #getService()}
         * @param hostname
         *            see {@link #getHostname()}
         * @param defaultNode
         *            see {@link #getDefaultNode()}
         * @param initialInstances
         *            see {@link #getInitialInstances()}
         * @param computeCapacity
         *            see {@link #getComputeCapacity()}
         * @param networkCapacity
         *            see {@link #getNetworkCapacity()}
         * @param defaultNodeRegion
         *            see {@link #getDefaultNodeRegion()}
         */
        @SuppressWarnings("unused") // called from JSON parser
        ParsedServiceConfiguration(@JsonProperty("service") @Nonnull final ApplicationCoordinates service,
                @JsonProperty("hostname") @Nonnull final String hostname,
                @JsonProperty("defaultNode") @Nonnull final String defaultNode,
                @JsonProperty("defaultNodeRegion") @Nonnull final String defaultNodeRegion,
                @JsonProperty("initialInstances") final int initialInstances,
                @JsonProperty("computeCapacity") final ImmutableMap<NodeMetricName, Double> computeCapacity,
                @JsonProperty("networkCapacity") final ImmutableMap<LinkMetricName, Double> networkCapacity) {
            this.service = service;
            this.hostname = hostname;
            this.defaultNode = defaultNode;
            this.defaultNodeRegion = defaultNodeRegion;
            this.initialInstances = initialInstances;
            this.computeCapacity = computeCapacity;
            this.networkCapacity = networkCapacity;
        }

        private final ApplicationCoordinates service;

        /**
         * 
         * @return the service identifier
         */
        @Nonnull
        public ApplicationCoordinates getService() {
            return service;
        }

        private final String hostname;

        /**
         * 
         * @return the hostname used to find instances of this service
         */
        @Nonnull
        public String getHostname() {
            return hostname;
        }

        private final String defaultNode;

        /**
         * 
         * @return the node that this service runs on by default
         */
        @Nonnull
        public String getDefaultNode() {
            return defaultNode;
        }

        private final String defaultNodeRegion;

        /**
         * 
         * @return the region for {@link #getDefaultNode()}
         */
        @Nonnull
        public String getDefaultNodeRegion() {
            return defaultNodeRegion;
        }

        private final int initialInstances;

        /**
         * 
         * @return the initial number of instances of the service to run on the
         *         default node
         * @see #getDefaultNode()
         */
        public int getInitialInstances() {
            return initialInstances;
        }

        private final ImmutableMap<NodeMetricName, Double> computeCapacity;

        /**
         * 
         * @return the compute capacity for containers running this service
         */
        @Nonnull
        public ImmutableMap<NodeMetricName, Double> getComputeCapacity() {
            return computeCapacity;
        }

        private final ImmutableMap<LinkMetricName, Double> networkCapacity;

        /**
         * 
         * @return the network capacity for all links connected to containers
         *         running this service
         */
        @Nonnull
        public ImmutableMap<LinkMetricName, Double> getNetworkCapacity() {
            return networkCapacity;
        }
    }

}
