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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.ServiceConfiguration;
import com.bbn.map.simulator.Simulation;
import com.bbn.protelis.networkresourcemanagement.NetworkServerProperties;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

/**
 * Groups DNS entries into separate CSV files according to (source region,
 * destination region) pairs and (source region, service, destination) triples.
 * 
 * Counts DNS request container and region resolutions for each client--service
 * pair.
 * 
 * @author awald
 *
 */
public class DNSAnalysisTableGenerator {
    private static final Logger LOGGER = LogManager.getLogger(DNSAnalysisTableGenerator.class);

    private static final String OUTPUT_FILE_GROUPED_DNS_REQUEST_PREFIX = "dns_";
    private static final String OUTPUT_FILE_DNS_REQUEST_COUNT_PREFIX = "dns_req_count_";
    private static final String OUTPUT_FILE_DNS_RESPONSE_DEVIATION_PREFIX = "dns_res_dev_";

    private static final String CSV_HEADER_TIMESTAMP = "timestamp";
    private static final String CSV_HEADER_CLIENT_ADDRESS = "clientAddress";
    private static final String CSV_HEADER_NAME_TO_RESOLVE = "name_to_resolve";
    private static final String CSV_HEADER_RESOLVED_NAME = "resolved_name";
    private static final String[] CSV_HEADER = { CSV_HEADER_TIMESTAMP, CSV_HEADER_CLIENT_ADDRESS,
            CSV_HEADER_NAME_TO_RESOLVE, CSV_HEADER_RESOLVED_NAME };

    private static final String[] CLIENT_PROCESSING_LATENCY_WITH_HOP_INFO_CSV_HEADER = { "timestamp", "event", "server",
            "time_sent", "time_ack_received", "latency", "hop_count" };

    private static final String[] CLIENT_PROCESSING_LATENCY_BY_SERVICE_WITH_HOP_INFO_CSV_HEADER = { "timestamp",
            "event", "client", "server", "time_sent", "time_ack_received", "latency", "hop_count", "success",
            "message" };

    private static final String[] SERVER_PROCESSING_LATENCY_BY_SERVICE_WITH_HOP_INFO_CSV_HEADER = { "timestamp",
            "event", "client", "server", "time_received", "time_finished", "latency", "hop_count" };

    private static final String[] CLIENT_PROCESSING_LATENCY_RESOLUTION_SUMMARY_HEADER = { "service", "client_region",
            "client", "resolutions_in_region", "resolutions_delegated" };

    private static final long SERVER_PROCESSING_LATENCY_COUNT_BIN_SIZE = 30000;

    /**
     * The number of threads to use for the thread pool, which gathers latency
     * entries for individual nodes in parallel.
     */
    public static final int N_WORKER_THREADS = Runtime.getRuntime().availableProcessors();

    private Map<String, String> nodeToRegionMap = new HashMap<>(); // created
                                                                   // from
                                                                   // topology.ns
    private Map<String, String> ipAddressToNodeMap = new HashMap<>(); // created
                                                                      // from
                                                                      // topology.ns
    private Map<String, String> domainBaseToServiceNameMap = new HashMap<>(); // created
                                                                              // from
                                                                              // service-configurations.json

    private Map<String, String> containerIpToNodeIpMap = new HashMap<>(); // created
                                                                          // from
                                                                          // ipAddressToNodeMap
                                                                          // and
                                                                          // discovered
                                                                          // container
                                                                          // IPs

    // client --> ncp --> route hop information
    private Map<String, Map<String, RouteHopInfo>> hopsMap = new HashMap<>();

    private static class RouteHopInfo {
        private int hops;

        RouteHopInfo(int hops) {
            this.hops = hops;
        }

        int getHops() {
            return hops;
        }
    }

    private Set<String> sourceRegions = new HashSet<>();
    private Set<String> services = new HashSet<>();
    private Set<String> destinationRegions = new HashSet<>();

    private long binSize;

    // time bin --> plan region --> service name --> to region --> overflow
    // amount
    private Map<Long, Map<String, Map<String, Map<String, Double>>>> rlgRegionOverflowPlan = new HashMap<>();

