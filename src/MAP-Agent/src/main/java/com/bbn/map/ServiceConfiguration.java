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
package com.bbn.map;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Specifies the configuration of a service in a network.
 * 
 */
public class ServiceConfiguration implements Serializable {

    private static final long serialVersionUID = 1356276032114705011L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceConfiguration.class);

    /**
     * 
     * @param service
     *            see {@link #getService()}
     * @param hostname
     *            see {@link #getHostname()}
     * @param defaultNodes
     *            see {@link #getDefaultNodes()}
     * @param defaultNodeRegion
     *            see {@link #getDefaultNodeRegion()}
     * @param containerParameters
     *            see {@link #getContainerParameters()}
     * @param priority
     *            see {@link #getPriority()}
     * @param replicable
     *            see {@link #isReplicable()}
     * @param imageName
     *            see {@link #getImageName()}
     * @param serverPort
     *            see {@link #getServerPort()}
     */
    public ServiceConfiguration(@Nonnull final ApplicationCoordinates service,
            @Nonnull final String hostname,
            @Nonnull final ImmutableMap<NodeIdentifier, Integer> defaultNodes,
            @Nonnull final RegionIdentifier defaultNodeRegion,
            final ContainerParameters containerParameters,
            final int priority,
            final boolean replicable,
            final String imageName,
            final int serverPort) {
        this.service = service;
        this.hostname = hostname;
        this.defaultNodes = defaultNodes;
        this.defaultNodeRegion = defaultNodeRegion;
        this.containerParameters = containerParameters;
        this.priority = priority;
        this.replicable = replicable;
        this.imageName = imageName;
        this.serverPort = serverPort;
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

    private final ImmutableMap<NodeIdentifier, Integer> defaultNodes;

    /**
     * 
     * @return node to run on : number of instances
     */
    @Nonnull
    public ImmutableMap<NodeIdentifier, Integer> getDefaultNodes() {
        return defaultNodes;
    }

    private final RegionIdentifier defaultNodeRegion;

    /**
     * @return the region that {@link #getDefaultNode()} is in
     */
    @Nonnull
    public RegionIdentifier getDefaultNodeRegion() {
        return defaultNodeRegion;
    }

    private final ContainerParameters containerParameters;

    /**
     * 
     * @return the parameters for a container that will run this service
     */
    public ContainerParameters getContainerParameters() {
        return containerParameters;
    }

    private final int priority;

    /**
     * @return the priority for this service
     */
    public int getPriority() {
        return priority;
    }

    private final boolean replicable;

    /**
     * @return see {@link ApplicationSpecification#isReplicable()}
     */
    public boolean isReplicable() {
        return replicable;
    }

    private final String imageName;

    /**
     * @return see {@link ApplicationSpecification#getImageName()}
     */
    public String getImageName() {
        return imageName;
    }

    private final int serverPort;

    /**
     * @return see {@link ApplicationSpecification#getServerPort()}
     */
    public int getServerPort() {
        return serverPort;
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
        LOGGER.debug("Loading service configurations from {}", path);

        if (!Files.exists(path)) {
            // no configs
            LOGGER.debug("No service configurations at {}", path);
            return ImmutableMap.of();
        }

        final SimpleModule module = new SimpleModule();
        module.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                    BeanDescription beanDesc,
                    JsonDeserializer<?> deserializer) {
                if (beanDesc.getBeanClass() == ParsedServiceConfiguration.class)
                    return new DeserializeServiceConfig(deserializer);
                return deserializer;
            }
        });
        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper().registerModule(module)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            final ImmutableList<ParsedServiceConfiguration> list = mapper.readValue(reader,
                    new TypeReference<ImmutableList<ParsedServiceConfiguration>>() {
                    });

            final ImmutableMap.Builder<ApplicationCoordinates, ServiceConfiguration> map = ImmutableMap.builder();
            list.forEach(config -> {
                final ApplicationCoordinates service = config.getService();

                final ImmutableMap<NodeIdentifier, Integer> defaultNodes = ImmutableMap
                        .copyOf(config.getDefaultNodes().entrySet().stream().collect(
                                Collectors.toMap(e -> new DnsNameIdentifier(e.getKey()), Map.Entry::getValue)));

                final RegionIdentifier defaultNodeRegion = new StringRegionIdentifier(config.getDefaultNodeRegion());
                // map copies needed to get the generic types correct
                final ContainerParameters containerParams = new ContainerParameters(
                        ImmutableMap.copyOf(config.getComputeCapacity()),
                        ImmutableMap.copyOf(config.getNetworkCapacity()));

                LOGGER.debug("Loaded service {} with priority {}", service.getArtifact(), config.getPriority());

                final ServiceConfiguration sconfig = new ServiceConfiguration(service, config.getHostname(),
                        defaultNodes, defaultNodeRegion, containerParams, config.getPriority(), config.isReplicable(),
                        config.getImageName(), config.getServerPort());
                map.put(sconfig.getService(), sconfig);
            });

            return map.build();
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ParsedServiceConfiguration {

        private ApplicationCoordinates service;

        /**
         * 
         * @return the service identifier
         */
        @Nonnull
        public ApplicationCoordinates getService() {
            return service;
        }

        @SuppressWarnings("unused")
        private void setService(final ApplicationCoordinates v) {
            service = v;
        }

        private String hostname;

        /**
         * 
         * @return the hostname used to find instances of this service
         */
        @Nonnull
        public String getHostname() {
            return hostname;
        }

        @SuppressWarnings("unused")
        private void setHostname(final String v) {
            hostname = v;
        }

        private Map<String, Integer> defaultNodes = new HashMap<>();

        /**
         * 
         * @return node to run on : number of instances
         */
        @Nonnull
        public Map<String, Integer> getDefaultNodes() {
            return defaultNodes;
        }

        @SuppressWarnings("unused")
        private void setDefaultNodes(final Map<String, Integer> v) {
            if (null == v) {
                defaultNodes = new HashMap<>();
            } else {
                defaultNodes = v;
            }
        }

        private String defaultNodeRegion;

        /**
         * 
         * @return the region for {@link #getDefaultNode()}
         */
        @Nonnull
        public String getDefaultNodeRegion() {
            return defaultNodeRegion;
        }

        @SuppressWarnings("unused")
        private void setDefaultNodeRegion(final String v) {
            defaultNodeRegion = v;
        }

        private ImmutableMap<NodeAttribute, Double> computeCapacity = ImmutableMap.of();

        /**
         * 
         * @return the compute capacity for containers running this service
         */
        @Nonnull
        public ImmutableMap<NodeAttribute, Double> getComputeCapacity() {
            return computeCapacity;
        }

        @SuppressWarnings("unused")
        private void setComputeCapacity(final ImmutableMap<NodeAttribute, Double> v) {
            if (null == v) {
                computeCapacity = ImmutableMap.of();
            } else {
                computeCapacity = v;
            }
        }

        private ImmutableMap<LinkAttribute, Double> networkCapacity = ImmutableMap.of();

        /**
         * 
         * @return the network capacity for all links connected to containers
         *         running this service
         */
        @Nonnull
        public ImmutableMap<LinkAttribute, Double> getNetworkCapacity() {
            return networkCapacity;
        }

        @SuppressWarnings("unused")
        private void setNetworkCapacity(final ImmutableMap<LinkAttribute, Double> v) {
            if (null == v) {
                networkCapacity = ImmutableMap.of();
            } else {
                networkCapacity = v;
            }
        }

        private int priority = ApplicationSpecification.DEFAULT_PRIORITY;

        /**
         * @return the priority for this service
         */
        public int getPriority() {
            return priority;
        }

        @SuppressWarnings("unused")
        private void setPriority(final int v) {
            priority = v;
        }

        private boolean replicable = true;

        /**
         * @return see {@link ApplicationSpecification#isReplicable()}
         */
        @SuppressWarnings("unused")
        public boolean isReplicable() {
            return replicable;
        }

        /**
         * 
         * @param v
         *            see {@link #isReplicable()}
         */
        @SuppressWarnings("unused")
        public void setReplicable(final boolean v) {
            this.replicable = v;
        }

        private String imageName = null;

        /**
         * @return see {@link ApplicationSpecification#getImageName()}
         */
        @SuppressWarnings("unused")
        public String getImageName() {
            return imageName;
        }

        /**
         * 
         * @param v
         *            see {@link #getImageName()}
         */
        @SuppressWarnings("unused")
        public void setImageName(final String v) {
            this.imageName = v;
        }

        private int serverPort = 0;

        /**
         * 
         * @return the port that the server runs on
         */
        @SuppressWarnings("unused")
        public int getServerPort() {
            return serverPort;
        }

        /**
         * 
         * @param v
         *            see {@link #getServerPort()}
         */
        @SuppressWarnings("unused")
        public void setServerPort(final int v) {
            this.serverPort = v;
        }
    }

    private static final class DeserializeServiceConfig extends StdDeserializer<ParsedServiceConfiguration>
            implements ResolvableDeserializer {

        private static final long serialVersionUID = 1L;

        private final JsonDeserializer<?> delegate;

        DeserializeServiceConfig(final JsonDeserializer<?> delegate) {
            super(ParsedServiceConfiguration.class);
            this.delegate = delegate;
        }

        @Override
        public ParsedServiceConfiguration deserialize(final JsonParser parser, final DeserializationContext ctx)
                throws IOException, JsonProcessingException {

            final ObjectCodec codec = parser.getCodec();
            final JsonNode node = codec.readTree(parser);

            final JsonParser secondParser = codec.treeAsTokens(node);
            // need to call next token since delegate believes it's in the
            // middle of parsing. This logic comes from how
            // ObjectMapper.readValue() initializes the parser for reading.
            JsonToken t = secondParser.getCurrentToken();
            if (t == null) {
                // and then we must get something...
                t = secondParser.nextToken();
                if (t == null) {
                    // Throw mapping exception, since it's failure to map,
                    // not an actual parsing problem
                    throw JsonMappingException.from(secondParser, "No content to map due to end-of-input");
                }
            }
            if (t == JsonToken.VALUE_NULL) {
                return null;
            } else if (t == JsonToken.END_ARRAY || t == JsonToken.END_OBJECT) {
                return null;
            }

            final ParsedServiceConfiguration parsed = (ParsedServiceConfiguration) delegate.deserialize(secondParser,
                    ctx);

            if (node.has("defaultNode")) {
                // old code, need to add in the extra fields
                final String defaultNode = node.get("defaultNode").asText();
                final int instances = node.get("initialInstances").asInt();

                parsed.getDefaultNodes().put(defaultNode, instances);
            }

            return parsed;
        }

        // for some reason you have to implement ResolvableDeserializer when
        // modifying BeanDeserializer
        // otherwise deserializing throws JsonMappingException??
        @Override
        public void resolve(DeserializationContext ctxt) throws JsonMappingException {
            ((ResolvableDeserializer) delegate).resolve(ctxt);
        }
    }

}
