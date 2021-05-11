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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NetworkServerProperties;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;

import java8.util.Lists;

/**
 * Class for producing a version of the DCOP region plan that has actually been
 * realized by scanning through dns-records.json files.
 * 
 * @author awald
 *
 */
public class DNSRegionPlanTableGenerator {
    private static final Logger LOGGER = LogManager.getLogger(DNSRegionPlanTableGenerator.class);

    private static final String DNS_RECORDS_FILE_NAME = "dns-records.json";

    private static final double DEFAULT_PLAN_PERCENT_ROUND_UNIT = 0.01;

    private final ObjectMapper mapper;

    private Map<String, String> nodeToRegionMap = new HashMap<>();

    // time -> region -> DNS record -> weight
    private Map<Long, Map<String, Map<DnsRecord, Double>>> timeDnsRecords = new HashMap<>();
    private Map<Long, Map<String, Map<DnsRecord, Double>>> timeBinnedDnsRecords = new HashMap<>();

    private Set<String> regions = new HashSet<>();

    // service -> time -> from region -> to region -> weight
    private Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> timeRegionWeights = new HashMap<>();
    private Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> normalizedTimeRegionWeights = new HashMap<>();

    // service -> time -> region -> node -> weight
    private Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> timeRegionNodeWeights = new HashMap<>();
    private Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> normalizedTimeRegionNodeWeights = new HashMap<>();

    /**
     * Constructs an instance with the modules necessary for JSON
     * deserialization added to the ObjectMapper.
     */
    public DNSRegionPlanTableGenerator() {
        mapper = JsonUtils.getStandardMapObjectMapper();
    }

    private AgentConfiguration readAgentConfiguration(final Path inputFolder) {
        LOGGER.trace("Looking for agent configuration in {}", inputFolder);

        try (DirectoryStream<Path> inputFolderStream = Files.newDirectoryStream(inputFolder)) {
            for (final Path nodeBaseFolder : inputFolderStream) {
                final Path nodeNamePath = nodeBaseFolder.getFileName();

                if (nodeNamePath != null) {
                    if (Files.isDirectory(nodeBaseFolder)) {
                        try {
                            final List<Path> nodeDumpParentFolders = ChartGenerationUtils
                                    .findNcpFolders(nodeBaseFolder);

                            for (Path nodeDumpParentFolder : nodeDumpParentFolders) {
                                if (null == nodeDumpParentFolder) {
                                    continue;
                                }

                                final Path nodeFilename = nodeDumpParentFolder.getFileName();
                                if (null == nodeFilename) {
                                    continue;
                                }

                                // if the folder is for an NCP
                                if (!nodeFilename.toString().equals(ChartGenerator.SIMULATION_FILES_FOLDER_NAME)) {
                                    final Path agentConfigFile = nodeDumpParentFolder
                                            .resolve("agent-configuration.json");
                                    if (Files.exists(agentConfigFile)) {
                                        LOGGER.trace("Agent configuration found in {}", agentConfigFile);
                                        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();
                                        try (BufferedReader reader = Files.newBufferedReader(agentConfigFile)) {
                                            final AgentConfiguration value = mapper.readValue(reader,
                                                    AgentConfiguration.class);
                                            return value;
                                        } catch (final IOException e) {
                                            LOGGER.error("Error parsing agent configuration file '{}'", agentConfigFile, e);
                                        }
                                    } else {
                                        LOGGER.trace("Agent configuration not found in {}", agentConfigFile);
                                    }

                                } // NCP folder
                            }
                        } catch (final IOException e) {
                            LOGGER.error("Error finding first long resource report", e);
                        }
                    } // node folder
                }
            } // foreach node folder

        } catch (final NotDirectoryException e) {
            LOGGER.error("Node folder {} is not a directory", inputFolder);
        } catch (final IOException e) {
            LOGGER.error("Error closing inputFolder directory stream", e);
        }

        throw new RuntimeException("Cannot find agent configuration");
    }