    /**
     * Analyzes the client latency files to check the number of DNS resolutions
     * in region and out of region.
     * 
     * @param scenarioFolder
     *            the folder containing the scenario configuration
     * @param inputFolder
     *            the folder containing the simulation results
     * @param outputFolder
     *            the folder to output the tables to
     * @param outputModifiedLatencyFiles
     *            if true, output modified latency files, which inlude hop count
     *            information
     */
    public void processLatencyDNSResolutions(File scenarioFolder,
            File inputFolder,
            File outputFolder,
            boolean outputModifiedLatencyFiles) {
        LOGGER.info("Scenario folder: '{}'", scenarioFolder);
        LOGGER.info("Data input folder: '{}'", inputFolder);

        if (!scenarioFolder.exists()) {
            LOGGER.error("Scenario folder does not exist: {}", scenarioFolder);
            return;
        }

        if (!inputFolder.exists()) {
            LOGGER.error("Input folder does not exist: {}", inputFolder);
            return;
        }

        processScenarioConfiguration(scenarioFolder.getName(), scenarioFolder.toPath());

        // load hop counts from the first hop count file found in the scenario
        // folder if it exists
        if (outputModifiedLatencyFiles) {
            File[] hopCountFiles = scenarioFolder.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.matches("hop_counts.*" + Pattern.quote(ChartGenerationUtils.CSV_FILE_EXTENSION));
                }
            });

            if (hopCountFiles != null && hopCountFiles.length > 0) {
                processScenarioHopCountsFile(hopCountFiles[0], hopsMap);
            } else {
                LOGGER.warn("Found no hop count files with pattern 'hop_counts*.csv' in the scenario folder: {}",
                        scenarioFolder);
            }
        }

        // service --> client region --> client --> resolution region --> count
        final Map<String, Map<String, Map<String, Map<String, Integer>>>> regionResolutionCounts = new HashMap<>();

        // service --> client --> container instance --> list of CSV latency
        // records as Maps
        final Map<String, Map<String, Map<String, List<Map<String, Object>>>>> clientLatencyRecords = new HashMap<>();

        // service --> server --> container instance --> list of CSV latency
        // records as Maps
        final Map<String, Map<String, Map<String, List<Map<String, Object>>>>> serverLatencyRecords = new HashMap<>();

        // service --> list of CSV latency records as Maps
        final Map<String, List<Map<String, Object>>> serviceClientLatencyRecords = new HashMap<>();

        // service --> list of CSV latency records as Maps
        final Map<String, List<Map<String, Object>>> serviceServerProcessingLatencyRecords = new HashMap<>();

        // service --> time bin --> client pool --> request count
        final Map<String, Map<Long, Map<String, Integer>>> serviceServerRequestClientPoolDistribution = new HashMap<>();

        LOGGER.info("Using thread pool size of {} threads.", N_WORKER_THREADS);
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(N_WORKER_THREADS);
        List<Future<?>> clientThreadPoolSubmissions = new LinkedList<>();
        List<Future<?>> serverThreadPoolSubmissions = new LinkedList<>();

        List<ClientLatencyRecordWorker> clientLatencyRecordWorkers = new LinkedList<>();
        List<ServerLatencyRecordWorker> serverLatencyRecordWorkers = new LinkedList<>();

        File[] nodeFolders = inputFolder.listFiles();

        if (nodeFolders != null) {
            for (File nodeFolder : nodeFolders) {

                // collect client data from edge client
                File clientContainerDataFolder = nodeFolder.toPath().resolve("client").resolve("container_data")
                        .toFile();

                if (!clientContainerDataFolder.exists() || !clientContainerDataFolder.isDirectory()) {
                    clientContainerDataFolder = nodeFolder.toPath().resolve("agent").resolve("container_data").toFile();
                }

                if (!clientContainerDataFolder.exists() || !clientContainerDataFolder.isDirectory()) {
                    clientContainerDataFolder = null;
                }

                if (clientContainerDataFolder != null) {

                    File[] clientServiceFolders = clientContainerDataFolder.listFiles();

                    if (clientServiceFolders != null) {
                        for (File clientServiceFolder : clientServiceFolders) {
                            if (clientServiceFolder.isDirectory()) {
                                final String service = ChartGenerationUtils
                                        .serviceGroupArtifactVersionFolderNameToService(clientServiceFolder.getName());

                                final String clientName = nodeFolder.getName();
                                final String clientRegion = mapNodeAddressToRegion(clientName);

                                ClientLatencyRecordWorker clientLatencyRecordWorker = new ClientLatencyRecordWorker(
                                        service, clientName, clientRegion, clientServiceFolder,
                                        outputModifiedLatencyFiles);
                                clientLatencyRecordWorkers.add(clientLatencyRecordWorker);
                            }
                        }
                    }
                }

                // collect server data
                File serverContainerDataFolder = nodeFolder.toPath().resolve("agent").resolve("container_data")
                        .toFile();

                if (serverContainerDataFolder.exists() && serverContainerDataFolder.isDirectory()) {

                    File[] serverServiceFolders = serverContainerDataFolder.listFiles();

                    if (serverServiceFolders != null) {
                        for (File serverServiceFolder : serverServiceFolders) {
                            if (serverServiceFolder.isDirectory()) {
                                final String service = ChartGenerationUtils
                                        .serviceGroupArtifactVersionFolderNameToService(serverServiceFolder.getName());

                                final String serverName = nodeFolder.getName();
                                final String serverRegion = mapNodeAddressToRegion(serverName);

                                ServerLatencyRecordWorker serverLatencyRecordWorker = new ServerLatencyRecordWorker(
                                        service, serverName, serverRegion, serverServiceFolder);
                                serverLatencyRecordWorkers.add(serverLatencyRecordWorker);
                            }
                        }
                    }
                }
            }

            for (ClientLatencyRecordWorker worker : clientLatencyRecordWorkers) {
                clientThreadPoolSubmissions.add(threadPoolExecutor.submit(worker));
            }

            int nFinished = 0;

            // wait until all client worker threads finish
            for (Future<?> f : clientThreadPoolSubmissions) {
                try {
                    f.get();
                    nFinished++;
                } catch (InterruptedException e) {
                    throw new RuntimeException("Error waiting for computation " + f + " to complete.", e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("Error with computation" + f + ".", e);
                }
            }

            LOGGER.debug("{} future objects finshed", nFinished);

            // combine client latency results into large Maps
            for (ClientLatencyRecordWorker cw : clientLatencyRecordWorkers) {
                String service = cw.getService();
                String clientName = cw.getClientName();
                String clientRegion = cw.getClientRegion();

                // container instance --> list of CSV latency
                final Map<String, List<Map<String, Object>>> workerLatencyRecords = cw.getLatencyRecords();

                // service --> client --> container instance --> list of CSV
                // latency
                clientLatencyRecords.computeIfAbsent(service, k -> new HashMap<>()).put(clientName,
                        workerLatencyRecords);

                // resolution region --> count
                final Map<String, Integer> workerRegionResolutionCounts = cw.getRegionResolutionCounts();

                // service --> client region --> client --> resolution region
                // --> count
                regionResolutionCounts.computeIfAbsent(service, k -> new HashMap<>())
                        .computeIfAbsent(clientRegion, k -> new HashMap<>())
                        .put(clientName, workerRegionResolutionCounts);

                // back end service --> container instance --> list of CSV
                // latency
                final Map<String, Map<String, List<Map<String, Object>>>> workerDependentServiceLatencyRecords = cw
                        .getDependentServiceLatencyRecords();

                if (null != workerDependentServiceLatencyRecords) {
                    LOGGER.debug("workerDependentServiceLatencyRecords = {}, clientName = {}",
                            workerDependentServiceLatencyRecords, clientName);

                    workerDependentServiceLatencyRecords.forEach((backEndService, containerInstanceRecords) -> {
                        clientLatencyRecords.computeIfAbsent(backEndService, k -> new HashMap<>()).put(clientName,
                                containerInstanceRecords);
                    });
                }

                // back end service --> resolution region --> count
                final Map<String, Map<String, Integer>> workerBackEndRegionResolutionCounts = cw
                        .getDependentServiceRegionResolutionCounts();

                if (null != workerBackEndRegionResolutionCounts) {
                    workerBackEndRegionResolutionCounts.forEach((backEndService, resolutionRegionCounts) -> {
                        // service --> client region --> client --> resolution
                        // region --> count
                        regionResolutionCounts.computeIfAbsent(backEndService, k -> new HashMap<>())
                                .computeIfAbsent(clientRegion, k -> new HashMap<>())
                                .put(clientName, resolutionRegionCounts);

                    });
                }

                // LOGGER.debug("Added results for service '{}' client '{}'
                // consisting of {} container instances "
                // + "to clientLatencyRecords", service, clientName,
                // workerLatencyRecords.keySet().size());
                LOGGER.debug("Added results for service '{}' client '{}' " + "to clientLatencyRecords", service,
                        clientName);
            }

            for (ServerLatencyRecordWorker worker : serverLatencyRecordWorkers) {
                serverThreadPoolSubmissions.add(threadPoolExecutor.submit(worker));
            }

            // wait until all worker threads finish
            for (Future<?> f : serverThreadPoolSubmissions) {
                try {
                    f.get();
                    nFinished++;
                } catch (InterruptedException e) {
                    throw new RuntimeException("Error waiting for computation " + f + " to complete.", e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("Error with computation" + f + ".", e);
                }
            }

            LOGGER.debug("{} future objects finshed", nFinished);

            // combine server latency results into large Maps
            for (ServerLatencyRecordWorker sw : serverLatencyRecordWorkers) {
                String service = sw.getService();
                String serverName = sw.getServerName();

                // container instance --> list of CSV latency
                Map<String, List<Map<String, Object>>> workerLatencyRecords = sw.getLatencyRecords();

                // service --> server --> container instance --> list of CSV
                // latency
                serverLatencyRecords.computeIfAbsent(service, k -> new HashMap<>()).put(serverName,
                        workerLatencyRecords);

                // LOGGER.debug("Added results for service '{}' server '{}'
                // consisting of {} container instances "
                // + "to clientLatencyRecords", service, serverName,
                // workerLatencyRecords.keySet().size());

                LOGGER.debug("Added results for service '{}' server '{}' " + "to clientLatencyRecords", service,
                        serverName);
            }
        }

        // create resolution summary output file
        File summaryOutputFile = outputFolder.toPath().resolve("processing_latency_dns_resolution_count.csv").toFile();
        if (outputFolder.exists() || outputFolder.mkdirs()) {
            outputLatencyResolutionSummary(summaryOutputFile, regionResolutionCounts);

            // create an aggregate file containing all latency records for each
            // service
            aggregateLatencyRecordsPerService(clientLatencyRecords, serviceClientLatencyRecords);
            aggregateLatencyRecordsPerService(serverLatencyRecords, serviceServerProcessingLatencyRecords);

            serviceClientLatencyRecords.forEach((service, records) -> {
                records.forEach((record) -> {
                    record.computeIfPresent("server", (k, serverIp) -> {
                        return mapNodeAddressToNodeName(mapContainerIpToNodeIp((String) serverIp));
                    });
                });
            });

            serviceServerProcessingLatencyRecords.forEach((service, records) -> {
                records.forEach((record) -> {
                    record.computeIfPresent("client", (k, clientIp) -> {
                        return mapNodeAddressToNodeName(mapContainerIpToNodeIp((String) clientIp));
                    });
                });
            });

            outputAggregatedByServiceLatencyRecords(outputFolder,
                    CLIENT_PROCESSING_LATENCY_BY_SERVICE_WITH_HOP_INFO_CSV_HEADER, "client_processing_latency-",
                    serviceClientLatencyRecords);

            outputAggregatedByServiceLatencyRecords(outputFolder,
                    SERVER_PROCESSING_LATENCY_BY_SERVICE_WITH_HOP_INFO_CSV_HEADER, "server_processing_latency-",
                    serviceServerProcessingLatencyRecords);

            // region --> service --> list of CSV latency records as Maps
            Map<String, Map<String, List<Map<String, Object>>>> regionServiceServerProcessingLatencyRecords = new HashMap<>();

            serviceServerProcessingLatencyRecords.forEach((service, serviceRecords) -> {
                serviceRecords.forEach((serviceRecord) -> {
                    String serverRegion = mapAddressToRegion((String) serviceRecord.get("server"));

                    regionServiceServerProcessingLatencyRecords.computeIfAbsent(serverRegion, k -> new HashMap<>())
                            .computeIfAbsent(service, k -> new LinkedList<>()).add(serviceRecord);
                });
            });

            regionServiceServerProcessingLatencyRecords.forEach((region, serviceRecords) -> {
                serviceRecords.forEach((service, records) -> {
                    services.add(service);
                });
            });

            regionServiceServerProcessingLatencyRecords.forEach((region, regionRecords) -> {
                services.forEach((service) -> {
                    regionRecords.computeIfAbsent(service, k -> new LinkedList<>());
                });

                outputAggregatedByServiceLatencyRecords(outputFolder,
                        SERVER_PROCESSING_LATENCY_BY_SERVICE_WITH_HOP_INFO_CSV_HEADER,
                        "server_processing_latency-" + region + "-", regionRecords);
            });

            // service --> time bin --> region --> list of CSV latency records
            // as Maps
            Map<String, Map<Long, Map<String, List<Map<String, Object>>>>> binnedRegionServiceServerProcessingLatencyRecords = new HashMap<>();

            // service --> time bin --> region --> count of requests received
            Map<String, Map<Long, Map<String, Integer>>> binnedRegionServiceServerProcessingLatencyRecordCounts = new HashMap<>();

            regionServiceServerProcessingLatencyRecords.forEach((region, serviceRecords) -> {
                serviceRecords.forEach((service, records) -> {
                    records.forEach((record) -> {
                        long time = (long) record.get("time_received");
                        long timeBin = ChartGenerationUtils.getBinForTime(time, 0,
                                SERVER_PROCESSING_LATENCY_COUNT_BIN_SIZE);

                        binnedRegionServiceServerProcessingLatencyRecords.computeIfAbsent(service, k -> new HashMap<>())
                                .computeIfAbsent(timeBin, k -> new HashMap<>())
                                .computeIfAbsent(region, k -> new LinkedList<>()).add(record);

                        binnedRegionServiceServerProcessingLatencyRecordCounts
                                .computeIfAbsent(service, k -> new HashMap<>())
                                .computeIfAbsent(timeBin, k -> new HashMap<>()).merge(region, 1, Integer::sum);
                    });
                });
            });

            outputAggregatedByServiceLatencyRecordBinCounts(outputFolder, "binned_server_processing_latency_counts-",
                    binnedRegionServiceServerProcessingLatencyRecordCounts);

            // output service server request client pool distribution files
            computeServiceServerRequestClientPoolDistribution(serviceServerRequestClientPoolDistribution,
                    serviceServerProcessingLatencyRecords, 1000);
            outputServiceServerRequestClientPoolDistribution(outputFolder, "server_request_client_pool_distribution-",
                    serviceServerRequestClientPoolDistribution);
        } else {
            LOGGER.error("Unable to create output folder {}", outputFolder);
        }
    }

    private void outputServiceServerRequestClientPoolDistribution(File outputFolder,
            String outputFilePrefix,
            Map<String, Map<Long, Map<String, Integer>>> serviceServerRequestClientPoolDistribution) {

        final Set<String> clientPools = new HashSet<>();
        serviceServerRequestClientPoolDistribution.forEach((service, timeEntries) -> {
            timeEntries.forEach((timeBin, clientEntries) -> {
                clientPools.addAll(clientEntries.keySet());
            });
        });

        final List<String> clientPoolsList = clientPools.stream().sorted().collect(Collectors.toList());
        final String[] header = new String[1 + clientPoolsList.size()];
        header[0] = "time";

        for (int c = 0; c < clientPoolsList.size(); c++) {
            header[c + 1] = clientPoolsList.get(c);
        }

        final CSVFormat format = CSVFormat.EXCEL.withHeader(header);

        for (Entry<String, Map<Long, Map<String, Integer>>> entry : serviceServerRequestClientPoolDistribution
                .entrySet()) {
            String service = entry.getKey();
            Map<Long, Map<String, Integer>> timeClientPoolCounts = entry.getValue();

            final File serverRequestClientPoolDistributionFile = outputFolder.toPath()
                    .resolve(outputFilePrefix + service + ChartGenerationUtils.CSV_FILE_EXTENSION).toFile();

            try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(
                    new FileOutputStream(serverRequestClientPoolDistributionFile), Charset.defaultCharset()), format)) {

                final List<Long> times = timeClientPoolCounts.keySet().stream().sorted().collect(Collectors.toList());

                for (int t = 0; t < times.size(); t++) {
                    Map<String, Integer> clientPoolCounts = timeClientPoolCounts.get(times.get(t));
                    Object[] record = new Object[header.length];
                    Arrays.fill(record, ChartGenerationUtils.EMPTY_CELL_VALUE);
                    record[0] = times.get(t);

                    for (int c = 0; c < clientPoolsList.size(); c++) {
                        final String clientPool = clientPoolsList.get(c);
                        Integer count = clientPoolCounts.get(clientPool);

                        if (count != null) {
                            record[c + 1] = count;
                        }
                    }

                    printer.printRecord(record);
                }

                LOGGER.info("Output service server request client pool distribution file: {}",
                        serverRequestClientPoolDistributionFile);
            } catch (IOException e) {
                LOGGER.error("Could not write service server request client pool distribution file: {}",
                        serverRequestClientPoolDistributionFile, e);
            }
        }
    }

    private static void computeServiceServerRequestClientPoolDistribution(
            Map<String, Map<Long, Map<String, Integer>>> serviceServerRequestClientPoolDistribution,
            Map<String, List<Map<String, Object>>> serviceServerProcessingLatencyRecords,
            long binSize) {

        serviceServerProcessingLatencyRecords.forEach((service, serverRecords) -> {
            serverRecords.forEach((serverRecord) -> {
                final long time = (long) serverRecord.get("timestamp");
                final long timeBin = ChartGenerationUtils.getBinForTime(time, 0, binSize);
                final String clientPool = (String) serverRecord.get("client");

                serviceServerRequestClientPoolDistribution.computeIfAbsent(service, k -> new HashMap<>())
                        .computeIfAbsent(timeBin, k -> new HashMap<>()).merge(clientPool, 1, Integer::sum);
            });
        });
    }

    private static final Comparator<Map<String, Object>> LATENCY_RECORD_COMPARATOR = new Comparator<Map<String, Object>>() {
        @Override
        public int compare(Map<String, Object> e1, Map<String, Object> e2) {
            int diff = Long.compare((long) e1.get("timestamp"), (long) e2.get("timestamp"));

            // compare by client name and then server name if timestamps are
            // equal
            if (e1.containsKey("client") && e2.containsKey("client")) {
                if (diff == 0) {
                    diff = ((String) e1.get("client")).compareTo((String) e2.get("client"));
                }
            }

            if (e1.containsKey("server") && e2.containsKey("server")) {
                if (diff == 0) {
                    diff = ((String) e1.get("server")).compareTo((String) e2.get("server"));
                }
            }

            return diff;
        }
    };

    private static final Comparator<CSVRecord> LATENCY_CSV_RECORD_COMPARATOR = new Comparator<CSVRecord>() {
        @Override
        public int compare(CSVRecord e1, CSVRecord e2) {
            int diff = Long.compare(Long.parseLong(e1.get("timestamp")), Long.parseLong(e2.get("timestamp")));

            return diff;
        }
    };

    private void aggregateLatencyRecordsPerService(
            Map<String, Map<String, Map<String, List<Map<String, Object>>>>> latencyRecords,
            Map<String, List<Map<String, Object>>> latencyRecordsPerService) {

        latencyRecords.forEach((service, clientContainerMap) -> {
            List<Map<String, Object>> serviceRecords = new LinkedList<>();

            clientContainerMap.forEach((client, containerMap) -> {
                if (null != containerMap) {
                    containerMap.forEach((container, containerInstanceRecords) -> {
                        serviceRecords.addAll(containerInstanceRecords);
                    });
                }
            });

            Collections.sort(serviceRecords, LATENCY_RECORD_COMPARATOR);
            latencyRecordsPerService.put(service, serviceRecords);
        });

    }

    private void outputAggregatedByServiceLatencyRecords(File outputFolder,
            String[] csvHeader,
            String outputFilePrefix,
            Map<String, List<Map<String, Object>>> serviceLatencyRecords) {
        CSVFormat format = CSVFormat.EXCEL.withHeader(csvHeader);

        for (Entry<String, List<Map<String, Object>>> entry : serviceLatencyRecords.entrySet()) {
            String service = entry.getKey();
            List<Map<String, Object>> latencyRecords = entry.getValue();

            File serviceLatencyRecordsFile = outputFolder.toPath()
                    .resolve(outputFilePrefix + service + ChartGenerationUtils.CSV_FILE_EXTENSION).toFile();

            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(serviceLatencyRecordsFile), Charset.defaultCharset()),
                    format)) {
                String[] header = format.getHeader();

                for (int r = 0; r < latencyRecords.size(); r++) {
                    Map<String, Object> record = latencyRecords.get(r);

                    for (String column : header) {
                        if (!record.containsKey(column)) {
                            record.put(column, ChartGenerationUtils.EMPTY_CELL_VALUE);
                        }
                    }

                    printer.printRecord(mapToRecordArray(record, header));
                }

                LOGGER.info("Output service latency file: {}", serviceLatencyRecordsFile);
            } catch (IOException e) {
                LOGGER.error("Could not write service latency file: {}", serviceLatencyRecordsFile, e);
            }
        }
    }

    private void outputAggregatedByServiceLatencyRecordBinCounts(File outputFolder,
            String outputFilePrefix,
            Map<String, Map<Long, Map<String, Integer>>> regionRecordCounts) {
        for (Entry<String, Map<Long, Map<String, Integer>>> entry : regionRecordCounts.entrySet()) {
            String service = entry.getKey();
            Map<Long, Map<String, Integer>> counts = entry.getValue();

            Set<String> regions = new HashSet<>();
            counts.forEach((time, regionCounts) -> {
                regions.addAll(regionCounts.keySet());
            });
            List<String> regionsList = new ArrayList<>();
            regionsList.addAll(regions);
            Collections.sort(regionsList);

            String[] header = new String[1 + regionsList.size()];
            header[0] = "time";
            for (int r = 0; r < regionsList.size(); r++) {
                header[1 + r] = regionsList.get(r);
            }

            CSVFormat format = CSVFormat.EXCEL.withHeader(header);

            File serviceLatencyRecordsFile = outputFolder.toPath()
                    .resolve(outputFilePrefix + service + ChartGenerationUtils.CSV_FILE_EXTENSION).toFile();

            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(serviceLatencyRecordsFile), Charset.defaultCharset()),
                    format)) {
                List<Long> times = new ArrayList<>();
                times.addAll(counts.keySet());
                Collections.sort(times);

                for (int t = 0; t < times.size(); t++) {
                    long time = times.get(t);

                    Object[] record = new Object[header.length];
                    record[0] = time;

                    for (int r = 0; r < regionsList.size(); r++) {
                        int count = counts.get(time).getOrDefault(regionsList.get(r), 0);
                        record[1 + r] = count;
                    }

                    printer.printRecord(record);
                }

                LOGGER.info("Output service latency file: {}", serviceLatencyRecordsFile);
            } catch (IOException e) {
                LOGGER.error("Could not write service latency file: {}", serviceLatencyRecordsFile, e);
            }
        }
    }

    private <T> Object[] mapToRecordArray(Map<String, T> map, String[] header) {
        Object[] record = new Object[header.length];

        for (int c = 0; c < header.length; c++) {
            record[c] = map.get(header[c]);
        }

        return record;
    }

    private void outputLatencyResolutionSummary(File summaryOutputFile,
            Map<String, Map<String, Map<String, Map<String, Integer>>>> regionResolutionCounts) {
        LOGGER.debug("Latency region resolution counts: {}", regionResolutionCounts);

        CSVFormat format = CSVFormat.EXCEL.withHeader(CLIENT_PROCESSING_LATENCY_RESOLUTION_SUMMARY_HEADER);

        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(new FileOutputStream(summaryOutputFile), Charset.defaultCharset()), format)) {

            regionResolutionCounts.forEach((service, serviceClientMap) -> {
                serviceClientMap.forEach((clientRegion, clientNameMap) -> {
                    clientNameMap.forEach((clientName, resolvedRegionMap) -> {
                        int inRegion = 0;
                        int delegated = 0;

                        for (Entry<String, Integer> entry : resolvedRegionMap.entrySet()) {
                            String region = entry.getKey();
                            Integer resolutions = entry.getValue();
                            resolutions = (resolutions != null ? resolutions : 0);

                            if (region.equals(clientRegion)) {
                                inRegion += resolutions;
                            } else {
                                delegated += resolutions;
                            }
                        }

                        Object[] record = { service, clientRegion, clientName, Integer.toString(inRegion),
                                Integer.toString(delegated) };

                        try {
                            printer.printRecord(record);
                        } catch (IOException e) {
                            LOGGER.error("Problem printing record {}:", record, e);
                        }
                    });
                });
            });

            LOGGER.info("Output latency resolution summary file: {}", summaryOutputFile);

        } catch (IOException e) {
            LOGGER.error("Problems outputting latency resolution summary file: {}", summaryOutputFile, e);
        }
    }

    private void outputModifiedContainerInstanceLatencyFile(File modifiedContainerInstanceLatencyFile,
            List<Map<String, Object>> containerInstanceLatencyRecords) {
        CSVFormat format = CSVFormat.EXCEL.withHeader(CLIENT_PROCESSING_LATENCY_WITH_HOP_INFO_CSV_HEADER);

        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(
                new FileOutputStream(modifiedContainerInstanceLatencyFile), Charset.defaultCharset()), format)) {

            String[] header = format.getHeader();

            for (int r = 0; r < containerInstanceLatencyRecords.size(); r++) {
                printer.printRecord(mapToRecordArray(containerInstanceLatencyRecords.get(r), header));
            }

        } catch (IOException e) {
            LOGGER.error("Could not write modified container instance latency file: {}",
                    modifiedContainerInstanceLatencyFile, e);
        }
    }

    private void processScenarioHopCountsFile(File hopCountFile, Map<String, Map<String, RouteHopInfo>> hopsMap) {
        try (CSVParser parser = new CSVParser(
                new InputStreamReader(new FileInputStream(hopCountFile), Charset.defaultCharset()),
                CSVFormat.EXCEL.withHeader().withSkipHeaderRecord())) {
            parser.forEach((record) -> {

                String clientName = record.get("client");
                String ncpName = record.get("ncp");
                int hopCount = Integer.parseInt(record.get("hop_count"));

                hopsMap.computeIfAbsent(clientName.toLowerCase(), k -> new HashMap<>()).put(ncpName.toLowerCase(),
                        new RouteHopInfo(hopCount));
            });

            LOGGER.info("Processed hop count file: {}", hopCountFile);
        } catch (NoSuchFileException | FileNotFoundException e) {
            LOGGER.error("Could not find hop count file: {}", hopCountFile);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error("Error processing hop count file: {}", hopCountFile);
        }
    }

    private synchronized String mapContainerIpToNodeIp(String containerIp) {
        String nodeIp = containerIpToNodeIpMap.computeIfAbsent(containerIp, ip -> {
            Set<String> nodeIps = ipAddressToNodeMap.keySet();

            String[] ipParts = ip.split(Pattern.quote("."));

            if (ipParts.length == 4) {
                int leastSignificantPart = Integer.parseInt(ipParts[3]);

                // find the largest node IP address that is <= the given
                // container/nodeIp
                String newIp;
                do {
                    newIp = ipParts[0] + "." + ipParts[1] + "." + ipParts[2] + "." + leastSignificantPart;
                    leastSignificantPart--; // decrement the least significant
                                            // number in the IP address for the
                                            // next attempt

                    if (nodeIps.contains(newIp)) {
                        LOGGER.trace("mapContainerIpToNodeIp: mapping IP {} to node NCP IP {}", containerIp, newIp);
                        return newIp;
                    }

                } while (leastSignificantPart >= 0);
            }

            return null;
        });

        if (nodeIp == null) {
            LOGGER.warn(
                    "mapContainerIpToNodeIp: Could not find NCP IP for {}. Defaulting to {}. ipAddressToNodeMap = {}",
                    containerIp, containerIp, ipAddressToNodeMap);
            return containerIp;
        }

        return nodeIp;
    }

    private void processClientLatencyFile(File processingLatencyFile,
            String service,
            String clientName,
            String containerInstanceName,
            String clientRegion,
            Map<String, Map<String, Map<String, Map<String, Integer>>>> regionResolutionCounts,
            List<Map<String, String>> containerInstanceLatencyRecords) {

        try (CSVParser parser = new CSVParser(
                new InputStreamReader(new FileInputStream(processingLatencyFile), Charset.defaultCharset()),
                CSVFormat.EXCEL.withHeader().withSkipHeaderRecord())) {
            parser.forEach((record) -> {
                String serverAddress = record.get("server");
                String serverNodeAddress = mapContainerIpToNodeIp(serverAddress);
                String serverRegion = mapNodeAddressToRegion(serverNodeAddress);

                regionResolutionCounts.computeIfAbsent(service, k -> new HashMap<>())
                        .computeIfAbsent(clientRegion, k -> new HashMap<>())
                        .computeIfAbsent(clientName, k -> new HashMap<>()).merge(serverRegion, 1, Integer::sum);

                // add additional information to record as a Map and add the Map
                // to latencyEntries
                Map<String, String> recordMap = record.toMap();
                recordMap.put("client", clientName);
                RouteHopInfo hopInfo = getClientToServerNodeRouteHopInfo(clientName, serverNodeAddress);
                recordMap.put("hop_count", (hopInfo != null ? Integer.toString(hopInfo.getHops())
                        : ChartGenerationUtils.EMPTY_CELL_VALUE.toString()));

                containerInstanceLatencyRecords.add(recordMap);
            });

            // LOGGER.debug("Processed latency file: {}",
            // processingLatencyFile);
        } catch (NoSuchFileException | FileNotFoundException e) {
            LOGGER.error("Could not find latency file: {}", processingLatencyFile);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error("Error processing latency file: {}", processingLatencyFile);
        }
    }

    private synchronized RouteHopInfo getClientToServerNodeRouteHopInfo(String clientAddress,
            String serverNodeAddress) {
        String clientName = mapNodeAddressToNodeName(clientAddress);
        String serverNodeName = mapNodeAddressToNodeName(serverNodeAddress);

        RouteHopInfo hopInfo = hopsMap.getOrDefault(clientName.toLowerCase(), new HashMap<>())
                .get(serverNodeName.toLowerCase());

        return hopInfo;
    }

    /**
     * Performs the routine that groups DNS entries into files according to
     * (source region, destination region) pairs and (source region, service,
     * destination) triples.
     * 
     * @param scenarioFolder
     *            the folder containing the scenario configuration
     * @param inputFolder
     *            the folder containing the simulation results
     * @param outputFolder
     *            the folder to output the tables to
     */
    public void processDNSCSVFiles(File scenarioFolder, File inputFolder, File outputFolder) {
        LOGGER.info("Scenario folder: '{}'", scenarioFolder);
        LOGGER.info("Data input folder: '{}'", inputFolder);

        if (!scenarioFolder.exists()) {
            LOGGER.error("Scenario folder does not exist: {}", scenarioFolder);
            return;
        }

        if (!inputFolder.exists()) {
            LOGGER.error("Input folder does not exist: {}", inputFolder);
            return;
        }

        processScenarioConfiguration(scenarioFolder.getName(), scenarioFolder.toPath());
        final Map<ImmutableList<String>, List<CSVRecord>> dnsEntries = new HashMap<>();

        // time bin --> name to resolve region --> service name --> container
        // name --> count
        final Map<Long, Map<String, Map<String, Map<String, Integer>>>> binnedContainerResolutionDNSCounts = new HashMap<>();

        // time bin --> name to resolve region --> service name --> destination
        // region name --> count
        final Map<Long, Map<String, Map<String, Map<String, Integer>>>> binnedRegionResolutionDNSCounts = new HashMap<>();

        // time bin --> name to resolve region --> service name --> destination
        // region name --> deviation from expectation
        final Map<Long, Map<String, Map<String, Map<String, Object>>>> binnedRegionDNSDeviations = new HashMap<>();

        final File[] files = inputFolder.listFiles(lofiDnsCSVFileFilter);

        if (null == files) {
            LOGGER.error("{} is an invalid directory, cannot process", inputFolder);
            return;
        }

        for (File dnsCSVFile : files) {
            LOGGER.info("Reading lo-fi DNS file: {}", dnsCSVFile);
            readDNSFile(dnsCSVFile, dnsEntries, binnedContainerResolutionDNSCounts, binnedRegionResolutionDNSCounts);
        }

        // read files for hifi testbed output
        File[] nodeFolders = inputFolder.listFiles();

        if (nodeFolders != null) {
            for (File nodeFolder : nodeFolders) {
                if (nodeFolder.isDirectory()) {
                    File dnsFolder = new File(nodeFolder.getAbsolutePath() + File.separator + "dns");

                    if (dnsFolder.exists() && dnsFolder.isDirectory()) {
                        File[] dnsCSVFiles = dnsFolder.listFiles(hifiDnsCSVFileFilter);

                        if (dnsCSVFiles != null) {
                            for (File dnsCSVFile : dnsCSVFiles) {
                                LOGGER.info("Reading hi-fi DNS file: {}", dnsCSVFile);
                                readDNSFile(dnsCSVFile, dnsEntries, binnedContainerResolutionDNSCounts,
                                        binnedRegionResolutionDNSCounts);
                            }
                        }
                    }
                }
            }
        }

        outputDNSLinesToCSV(outputFolder, OUTPUT_FILE_GROUPED_DNS_REQUEST_PREFIX, dnsEntries);
        outputRequestCountsToCSV(outputFolder, binnedContainerResolutionDNSCounts, binnedRegionResolutionDNSCounts);

        computeBinnedRegionDeviations(rlgRegionOverflowPlan, binnedRegionResolutionDNSCounts,
                binnedRegionDNSDeviations);
        outputRequestDeviationsToCSV(outputFolder, binnedRegionDNSDeviations);
    }

    private void computeBinnedRegionDeviations(
            final Map<Long, Map<String, Map<String, Map<String, Double>>>> binnedRlgRegionOverflowPlans,
            final Map<Long, Map<String, Map<String, Map<String, Integer>>>> binnedRegionResolutionDNSCounts,
            final Map<Long, Map<String, Map<String, Map<String, Object>>>> binnedRegionDNSDeviations) {
        List<Long> times = new LinkedList<>();
        times.addAll(binnedRlgRegionOverflowPlans.keySet());
        times.addAll(binnedRegionResolutionDNSCounts.keySet());
        Collections.sort(times);

        LOGGER.info("nodeToRegionMap: {}", nodeToRegionMap);

        final Set<String> regions = getRegions();

        for (long time : times) {
            Map<String, Map<String, Map<String, Object>>> regionDNSDeviationsAtTime = binnedRegionDNSDeviations
                    .computeIfAbsent(time, k -> new HashMap<>());

            for (String planRegion : regions) {
                for (String service : services) {
                    Map<String, Double> expectedResolutionDistribution = binnedRlgRegionOverflowPlans
                            .getOrDefault(time, new HashMap<>()).getOrDefault(planRegion, new HashMap<>())
                            .getOrDefault(service, new HashMap<>());

                    Map<String, Integer> actualResolutionDistribution = binnedRegionResolutionDNSCounts
                            .getOrDefault(time, new HashMap<>()).getOrDefault(planRegion, new HashMap<>())
                            .getOrDefault(service, new HashMap<>());

                    int actualTotalResolutions = 0;

                    for (String destinationRegion : regions) {
                        actualTotalResolutions += actualResolutionDistribution.getOrDefault(destinationRegion, 0);
                    }

                    for (String destinationRegion : regions) {
                        final int actual = actualResolutionDistribution.getOrDefault(destinationRegion, 0);
                        final double expected = actualTotalResolutions
                                * expectedResolutionDistribution.getOrDefault(destinationRegion, 0.0);
                        // final double deviation = actual - expected;

                        regionDNSDeviationsAtTime.computeIfAbsent(planRegion, k -> new HashMap<>())
                                .computeIfAbsent(service, k -> new HashMap<>())
                                .computeIfAbsent(destinationRegion, r -> (actual + " - " + expected));
                    }
                }
            }
        }
    }

    private void readDNSFile(File dnsCSVFile,
            Map<ImmutableList<String>, List<CSVRecord>> dnsEntries,
            Map<Long, Map<String, Map<String, Map<String, Integer>>>> binnedContainerResolutionDNSCounts,
            Map<Long, Map<String, Map<String, Map<String, Integer>>>> binnedRegionResolutionDNSCounts) {
        try (CSVParser parser = new CSVParser(
                new InputStreamReader(new FileInputStream(dnsCSVFile), Charset.defaultCharset()),
                CSVFormat.EXCEL.withHeader(CSV_HEADER).withSkipHeaderRecord())) {
            parser.forEach((record) -> {
                try {
                    processDNSLine(record, dnsEntries, binnedContainerResolutionDNSCounts,
                            binnedRegionResolutionDNSCounts);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Skipping a problematic record in file {}: {}", dnsCSVFile, record);
                }
            });
        } catch (NoSuchFileException | FileNotFoundException e) {
            LOGGER.error("Could not find DNS file: {}", dnsCSVFile, e);
        } catch (IOException e) {
            LOGGER.error("Could not read DNS file: {}", dnsCSVFile, e);
        }
    }

    private void processDNSLine(CSVRecord dnsLine,
            Map<ImmutableList<String>, List<CSVRecord>> dnsEntries,
            Map<Long, Map<String, Map<String, Map<String, Integer>>>> binnedContainerResolutionDNSCounts,
            Map<Long, Map<String, Map<String, Map<String, Integer>>>> binnedRegionResolutionDNSCounts)
            throws IllegalArgumentException {

        long timestamp = Long.parseLong(dnsLine.get(CSV_HEADER_TIMESTAMP));
        long binnedTime = ChartGenerationUtils.getBinForTime(timestamp, 0, binSize);

        String clientAddress = dnsLine.get(CSV_HEADER_CLIENT_ADDRESS); // source
                                                                       // node
        String clientNode = mapNodeAddressToNodeName(mapContainerIpToNodeIp(clientAddress));
        String sourceRegion = mapAddressToRegion(mapContainerIpToNodeIp(clientAddress));

        if (clientNode == null) {
            LOGGER.warn("Could not find client node for address '{}'", clientAddress);
        }

        if (sourceRegion == null) {
            LOGGER.warn("Could not find source region for address '{}'", clientAddress);
        }

        String nameToResolve = dnsLine.get(CSV_HEADER_NAME_TO_RESOLVE); // service

        String serviceName = mapServiceDomainNameToServiceName(nameToResolve);
        String nameToResolveRegion = mapAddressToRegion(nameToResolve);

        if (serviceName == null) {
            LOGGER.warn("Could not find service name for name to resolve '{}'", nameToResolve);
        }

        String destinationAddress = dnsLine.get(CSV_HEADER_RESOLVED_NAME);
        String containerName = mapAddressToContainerName(destinationAddress);
        String destinationRegion = mapAddressToRegion(destinationAddress);

        if (destinationRegion == null) {
            LOGGER.warn("Could not find region for destination address '{}'", destinationAddress);
        }

        LOGGER.trace("Mapped DNS entry [{}-->{}, {}, {}] to [{}, {}, {}]", clientAddress, clientNode, nameToResolve,
                destinationAddress, sourceRegion, serviceName, destinationRegion);

        if (sourceRegion != null && destinationRegion != null && serviceName != null) {
            sourceRegions.add(sourceRegion);
            services.add(serviceName);

            destinationRegions.add(destinationRegion);

            // source region -> destination region
            {
                final ImmutableList<String> keyValues = ImmutableList.of(sourceRegion, destinationRegion);
                dnsEntries.computeIfAbsent(keyValues, k -> new ArrayList<>()).add(dnsLine);
            }

            // source region -> service -> destination region
            {
                final ImmutableList<String> keyValues = ImmutableList.of(sourceRegion, serviceName, destinationRegion);
                dnsEntries.computeIfAbsent(keyValues, k -> new ArrayList<>()).add(dnsLine);
            }

            // service
            {
                final ImmutableList<String> keyValues = ImmutableList.of(serviceName);
                dnsEntries.computeIfAbsent(keyValues, k -> new ArrayList<>()).add(dnsLine);
            }

            // stores a suffix indicating whether clientNode is a MAP client or
            // NCP making a request,
            // or a DNS server making a request
            String clientType;

            // if the source address is either for an edge client or NCP acting
            // as a client
            // (nameToResolve has no region or requester is in the same region
            // as nameToResolveRegion)
            if (nameToResolveRegion == null || sourceRegion.equals(nameToResolveRegion)) {
                clientType = "";
            } else // if the source address is for an NCP acting as a DNS server
                   // making a request
            {
                clientType = "-dns";
            }

            String clientName = sourceRegion + "-" + clientNode + clientType;

            // increment count for destination region if the entry is either a
            // direct service to container resolution
            // or name to resolve has a region (entry mapping is service.region
            // --> service.region or service.region --> container)
            if (containerName != null || nameToResolveRegion != null) {
                LOGGER.trace(
                        "Incrementing region '{}' count for service '{}', time {}, client '{}', name to resolve region '{}'",
                        destinationRegion, serviceName, binnedTime, clientName, nameToResolveRegion);

                binnedRegionResolutionDNSCounts.computeIfAbsent(binnedTime, k -> new HashMap<>())
                        .computeIfAbsent(nameToResolveRegion, k -> new HashMap<>())
                        .computeIfAbsent(serviceName, k -> new HashMap<>()).merge(destinationRegion, 1, Integer::sum);
            }

            // increment count for container if destination is a container
            // address
            if (containerName != null) {
                LOGGER.trace("Incrementing container '{}' count for service '{}', time {}, client '{}'",
                        destinationRegion + "-" + containerName, serviceName, binnedTime, clientName);

                binnedContainerResolutionDNSCounts.computeIfAbsent(binnedTime, k -> new HashMap<>())
                        .computeIfAbsent(nameToResolveRegion, k -> new HashMap<>())
                        .computeIfAbsent(serviceName, k -> new HashMap<>())
                        .merge(destinationRegion + "-" + containerName, 1, Integer::sum);
            }
        }
    }

    /**
     * Removes .map.dcomp or .[region].map.dcomp from service domainName
     * 
     * @param domainName
     *            domain name of service
     * @return base part of domain name
     */
    private String serviceDomainToBaseName(String domainName) {
        return domainName.replaceFirst("(\\..+?)?" + Pattern.quote(ChartGenerationUtils.MAP_DOMAIN_NAME_SUFFIX) + "\\z",
                "");
    }

    private synchronized String mapServiceDomainNameToServiceName(String addressToResolve) {
        String baseName = serviceDomainToBaseName(addressToResolve);

        if (baseName.endsWith("-containers")) {
            // handle 2 layer DNS
            baseName = baseName.substring(0, baseName.length() - "-containers".length());
        }

        if (domainBaseToServiceNameMap.containsKey(baseName)) {
            return domainBaseToServiceNameMap.get(baseName);
        } else {
            LOGGER.warn(
                    "Service address '{}' could not be mapped to service name using service configurations, defaulting to '{}'",
                    addressToResolve, baseName);
            return baseName;
        }
    }

    private synchronized String mapAddressToContainerName(String address) {
        address = address // drop MAP domain name suffix from the name - this
                          // comes from the hi-fi environment
                .replaceFirst(Pattern.quote(ChartGenerationUtils.MAP_DOMAIN_NAME_SUFFIX) + "\\z", "");

        // if the remaining part of address contains a ".", it cannot be a
        // container name
        if (address.matches(".*\\..*")) {
            return null;
        }

        // if address contains "_c", then it is probably a container name
        if (address.toLowerCase().matches(".*_c.*")) {
            return address;
        }

        return null;
    }

    private synchronized String mapAddressToRegion(String address) {
        // if address refers to a service region, parse the region name from the
        // address
        if (address.matches("\\A.*?\\..+" + Pattern.quote(ChartGenerationUtils.MAP_DOMAIN_NAME_SUFFIX) + "\\z")) {
            // turn "simple-webserver.D.map.dcomp" into "D"
            return //
                   // remove the first component of the name
                   // "simple-webserver.D.map.dcomp" -> "D.map.dcomp"
            address.replaceFirst("\\A.*?\\.", "")//
                    // remove ".map.dcomp" leaving "D"
                    .replaceFirst(Pattern.quote(ChartGenerationUtils.MAP_DOMAIN_NAME_SUFFIX) + "\\z", "");
        }

        // if address refers to a single node or container
        String nodeRegion = mapNodeAddressToRegion(address);

        if (nodeRegion != null) {
            return nodeRegion;
        }

        LOGGER.trace("The address '{}' could not be mapped to a region.", address);
        return null;
    }

    private synchronized String mapNodeAddressToRegion(String nodeAddress) {
        return nodeToRegionMap.get(mapNodeAddressToNodeName(nodeAddress).toLowerCase());
    }

    private synchronized String mapNodeAddressToNodeName(String nodeAddress) {
        // remove container suffix if it is present
        nodeAddress = nodeAddress //
                // drop .map.dcomp from the name - this comes from the hi-fi
                // environment
                .replaceFirst(Pattern.quote(ChartGenerationUtils.MAP_DOMAIN_NAME_SUFFIX) + "\\z", "")//
                // remove _c<digits> from the name - this is how containers are
                // named by convention
                .replaceFirst("_c\\d+\\z", "")//
                // lowercase everything to make lookups consistent
                .toLowerCase();

        return ipAddressToNodeMap.getOrDefault(nodeAddress, nodeAddress);
    }

    private void processScenarioConfiguration(String scenarioName, Path scenarioFolder) {
        LOGGER.info("Process scenario configuration for '{}' in folder '{}'.", scenarioName, scenarioFolder);

        nodeToRegionMap.clear();

        Topology topology;
        try {
            topology = NS2Parser.parse(scenarioName, scenarioFolder);

            topology.getNodes().forEach((name, node) -> {
                String regionName = NetworkServerProperties.parseRegionName(node.getExtraData());
                nodeToRegionMap.put(node.getName().toLowerCase(), regionName);

                node.getAllIpAddresses().forEach((link, ipAddress) -> {
                    if (ipAddress != null) {
                        ipAddressToNodeMap.put(ipAddress.getHostAddress(), node.getName());
                    }
                });
            });

        } catch (IOException e) {
            LOGGER.error("Unable to parse ns2 file:", e);
        }

        LOGGER.debug("Node to region map: {}", nodeToRegionMap);
        LOGGER.debug("IP address to node map: {}", ipAddressToNodeMap);

        Path serviceConfigurationsPath = scenarioFolder.resolve(Simulation.SERVICE_CONFIGURATIONS_FILENAME);

        try {
            ImmutableCollection<ServiceConfiguration> serviceConfigurations = ServiceConfiguration
                    .parseServiceConfigurations(serviceConfigurationsPath).values();

            for (ServiceConfiguration serviceConfiguration : serviceConfigurations) {
                final String serviceArtifactName = serviceConfiguration.getService().getArtifact();
                final String serviceDomainName = serviceConfiguration.getHostname();
                final String serviceDomainNameBase = serviceDomainToBaseName(serviceDomainName);

                domainBaseToServiceNameMap.put(serviceDomainNameBase, serviceArtifactName);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read service configurations file {}:", serviceConfigurationsPath, e);
        }

        LOGGER.debug("Service domain name to service name map: {}", domainBaseToServiceNameMap);
    }

    private void outputDNSLinesToCSV(File outputFolder,
            String filenamePrefix,
            Map<ImmutableList<String>, List<CSVRecord>> dnsEntries) {
        if (!outputFolder.exists()) {
            if (!outputFolder.mkdirs()) {
                throw new RuntimeException("Unable to create directory " + outputFolder);
            }
        }

        // Make sure empty files are written out where there is no data. This is
        // why we don't just iterate over the keys.

        // output: source region -> destination region
        for (final String sourceRegion : sourceRegions) {
            for (final String destinationRegion : destinationRegions) {
                final ImmutableList<String> keyValues = ImmutableList.of(sourceRegion, destinationRegion);
                final List<CSVRecord> entries = dnsEntries.getOrDefault(keyValues, Collections.emptyList());
                outputDNSLinesToCSV(outputFolder, filenamePrefix, keyValues, entries);
            }
        }

        // output: source region -> service -> destination region
        for (String sourceRegion : sourceRegions) {
            for (String service : services) {
                for (String destinationRegion : destinationRegions) {
                    final ImmutableList<String> keyValues = ImmutableList.of(sourceRegion, service, destinationRegion);
                    final List<CSVRecord> entries = dnsEntries.getOrDefault(keyValues, Collections.emptyList());
                    outputDNSLinesToCSV(outputFolder, filenamePrefix, keyValues, entries);
                }
            }
        }

        // output: service
        for (String service : services) {
            final ImmutableList<String> keyValues = ImmutableList.of(service);
            final List<CSVRecord> entries = dnsEntries.getOrDefault(keyValues, Collections.emptyList());
            outputDNSLinesToCSV(outputFolder, filenamePrefix, keyValues, entries);
        }
    }

    private void outputDNSLinesToCSV(final File outputFolder,
            final String filenamePrefix,
            final ImmutableList<String> keyValues,
            final List<CSVRecord> entries) {
        StringBuilder b = new StringBuilder();

        b.append(filenamePrefix);

        for (int k = 0; k < keyValues.size(); k++) {
            if (k > 0)
                b.append("--");

            b.append(keyValues.get(k));
        }

        b.append(ChartGenerationUtils.CSV_FILE_EXTENSION);

        final File file = new File(outputFolder + File.separator + b.toString());
        outputDNSLinesToCSV(file, entries);
    }

    private void outputDNSLinesToCSV(File outputFile, List<CSVRecord> dnsEntries) {
        dnsEntries.sort(LATENCY_CSV_RECORD_COMPARATOR);

        CSVFormat format = CSVFormat.EXCEL.withHeader(CSV_HEADER);

        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(new FileOutputStream(outputFile), Charset.defaultCharset()), format)) {
            for (CSVRecord dnsEntry : dnsEntries) {
                printer.printRecord(dnsEntry);
            }
        } catch (IOException e) {
            LOGGER.error("Error writing to CSV file {}:", outputFile, e);
            e.printStackTrace();
        }
    }

    private void outputRequestCountsToCSV(File outputFolder,
            Map<Long, Map<String, Map<String, Map<String, Integer>>>> binnedContainerResolutionDNSCounts,
            Map<Long, Map<String, Map<String, Map<String, Integer>>>> binnedRegionResolutionDNSCounts) {

        // service --> container names
        Map<String, Set<String>> serviceContainers = new HashMap<>();

        Set<String> clients = new HashSet<>();

        binnedRegionResolutionDNSCounts.forEach((timeBin, clientServiceRegionCounts) -> {
            clientServiceRegionCounts.forEach((client, serviceRegionCounts) -> {
                clients.add(client);
            });
        });

        binnedContainerResolutionDNSCounts.forEach((timeBin, clientServiceContainerCounts) -> {
            clientServiceContainerCounts.forEach((client, serviceContainerCounts) -> {
                clients.add(client);

                serviceContainerCounts.forEach((service, containerCounts) -> {
                    Set<String> containers = serviceContainers.computeIfAbsent(service, k -> new HashSet<>());
                    containers.addAll(containerCounts.keySet());
                });
            });
        });

        List<String> regions = new ArrayList<>(getRegions());
        Collections.sort(regions);

        Set<Long> timeBinSet = new HashSet<>();
        timeBinSet.addAll(binnedContainerResolutionDNSCounts.keySet());
        timeBinSet.addAll(binnedRegionResolutionDNSCounts.keySet());

        List<Long> timeBins = new ArrayList<>();
        timeBins.addAll(timeBinSet);
        Collections.sort(timeBins);

        long minTime = 0;
        long maxTime = 0;

        if (!timeBins.isEmpty()) {
            minTime = timeBins.get(0);
            maxTime = timeBins.get(timeBins.size() - 1);
            timeBins.clear();

            for (long t = minTime; t <= maxTime; t += binSize) {
                timeBins.add(t);
            }
        }

        LOGGER.debug("serviceContainers: {}", serviceContainers);

        for (String service : services) {
            // time bin --> service --> container name --> count
            Map<Long, Map<String, Map<String, Integer>>> binnedServiceTotalContainerResolutionCounts = new HashMap<>();

            // time bin --> service --> region name --> count
            Map<Long, Map<String, Map<String, Integer>>> binnedServiceTotalRegionResolutionCounts = new HashMap<>();

            List<String> containerList = new ArrayList<>();

            if (serviceContainers.containsKey(service)) {
                containerList.addAll(serviceContainers.get(service));
            }

            if (containerList.isEmpty()) {
                LOGGER.warn("outputRequestCountsToCSV: Found no containers for service '{}'.", service);
            }

            Collections.sort(containerList);

            String[] header = new String[1 + regions.size() + containerList.size()];
            header[0] = ChartGenerationUtils.CSV_TIME_COLUMN_LABEL;

            for (int r = 0; r < regions.size(); r++) {
                header[1 + r] = regions.get(r);
            }

            for (int c = 0; c < containerList.size(); c++) {
                header[1 + regions.size() + c] = containerList.get(c);
            }

            CSVFormat format = CSVFormat.EXCEL.withHeader(header);

            Set<String> regions2 = new HashSet<>();
            regions2.addAll(regions);
            // regions2.add(null);

            for (String nameToResolveRegion : regions2) {
                File clientServiceCSVFile = new File(
                        outputFolder + File.separator + OUTPUT_FILE_DNS_REQUEST_COUNT_PREFIX
                                + (nameToResolveRegion != null ? nameToResolveRegion : "") + "--" + service + ".csv");
                LOGGER.info("Outputting to nameToResolveRegion--service request count file: {}", clientServiceCSVFile);

                try (CSVPrinter printer = new CSVPrinter(
                        new OutputStreamWriter(new FileOutputStream(clientServiceCSVFile), Charset.defaultCharset()),
                        format)) {

                    for (int b = 0; b < timeBins.size(); b++) {
                        long time = timeBins.get(b);
                        Object[] record = new Object[header.length];

                        record[0] = time;

                        for (int r = 0; r < regions.size(); r++) {
                            int count = binnedRegionResolutionDNSCounts.getOrDefault(time, new HashMap<>())
                                    .getOrDefault(nameToResolveRegion, new HashMap<>())
                                    .getOrDefault(service, new HashMap<>()).getOrDefault(regions.get(r), 0);

                            binnedServiceTotalRegionResolutionCounts.computeIfAbsent(time, k -> new HashMap<>())
                                    .computeIfAbsent(service, k -> new HashMap<>())
                                    .merge(regions.get(r), count, Integer::sum);

                            record[1 + r] = count;
                        }
                        for (int c = 0; c < containerList.size(); c++) {
                            int count = binnedContainerResolutionDNSCounts.getOrDefault(time, new HashMap<>())
                                    .getOrDefault(nameToResolveRegion, new HashMap<>())
                                    .getOrDefault(service, new HashMap<>()).getOrDefault(containerList.get(c), 0);

                            binnedServiceTotalContainerResolutionCounts.computeIfAbsent(time, k -> new HashMap<>())
                                    .computeIfAbsent(service, k -> new HashMap<>())
                                    .merge(containerList.get(c), count, Integer::sum);

                            record[1 + regions.size() + c] = count;
                        }

                        printer.printRecord(record);
                    }

                } catch (IOException e) {
                    LOGGER.error("Error writing to CSV file {}:", clientServiceCSVFile, e);
                }
            }

            // output file for service
            File clientServiceCSVFile = new File(
                    outputFolder + File.separator + OUTPUT_FILE_DNS_REQUEST_COUNT_PREFIX + service + ".csv");
            LOGGER.info("Outputting to service request count file: {}", clientServiceCSVFile);

            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(clientServiceCSVFile), Charset.defaultCharset()),
                    format)) {

                for (int b = 0; b < timeBins.size(); b++) {
                    long time = timeBins.get(b);
                    Object[] record = new Object[header.length];

                    record[0] = time;

                    for (int r = 0; r < regions.size(); r++) {
                        int count = binnedServiceTotalRegionResolutionCounts.getOrDefault(time, new HashMap<>())
                                .getOrDefault(service, new HashMap<>()).getOrDefault(regions.get(r), 0);

                        record[1 + r] = count;
                    }

                    for (int c = 0; c < containerList.size(); c++) {
                        int count = binnedServiceTotalContainerResolutionCounts.getOrDefault(time, new HashMap<>())
                                .getOrDefault(service, new HashMap<>()).getOrDefault(containerList.get(c), 0);

                        record[1 + regions.size() + c] = count;
                    }

                    printer.printRecord(record);
                }

            } catch (IOException e) {
                LOGGER.error("Error writing to CSV file {}:", clientServiceCSVFile, e);
            }

        }
    }

    private void outputRequestDeviationsToCSV(File outputFolder,
            Map<Long, Map<String, Map<String, Map<String, Object>>>> binnedRegionDeviations) {

        // service --> container names
        Map<String, Set<String>> serviceContainers = new HashMap<>();

        Set<String> clients = new HashSet<>();

        binnedRegionDeviations.forEach((timeBin, clientServiceRegionCounts) -> {
            clientServiceRegionCounts.forEach((client, serviceRegionCounts) -> {
                clients.add(client);
            });
        });

        List<String> regions = new ArrayList<>(getRegions());
        Collections.sort(regions);

        Set<Long> timeBinSet = new HashSet<>();
        // timeBinSet.addAll(binnedContainerResolutionDNSCounts.keySet());
        timeBinSet.addAll(binnedRegionDeviations.keySet());

        List<Long> timeBins = new ArrayList<>();
        timeBins.addAll(timeBinSet);
        Collections.sort(timeBins);

        long minTime = 0;
        long maxTime = 0;

        if (!timeBins.isEmpty()) {
            minTime = timeBins.get(0);
            maxTime = timeBins.get(timeBins.size() - 1);
            timeBins.clear();

            for (long t = minTime; t <= maxTime; t += binSize) {
                timeBins.add(t);
            }
        }

        LOGGER.debug("serviceContainers: {}", serviceContainers);

        for (String service : services) {

            String[] header = new String[1 + regions.size()];
            header[0] = ChartGenerationUtils.CSV_TIME_COLUMN_LABEL;

            for (int r = 0; r < regions.size(); r++) {
                header[1 + r] = regions.get(r);
            }

            CSVFormat format = CSVFormat.EXCEL.withHeader(header);

            for (String nameToResolveRegion : regions) {
                File regionServiceCSVFile = new File(outputFolder + File.separator
                        + OUTPUT_FILE_DNS_RESPONSE_DEVIATION_PREFIX + nameToResolveRegion + "--" + service + ".csv");
                LOGGER.info("Outputting to nameToResolveRegion--service deviation file: {}", regionServiceCSVFile);

                try (CSVPrinter printer = new CSVPrinter(
                        new OutputStreamWriter(new FileOutputStream(regionServiceCSVFile), Charset.defaultCharset()),
                        format)) {

                    for (int b = 0; b < timeBins.size(); b++) {
                        long time = timeBins.get(b);
                        Object[] record = new Object[header.length];

                        record[0] = time;

                        for (int r = 0; r < regions.size(); r++) {
                            Object deviation = binnedRegionDeviations.getOrDefault(time, new HashMap<>())
                                    .getOrDefault(nameToResolveRegion, new HashMap<>())
                                    .getOrDefault(service, new HashMap<>()).getOrDefault(regions.get(r), 0.0);

                            record[1 + r] = deviation;
                        }

                        printer.printRecord(record);
                    }

                } catch (IOException e) {
                    LOGGER.error("Error writing to CSV file {}:", regionServiceCSVFile, e);
                }
            }
        }
    }

    /**
     * Sets the size of the bins to use for placing DNS resolutions in time
     * bins.
     * 
     * @param binSize
     *            size of bins in milliseconds.
     */
    public void setBinSize(long binSize) {
        this.binSize = binSize;
    }

    /**
     * Sets the RLG overflow plans to use for comparison with actual proportions
     * of delegate DNS resolutions to regions.
     * 
     * @param rlgOverflowPlan
     *            the RLG plans
     */
    public void setServiceRLGROverflowPlans(
            Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<RegionIdentifier, Double>>>>> rlgOverflowPlan) {
        LOGGER.debug("Setting RLG Overflow Plan: {}", rlgOverflowPlan);
        this.rlgRegionOverflowPlan = convertRLGOverflowPlan(rlgOverflowPlan);
    }

    private Map<Long, Map<String, Map<String, Map<String, Double>>>> convertRLGOverflowPlan(
            Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<RegionIdentifier, Double>>>>> rlgOverflowPlan) {
        // time --> plan region --> service name --> overflow destination region
        // name --> overflow amount
        final Map<Long, Map<String, Map<String, Map<String, Double>>>> rlgOverflowPlans = new HashMap<>();

        rlgOverflowPlan.forEach((service, servicePlans) -> {
            List<Long> times = new LinkedList<>();
            times.addAll(servicePlans.keySet());
            Collections.sort(times);

            for (long timestamp : times) {
                // long binnedTime =
                // ChartGenerationUtils.getBinForTime(timestamp, 0, binSize);

                servicePlans.get(timestamp).forEach((fromRegion, leaderNodesPlans) -> {
                    if (leaderNodesPlans.keySet().size() > 1) {
                        LOGGER.warn("Multiple RLG plans found in region '{}' for time {}-->{} from leaders {}",
                                fromRegion, timestamp, timestamp, leaderNodesPlans.keySet());
                    }

                    leaderNodesPlans.forEach((leaderNode, plan) -> {
                        // for empty plans, specify that 100% of traffic remains
                        // within the region of the RLG plan
                        if (plan.isEmpty()) {
                            plan.put(fromRegion, 1.0);
                        }

                        // set plan for time to be the first plan published
                        // within its time range
                        rlgOverflowPlans.computeIfAbsent(timestamp, k -> new HashMap<>())
                                .computeIfAbsent(fromRegion.getName(), k -> new HashMap<>())
                                .computeIfAbsent(ChartGenerationUtils.serviceToFilenameString(service), k -> {
                                    Map<String, Double> timePlanForDestinationRegion = new HashMap<>();

                                    double total = 0.0;

                                    for (double overflow : plan.values()) {
                                        total += overflow;
                                    }

                                    for (Entry<RegionIdentifier, Double> entry : plan.entrySet()) {
                                        timePlanForDestinationRegion.put(entry.getKey().getName(),
                                                entry.getValue() / total);
                                    }

                                    return timePlanForDestinationRegion;
                                });
                    });
                });
            }
        });

        return rlgOverflowPlans;
    }

    private FileFilter hifiDnsCSVFileFilter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            if (file.getName().matches("weighted-dns" + Pattern.quote(ChartGenerationUtils.CSV_FILE_EXTENSION)))
                return true;

            return false;
        }
    };

    private FileFilter lofiDnsCSVFileFilter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            if (file.getName().matches("dns-.*" + Pattern.quote(ChartGenerationUtils.CSV_FILE_EXTENSION)))
                return true;

            return false;
        }
    };

    /**
     * @return the regions from {@link #nodeToRegionMap}. This filters out the
     *         null regions that come from underlay nodes.
     */
    private Set<String> getRegions() {
        return nodeToRegionMap.entrySet().stream().map(Map.Entry::getValue).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private class ClientLatencyRecordWorker implements Runnable {
        private String service;
        private String clientName;
        private String clientRegion;
        private File clientServiceFolder;
        private boolean outputModifiedLatencyFiles;

        // resolution region --> count
        private final Map<String, Integer> regionResolutionCounts = new HashMap<>();

        // back end service --> resolution region --> count
        private final Map<String, Map<String, Integer>> dependentServiceRegionResolutionCounts = new HashMap<>();

        // container instance --> list of CSV latency
        private Map<String, List<Map<String, Object>>> latencyRecords = null;

        // back end service --> container instance --> list of CSV latency
        private Map<String, Map<String, List<Map<String, Object>>>> dependentServiceLatencyRecords = null;

        ClientLatencyRecordWorker(String service,
                String clientName,
                String clientRegion,
                File clientServiceFolder,
                boolean outputModifiedLatencyFiles) {
            this.service = service;
            this.clientName = clientName;
            this.clientRegion = clientRegion;
            this.clientServiceFolder = clientServiceFolder;
            this.outputModifiedLatencyFiles = outputModifiedLatencyFiles;
        }

        Map<String, List<Map<String, Object>>> getLatencyRecords() {
            return latencyRecords;
        }

        Map<String, Map<String, List<Map<String, Object>>>> getDependentServiceLatencyRecords() {
            return dependentServiceLatencyRecords;
        }

        Map<String, Integer> getRegionResolutionCounts() {
            return regionResolutionCounts;
        }

        Map<String, Map<String, Integer>> getDependentServiceRegionResolutionCounts() {
            return dependentServiceRegionResolutionCounts;
        }

        String getService() {
            return service;
        }

        String getClientName() {
            return clientName;
        }

        String getClientRegion() {
            return clientRegion;
        }

        @Override
        public void run() {
            LOGGER.debug("Starting processing for service '{}' client '{}'.", service, clientName);

            final Map<String, List<Map<String, Object>>> latencyRecords = new HashMap<>();
            final Map<String, Map<String, List<Map<String, Object>>>> dependentServiceLatencyRecords = new HashMap<>();

            final File[] clientServiceContainerFolders = clientServiceFolder.listFiles();

            if (clientServiceContainerFolders != null) {
                for (File clientServiceContainerFolder : clientServiceContainerFolders) {
                    final String containerInstanceName = clientServiceContainerFolder.getName();

                    if (clientServiceContainerFolder.isDirectory()) {
                        final File processingLatencyFile = clientServiceContainerFolder.toPath()
                                .resolve("app_metrics_data").resolve("processing_latency.csv").toFile();

                        final File requestStatusFile = clientServiceContainerFolder.toPath().resolve("app_metrics_data")
                                .resolve("request_status.csv").toFile();

                        final File modifiedContainerInstanceLatencyFile = clientServiceContainerFolder.toPath()
                                .resolve("app_metrics_data").resolve("processing_latency-with_hop_count.csv").toFile();

                        if (processingLatencyFile.exists()) {
                            final List<Map<String, Object>> containerInstanceLatencyRecords = new LinkedList<>();

                            processClientLatencyFile(processingLatencyFile, service, containerInstanceName,
                                    regionResolutionCounts, containerInstanceLatencyRecords);

                            processClientRequestStatusFile(requestStatusFile, containerInstanceLatencyRecords);

                            latencyRecords.merge(containerInstanceName, containerInstanceLatencyRecords, (a, b) -> {
                                List<Map<String, Object>> c = new LinkedList<>();
                                c.addAll(a);
                                c.addAll(b);
                                Collections.sort(c, LATENCY_RECORD_COMPARATOR);

                                return c;
                            });

                            if (outputModifiedLatencyFiles) {
                                outputModifiedContainerInstanceLatencyFile(modifiedContainerInstanceLatencyFile,
                                        containerInstanceLatencyRecords);
                            }
                        } else {
                            LOGGER.warn("Latency file does not exist: {}", processingLatencyFile.getAbsolutePath());
                        }

                        // process dependent service request latency data

                        File[] clientFrontEndServiceContainerTimeFolders = clientServiceContainerFolder.listFiles();

                        if (clientFrontEndServiceContainerTimeFolders != null) {
                            for (File clientFrontEndServiceContainerTimeFolder : clientFrontEndServiceContainerTimeFolders) {
                                if (clientServiceContainerFolder.isDirectory()
                                        && !clientFrontEndServiceContainerTimeFolder.getName()
                                                .equals("app_metrics_data")) {

                                    File[] clientServiceContainerTimeFolders = clientServiceContainerFolder.listFiles();

                                    if (clientServiceContainerTimeFolders != null) {
                                        for (File clientServiceContainerTimeFolder : clientServiceContainerTimeFolders) {
                                            final File dependentServicesParentFolder = clientServiceContainerTimeFolder
                                                    .toPath().resolve("app_metrics_data").resolve("dependent-services")
                                                    .toFile();

                                            File[] backEndServiceFolders = dependentServicesParentFolder.listFiles();

                                            if (backEndServiceFolders != null) {
                                                LOGGER.debug("Found depenent-services folder: {}",
                                                        dependentServicesParentFolder);

                                                for (File backEndServiceFolder : backEndServiceFolders) {
                                                    final String backEndServiceGroupArtifactVersionString = backEndServiceFolder
                                                            .getName();
                                                    final String backEndService = ChartGenerationUtils
                                                            .serviceGroupArtifactVersionFolderNameToService(
                                                                    backEndServiceGroupArtifactVersionString);

                                                    File[] backEndServiceTimeFolders = backEndServiceFolder.listFiles();

                                                    if (backEndServiceTimeFolders != null) {
                                                        for (File backEndServiceTimeFolder : backEndServiceTimeFolders) {
                                                            String backEndServiceTime = backEndServiceTimeFolder
                                                                    .getName();

                                                            final File backEndServiceProcessingLatencyFile = backEndServiceTimeFolder
                                                                    .toPath().resolve("processing_latency.csv")
                                                                    .toFile();

                                                            final File backEndServiceRequestStatusFile = backEndServiceTimeFolder
                                                                    .toPath().resolve("request_status.csv").toFile();

                                                            List<Map<String, Object>> backendServiceContainerInstanceLatencyRecords = dependentServiceLatencyRecords
                                                                    .computeIfAbsent(backEndService,
                                                                            k -> new HashMap<>())
                                                                    .computeIfAbsent(backEndServiceTime,
                                                                            k -> new LinkedList<>());

                                                            LOGGER.debug("backEndServiceProcessingLatencyFile = {}",
                                                                    backEndServiceProcessingLatencyFile);

                                                            Map<String, Integer> backEndServiceRegionResolutionCounts = dependentServiceRegionResolutionCounts
                                                                    .computeIfAbsent(backEndService,
                                                                            k -> new HashMap<>());

                                                            processClientLatencyFile(
                                                                    backEndServiceProcessingLatencyFile, backEndService,
                                                                    backEndServiceTime,
                                                                    backEndServiceRegionResolutionCounts,
                                                                    backendServiceContainerInstanceLatencyRecords);

                                                            processClientRequestStatusFile(
                                                                    backEndServiceRequestStatusFile,
                                                                    backendServiceContainerInstanceLatencyRecords);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            this.latencyRecords = latencyRecords;
            this.dependentServiceLatencyRecords = dependentServiceLatencyRecords;
            LOGGER.info("Finished processing for service '{}' client '{}'.", service, clientName);
        }

        private void processClientLatencyFile(File processingLatencyFile,
                String service,
                String containerInstanceName,
                Map<String, Integer> regionResolutionCounts,
                List<Map<String, Object>> containerInstanceLatencyRecords) {

            try (CSVParser parser = new CSVParser(
                    new InputStreamReader(new FileInputStream(processingLatencyFile), Charset.defaultCharset()),
                    CSVFormat.EXCEL.withHeader().withSkipHeaderRecord())) {
                parser.forEach((record) -> {
                    Map<String, Object> recordMap = csvLatencyRecordToMap(record);

                    if (recordMap != null) {
                        String serverAddress = (String) recordMap.get("server");
                        String serverNodeAddress = mapContainerIpToNodeIp(serverAddress);
                        String serverRegion = mapNodeAddressToRegion(serverNodeAddress);

                        regionResolutionCounts.merge(serverRegion, 1, Integer::sum);

                        // add additional information to record as a Map and add
                        // the Map to latencyEntries
                        recordMap.put("client", clientName);
                        RouteHopInfo hopInfo = getClientToServerNodeRouteHopInfo(clientName, serverNodeAddress);
                        recordMap.put("hop_count", (hopInfo != null ? Integer.toString(hopInfo.getHops())
                                : ChartGenerationUtils.EMPTY_CELL_VALUE.toString()));

                        containerInstanceLatencyRecords.add(recordMap);
                    } else {
                        LOGGER.warn("Found issues with record {} in file {}.", record, processingLatencyFile);
                    }
                });

                LOGGER.trace("Processed latency file: {}", processingLatencyFile);
            } catch (NoSuchFileException | FileNotFoundException e) {
                LOGGER.error("Could not find latency file {}:", processingLatencyFile, e);
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.error("Error processing latency file {}:", processingLatencyFile, e);
            }
        }

        private void processClientRequestStatusFile(File requestStatusFile,
                List<Map<String, Object>> containerInstanceLatencyRecords) {

            try (CSVParser parser = new CSVParser(
                    new InputStreamReader(new FileInputStream(requestStatusFile), Charset.defaultCharset()),
                    CSVFormat.EXCEL.withHeader().withSkipHeaderRecord())) {
                parser.forEach((record) -> {
                    final Map<String, Object> recordMap = csvRequestStatusToMap(record);

                    if (recordMap != null) {
                        final long requestStatusTimestamp = (Long) recordMap.get("timestamp");

                        boolean matchFound = false;

                        // check to see if any of the existing records from
                        // processing_latency.csv have a time_sent
                        // equal to the timestamp of this request_status.csv
                        // record and, if so, merge in the data
                        for (Map<String, Object> containerInstanceLatencyRecord : containerInstanceLatencyRecords) {
                            final Long requestTimeSent = (Long) containerInstanceLatencyRecord.get("time_sent");

                            if (requestTimeSent != null && requestStatusTimestamp == requestTimeSent) {
                                matchFound = true;
                                containerInstanceLatencyRecord.put("success", recordMap.get("success"));
                                containerInstanceLatencyRecord.put("message", recordMap.get("message"));
                            }
                        }

                        // add a new record if a no existing record has a
                        // processing_latency.csv time_sent
                        // equal to timestamp of request_status.csv record
                        if (!matchFound) {
                            recordMap.put("client", clientName);
                            recordMap.put("time_sent", recordMap.get("timestamp"));

                            final Object address = (String) recordMap.get("address");

                            if (address != null && !((String) address).equals("")) {
                                recordMap.put("server", recordMap.get("address"));
                            }
                            containerInstanceLatencyRecords.add(recordMap);
                        }
                    } else {
                        LOGGER.warn("Found issues with record {} in file {}.", record, requestStatusFile);
                    }
                });

                LOGGER.trace("Processed request status file: {}", requestStatusFile);
            } catch (NoSuchFileException | FileNotFoundException e) {
                LOGGER.error("Could not find request status file {}:", requestStatusFile, e);
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.error("Error processing request status file {}:", requestStatusFile, e);
            }
        }
    }

    private class ServerLatencyRecordWorker implements Runnable {
        private String service;
        private String serverName;
        private String serverRegion;
        private File serverServiceFolder;

        // container instance --> list of CSV latency
        private Map<String, List<Map<String, Object>>> latencyRecords = null; // =
                                                                              // new
                                                                              // HashMap<>();

        ServerLatencyRecordWorker(String service, String serverName, String serverRegion, File serverServiceFolder) {
            this.service = service;
            this.serverName = serverName;
            this.serverRegion = serverRegion;
            this.serverServiceFolder = serverServiceFolder;
        }

        Map<String, List<Map<String, Object>>> getLatencyRecords() {
            return latencyRecords;
        }

        String getService() {
            return service;
        }

        String getServerName() {
            return serverName;
        }

        String getServerRegion() {
            return serverRegion;
        }

        @Override
        public void run() {
            LOGGER.debug("Starting processing for service '{}' server '{}'.", service, serverName);

            final Map<String, List<Map<String, Object>>> latencyRecords = new HashMap<>();

            File[] serverServiceContainerIdFolders = serverServiceFolder.listFiles();

            if (serverServiceContainerIdFolders != null) {
                for (File serverServiceContainerIdFolder : serverServiceContainerIdFolders) {
                    File[] serverServiceContainerInstanceFolders = serverServiceContainerIdFolder.listFiles();

                    if (serverServiceContainerInstanceFolders != null) {
                        for (File serverServiceContainerInstanceFolder : serverServiceContainerInstanceFolders) {
                            String containerInstanceName = serverServiceContainerIdFolder.getName() + "__"
                                    + serverServiceContainerInstanceFolder.getName();

                            File processingLatencyFile = serverServiceContainerInstanceFolder.toPath()
                                    .resolve("app_metrics_data").resolve("processing_latency.csv").toFile();

                            if (processingLatencyFile.exists()) {
                                List<Map<String, Object>> containerInstanceLatencyRecords = new LinkedList<>();

                                processServerLatencyFile(processingLatencyFile, containerInstanceName,
                                        containerInstanceLatencyRecords);

                                latencyRecords.merge(containerInstanceName, containerInstanceLatencyRecords, (a, b) -> {
                                    List<Map<String, Object>> c = new LinkedList<>();
                                    c.addAll(a);
                                    c.addAll(b);
                                    Collections.sort(c, LATENCY_RECORD_COMPARATOR);

                                    return c;
                                });

                                // outputModifiedContainerInstanceLatencyFile(modifiedContainerInstanceLatencyFile,
                                // containerInstanceLatencyRecords);
                            }
                        }
                    }
                }
            }

            this.latencyRecords = latencyRecords;
            LOGGER.info("Finished processing for service '{}' server '{}'.", service, serverName);
        }

        private void processServerLatencyFile(File processingLatencyFile,
                String containerInstanceName,
                List<Map<String, Object>> containerInstanceLatencyRecords) {

            try (CSVParser parser = new CSVParser(
                    new InputStreamReader(new FileInputStream(processingLatencyFile), Charset.defaultCharset()),
                    CSVFormat.EXCEL.withHeader().withSkipHeaderRecord())) {
                parser.forEach((record) -> {
                    Map<String, Object> recordMap = csvLatencyRecordToMap(record);

                    if (recordMap != null) {
                        String clientNodeAddress = (String) recordMap.get("client");

                        // add additional information to record as a Map and add
                        // the Map to latencyEntries
                        recordMap.put("server", serverName);
                        RouteHopInfo hopInfo = getClientToServerNodeRouteHopInfo(clientNodeAddress, serverName);
                        recordMap.put("hop_count", (hopInfo != null ? Integer.toString(hopInfo.getHops())
                                : ChartGenerationUtils.EMPTY_CELL_VALUE.toString()));

                        containerInstanceLatencyRecords.add(recordMap);
                    } else {
                        LOGGER.warn("Found issues with record {} in file {}.", record, processingLatencyFile);
                    }
                });

                LOGGER.info("Processed latency file: {}", processingLatencyFile);
            } catch (NoSuchFileException | FileNotFoundException e) {
                LOGGER.error("Could not find latency file: {}", processingLatencyFile, e);
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.error("Error processing latency file: {}", processingLatencyFile, e);
            }
        }
    }

    private Map<String, Object> csvLatencyRecordToMap(CSVRecord record) {
        Map<String, String> map = record.toMap();
        Map<String, Object> recordMap = new HashMap<>();

        AtomicBoolean validRecord = new AtomicBoolean(true);

        map.forEach((column, value) -> {
            switch (column) {
            case "timestamp":
            case "time_sent":
            case "time_received":
            case "time_ack_received":
            case "time_finished":
            case "latency":
                try {
                    Long longValue = Long.parseLong(value);
                    recordMap.put(column, longValue);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Unable to parse long value {} in column {}:", value, column, e);
                    validRecord.set(false);
                }
                break;

            default:
                if (!value.equals("")) {
                    recordMap.put(column, value);
                } else {
                    LOGGER.warn("Found empty value in CSV record colum '{}': {}", column, map);
                    validRecord.set(false);
                }
                break;
            }
        });

        if (validRecord.get()) {
            return recordMap;
        } else {
            return null;
        }
    }

    private Map<String, Object> csvRequestStatusToMap(CSVRecord record) {
        Map<String, String> map = record.toMap();
        Map<String, Object> recordMap = new HashMap<>();

        AtomicBoolean validRecord = new AtomicBoolean(true);

        map.forEach((column, value) -> {
            switch (column) {
            case "timestamp":
                try {
                    Long longValue = Long.parseLong(value);
                    recordMap.put(column, longValue);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Unable to parse long value {} in column {}:", value, column, e);
                    validRecord.set(false);
                }
                break;

            case "address":
            case "success":
            case "message":
                recordMap.put(column, value);
                break;

            default:
                if (!value.equals("")) {
                    recordMap.put(column, value);
                } else {
                    LOGGER.warn("Found empty value in CSV record colum '{}': {}", column, map);
                    validRecord.set(false);
                }
                break;
            }
        });

        if (validRecord.get()) {
            return recordMap;
        } else {
            return null;
        }
    }
}
