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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NetworkServerProperties;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.Node;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

/**
 * Class for generating tables for node and region compute load.
 * 
 * @author awald
 *
 */
public class LoadChartGenerator {
    private static final Logger LOGGER = LogManager.getLogger(LoadChartGenerator.class);

    /**
     * Name of a short resource report. Used for searching for the NCP node directory
     * and for loading the JSON.
     */
    public static final String RESOURCE_REPORT_SHORT_FILENAME = "resourceReport-SHORT.json";
    
    /**
     * Name of a long resource report. Used for searching for the NCP node directory
     * and for loading the JSON.
     */
    public static final String RESOURCE_REPORT_LONG_FILENAME = "resourceReport-LONG.json";


    
    private final ObjectMapper mapper;

    // time -> node name -> attribute -> value
    private Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> timeNodeDataShort = new HashMap<>();
    
    // time -> node name -> attribute -> value
    private Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> timeNodeDataLong = new HashMap<>();

    // service -> time -> node name -> attribute -> value
    private Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<NodeAttribute, LoadValue>>>> serviceTimeNodeDataShort = new HashMap<>();

    // service -> time -> node name -> attribute -> value
    private Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<NodeAttribute, LoadValue>>>> serviceTimeNodeDataLong = new HashMap<>();
    
    private Set<NodeAttribute> attributes = new HashSet<>(); // used to
                                                             // keep track
                                                             // of
                                                             // attributes
                                                             // that were
                                                             // discovered
                                                             // from the
                                                             // input data

    private Map<String, String> nodeToRegionMap = new HashMap<>();

    /**
     * Constructs an object to generate tables for projected client demand.
     */
    public LoadChartGenerator() {
        mapper = JsonUtils.getStandardMapObjectMapper();
    }

