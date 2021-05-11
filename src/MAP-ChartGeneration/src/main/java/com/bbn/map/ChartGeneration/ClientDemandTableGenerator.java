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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Class for converting client pool demand json files to a table format.
 * 
 * @author awald
 *
 */
public class ClientDemandTableGenerator {
    private static final Logger LOGGER = LogManager.getLogger(ClientDemandTableGenerator.class);

    private final ObjectMapper mapper;
    private static final String JSON_FILE_SUFFIX = ".json";

    private Set<ServiceIdentifier<?>> scenarioServices = null;

    private Set<NodeAttribute> attributes = new HashSet<>();

    /**
     * Constructs object for converting client pool demand to tables.
     */
    public ClientDemandTableGenerator() {
        mapper = JsonUtils.getStandardMapObjectMapper();
    }

    /**
     * Processes data for a scenario to produce projected client pool demand
     * tables.
     *
     * @param demandScenarioInputFolder
     *            the folder containing the configuration information for a
     *            demand scenario
     * @param demandTableOutputFolder
     *            the folder to output the resulting tables to
     * @param startAtZero
     *            if true, the accumulation of load will start at 0 rather than
     *            at the first found load value in time
     * @param dataSampleInterval
     *            the amount of time between consecutive result updates in the
     *            simulation of the scenario
     */
    @SuppressFBWarnings(value = "UC_USELESS_OBJECT", justification = "Findbugs appears to have a bug thinking that cumulativeLoad isn't both read and written")
    public void processClientRequestDemandFiles(File demandScenarioInputFolder,
            File demandTableOutputFolder,
            boolean startAtZero,
            long dataSampleInterval) {
        LOGGER.info("Demand scenario folder: '{}'", demandScenarioInputFolder);
        LOGGER.info("Output folder: '{}'", demandTableOutputFolder);
        LOGGER.info("Data sample interval: {} ms", dataSampleInterval);

        if (!demandScenarioInputFolder.exists()) {
            LOGGER.error("Demand scenario input folder does not exist: {}", demandScenarioInputFolder);
            return;
        }

        File[] jsonFiles = demandScenarioInputFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(JSON_FILE_SUFFIX);
            }
        });
        if (null == jsonFiles) {
            LOGGER.error("Invalid path name {}, cannot process client request demand", demandScenarioInputFolder);
            return;
        }

        // client name -> time -> service name -> demand attribute -> value
        final Map<String, Map<Long, Map<String, Map<NodeAttribute, Double>>>> cumulativeLoad = new HashMap<>();

        // Set for storing all discovered services when reading input client
        // request files before outputting any files
        final Set<ServiceIdentifier<?>> services = new HashSet<>();
        if (scenarioServices != null) {
            services.addAll(scenarioServices);
        }
        LOGGER.debug("services: {}", services);

        for (File clientRequestFile : jsonFiles) {
            if (Simulation.BACKGROUND_TRAFFIC_FILENAME.equals(clientRequestFile.getName())) {
                // don't parse the background traffic file as a client demand
                // file
                continue;
            }

            try {
                String clientName = clientRequestFile.getName().replace(".json", "");
                ClientLoad[] clientRequests = mapper.readValue(clientRequestFile, ClientLoad[].class);

                // read in data from the requests and store as changes in load
                final Map<Long, Map<String, Map<NodeAttribute, Double>>> changeInLoadForClient = new HashMap<>();

                for (ClientLoad cr : clientRequests) {
                    services.add(cr.getService());
                    processClientRequest(cr, changeInLoadForClient, dataSampleInterval);
                }

                // sum together the changes in load to obtain the current total
                // load at each point in time that load changes
                final Map<Long, Map<String, Map<NodeAttribute, Double>>> cumulativeLoadForClient = accumulateChangeInLoad(
                        changeInLoadForClient, startAtZero);

                if (dataSampleInterval > 0) {
                    // sample cumulative load before storing it
                    final Map<Long, Map<String, Map<NodeAttribute, Double>>> sampledCumulativeLoadForClient = sampleLoad(
                            cumulativeLoadForClient, dataSampleInterval);
                    cumulativeLoad.put(clientName, sampledCumulativeLoadForClient);
                } else {
                    // store cumulative load without sampling it
                    cumulativeLoad.put(clientName, cumulativeLoadForClient);
                }
            } catch (IOException e) {
                LOGGER.error("Unable to deserialize file: {}", clientRequestFile, e);
            }
        }

        // ensure that a column for every discovered service appears in each
        // output file
        cumulativeLoad.forEach((clientName, cumulativeLoadForClient) -> {
            cumulativeLoadForClient.forEach((time, serviceLoad) -> {
                services.forEach((service) -> {
                    Map<NodeAttribute, Double> attrValues = serviceLoad.computeIfAbsent(
                            ChartGenerationUtils.serviceToFilenameString(service), k -> new HashMap<>());

                    attributes.forEach((attr) -> {
                        attrValues.merge(attr, 0.0, Double::sum);
                    });
                });
            });
        });

        LOGGER.debug("cumulativeLoad: {}", cumulativeLoad);

        // output a file for each client
        cumulativeLoad.forEach((clientName, cumulativeLoadForClient) -> {
            ChartGenerationUtils.outputDataToCSV(demandTableOutputFolder, "client_" + clientName, v -> v,
                    cumulativeLoadForClient);
        });

        // service name -> time -> client name -> demand attribute -> value
        final Map<String, Map<Long, Map<String, Map<NodeAttribute, Double>>>> cumulativeLoadByService = new HashMap<>();
        final Map<String, Map<Long, Map<String, Map<NodeAttribute, Double>>>> totalCumulativeLoadByService = new HashMap<>();
        final Set<NodeAttribute> attributes = new HashSet<>();

        cumulativeLoad.forEach((client, clientLoadMap) -> {
            clientLoadMap.forEach((time, serviceLoadMap) -> {
                serviceLoadMap.forEach((service, load) -> {
                    Map<Long, Map<String, Map<NodeAttribute, Double>>> newServiceLoad = cumulativeLoadByService
                            .computeIfAbsent(service, k -> new HashMap<>());
                    Map<String, Map<NodeAttribute, Double>> newClientLoad = newServiceLoad.computeIfAbsent(time,
                            k -> new HashMap<>());
                    newClientLoad.computeIfAbsent(client, k -> load);

                    Map<Long, Map<String, Map<NodeAttribute, Double>>> newTotalServiceLoad = totalCumulativeLoadByService
                            .computeIfAbsent(service, k -> new HashMap<>());
                    Map<String, Map<NodeAttribute, Double>> newTotalTimeServiceLoad = newTotalServiceLoad
                            .computeIfAbsent(time, k -> new HashMap<>());
                    Map<NodeAttribute, Double> newTotalTimeServiceLoadTotal = newTotalTimeServiceLoad
                            .computeIfAbsent("total", k -> new HashMap<>());

                    load.forEach((attr, value) -> {
                        attributes.add(attr);
                        newTotalTimeServiceLoadTotal.merge(attr, value, Double::sum);
                    });
                });
            });
        });

        // output a load by client file for each service
        cumulativeLoadByService.forEach((serviceName, cumulativeLoadForService) -> {
            LOGGER.debug("output for service '{}'", serviceName);
            ChartGenerationUtils.outputDataToCSV(demandTableOutputFolder, "service_" + serviceName, attributes, v -> v,
                    cumulativeLoadForService);
        });

        // output a total load file for each service
        totalCumulativeLoadByService.forEach((serviceName, cumulativeLoadForService) -> {
            ChartGenerationUtils.outputDataToCSV(demandTableOutputFolder, "total_service_" + serviceName, attributes,
                    v -> v, cumulativeLoadForService);
        });

        generateNumClientsTables(demandScenarioInputFolder.toPath(), demandTableOutputFolder.toPath());
    }

    private void generateNumClientsTables(final Path demandScenarioInputFolder, final Path demandTableOutputFolder) {
        // service -> time -> numClients delta
        final Map<String, SortedMap<Long, Integer>> data = gatherNumClientsData(demandScenarioInputFolder);

        for (final Map.Entry<String, SortedMap<Long, Integer>> entry : data.entrySet()) {
            final String service = entry.getKey();
            final Path outputFile = demandTableOutputFolder
                    .resolve(String.format("num_clients-%s%s", service, ChartGenerationUtils.CSV_FILE_EXTENSION));
            writeNumClientsData(outputFile, service, entry.getValue());
        }
    }

    private void
            writeNumClientsData(final Path output, final String service, final SortedMap<Long, Integer> serviceData) {
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {

            final CSVFormat format = CSVFormat.EXCEL.withHeader(ChartGenerationUtils.CSV_TIME_COLUMN_LABEL,
                    "num clients");

            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                int numClients = 0;
                for (Map.Entry<Long, Integer> timeEntry : serviceData.entrySet()) {
                    numClients += timeEntry.getValue();
                    printer.printRecord(timeEntry.getKey(), numClients);
                } // foreach time entry
            } // printer
        } catch (final IOException e) {
            throw new RuntimeException("Error writing num clients data", e);
        }
    }

    /**
     * Compute the increment or decrement at each time interval. To get the
     * actual number of clients at a time, one needs to sum the values of all
     * keys before the time requested. See
     * {@link #writeNumClientsData(Path, String, SortedMap)} for processing
     * information.
     */
    private Map<String, SortedMap<Long, Integer>> gatherNumClientsData(final Path demandScenarioInputFolder) {
        final Map<String, SortedMap<Long, Integer>> data = new HashMap<>();

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(demandScenarioInputFolder,
                "*" + JSON_FILE_SUFFIX)) {
            for (final Path demandFile : dirStream) {
                final Path filename = demandFile.getFileName();
                if (null != filename && Simulation.BACKGROUND_TRAFFIC_FILENAME.equals(filename.toString())) {
                    // don't parse the background traffic file as a client
                    // demand
                    // file
                    continue;
                }

                try (Reader reader = Files.newBufferedReader(demandFile)) {
                    final ClientLoad[] clientRequests = mapper.readValue(reader, ClientLoad[].class);
                    for (final ClientLoad req : clientRequests) {
                        final String service = req.getService().getArtifact();

                        // initialize to 0 clients at time 0
                        final Map<Long, Integer> serviceData = data.computeIfAbsent(service,
                                k -> new TreeMap<>(Collections.singletonMap(0L, 0)));

                        // step up when starting the request
                        serviceData.merge(req.getStartTime(), req.getNumClients(), Integer::sum);

                        // NOTE: using max of network and server duration. One
                        // could create separate tables for each duration

                        // step down when ending the request
                        final long endTime = req.getStartTime()
                                + Math.max(req.getServerDuration(), req.getNetworkDuration());
                        serviceData.merge(endTime, -1 * req.getNumClients(), Integer::sum);

                    } // foreach client request
                } catch (final IOException e) {
                    throw new RuntimeException("Error gather num clients data from " + demandFile, e);
                }
            } // foreach JSON file
        } catch (final IOException e) {
            throw new RuntimeException("Error gathering num clients data", e);
        }

        return data;
    }

    /**
     * Provides a set of services to use for the demand scenario addition to the
     * services discovered through requests.
     * 
     * @param services
     *            the set of services in the scenario
     */
    public void setScenarioServices(Set<ServiceIdentifier<?>> services) {
        scenarioServices = services;
    }

    /**
     * Adds the given ClientRequest to the given data map.
     * 
     * @param clientRequest
     *            an object representing an amount of demand that a client pool
     *            places on the MAP system.
     * @param changeInLoadData
     *            the Map representing the data table to add the request to
     * @param sampleInterval
     *            the interval at which to sample the request when adding it to
     *            the table in milliseconds
     */
    private void processClientRequest(ClientLoad clientRequest,
            Map<Long, Map<String, Map<NodeAttribute, Double>>> changeInLoadData,
            long sampleInterval) {
        ApplicationCoordinates app = clientRequest.getService();
        String appString = ChartGenerationUtils.serviceToFilenameString(app);

        int clients = clientRequest.getNumClients();
        long startTime = clientRequest.getStartTime();
        long duration = clientRequest.getServerDuration();
        long endTime = startTime + duration;
        Map<NodeAttribute, Double> deltaLoadStart = new HashMap<>();

        // place positive changes in load at the start of request
        clientRequest.getNodeLoad().forEach((attribute, value) -> {
            deltaLoadStart.put(attribute, value * clients);
        });

        if (!changeInLoadData.containsKey(startTime))
            changeInLoadData.put(startTime, new HashMap<>());

        changeInLoadData.get(startTime).merge(appString, deltaLoadStart, (oldLoad, deltaLoad) -> {
            // apply change in load to each value
            deltaLoad.forEach((key, value) -> {
                oldLoad.merge(key, value, (a, b) -> a + b);
            });

            return oldLoad;
        });

        Map<NodeAttribute, Double> deltaLoadEnd = new HashMap<>();

        // place negative changes in load at the end of request
        clientRequest.getNodeLoad().forEach((attribute, value) -> {
            deltaLoadEnd.put(attribute, -value * clients);
            attributes.add(attribute);
        });

        if (!changeInLoadData.containsKey(endTime))
            changeInLoadData.put(endTime, new HashMap<>());

        changeInLoadData.get(endTime).merge(appString, deltaLoadEnd, (oldLoad, deltaLoad) -> {
            // apply change in load to each value
            deltaLoad.forEach((key, value) -> {
                oldLoad.merge(key, value, (a, b) -> a + b);
            });

            return oldLoad;
        });
    }

    private static Map<Long, Map<String, Map<NodeAttribute, Double>>> accumulateChangeInLoad(
            Map<Long, Map<String, Map<NodeAttribute, Double>>> changeInLoad,
            boolean startAtZero) {
        Map<Long, Map<String, Map<NodeAttribute, Double>>> cumulativeResult = new HashMap<>();

        if (startAtZero) {
            long startTime = 0;
            cumulativeResult.put(startTime, new HashMap<>());

            if (!cumulativeResult.containsKey(startTime))
                cumulativeResult.put(startTime, new HashMap<>());

            changeInLoad.forEach((time, serviceLoad) -> {
                serviceLoad.forEach((service, load) -> {
                    if (!cumulativeResult.get(startTime).containsKey(service))
                        cumulativeResult.get(startTime).put(service, new HashMap<>());

                    load.forEach((attr, value) -> {
                        if (!cumulativeResult.get(startTime).get(service).containsKey(attr))
                            cumulativeResult.get(startTime).get(service).put(attr, 0.0);
                    });
                });
            });
        }

        List<Long> times = new ArrayList<>();
        times.addAll(changeInLoad.keySet());
        Collections.sort(times);
        LOGGER.debug("Times: {}", times);

        Map<String, Map<NodeAttribute, Double>> currentLoad = new HashMap<>();

        for (Long time : times) {
            changeInLoad.get(time).forEach((app, load) -> {
                if (!currentLoad.containsKey(app))
                    currentLoad.put(app, new HashMap<>());

                // add to current load for each load attribute
                load.forEach((key, deltaValue) -> {
                    currentLoad.get(app).merge(key, deltaValue, (oldValue, dV) -> oldValue + dV);
                });
            });

            LOGGER.debug("Load change at time {}: {}", time, changeInLoad.get(time));
            LOGGER.debug("Load at time {}: {}", time, currentLoad);
            cumulativeResult.put(time, copy(currentLoad));
        }

        LOGGER.debug("Cummulative result: {}", cumulativeResult);

        return cumulativeResult;
    }

    private static Map<String, Map<NodeAttribute, Double>> copy(Map<String, Map<NodeAttribute, Double>> loadByApp) {
        Map<String, Map<NodeAttribute, Double>> result = new HashMap<>();

        loadByApp.forEach((app, load) -> {
            if (!result.containsKey(app))
                result.put(app, new HashMap<>());

            load.forEach((key, value) -> {
                result.get(app).put(key, value);
            });
        });

        LOGGER.debug("copy: result: {}", result);

        return result;
    }

    private static Map<Long, Map<String, Map<NodeAttribute, Double>>>
            sampleLoad(Map<Long, Map<String, Map<NodeAttribute, Double>>> cumulativeLoad, long sampleInterval) {
        Map<Long, Map<String, Map<NodeAttribute, Double>>> sampledResult = new HashMap<>();

        List<Long> times = new ArrayList<>();
        times.addAll(cumulativeLoad.keySet());
        Collections.sort(times);

        if (!times.isEmpty()) {
            long lastLoadTime = times.remove(0);
            long currentTime = lastLoadTime;

            while (!times.isEmpty()) {
                // if the current time passed one or more times in the list
                while (!times.isEmpty() && currentTime >= times.get(0))
                    lastLoadTime = times.remove(0); // record the current time
                                                    // and remove it from the
                                                    // list to start duplicating
                                                    // the data for each sample

                // duplicate the value in cumulativeLoad for the sample at
                // currentTime
                sampledResult.put(currentTime, cumulativeLoad.get(lastLoadTime));

                currentTime += sampleInterval;
            }
        }

        return sampledResult;
    }
}
