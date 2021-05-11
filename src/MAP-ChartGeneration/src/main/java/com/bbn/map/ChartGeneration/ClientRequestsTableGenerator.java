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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.simulator.ClientRequestRecord;
import com.bbn.map.simulator.ClientState;
import com.bbn.map.simulator.RequestResult;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

/**
 * Class for generating tables with information about the number of client requests that succeeded, failed, or are slow.
 * 
 * @author awald
 *
 */
public class ClientRequestsTableGenerator
{
    private static final Logger LOGGER = LogManager.getLogger(ClientRequestsTableGenerator.class);
    
    private static final String SCENARIO_SIMULATION_FOLDER_NAME = "simulation";
    
    private static final String REQUESTS_ATTEMPTED_LABEL = "attempted";
    private static final String REQUESTS_SUCCEEDED_LABEL = "succeeded";
    private static final String REQUESTS_FAILED_LABEL = "failed";
    private static final String REQUESTS_FAILED_FOR_SERVER_LABEL = "failed_for_server";
    private static final String REQUESTS_FAILED_FOR_NETWORK_LABEL = "failed_for_network";
    private static final String REQUESTS_SLOW_FOR_SERVER_LABEL = "slow_for_server";
    private static final String REQUESTS_SLOW_FOR_NETWORK_LABEL = "slow_for_network";
    