    /**
     * Processes data for a scenario to produce compute load tables.
     * 
     * @param scenarioFolder     the folder containing the scenario configuration
     * @param inputFolder        the folder containing the results of the scenario
     *                           simulation
     * @param outputFolder       the folder to output the chart tables to
     * @param startAtZero        if true, the center of the first bin will be 0
     *                           rather than at the first data point in time
     * @param dataSampleInterval the amount of time between consecutive result
     *                           updates in the simulation of the scenario
     */
    public void processScenarioData(File scenarioFolder, File inputFolder, File outputFolder, boolean startAtZero,
            long dataSampleInterval) {
        LOGGER.info("Scenario folder: '{}'", scenarioFolder);
        LOGGER.info("Data input folder: '{}'", inputFolder);
        LOGGER.info("Data sample interval: {} ms", dataSampleInterval);

        if (!scenarioFolder.exists()) {
            LOGGER.error("Scenario folder does not exist: {}", scenarioFolder);
            return;
        }

        if (!inputFolder.exists()) {
            LOGGER.error("Input folder does not exist: {}", inputFolder);
            return;
        }

        attributes.clear();

        processScenarioConfiguration(scenarioFolder.getName(), scenarioFolder.toPath(), outputFolder.toPath());

        ChartGenerationUtils.visitTimeFolders(inputFolder.toPath(), (nodeName, timeFolder, folderTime) -> {
            processFolder(nodeName, timeFolder, timeNodeDataShort, serviceTimeNodeDataShort, RESOURCE_REPORT_SHORT_FILENAME);
            processFolder(nodeName, timeFolder, timeNodeDataLong, serviceTimeNodeDataLong, RESOURCE_REPORT_LONG_FILENAME);
        });

        // output chart tables for nodes
        LOGGER.info("Output to folder: '{}'", outputFolder);
        
        output(timeNodeDataShort, serviceTimeNodeDataShort, outputFolder, dataSampleInterval, startAtZero, "SHORT");
        output(timeNodeDataLong, serviceTimeNodeDataLong, outputFolder, dataSampleInterval, startAtZero, "LONG");
    }
    
    
    private void processFolder(String nodeName, Path timeFolder,
            Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> timeNodeData,
            Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<NodeAttribute, LoadValue>>>> serviceTimeNodeData,
            String resourceReportFilename)
    {
        final Path resourceReportFile = timeFolder.resolve(resourceReportFilename);
        try (BufferedReader reader = Files.newBufferedReader(resourceReportFile)) {
            final ResourceReport resourceReport = mapper.readValue(reader, ResourceReport.class);
            final String label = nodeName;

            final long reportTime = resourceReport.getTimestamp();

            if (reportTime > 0) {
                addData(reportTime, label,
                        combineLoadAndCapacityMaps(getTotalComputeLoad(resourceReport.getComputeLoad()),
                                getTotalComputeLoad(resourceReport.getComputeDemand()),
                                resourceReport.getNodeComputeCapacity()),
                                timeNodeData);
            } else {
                LOGGER.warn("Skipping loading ResourceReport with time 0: {}", resourceReport);
            }

            combineValueAndCapacityMapsByService(getTotalComputeLoadByService(resourceReport.getComputeLoad()),
                    getTotalComputeLoadByService(resourceReport.getComputeDemand()),
                    getAllocatedCapacityByService(resourceReport)).forEach((service, serviceLoadValue) -> {
                        if (service != null) {
                            addData(service, reportTime, label, serviceLoadValue, serviceTimeNodeData);
                        } else {
                            LOGGER.info("ERROR: Found null service at time {} from node '{}'.", reportTime, label);
                        }
                    });

        } catch (JsonParseException | JsonMappingException e) {
            LOGGER.error("Error parsing JSON file {}", resourceReportFile, e);
        } catch (NoSuchFileException | FileNotFoundException e) {
            LOGGER.warn("Could not find resource report file: {}", resourceReportFile, e);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
    
    
    private void output(Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> timeNodeData,
            Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<NodeAttribute, LoadValue>>>> serviceTimeNodeData,
            File outputFolder, long dataSampleInterval, boolean startAtZero, String windowSuffix) {
        
        LOGGER.debug("Time node data {} map: {}", windowSuffix, timeNodeData);
        ChartGenerationUtils.outputDataToCSV(outputFolder, "ncp_load-" + windowSuffix, attributes, v -> v.getLoad(), timeNodeData);

        LOGGER.debug("serviceTimeNodeDataLoad: {}", serviceTimeNodeData);
        
        
        serviceTimeNodeData.forEach((service, serviceNodeData) -> {
            LOGGER.debug("serviceTimeNodeData {}: {} -> {}", windowSuffix, service, serviceNodeData);

            String serviceString = ChartGenerationUtils.serviceToFilenameString(service);

            Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> binnedServiceNodeData = binNodeData(serviceNodeData,
                    0, dataSampleInterval);
            
            if (binnedServiceNodeData.containsKey(0L)) {
                Map<String, Map<NodeAttribute, LoadValue>> timeZeroReport = binnedServiceNodeData.remove(0L);
                LOGGER.error("Found and removed report data with time 0 in binnedServiceNodeData during output: {}", timeZeroReport);
            }

            ChartGenerationUtils.outputDataToCSV(outputFolder, "ncp_load-" + windowSuffix + "-" + serviceString, attributes,
                    v -> v.getLoad(), binnedServiceNodeData);
            ChartGenerationUtils.outputDataToCSV(outputFolder, "ncp_demand-" + windowSuffix + "-" + serviceString, attributes,
                    v -> v.getDemand(), binnedServiceNodeData);
            ChartGenerationUtils.outputDataToCSV(outputFolder, "ncp_allocated_capacity-" + windowSuffix + "-" + serviceString, attributes,
                    v -> v.getCapacity(), binnedServiceNodeData);
        });

        // output chart tables for regions
        final Map<Long, Map<String, Map<NodeAttribute, Object>>> regionLoadChart = createRegionLoadChart(timeNodeData,
                nodeToRegionMap, startAtZero, dataSampleInterval);
        LOGGER.debug("Region load chart map: {}", regionLoadChart);
        ChartGenerationUtils.outputDataToCSV(outputFolder, "region_load-" + windowSuffix, attributes, v -> v, regionLoadChart);
    }

    private Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> binNodeData(
            Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> data, long firstBinCenter, long binSize) {
        Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> binnedData = new HashMap<>();

        data.forEach((time, nodeToDataMap) -> {
            long binTime = ChartGenerationUtils.getBinForTime(time, firstBinCenter, binSize);
            Map<String, Map<NodeAttribute, LoadValue>> binnedNodeToDataMap = binnedData.computeIfAbsent(binTime,
                    k -> new HashMap<>());

            nodeToDataMap.forEach((node, attrValueMap) -> {
                if (binnedNodeToDataMap.containsKey(node)) {
                    LOGGER.info("WARNING: Node '{}' mapped to time bin '{}' more than once.", node, binTime);
                }

                Map<NodeAttribute, LoadValue> binnedAttrValueMap = binnedNodeToDataMap.computeIfAbsent(node,
                        k -> new HashMap<>());

                attrValueMap.forEach((attr, value) -> {
                    binnedAttrValueMap.put(attr, value);
                });
            });
        });

        return binnedData;
    }

    private Map<NodeAttribute, LoadValue> combineLoadAndCapacityMaps(Map<NodeAttribute, Double> loads,
            Map<NodeAttribute, Double> demands, Map<NodeAttribute, Double> capacities) {
        Map<NodeAttribute, LoadValue> result = new HashMap<>();

        capacities.forEach((attr, capacity) -> {
            double load = 0.0;
            double demand = 0.0;

            if (loads.containsKey(attr)) {
                load = loads.get(attr);
            } else {
                LOGGER.debug("combineValueAndCapacityMaps: attribute {} has capacity {} and null value.", attr,
                        capacity);
            }

            if (demands.containsKey(attr)) {
                demand = demands.get(attr);
            } else {
                LOGGER.debug("combineValueAndCapacityMaps: attribute {} has capacity {} and null value.", attr,
                        capacity);
            }

            result.merge(attr, new LoadValue(load, demand, capacity), LoadValue::sum);
        });

        return result;
    }

    private Map<ServiceIdentifier<?>, Map<NodeAttribute, LoadValue>> combineValueAndCapacityMapsByService(
            Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> loadValues,
            Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> demandValues,
            Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> allocatedCapacities) {
        Map<ServiceIdentifier<?>, Map<NodeAttribute, LoadValue>> result = new HashMap<>();

        allocatedCapacities.forEach((service, attrCapacities) -> {
            Map<NodeAttribute, LoadValue> newAttrValues = result.computeIfAbsent(service, k -> new HashMap<>());

            Map<NodeAttribute, Double> serviceLoadValues = loadValues.computeIfAbsent(service, k -> new HashMap<>());
            Map<NodeAttribute, Double> serviceDemandValues = demandValues.computeIfAbsent(service,
                    k -> new HashMap<>());

            attrCapacities.forEach((attr, attrCapacity) -> {
                Double attrLoad = serviceLoadValues.computeIfAbsent(attr, k -> 0.0);
                Double attrDemand = serviceDemandValues.computeIfAbsent(attr, k -> 0.0);

                LoadValue value = new LoadValue(attrLoad, attrDemand, attrCapacity);
                newAttrValues.put(attr, value);
            });
        });

        return result;
    }

    private void addData(long time, String label, Map<NodeAttribute, LoadValue> attributeValues,
            Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> timeNodeData) {

        timeNodeData.computeIfAbsent(time, k -> new HashMap<>()).computeIfAbsent(label, k -> attributeValues);

        attributeValues.forEach((attribute, value) -> {
            attributes.add(attribute);
        });
    }

    private void addData(ServiceIdentifier<?> service, long time, String label,
            Map<NodeAttribute, LoadValue> attributeValues,
            Map<ServiceIdentifier<?>, Map<Long, Map<String, Map<NodeAttribute, LoadValue>>>> serviceTimeNodeData) {
        Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> serviceValues = serviceTimeNodeData
                .computeIfAbsent(service, k -> new HashMap<>());
        Map<String, Map<NodeAttribute, LoadValue>> serviceTimeValues = serviceValues.computeIfAbsent(time,
                k -> new HashMap<>());
        Map<NodeAttribute, LoadValue> serviceTimeLabelValues = serviceTimeValues.computeIfAbsent(label,
                k -> new HashMap<>());

        attributeValues.forEach((attr, value) -> {
            if (serviceTimeLabelValues.containsKey(attr)) {
                LOGGER.info("WARNING: Service '{}', time'{}, node '{}', attribute '{}' was found more than once.",
                        service, time, label, attr);
            }

            serviceTimeLabelValues.computeIfAbsent(attr, k -> value);
            attributes.add(attr);
        });
    }

    private static Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> getAllocatedCapacityByService(
            ResourceReport report) {
        Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> allocatedCapacity = new HashMap<>();

        report.getContainerReports().forEach((container, containerReport) -> {
            Map<NodeAttribute, Double> serviceAllocatedCapacity = allocatedCapacity
                    .computeIfAbsent(containerReport.getService(), k -> new HashMap<>());

            containerReport.getComputeCapacity().forEach((attr, value) -> {
                serviceAllocatedCapacity.merge(attr, value, Double::sum);
            });
        });

        return allocatedCapacity;
    }

    private static Map<NodeAttribute, Double> getTotalComputeLoad(
            ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>> load) {
        Map<NodeAttribute, Double> result = new HashMap<>();

        load.forEach((service, loadByNode) -> {
            loadByNode.forEach((node, attrs) -> {
                attrs.forEach((attr, value) -> {
                    if (!result.containsKey(attr))
                        result.merge(attr, value, Double::sum);
                });
            });
        });

        return result;
    }

    private static Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> getTotalComputeLoadByService(
            ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>> load) {
        Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> result = new HashMap<>();

        load.forEach((service, loadByNode) -> {
            Map<NodeAttribute, Double> serviceResult = result.computeIfAbsent(service, k -> new HashMap<>());

            loadByNode.forEach((node, attrValues) -> {
                attrValues.forEach((attr, value) -> {
                    serviceResult.merge(attr, value, Double::sum);
                });
            });
        });

        return result;
    }

    private void processScenarioConfiguration(final String scenarioName, final Path scenarioFolder,
            final Path outputFolder) {
        LOGGER.info("Process scenario configuration for '{}' in folder '{}'.", scenarioName, scenarioFolder);

        nodeToRegionMap.clear();

        final Path nodeInfo = outputFolder.resolve("node-info.csv");

        final CSVFormat format = CSVFormat.EXCEL.withHeader("ip", "node", "region", "client");

        try {
            Files.createDirectories(outputFolder);

            try (Writer writer = Files.newBufferedWriter(nodeInfo);
                    CSVPrinter printer = new CSVPrinter(writer, format)) {
                final Topology topology = NS2Parser.parse(scenarioName, scenarioFolder);

                for (final Map.Entry<String, Node> entry : topology.getNodes().entrySet()) {
                    final String name = entry.getKey();
                    final Node node = entry.getValue();

                    final String regionName = NetworkServerProperties.parseRegionName(node.getExtraData());
                    nodeToRegionMap.put(node.getName().toLowerCase(), regionName);

                    for (final Map.Entry<?, InetAddress> ipEntry : node.getAllIpAddresses().entrySet()) {
                        if (ipEntry.getValue() != null) {
                            final String addr = ipEntry.getValue().getHostAddress();
                            printer.printRecord(addr, name, regionName, node.isClient());
                        } else {
                            LOGGER.warn(
                                    "Unable to find IP address for node '{}' in ns2 file. You may be using a version of the ns2 file with no IP addresses.",
                                    ipEntry.getKey());
                        }
                    }
                }
            } // allocate writer and printer
        } catch (final IOException e) {
            throw new RuntimeException("Unable to parse ns2 file or unable to write node information", e);
        }

        LOGGER.debug("Node to region map: {}", nodeToRegionMap);
    }

    private static Map<Long, Map<String, Map<NodeAttribute, Object>>> createRegionLoadChart(
            Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> nodeData, Map<String, String> nodeToRegionMap,
            boolean startAtZero, long dataTimeInterval) {
        List<Long> times = new ArrayList<>();
        times.addAll(nodeData.keySet());
        Collections.sort(times);

        // find the minimum recorded time value, and use it as the center of the
        // first bin
        long firstBinCenter = (startAtZero ? 0 : times.get(0));

        // place data in time bins, and within each bin, place data into region
        // groups
        Map<Long, Map<String, Map<String, Map<NodeAttribute, LoadValue>>>> binnedData = binByLabelGroup(nodeData,
                nodeToRegionMap, firstBinCenter, dataTimeInterval);

        LOGGER.debug("Binned data: {}", binnedData);
        checkForMissingBinContributions(binnedData, getNodeLabelsFromNodeData(nodeData));

        // sum the data for each region group within each bin
        Map<Long, Map<String, Map<NodeAttribute, SumCount>>> collapsedBinnedData = collapseBins(binnedData);

        Map<Long, Map<String, Map<NodeAttribute, Object>>> tableData = new HashMap<>();

        collapsedBinnedData.forEach((time, group) -> {
            if (!tableData.containsKey(time))
                tableData.put(time, new HashMap<>());

            group.forEach((groupLabel, attributeValues) -> {
                String groupCapacitySumLabel = groupLabel + "-capacity_sum";
                String groupValueSumLabel = groupLabel + "-value_sum";
                String groupCountLabel = groupLabel + "-count";

                if (!tableData.get(time).containsKey(groupCapacitySumLabel))
                    tableData.get(time).put(groupCapacitySumLabel, new HashMap<>());

                if (!tableData.get(time).containsKey(groupValueSumLabel))
                    tableData.get(time).put(groupValueSumLabel, new HashMap<>());

                if (!tableData.get(time).containsKey(groupCountLabel))
                    tableData.get(time).put(groupCountLabel, new HashMap<>());

                attributeValues.forEach((attribute, value) -> {
                    tableData.get(time).get(groupCapacitySumLabel).put(attribute, value.getSum().getCapacity());
                    tableData.get(time).get(groupValueSumLabel).put(attribute, value.getSum().getLoad());
                    tableData.get(time).get(groupCountLabel).put(attribute, value.getCount());
                });
            });
        });

        LOGGER.debug("return table data: " + tableData);

        return tableData;
    }

    /**
     * Collapses time, group (region) bins by summing the values in each bin.
     * 
     * @param binnedAttributeValues data that has been binned by time and group in
     *                              the form (time -> groupLabel -> label ->
     *                              NodeAttribute -> Value)
     * @return a Map with a {@link SumCount} object for each time, group bin
     */
    protected static Map<Long, Map<String, Map<NodeAttribute, SumCount>>> collapseBins(
            Map<Long, Map<String, Map<String, Map<NodeAttribute, LoadValue>>>> binnedAttributeValues) {
        Map<Long, Map<String, Map<NodeAttribute, SumCount>>> result = new HashMap<>();

        binnedAttributeValues.forEach((time, bin) -> {
            // ensure time is in the Map
            if (!result.containsKey(time))
                result.put(time, new HashMap<>());

            bin.forEach((groupLabel, attributeValuesList) -> {
                Map<NodeAttribute, SumCount> attributeValues = new HashMap<>();

                // sum values for the different attributes for the group label
                attributeValuesList.forEach((label, av) -> {
                    av.forEach((attr, value) -> {
                        if (!attributeValues.containsKey(attr))
                            attributeValues.put(attr, new SumCount());

                        attributeValues.get(attr).addToSum(value);
                    });
                });

                result.get(time).put(groupLabel, attributeValues);
            });
        });

        return result;
    }

    // check and log if any of the time bins are missing information for any of
    // the given labels
    private static void checkForMissingBinContributions(
            Map<Long, Map<String, Map<String, Map<NodeAttribute, LoadValue>>>> timeLabelBins,
            Set<String> expectedLabels) {
        LOGGER.debug("checkForMissingBinContributions: Expected labels: {}", expectedLabels);

        List<Long> bins = new ArrayList<>();
        bins.addAll(timeLabelBins.keySet());
        Collections.sort(bins);

        for (Long bin : bins) {
            Set<String> remainingLabels = new HashSet<>();
            remainingLabels.addAll(expectedLabels);

            timeLabelBins.get(bin).forEach((groupLabel, contributingLabelMap) -> {
                contributingLabelMap.forEach((contributingLabel, attributeValues) -> {
                    remainingLabels.remove(contributingLabel);
                });
            });

            if (!remainingLabels.isEmpty())
                LOGGER.info("WARNING: Time bin '{}' is missing data for the following: {}", bin, remainingLabels);
        }
    }

    private static Set<String> getNodeLabelsFromNodeData(
            Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> nodeData) {
        Set<String> labels = new HashSet<>();

        nodeData.forEach((time, labelToAttributesMap) -> {
            labels.addAll(labelToAttributesMap.keySet());
        });

        return labels;
    }

    /**
     * Places data into time bins.
     * 
     * @param timeLabelData        data Map in the form (time -> node name ->
     *                             NodeAttribute -> Value)
     * @param labelToGroupLabelMap map from label (node) to group label (region)
     * @param firstBinCenter       the center of the first time bin
     * @param binSize              the size of each time bin
     * @return data that has been binned by time and group in the form (time ->
     *         groupLabel -> label -> NodeAttribute -> Value)
     */
    protected static Map<Long, Map<String, Map<String, Map<NodeAttribute, LoadValue>>>> binByLabelGroup(
            Map<Long, Map<String, Map<NodeAttribute, LoadValue>>> timeLabelData,
            Map<String, String> labelToGroupLabelMap, long firstBinCenter, long binSize) {
        // time -> group label -> contributing label -> attribute values
        Map<Long, Map<String, Map<String, Map<NodeAttribute, LoadValue>>>> timeLabelBins = new HashMap<>();

        timeLabelData.forEach((time, labelGroupData) -> {
            Long bin = ChartGenerationUtils.getBinForTime(time, firstBinCenter, binSize);

            // create a new time bin if necessary
            if (!timeLabelBins.containsKey(bin))
                timeLabelBins.put(bin, new HashMap<>());

            labelGroupData.forEach((label, attributeValues) -> {
                // determine the group for the current label
                String groupLabel = labelToGroupLabelMap.get(ChartGenerationUtils.extractNodeName(label).toLowerCase());

                if (groupLabel != null) {
                    // start the group's list in the current bin for attribute
                    // values if it does not yet exist
                    if (!timeLabelBins.get(bin).containsKey(groupLabel))
                        timeLabelBins.get(bin).put(groupLabel, new HashMap<>());

                    // check if label has already contributed to the bin group
                    if (timeLabelBins.get(bin).get(groupLabel).containsKey(label))
                        LOGGER.error("ERROR: '{}' is contributing to group '{}' in the time bin '{}' more than once.",
                                label, groupLabel, bin);

                    // add the attribute values to the appropriate group list
                    // within the appropriate time bin
                    // and keep track of which label contributed to a bin
                    LOGGER.debug("Add values for '{}' to group '{}' in time bin '{}'", label, groupLabel, bin);
                    timeLabelBins.get(bin).get(groupLabel).put(label, attributeValues);
                } else {
                    LOGGER.error("Could not find group for label '{}'.", label);
                }
            });
        });

        return timeLabelBins;
    }

    /**
     * 
     * @return the set of services with load data
     */
    public Set<ServiceIdentifier<?>> getLoadServices() {
        return serviceTimeNodeDataLong.keySet();
    }
}