    /**
     * Processes DNS data for a scenario run and produces a realized region plan
     * table.
     * 
     * @param scenarioFolder
     *            the folder containing the scenario configuration
     * @param inputFolder
     *            the folder containing the results of the scenario simulation
     * @param outputFolder
     *            the folder to output the chart tables to
     * @param binSize
     *            the size of the time bins for RegionPlan times
     */
    public void processScenarioData(File scenarioFolder, File inputFolder, File outputFolder, long binSize) {
        LOGGER.info("Scenario folder: '{}'", scenarioFolder);
        LOGGER.info("Data input folder: '{}'", inputFolder);
        LOGGER.info("Data output folder: '{}'", outputFolder);
        LOGGER.info("Data bin size: {} ms", binSize);

        if (!scenarioFolder.exists()) {
            LOGGER.error("Scenario folder does not exist: {}", scenarioFolder);
            return;
        }

        if (!inputFolder.exists()) {
            LOGGER.error("Input folder does not exist: {}", inputFolder);
            return;
        }

        final AgentConfiguration agentConfig = readAgentConfiguration(inputFolder.toPath());
        final boolean twoLayerDns = AgentConfiguration.DnsResolutionType.RECURSIVE_TWO_LAYER
                .equals(agentConfig.getDnsResolutionType());
        if (twoLayerDns) {
            LOGGER.info("2 layer DNS is in use");
        }

        processScenarioConfiguration(scenarioFolder.getName(), scenarioFolder.toPath());

        ChartGenerationUtils.visitTimeFolders(inputFolder.toPath(), (nodeName, timeFolder, folderTime) -> {
            processFolder(nodeName, timeFolder, folderTime, timeDnsRecords);
        });

        timeBinDnsRecords(timeDnsRecords, timeBinnedDnsRecords, binSize);

        if (twoLayerDns) {
            computeTimeRegionWeights2Layer(timeBinnedDnsRecords, timeRegionWeights, timeRegionNodeWeights);
        } else {
            computeTimeRegionWeights(timeBinnedDnsRecords, timeRegionWeights, timeRegionNodeWeights);
        }

        LOGGER.debug("timeDnsRecords: {}", timeDnsRecords);
        LOGGER.debug("timeRegionWeights: {}", timeRegionWeights);
        LOGGER.debug("timeRegionNodeWeights: {}", timeRegionNodeWeights);

        List<String> regionList = regions.stream().sorted().collect(Collectors.toList());

        outputServicePlanChangeMatrices(outputFolder, timeRegionWeights,
                Lists.copyOf(ChartGenerationUtils.getSecondOrderKeys(timeRegionWeights)), regionList,
                DEFAULT_PLAN_PERCENT_ROUND_UNIT);

        try {
            outputDnsRegionPlans(outputFolder.toPath(), timeRegionWeights, regionList);
        } catch (final IOException e) {
            LOGGER.error("Unable to print DCOP plans", e);
        }
    }

    private void processScenarioConfiguration(String scenarioName, Path scenarioFolder) {
        LOGGER.info("Process scenario configuration for '{}' in folder '{}'.", scenarioName, scenarioFolder);

        Topology topology;
        try {
            topology = NS2Parser.parse(scenarioName, scenarioFolder);

            topology.getNodes().forEach((name, node) -> {
                String regionName = NetworkServerProperties.parseRegionName(node.getExtraData());
                nodeToRegionMap.put(node.getName().toLowerCase(), regionName);
            });
        } catch (IOException e) {
            LOGGER.error("Unable to parse ns2 file: {}", e);
            e.printStackTrace();
        }
    }

