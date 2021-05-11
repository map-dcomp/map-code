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
package com.bbn.map.demand_template;

import java.io.File;
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
import java.util.Stack;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * A template containing the references to source information for generating
 * {@link ClientLoad} objects.
 */
public class ClientLoadTemplate implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLoadTemplate.class);

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
    public ClientLoadTemplate(@JsonProperty("startTime") final String startTime,
            @JsonProperty("serverDuration") final String serverDuration,
            @JsonProperty("networkDuration") final String networkDuration,
            @JsonProperty("numClients") final String numClients,
            @JsonProperty("service") final ApplicationCoordinates service,
            @JsonProperty("nodeLoad") final ImmutableMap<NodeAttribute, String> nodeLoad,
            @JsonProperty("networkLoad") final ImmutableMap<LinkAttribute, String> networkLoad) {
        this.startTime = startTime;
        this.serverDuration = serverDuration;
        this.networkDuration = networkDuration;
        this.numClients = numClients;
        this.service = service;
        this.nodeLoad = nodeLoad;
        this.networkLoad = networkLoad;
    }

    private final String startTime;

    /**
     * @return when the request starts
     */
    public String getStartTime() {
        return startTime;
    }

    private final String serverDuration;

    /**
     * This duration is the minimum duration for the request to effect the
     * server. Depending on how busy the server is the actual server processing
     * time may be longer.
     * 
     * @return how long the request is active for
     */
    public String getServerDuration() {
        return serverDuration;
    }

    private final String networkDuration;

    /**
     * This duration is the minimum duration for the request to effect the
     * network. Depending on how busy the network is the actual network
     * processing time may be longer.
     * 
     * @return how long the request is active for
     */
    public String getNetworkDuration() {
        return networkDuration;
    }

    private final String numClients;

    /**
     * The number of clients to simulate. This defaults to 1.
     * 
     * @return the number of clients to simulate
     */
    public String getNumClients() {
        return numClients;
    }

    private final ApplicationCoordinates service;

    /**
     * @return which service is being used
     */
    public ApplicationCoordinates getService() {
        return service;
    }

    private final ImmutableMap<NodeAttribute, String> nodeLoad;

    /**
     * 
     * @return how much load is on the node
     */
    public ImmutableMap<NodeAttribute, String> getNodeLoad() {
        return nodeLoad;
    }

    private final ImmutableMap<LinkAttribute, String> networkLoad;

    /**
     * 
     * @return how much load there is on the network
     */
    public ImmutableMap<LinkAttribute, String> getNetworkLoad() {
        return networkLoad;
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
     * Reads in a folder structure of demand templates.
     * 
     * @param clientDemandTemplatesPath
     *          the path of the folder containing the sub folders and demand templates
     * @return a Map from relative path client node to set of templates
     */
    public static Map<String, Map<NodeIdentifier, ImmutableSet<ClientLoadTemplate>>> parseAllClientDemandTemplates(
            final Path clientDemandTemplatesPath)
    {
        Map<String, Map<NodeIdentifier, ImmutableSet<ClientLoadTemplate>>> allDemandTemplates = new HashMap<>();
        
        Stack<Path> pathStack = new Stack<>();
        
        pathStack.push(clientDemandTemplatesPath);
        
        while (!pathStack.isEmpty())
        {
            Path path = pathStack.pop();
            File file = path.toFile();
            
            if (file.isDirectory())
            {
                File[] subFiles = file.listFiles();
                
                if (subFiles != null)
                {
                    for (File subFile : subFiles)
                    {
                        pathStack.push(subFile.toPath());                    
                    }
                }
            }
            else if (file.isFile() && file.getName().endsWith(".json"))
            {
                ImmutableSet<ClientLoadTemplate> templates = null;
                
                try
                {
                    templates = parseClientDemandTemplates(file.toPath());
                }
                catch (Exception e)
                {
//                    LOGGER.error("Failed to parse demand template file '{}':", file.getAbsolutePath(), e);
                }
                
                NodeIdentifier nodeId = new DnsNameIdentifier(file.getName().replace(".json", ""));
                
                String relativePath = file.getParent().replace(clientDemandTemplatesPath.toAbsolutePath().toString(), "");
                
                allDemandTemplates
                        .computeIfAbsent(relativePath, k -> new HashMap<>())
                        .put(nodeId, templates);
            }
        }
        
        return allDemandTemplates;
    }


    /**
     * Parses the client load request templates in the specified file. If the file doesn't
     * exist an empty list is returned without error. If there is an error
     * reading the file, an error is logged and an empty list is returned.
     * 
     * @param clientDemandTemplatePath
     *            where to read from
     * @return client demand templates
     */
    @Nonnull
    public static ImmutableSet<ClientLoadTemplate> parseClientDemandTemplates(final Path clientDemandTemplatePath) {
        if (!Files.exists(clientDemandTemplatePath)) {
            return ImmutableSet.of();
        }

        try {
            try (Reader reader = Files.newBufferedReader(clientDemandTemplatePath, StandardCharsets.UTF_8)) {

                ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper().registerModule(new GuavaModule());

                final List<ClientLoadTemplate> demand = mapper.readValue(reader, new TypeReference<LinkedList<ClientLoadTemplate>>() {
                });
//                demand.sort((one, two) -> Long.compare(one.getStartTime(), two.getStartTime()));

                return ImmutableSet.copyOf(demand);
            }
        } catch (final IOException e) {
            LOGGER.error(
                    "Error reading client demand from " + clientDemandTemplatePath.toString() + ", ignoring client demand template file",
                    e);
            return ImmutableSet.of();
        }
    }
}
