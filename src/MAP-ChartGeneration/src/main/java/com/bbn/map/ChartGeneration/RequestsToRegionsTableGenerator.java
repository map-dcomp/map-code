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
package com.bbn.map.ChartGeneration;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.simulator.ClientState;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NetworkServerProperties;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Creates a CSV file showing the number of requests sent to each region over time for each client pool.
 * 
 * @author awald
 *
 */
public class RequestsToRegionsTableGenerator
{
    private static final Logger LOGGER = LogManager.getLogger(RequestsToRegionsTableGenerator.class);

    private final ObjectMapper mapper;
    private List<String> regions = new ArrayList<>();
    
    
    /**
     * Constructs an instance with the modules necessary for JSON deserialization added to the ObjectMapper.
     */
    public RequestsToRegionsTableGenerator()
    {        
        mapper = JsonUtils.getStandardMapObjectMapper();
       
        //TODO: Eliminate the need for this later
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    
    /**
     * Processes data for a scenario to produce request results tables.
     * 
     * @param scenarioFolder
     *              the folder containing the scenario configuration
     * @param inputFolder
     *              the folder containing the results of the scenario simulation
     * @param outputFolder
     *              the folder to output the chart tables to
     */
    public void processScenarioData(File scenarioFolder, File inputFolder, File outputFolder)
    {
        LOGGER.info("Data input folder: '{}'", inputFolder);

        if (!scenarioFolder.exists())
        {
            LOGGER.error("Scenario folder does not exist: {}", scenarioFolder);
            return;
        }
        
        if (!inputFolder.exists())
        {
            LOGGER.error("Input folder does not exist: {}", inputFolder);
            return;
        }
        
        File simulationFolder = new File(inputFolder + File.separator + ChartGenerationUtils.SCENARIO_SIMULATION_FOLDER_NAME);

        
        // time -> client pool -> region -> requests
        Map<Long, Map<String, Map<String, Integer>>> requestsToRegions = new HashMap<>();
        
        
        processScenarioConfiguration(scenarioFolder.getName(), scenarioFolder.toPath());
        
        if (simulationFolder.exists())
        {
            final File[] timeFolders = simulationFolder.listFiles();
            if(null == timeFolders) {
                LOGGER.error("{} is not a directory, cannot process", simulationFolder);
                return;
            }
            for (File timeFolder : timeFolders)
            {
                if (timeFolder.isDirectory())
                {
                    long time = Long.parseLong(timeFolder.getName());
                    
                    final File[] clientFiles = timeFolder.listFiles(clientRequestFileFilter);
                    if(null == clientFiles) {
                        LOGGER.error("{} is not a directory, skipping", timeFolder);
                        continue;
                    }
                    for (File clientRequestsResultsFile : clientFiles)
                    {
                        try
                        {
                            ClientState clientRequestsResults = mapper.readValue(clientRequestsResultsFile, ClientState.class);
                            processClientRequestsByRegionResults(time, clientRequestsResults, requestsToRegions);             
                        } catch (IOException e)
                        {
                            LOGGER.error("{}", e);
                        }
                    }
                }
            }
        }
        else
        {
            LOGGER.error("Input simulation folder does not exist: {}", simulationFolder);            
        }
        
        ChartGenerationUtils.outputDataToCSV2(outputFolder, regions, "requests_to_regions", requestsToRegions);
    }
    
    
    private void processScenarioConfiguration(String scenarioName, Path scenarioFolder)
    {
        LOGGER.info("Process scenario configuration for '{}' in folder '{}'.", scenarioName, scenarioFolder);
        
        Topology topology;
        try
        {
            topology = NS2Parser.parse(scenarioName, scenarioFolder);
            
            topology.getNodes().forEach((name, node) ->
            {
                String regionName = NetworkServerProperties.parseRegionName(node.getExtraData());
                
                if (!regions.contains(regionName))
                    regions.add(regionName);
            });
        } catch (IOException e)
        {
            LOGGER.error("Unable to parse ns2 file: {}", e);
            e.printStackTrace();
        }
    }
    
    
    private void processClientRequestsByRegionResults(long time, ClientState clientRequestsResults, Map<Long, Map<String, Map<String, Integer>>> regionRequestsResultsData)
    {
        String clientName = clientRequestsResults.getClientName();
        
        if (!regionRequestsResultsData.containsKey(time))
            regionRequestsResultsData.put(time, new HashMap<>());
        
        if (!regionRequestsResultsData.get(time).containsKey(clientName))
            regionRequestsResultsData.get(time).put(clientName, new HashMap<>());
        
        clientRequestsResults.getNumRequestsServicedByRegion().forEach((region, requests) ->
        {            
            regionRequestsResultsData.get(time).get(clientName).put(region.getName(), requests);
        });
    }
    
    
    private final FileFilter clientRequestFileFilter = new FileFilter()
    {
        @Override
        public boolean accept(File pathname)
        {
            return pathname.getName().matches("client-.*\\.json");
        }
    };
}