    private void timeBinDnsRecords(Map<Long, Map<String, Map<DnsRecord, Double>>> timeDnsRecords,
            Map<Long, Map<String, Map<DnsRecord, Double>>> timeBinnedDnsRecords,
            long binSize) {

        timeDnsRecords.forEach((time, regionDnsRecords) -> {
            long binTime = ChartGenerationUtils.getBinForTime(time, 0, binSize);

            regionDnsRecords.forEach((region, dnsRecords) -> {
                timeBinnedDnsRecords.computeIfAbsent(binTime, k -> new HashMap<>()).computeIfAbsent(region,
                        k -> dnsRecords);
            });
        });

    }

    private void processFolder(String nodeName,
            Path timeFolder,
            long folderTime,
            Map<Long, Map<String, Map<DnsRecord, Double>>> timeDnsRecords) {
        final Path dnsRecordsFilePath = timeFolder.resolve(DNS_RECORDS_FILE_NAME);

        if (dnsRecordsFilePath.toFile().exists()) {

            try (BufferedReader reader = Files.newBufferedReader(dnsRecordsFilePath)) {
                ImmutableCollection<Map.Entry<DnsRecord, Double>> dnsEntries = mapper.readValue(reader,
                        new TypeReference<ImmutableCollection<Map.Entry<DnsRecord, Double>>>() {
                        });

                dnsEntries.forEach((recordWeight) -> {
                    DnsRecord record = recordWeight.getKey();
                    double weight = recordWeight.getValue();

                    String region = mapContainerToRegionName(nodeName);

                    if (region != null) {
                        regions.add(region);
                    }

                    timeDnsRecords.computeIfAbsent(folderTime, k -> new HashMap<>())
                            .computeIfAbsent(region, k -> new HashMap<>()).put(record, weight);
                });

            } catch (IOException e) {
                LOGGER.error("Error parsing DNS records file {}:", dnsRecordsFilePath, e);
            }
        }
    }

    private String mapContainerToRegionName(String containerId) {
        String[] parts = containerId.toLowerCase().split("_");

        if (parts.length > 0) {
            String node = parts[0];

            return nodeToRegionMap.get(node);
        }

        return null;
    }

    private void computeTimeRegionWeights(Map<Long, Map<String, Map<DnsRecord, Double>>> timeDnsRecords,
            Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> timeRegionWeights,
            Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> timeRegionNodeWeights) {

        timeDnsRecords.forEach((time, regionDnsRecords) -> {
            regionDnsRecords.forEach((fromRegion, dnsRecords) -> {
                dnsRecords.entrySet().stream().filter((e) -> e.getKey() instanceof NameRecord).forEach(entry -> {
                    NameRecord record = (NameRecord) entry.getKey();
                    ServiceIdentifier<?> service = record.getService();
                    double weight = entry.getValue();

                    String nodeName = record.getNode().getName();
                    String toRegion = mapContainerToRegionName(nodeName);

                    if (toRegion != null) {
                        regions.add(toRegion);
                    }

                    if (toRegion == null) {
                        LOGGER.warn("Could not find region for node '{}'", nodeName);
                    }

                    timeRegionWeights.computeIfAbsent(service, k -> new HashMap<>())
                            .computeIfAbsent(time, k -> new HashMap<>())
                            .computeIfAbsent(fromRegion, k -> new HashMap<>()).merge(toRegion, weight, Double::sum);

                    timeRegionNodeWeights.computeIfAbsent(service, k -> new HashMap<>())
                            .computeIfAbsent(time, k -> new HashMap<>())
                            .computeIfAbsent(fromRegion, k -> new HashMap<>()).put(nodeName, weight);
                });

                dnsRecords.entrySet().stream().filter((e) -> e.getKey() instanceof DelegateRecord).forEach(entry -> {
                    DelegateRecord record = (DelegateRecord) entry.getKey();
                    ServiceIdentifier<?> service = record.getService();
                    double weight = entry.getValue();

                    String toRegion = record.getDelegateRegion().getName();

                    if (toRegion != null) {
                        regions.add(toRegion);
                    }

                    timeRegionWeights.computeIfAbsent(service, k -> new HashMap<>())
                            .computeIfAbsent(time, k -> new HashMap<>())
                            .computeIfAbsent(fromRegion, k -> new HashMap<>()).put(toRegion, weight);
                });
            });
        });

    }