    private static final ObjectMapper JSON_MAPPER = JsonUtils.getStandardMapObjectMapper()
            .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE).disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    
    // time -> client pool -> requests results label -> value
    private Map<Long, Map<String, Map<String, Integer>>> requestsResultsData = new HashMap<>();
    
    // service -> time -> resquests results summary
    private Map<ServiceIdentifier<?>, Map<Long, RequestResultsSummary>> requestsResultsSummaryDataByService = new HashMap<>();
    private Map<ServiceIdentifier<?>, Map<Long, RequestResultsSummary>> binnedRequestsResultsSummaryDataByService = new HashMap<>();
    private Map<ServiceIdentifier<?>, Map<Long, RequestResultsSummary>> cumulativeRequestsResultsSummaryDataByService = new HashMap<>();
    private Map<ServiceIdentifier<?>, Map<Long, RequestResultsSummary>> binnedCumulativeRequestsResultsSummaryDataByService = new HashMap<>();
    private Set<NodeAttribute> attributes = new HashSet<>();

    
    private Set<ServiceIdentifier<?>> scenarioServices = null;

 
    /**
     * Processes data for a scenario to produce request results tables.
     * 
     * @param inputFolder
     *              the folder containing the results of the scenario simulation
     * @param outputFolder
     *              the folder to output the chart tables to
     * @param binSize
     *              the size of the bins to use for summaries of request counts and load results by service
     */
    public void processScenarioData(File inputFolder, File outputFolder, long binSize)
    {
        LOGGER.info("Data input folder: '{}'", inputFolder);
        
        if (!inputFolder.exists())
        {
            LOGGER.error("Input folder does not exist: {}", inputFolder);
            return;
        }
        
        
        File simulationFolder = new File(inputFolder + File.separator + SCENARIO_SIMULATION_FOLDER_NAME);
        
        if (simulationFolder.exists())
        {
            final File[] files = simulationFolder.listFiles();
            if (null == files) {
                LOGGER.error("Invalid path name {}, cannot process client request demand", simulationFolder);
                return;
            }

            for (File timeFolder : files)
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
                            ClientState clientRequestsResults = JSON_MAPPER.readValue(clientRequestsResultsFile, ClientState.class);
                            processClientRequestsResults(time, clientRequestsResults, requestsResultsData);                     
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
        
        
        // process individual requests in client folder
        try (DirectoryStream<Path> inputFolderStream = Files.newDirectoryStream(inputFolder.toPath())) {
            for (final Path nodeBaseFolder : inputFolderStream) {
                if (Files.isDirectory(nodeBaseFolder)) {
                    
                    LOGGER.debug("nodeBaseFolder = {}", nodeBaseFolder);
                    
                    File[] clientRequestsFiles = nodeBaseFolder.toFile().listFiles(new FilenameFilter()
                    {
                        @Override
                        public boolean accept(File dir, String name)
                        {
                            return (name.matches("client_requests_sent-.*\\.json"));
                        }
                    });
                    
                    if (clientRequestsFiles != null)
                    {
                        for (File file : clientRequestsFiles)
                        {
                            try(BufferedReader reader = Files.newBufferedReader(file.toPath())) {
                                final JsonFactory generator = JSON_MAPPER.getFactory();
                                final JsonParser parser = generator.createParser(reader);
                                
                                parser.nextToken();
                                while (parser.hasCurrentToken()) {
                                    final ClientRequestRecord record = parser.readValueAs(ClientRequestRecord.class);
                                    processClientRequestRecord(record, requestsResultsSummaryDataByService, attributes);
                                    
                                    parser.nextToken();
                                }
                                
                            } catch (IOException e)
                            {
                                LOGGER.error("Failed to parse client requests sent JSON file: {}", file);
                            }
                        }
                    }
                }
            }
        } catch (IOException e1)
        {
            LOGGER.error("Failed to find client requests sent files.");
            e1.printStackTrace();
        }
        
        if (scenarioServices != null)
        {
            scenarioServices.forEach((service) ->
            {
                requestsResultsSummaryDataByService.computeIfAbsent(service, k -> new HashMap<>());
            });
        }

        // bin request summaries
        requestsResultsSummaryDataByService.forEach((service, timeData) ->
        {
            Map<Long, RequestResultsSummary> binnedTimeData = binnedRequestsResultsSummaryDataByService.computeIfAbsent(service, k -> new HashMap<>());
            
            long maxBinTime = 0;
            
            for (Map.Entry<Long, RequestResultsSummary> entry : timeData.entrySet())
            {
                long binTime = ChartGenerationUtils.getBinForTime(entry.getKey(), 0, binSize);
                maxBinTime = Math.max(maxBinTime, binTime);
                binnedTimeData.merge(binTime, entry.getValue(), ClientRequestsTableGenerator::sumRequestResultsSummary);
            }   
           
            
            // add empty bins
            for (long time = 0; time <= maxBinTime; time += binSize)
            {
                binnedTimeData.merge(time, new RequestResultsSummary(), ClientRequestsTableGenerator::sumRequestResultsSummary);
            }
        });
        
        // accumulate request summaries
        accumulate(requestsResultsSummaryDataByService, cumulativeRequestsResultsSummaryDataByService);
        
        // accumulate binned request summaries
        accumulate(binnedRequestsResultsSummaryDataByService, binnedCumulativeRequestsResultsSummaryDataByService);
        
        
        
        List<String> table1Columns = new ArrayList<>();
        table1Columns.add(REQUESTS_SUCCEEDED_LABEL);
        table1Columns.add(REQUESTS_FAILED_LABEL);
        ChartGenerationUtils.outputDataToCSV2(outputFolder, table1Columns, "1", requestsResultsData);
        
        List<String> table23Columns = new ArrayList<>();
        table23Columns.add(REQUESTS_SUCCEEDED_LABEL);
        table23Columns.add(REQUESTS_FAILED_FOR_SERVER_LABEL);
        table23Columns.add(REQUESTS_FAILED_FOR_NETWORK_LABEL);
        ChartGenerationUtils.outputDataToCSV2(outputFolder, table23Columns, "2", requestsResultsData);
        
        table23Columns.add(REQUESTS_SLOW_FOR_SERVER_LABEL);
        table23Columns.add(REQUESTS_SLOW_FOR_NETWORK_LABEL);
        ChartGenerationUtils.outputDataToCSV2(outputFolder, table23Columns, "3", requestsResultsData);
        
        
        outputRequestsResultsDataByServiceToCSV(outputFolder, "", requestsResultsSummaryDataByService, attributes);
        outputRequestsResultsDataByServiceToCSV(outputFolder, "binned_", binnedRequestsResultsSummaryDataByService, attributes);
        outputRequestsResultsDataByServiceToCSV(outputFolder, "cumulative_", cumulativeRequestsResultsSummaryDataByService, attributes);
        outputRequestsResultsDataByServiceToCSV(outputFolder, "binned_cumulative_", binnedCumulativeRequestsResultsSummaryDataByService, attributes);
    }
    
    void accumulate(Map<ServiceIdentifier<?>, Map<Long, RequestResultsSummary>> input, Map<ServiceIdentifier<?>, Map<Long, RequestResultsSummary>> accumulation)
    {
        final RequestResultsSummary zeroRequestResultsSummary = new RequestResultsSummary();
        input.forEach((service, timeData) ->
        {
            Map<Long, RequestResultsSummary> cumulativeTimeData = accumulation.computeIfAbsent(service, k -> new HashMap<>());

            RequestResultsSummary sum = zeroRequestResultsSummary;
            List<Long> times = timeData.keySet().stream().sorted().collect(Collectors.toList());
            
            for (int t = 0; t < times.size(); t++)
            {
                Long time = times.get(t);
                sum = sumRequestResultsSummary(sum, timeData.get(time));
                cumulativeTimeData.put(time, sum);
            }
        });
    }
    
    /**
     * Provides a set of services to use in addition to the services discovered through requests results.
     * 
     * @param services
     *          the set of services in the scenario
     */
    public void setScenarioServices(Set<ServiceIdentifier<?>> services)
    {
        scenarioServices = services;
    }
    
    private void outputRequestsResultsDataByServiceToCSV(File outputFolder, String filenamePrefix, Map<ServiceIdentifier<?>, Map<Long, RequestResultsSummary>> requestsResultsSummaryDataByService, Set<NodeAttribute> attributes)
    {
        String[] header = {ChartGenerationUtils.CSV_TIME_COLUMN_LABEL, REQUESTS_ATTEMPTED_LABEL, REQUESTS_SUCCEEDED_LABEL, 
                REQUESTS_FAILED_FOR_SERVER_LABEL, REQUESTS_FAILED_FOR_NETWORK_LABEL};
        CSVFormat format = CSVFormat.EXCEL.withHeader(header);
        
        requestsResultsSummaryDataByService.forEach((service, timeRequestsData) ->
        {
            File countFile = new File(outputFolder + File.separator + filenamePrefix + "request_count-" + ChartGenerationUtils.serviceToFilenameString(service) + ".csv");
            
            List<Long> times = new LinkedList<>();
            for (long time : timeRequestsData.keySet())
                times.add(time);
            Collections.sort(times);
            
            
            try (CSVPrinter countPrinter = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(countFile), Charset.defaultCharset()), format))
            {
                for (long time : times)
                {
                    List<Object> countRow = new LinkedList<>();
                    countRow.add(time);
                    
                    RequestResultsSummary results = timeRequestsData.get(time);
                    
                    for (int c = 1; c < header.length; c++)
                    {
                        String columnLabel = header[c];
                        
                        switch (columnLabel)
                        {
                            case REQUESTS_ATTEMPTED_LABEL:
                                countRow.add(results.getRequestsAttempted());
                                break;
                            
                            case REQUESTS_SUCCEEDED_LABEL:
                                countRow.add(results.getRequestsSucceeded());
                                break;
                                
                            case REQUESTS_FAILED_FOR_SERVER_LABEL:
                                countRow.add(results.getRequestsFailedForServer());
                                break;
                                
                            case REQUESTS_FAILED_FOR_NETWORK_LABEL:
                                countRow.add(results.getRequestsFailedForNetwork());
                                break;
                                
                            default:
                                countRow.add(ChartGenerationUtils.EMPTY_CELL_VALUE);
                                break;
                        }
                        
                    }

                    countPrinter.printRecord(countRow);
                }

                LOGGER.info("Outputted data to file: {}", countFile);

            } catch (IOException e) {
                LOGGER.error("Error writing to CSV file:", e);
            }
            
            
            for (NodeAttribute attribute : attributes)
            {
                File loadFile = new File(outputFolder + File.separator + filenamePrefix + "request_load-" +
                        ChartGenerationUtils.serviceToFilenameString(service) + "-" + attribute.getName() + ".csv");
                
                try (CSVPrinter loadPrinter = new CSVPrinter(
                        new OutputStreamWriter(new FileOutputStream(loadFile), Charset.defaultCharset()), format))
                {
                    for (long time : times)
                    {                        
                        List<Object> loadRow = new LinkedList<>();
                        loadRow.add(time);
    
                        RequestResultsSummary results = timeRequestsData.get(time);
                        
                        for (int c = 1; c < header.length; c++)
                        {
                            String columnLabel = header[c];
                            
                            Object value = null;
                            
                            switch (columnLabel)
                            {
                                case REQUESTS_ATTEMPTED_LABEL:
                                    value = results.getLoadAttempted().get(attribute);
                                    break;
                                    
                                case REQUESTS_SUCCEEDED_LABEL:
                                    value = results.getLoadSucceeded().get(attribute);
                                    break;
                                    
                                case REQUESTS_FAILED_FOR_SERVER_LABEL:
                                    value = results.getLoadFailedForServer().get(attribute);
                                    break;
                                    
                                case REQUESTS_FAILED_FOR_NETWORK_LABEL:
                                    value = results.getLoadFailedForNetwork().get(attribute);
                                    break;
                                    
                                default:
                                    value = ChartGenerationUtils.EMPTY_CELL_VALUE;
                                    break;
                            }
                            
                            loadRow.add((value != null ? value : 0.0));
                        }
    
                        loadPrinter.printRecord(loadRow);
                    }
    
                    LOGGER.info("Outputted data to file: {}", loadFile);
    
                } catch (IOException e) {
                    LOGGER.error("Error writing to CSV file:", e);
                }
            }
        });
    }
    
    private void processClientRequestRecord(ClientRequestRecord record, Map<ServiceIdentifier<?>, Map<Long, RequestResultsSummary>> requestsResultsSummaryDataByService, Set<NodeAttribute> attributes)
    {
        RequestResult networkResult = record.getNetworkRequestResult();
        RequestResult serverResult = record.getServerResult();
        
        ServiceIdentifier<?> service = record.getRequest().getService();
        long startTime = record.getRequest().getStartTime();

        final ImmutableMap<NodeAttribute, Double> nodeLoad;
        if (record.getRequest() instanceof ClientLoad) {
            nodeLoad = ((ClientLoad) record.getRequest()).getNodeLoad();
        } else {
            // background traffic doesn't have node load
            nodeLoad = ImmutableMap.of();
        }
        attributes.addAll(nodeLoad.keySet());
        
        Map<Long, RequestResultsSummary> requestSummaryForService = requestsResultsSummaryDataByService.computeIfAbsent(service, k -> new HashMap<>());
        
        // increment requests attempted
        requestSummaryForService.merge(startTime, new RequestResultsSummary(
                nodeLoad, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                1, 0, 0, 0), ClientRequestsTableGenerator::sumRequestResultsSummary);
        
        if (!RequestResult.FAIL.equals(networkResult) && !RequestResult.FAIL.equals(serverResult))
        {
            // if request succeeded
            requestSummaryForService.merge(startTime, new RequestResultsSummary(
                    new HashMap<>(), nodeLoad, new HashMap<>(), new HashMap<>(),
                    0, 1, 0, 0), ClientRequestsTableGenerator::sumRequestResultsSummary);
        }
        else
        {
            // if request failed
            
            if (RequestResult.FAIL.equals(serverResult))
            {
                requestSummaryForService.merge(startTime, new RequestResultsSummary(
                        new HashMap<>(), new HashMap<>(), nodeLoad, new HashMap<>(),
                        0, 0, 1, 0), ClientRequestsTableGenerator::sumRequestResultsSummary);
            }
            
            if (RequestResult.FAIL.equals(networkResult))
            {
                requestSummaryForService.merge(startTime, new RequestResultsSummary(
                        new HashMap<>(), new HashMap<>(), new HashMap<>(), nodeLoad,
                        0, 0, 0, 1), ClientRequestsTableGenerator::sumRequestResultsSummary);
            }
        }
    }
    
    private void processClientRequestsResults(long time, ClientState clientRequestsResults, Map<Long, Map<String, Map<String, Integer>>> requestsResultsData)
    {
        String clientName = clientRequestsResults.getClientName();
        
        int succeeded = clientRequestsResults.getNumRequestsSucceeded();
        int failedForServer = clientRequestsResults.getNumRequestsFailedForServerLoad();
        int failedForNetwork = clientRequestsResults.getNumRequestsFailedForNetworkLoad();
        int failed = failedForNetwork + failedForServer;
        int slowForServer = clientRequestsResults.getNumRequestsSlowForServerLoad();
        int slowForNetwork = clientRequestsResults.getNumRequestsSlowForNetworkLoad();
        
        if (!requestsResultsData.containsKey(time))
            requestsResultsData.put(time, new HashMap<>());
        
        if (!requestsResultsData.get(time).containsKey(clientName))
            requestsResultsData.get(time).put(clientName, new HashMap<>());
        
        requestsResultsData.get(time).get(clientName).put(REQUESTS_SUCCEEDED_LABEL, succeeded);
        requestsResultsData.get(time).get(clientName).put(REQUESTS_FAILED_LABEL, failed);
        requestsResultsData.get(time).get(clientName).put(REQUESTS_FAILED_FOR_SERVER_LABEL, failedForServer);
        requestsResultsData.get(time).get(clientName).put(REQUESTS_FAILED_FOR_NETWORK_LABEL, failedForNetwork);
        requestsResultsData.get(time).get(clientName).put(REQUESTS_SLOW_FOR_SERVER_LABEL, slowForServer);
        requestsResultsData.get(time).get(clientName).put(REQUESTS_SLOW_FOR_NETWORK_LABEL, slowForNetwork);    
    }
    
    
    
    private final FileFilter clientRequestFileFilter = new FileFilter()
    {
        @Override
        public boolean accept(File pathname)
        {
            return pathname.getName().matches("client-.*\\.json");
        }
    };
            
            
    
    private static class RequestResultsSummary
    {
        private Map<NodeAttribute, Double> loadAttempted;
        private Map<NodeAttribute, Double> loadSucceeded;
        private Map<NodeAttribute, Double> loadFailedForServer;
        private Map<NodeAttribute, Double> loadFailedForNetwork;
        
        private int requestsAttempted;
        private int requestsSucceeded;
        private int requestsFailedForServer;
        private int requestsFailedForNetwork;
        
        RequestResultsSummary()
        {
            loadAttempted = new HashMap<>();
            loadSucceeded = new HashMap<>();
            loadFailedForServer = new HashMap<>();
            loadFailedForNetwork = new HashMap<>();
            
            requestsAttempted = 0;
            requestsSucceeded = 0;
            requestsFailedForServer = 0;
            requestsFailedForNetwork = 0;
        }
        
        RequestResultsSummary(RequestResultsSummary rrs)
        {
            this.loadAttempted = new HashMap<>();
            this.loadAttempted.putAll(rrs.loadAttempted);
            
            this.loadSucceeded = new HashMap<>();
            this.loadSucceeded.putAll(rrs.loadSucceeded);
            
            this.loadFailedForServer = new HashMap<>();
            this.loadFailedForServer.putAll(rrs.loadFailedForServer);
            
            this.loadFailedForNetwork = new HashMap<>();
            this.loadFailedForNetwork.putAll(rrs.loadFailedForNetwork);
            
            this.requestsAttempted = rrs.requestsAttempted;
            this.requestsSucceeded = rrs.requestsSucceeded;
            this.requestsFailedForServer = rrs.requestsFailedForNetwork;
            this.requestsFailedForNetwork = rrs.requestsFailedForNetwork;
        }
    
        RequestResultsSummary(final Map<NodeAttribute, Double> loadAttemped, 
                final Map<NodeAttribute, Double> loadSucceeded, 
                final Map<NodeAttribute, Double> loadFailedForServer, 
                final Map<NodeAttribute, Double> loadFailedForNetwork,
                final int requestsAttempted, final int requestsSucceeded,
                final int requestsFailedForServer, final int requestsFailedForNetwork)
        {
            this.loadAttempted = loadAttemped;
            this.loadSucceeded = loadSucceeded;
            this.loadFailedForServer = loadFailedForServer;
            this.loadFailedForNetwork = loadFailedForNetwork;
            
            this.requestsAttempted = requestsAttempted;
            this.requestsSucceeded = requestsSucceeded;
            this.requestsFailedForServer = requestsFailedForServer;
            this.requestsFailedForNetwork = requestsFailedForNetwork;
        }


        public Map<NodeAttribute, Double> getLoadAttempted()
        {
            return loadAttempted;
        }
        
        public Map<NodeAttribute, Double> getLoadSucceeded()
        {
            return loadSucceeded;
        }

        public Map<NodeAttribute, Double> getLoadFailedForServer()
        {
            return loadFailedForServer;
        }

        public Map<NodeAttribute, Double> getLoadFailedForNetwork()
        {
            return loadFailedForNetwork;
        }
        
        
        public int getRequestsAttempted()
        {
            return requestsAttempted;
        }

        public int getRequestsSucceeded()
        {
            return requestsSucceeded;
        }

        public int getRequestsFailedForServer()
        {
            return requestsFailedForServer;
        }

        public int getRequestsFailedForNetwork()
        {
            return requestsFailedForNetwork;
        }       
    }

    private static RequestResultsSummary sumRequestResultsSummary(RequestResultsSummary a, RequestResultsSummary b)
    {
        Map<NodeAttribute, Double> loadAttempted = new HashMap<>();
        Map<NodeAttribute, Double> loadSucceeded = new HashMap<>();
        Map<NodeAttribute, Double> loadFailedForServer = new HashMap<>();
        Map<NodeAttribute, Double> loadFailedForNetwork = new HashMap<>();
        
        a.loadAttempted.forEach((attr, value) ->
        {
            loadAttempted.merge(attr, value, Double::sum);
        });
        b.loadAttempted.forEach((attr, value) ->
        {
            loadAttempted.merge(attr, value, Double::sum);
        });
        
        a.loadSucceeded.forEach((attr, value) ->
        {
            loadSucceeded.merge(attr, value, Double::sum);
        });
        b.loadSucceeded.forEach((attr, value) ->
        {
            loadSucceeded.merge(attr, value, Double::sum);
        });
        
        a.loadFailedForServer.forEach((attr, value) ->
        {
            loadFailedForServer.merge(attr, value, Double::sum);
        });
        b.loadFailedForServer.forEach((attr, value) ->
        {
            loadFailedForServer.merge(attr, value, Double::sum);
        });
        
        a.loadFailedForNetwork.forEach((attr, value) ->
        {
            loadFailedForNetwork.merge(attr, value, Double::sum);
        });
        b.loadFailedForNetwork.forEach((attr, value) ->
        {
            loadFailedForNetwork.merge(attr, value, Double::sum);
        });
        
        
        return new RequestResultsSummary(loadAttempted, loadSucceeded, loadFailedForServer, loadFailedForNetwork, 
                a.requestsAttempted + b.requestsAttempted,
                a.requestsSucceeded + b.requestsSucceeded, 
                a.requestsFailedForServer + b.requestsFailedForServer,
                a.requestsFailedForNetwork + b.requestsFailedForNetwork);
    }      
}