    /**
     * Used for 2 layer DNS processing. When the 2 layer DNS is used, then the
     * delegate records specify all of the weights.
     */
    private void computeTimeRegionWeights2Layer(Map<Long, Map<String, Map<DnsRecord, Double>>> timeDnsRecords,
            Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> timeRegionWeights,
            Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> timeRegionNodeWeights) {

        timeDnsRecords.forEach((time, regionDnsRecords) -> {
            regionDnsRecords.forEach((fromRegion, dnsRecords) -> {
                dnsRecords.entrySet().stream().filter((e) -> e.getKey() instanceof DelegateRecord).forEach(entry -> {
                    final DelegateRecord record = (DelegateRecord) entry.getKey();
                    final ServiceIdentifier<?> service = record.getService();
                    final double weight = entry.getValue();

                    final String toRegion = record.getDelegateRegion().getName();

                    if (toRegion != null) {
                        regions.add(toRegion);
                    }

                    timeRegionWeights.computeIfAbsent(service, k -> new HashMap<>())
                            .computeIfAbsent(time, k -> new HashMap<>())
                            .computeIfAbsent(fromRegion, k -> new HashMap<>()).put(toRegion, weight);
                });
            });
        });

    }

    private void outputServicePlanChangeMatrices(File outputFolder,
            Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> serviceRegionPlans,
            List<Long> times,
            List<String> regions,
            Double percentRoundUnit) {
        if (!outputFolder.exists()) {
            if (!outputFolder.mkdirs()) {
                throw new RuntimeException("Unable to create directory " + outputFolder);
            }
        }

        Collections.sort(regions);

        serviceRegionPlans.forEach((service, servicePlans) -> {
            File serviceFile = new File(
                    outputFolder + File.separator + "service_" + ChartGenerationUtils.serviceToFilenameString(service)
                            + "-dns_overflow_plan_request_distribution" + ChartGenerationUtils.CSV_FILE_EXTENSION);

            String[] header = new String[1 + regions.size() * regions.size() + 1];
            header[0] = ChartGenerationUtils.CSV_TIME_COLUMN_LABEL;

            for (int f = 0; f < regions.size(); f++)
                for (int t = 0; t < regions.size(); t++)
                    header[1 + f * regions.size() + t] = regions.get(f) + "-" + regions.get(t);

            header[1 + regions.size() * regions.size()] = "error";

            CSVFormat format = CSVFormat.EXCEL.withHeader(header);

            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(serviceFile), Charset.defaultCharset()), format)) {
                List<Long> times2 = new ArrayList<>();
                times2.addAll(times);
                Collections.sort(times2);

                Object[] prevRowValues = null;

                for (int n = 0; n < times2.size(); n++) {
                    long time = times2.get(n);
                    int error = 0;
                    Map<String, Map<String, Double>> servicePlan = servicePlans.get(time);

                    Object[] rowValues = new Object[header.length];

                    rowValues[0] = time; // add time to row
                    Arrays.fill(rowValues, 1, regions.size() * regions.size() + 1,
                            ChartGenerationUtils.EMPTY_CELL_VALUE);

                    for (int f = 0; f < regions.size(); f++) {
                        String fromRegion = regions.get(f);

                        if (servicePlan != null && servicePlan.containsKey(fromRegion)) {
                            Map<String, Double> toRegionPercents = ChartGenerationUtils
                                    .normalizeWeights(servicePlan.get(fromRegion));

                            for (int t = 0; t < regions.size(); t++) {
                                String toRegion = regions.get(t);

                                // check if there is data available for
                                // this toRegion
                                if (toRegionPercents.containsKey(toRegion)) {
                                    Double value = toRegionPercents.get(toRegion);

                                    if (value != null && percentRoundUnit != null) {
                                        value = Math.round(value / percentRoundUnit) * percentRoundUnit;
                                    }

                                    rowValues[1 + f * regions.size() + t] = value; // add
                                                                                   // value
                                                                                   // to
                                                                                   // row
                                } else {
                                    LOGGER.info(
                                            "ERROR: For service '{}', no data found for to region '{}' at time {} in region '{}'",
                                            service, toRegion, time, fromRegion);
                                    error = 1;
                                }
                            }
                        } else {
                            LOGGER.info("ERROR: For service '{}', no data found for from region '{}' at time {}",
                                    service, fromRegion, time);
                            error = 1;
                        }
                    }

                    rowValues[1 + regions.size() * regions.size()] = error;

                    // print rowValues if it is the first or last entry or
                    // different from the previous entry
                    if (prevRowValues == null || !ChartGenerationUtils.compareArrays(prevRowValues, rowValues, 1,
                            regions.size() * regions.size() + 1) || n == times2.size() - 1)
                        printer.printRecord(rowValues);

                    prevRowValues = rowValues;
                }
            } catch (IOException e) {
                LOGGER.error("Error writing to CSV file: {}", e);
                e.printStackTrace();
            }
        });
    }

    private void outputDnsRegionPlans(final Path outputFolder,
            final Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> serviceTimeRegionWeights,
            final List<String> regions) throws IOException {

        final int numNonRegionColumns = 3;
        final String[] header = new String[numNonRegionColumns + regions.size()];
        header[0] = ChartGenerationUtils.CSV_TIME_COLUMN_LABEL;
        header[1] = "service";
        header[2] = "plan_region";
        for (int i = 0; i < regions.size(); ++i) {
            header[numNonRegionColumns + i] = regions.get(i);
        }

        final CSVFormat format = CSVFormat.EXCEL.withHeader(header);

        final Path outputFile = outputFolder.resolve("all_dns_region_plans" + ChartGenerationUtils.CSV_FILE_EXTENSION);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, Charset.defaultCharset());
                CSVPrinter printer = new CSVPrinter(writer, format)) {

            for (Map.Entry<ServiceIdentifier<?>, Map<Long, Map<String, Map<String, Double>>>> serviceEntry : serviceTimeRegionWeights
                    .entrySet()) {
                final ServiceIdentifier<?> service = serviceEntry.getKey();
                final Map<Long, Map<String, Map<String, Double>>> timeRegionWeights = serviceEntry.getValue();

                // use Object[] so that printer.printRecord handles the
                // variable arguments correctly
                final Object[] line = new Object[header.length];
                final String serviceString = ChartGenerationUtils.serviceToFilenameString(service);

                List<Long> times = timeRegionWeights.keySet().stream().sorted().collect(Collectors.toList());

                for (int t = 0; t < times.size(); t++) {
                    Long time = times.get(t);

                    Map<String, Map<String, Double>> dnsRegionPlans = timeRegionWeights.get(time);

                    for (int fr = 0; fr < regions.size(); fr++) {
                        String fromRegion = regions.get(fr);

                        Map<String, Double> toRegionWeights = dnsRegionPlans.get(fromRegion);

                        if (toRegionWeights != null) {
                            // mark all overflow regions as unknown values by
                            // default
                            Arrays.fill(line, 0, line.length, ChartGenerationUtils.EMPTY_CELL_VALUE);
                            line[0] = time;
                            line[1] = serviceString;
                            line[2] = fromRegion;

                            final double overflowSum = toRegionWeights.entrySet().stream()
                                    .mapToDouble(Map.Entry::getValue).sum();

                            for (int tr = 0; tr < regions.size(); tr++) {
                                String toRegion = regions.get(tr);
                                Double weight = toRegionWeights.get(toRegion);

                                if (weight != null) {
                                    line[numNonRegionColumns + tr] = weight / overflowSum;
                                }
                            }

                            printer.printRecord(line);
                        }
                    }
                }
            }

        }

    }
}
