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
package com.bbn.map.ChartGeneration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.Controller.NodeState;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan.ContainerInfo;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;

import java8.util.Lists;

/**
 * 
 * Create table for RLG plan updates.
 * 
 * @author awald
 *
 */
public class RLGPlanUpdateTableGenerator {
    private static final Logger LOGGER = LogManager.getLogger(DCOPPlanUpdateTableGenerator.class);

    private static final String RLG_PLAN_FILENAME = "loadBalancerPlan.json";
    private static final String NODE_STATE_FILENAME = "state.json";

    private static final String CSV_HEADER_TIMESTAMP = "timestamp";
    private static final String CSV_HEADER_SERVICE_PLAN_UPDATED = "service_plan_updated";
    private static final String CSV_HEADER_OVERFLOW_PLAN_UPDATED = "overflow_plan_updated";
    private static final String[] CSV_HEADER = { CSV_HEADER_TIMESTAMP, CSV_HEADER_SERVICE_PLAN_UPDATED,
            CSV_HEADER_OVERFLOW_PLAN_UPDATED };

    private static final String CSV_FILE_EXTENSION = ".csv";

    private final ObjectMapper mapper;

    /**
     * Constructs an instance with the modules necessary for JSON
     * deserialization added to the ObjectMapper.
     */
    public RLGPlanUpdateTableGenerator() {
        mapper = JsonUtils.getStandardMapObjectMapper();
    }

    /**
     * Generates a CSV file listing plan update times for each leader in each
     * region.
     * 
     * @param inputFolder
     *            the folder containing the results of the scenario simulation
     * @param outputFolder
     *            the folder to output the chart tables to
     * @param binSize
     *            the size of the time bins for RegionPlan times
     */
    public void processScenarioData(File inputFolder, File outputFolder, long binSize) {
        LOGGER.info("Data input folder: '{}'", inputFolder);
        LOGGER.info("Data sample interval: {} ms", binSize);

        if (!inputFolder.exists()) {
            LOGGER.error("Input folder does not exist: {}", inputFolder);
            return;
        }

        if (!outputFolder.exists()) {
            if (!outputFolder.mkdirs()) {
                throw new RuntimeException("Unable to create folder " + outputFolder);
            }
        }

        // region name -> time -> node name -> RegionPlan
        Map<String, Map<Long, Map<String, LoadBalancerPlan>>> rlgPlans = new HashMap<>();

        // service -> time -> from region -> name of node publishing the plan ->
        // to region -> percent
        // requests
        Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<RegionIdentifier, Double>>>>> serviceRLGROverflowPlans = new HashMap<>();
        List<RegionIdentifier> regions = new ArrayList<>();

        // service -> time -> region -> name of node publishing the plan -> node
        // -> number of instances
        // of service
        Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<NodeIdentifier, Integer>>>>> serviceRLGPlans = new HashMap<>();
        List<NodeIdentifier> nodes = new ArrayList<>();

        ChartGenerationUtils.visitTimeFolders(inputFolder.toPath(), (nodeName, timeFolder, time) -> {

            final Path nodeStateFile = timeFolder.resolve(NODE_STATE_FILENAME);

            try (BufferedReader nodeStateReader = Files.newBufferedReader(nodeStateFile)) {
                NodeState nodeState = mapper.readValue(nodeStateReader, NodeState.class);
                LOGGER.debug("Read state for node '{}': {}", nodeState.getName(), nodeState.isRLG());

                if (nodeState.isRLG()) {
                    final Path rlgPlanFile = timeFolder.resolve(RLG_PLAN_FILENAME);

                    if (Files.exists(rlgPlanFile)) {
                        try (BufferedReader rlgPlanReader = Files.newBufferedReader(rlgPlanFile)) {

                            LoadBalancerPlan rlgPlan = mapper.readValue(rlgPlanReader, LoadBalancerPlan.class);
                            LOGGER.debug("Read RLG plan for time {}: {}", time, rlgPlan);
                            processRLGPlan(time, nodeState.getRegion().getName(), nodeState.getName(), rlgPlan,
                                    rlgPlans);
                        } catch (final IOException e) {
                            LOGGER.error("Error parsing JSON file: {}", rlgPlanFile, e);
                        }
                    } else {
                        LOGGER.info("WARNING: At time {} RLG is running but load balancer plan file '{}' not found.",
                                time, rlgPlanFile);
                    }
                } else {
                    processRLGPlan(time, nodeState.getRegion().getName(), nodeState.getName(), null, rlgPlans);
                }

            } catch (final IOException e) {
                LOGGER.error("Error parsing JSON file {}", nodeStateFile, e);
            }
        });

        final List<Long> times = new ArrayList<>();
        times.addAll(ChartGenerationUtils.getSecondOrderKeys(rlgPlans));
        Collections.sort(times);

        final long firstBinCenter = (times.size() > 0 ? times.get(0) : 0);

        LOGGER.debug("Regions plans: {}", rlgPlans);
        final Map<String, Map<Long, Map<String, LoadBalancerPlan>>> timeBinnedRLGPlans = DCOPPlanUpdateTableGenerator
                .timeBinRegionPlans(rlgPlans, firstBinCenter, binSize);
        LOGGER.debug("Time binnned regions plans: {}", timeBinnedRLGPlans);

        final List<Long> binTimes = Lists.copyOf(ChartGenerationUtils.getSecondOrderKeys(timeBinnedRLGPlans));

        outputRLGPlanChangeToCSV(outputFolder, timeBinnedRLGPlans);

        timeBinnedRLGPlans.forEach((region, regionPlans) -> {
            regionPlans.forEach((time, leaderNodePlans) -> {
                leaderNodePlans.forEach((nodeName, regionPlan) -> {
                    if (regionPlan != null) {
                        processServiceOverflowRLGPlan(time, nodeName, regionPlan, serviceRLGROverflowPlans, regions);
                        processServiceRLGPlan(time, nodeName, regionPlan, serviceRLGPlans, nodes);
                    }
                });
            });
        });

        ChartGenerationUtils.outputRegionLeadersToCSV(outputFolder, "-rlg_leaders", timeBinnedRLGPlans);
        outputOverflowServicePlanChangeMatrices(outputFolder, serviceRLGROverflowPlans, binTimes, regions);
        outputServicePlanNodeChanges(outputFolder, serviceRLGPlans, binTimes, nodes);
    }

    private void processServiceRLGPlan(final Long time,
            final String nodeName,
            final LoadBalancerPlan rlgPlan,
            final Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<NodeIdentifier, Integer>>>>> serviceRLGPlans,
            final List<NodeIdentifier> nodes) {
        LOGGER.debug("Add region plan for time {} and node '{}': {}", time, nodeName, rlgPlan);

        final RegionIdentifier fromRegion = rlgPlan.getRegion();

        final ImmutableMap<NodeIdentifier, ImmutableCollection<ContainerInfo>> servicePlans = rlgPlan.getServicePlan();

        servicePlans.forEach((node, containerInfos) -> {
            if (!nodes.contains(node)) {
                nodes.add(node);
            }

            final Map<ServiceIdentifier<?>, Integer> numInstancesOfService = new HashMap<>();
            containerInfos.forEach(info -> {
                if (!info.isStop()) {
                    numInstancesOfService.merge(info.getService(), 1, Integer::sum);
                }
            });

            numInstancesOfService.forEach((service, numInstances) -> {
                // make sure that serviceRLGPlans has the base keys that we're
                // looking for
                serviceRLGPlans.computeIfAbsent(service, k -> new HashMap<>())
                        .computeIfAbsent(time, k -> new HashMap<>()).computeIfAbsent(fromRegion, k -> new HashMap<>());

                if (serviceRLGPlans.get(service).get(time).get(fromRegion).containsKey(nodeName)) {
                    LOGGER.info(
                            "WARNING: Replacing plan for service '{}' at time {} for region '{}' from leader node '{}'.",
                            service, time, fromRegion, nodeName);
                }

                serviceRLGPlans.get(service).get(time).get(fromRegion).computeIfAbsent(nodeName, k -> new HashMap<>())
                        .put(node, numInstances);
            }); // foreach service,numInstances

        }); // foreach node in the service plan
    }

    private void processRLGPlan(long time,
            String regionName,
            String nodeName,
            LoadBalancerPlan rlgPlan,
            Map<String, Map<Long, Map<String, LoadBalancerPlan>>> rlgPlans) {
        LOGGER.debug("Process plan at time {} for node '{}' in region '{}': {}", time, nodeName, regionName, rlgPlan);

        if (!rlgPlans.containsKey(regionName))
            rlgPlans.put(regionName, new HashMap<>());

        if (!rlgPlans.get(regionName).containsKey(time))
            rlgPlans.get(regionName).put(time, new HashMap<>());

        rlgPlans.get(regionName).get(time).put(nodeName, rlgPlan);
    }

    private void outputRLGPlanChangeToCSV(File outputFolder,
            Map<String, Map<Long, Map<String, LoadBalancerPlan>>> rlgPlans) {
        rlgPlans.forEach((region, regionPlans) -> {
            File regionFile = new File(
                    outputFolder + File.separator + "region_" + region + "-rlg_plan_change_times" + CSV_FILE_EXTENSION);
            LOGGER.debug("Start file for region {}: {}", region, regionFile);

            CSVFormat format = CSVFormat.EXCEL.withHeader(CSV_HEADER);

            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(regionFile), Charset.defaultCharset()), format)) {
                List<Long> times = new ArrayList<>();
                times.addAll(regionPlans.keySet());
                Collections.sort(times);

                LoadBalancerPlan previousPlan = null;

                for (long time : times) {
                    LoadBalancerPlan plan = null;

                    for (String nodeName : regionPlans.get(time).keySet()) {
                        LoadBalancerPlan rp = regionPlans.get(time).get(nodeName);

                        if (rp != null) {
                            if (plan == null) {
                                plan = rp;
                                LOGGER.debug("Plan was found at time {} in region '{}' from node {}:", time, region,
                                        nodeName, rp);
                            } else {
                                LOGGER.info(
                                        "ERROR: More than one region plan was found at time {} in region '{}' from node {}: {}",
                                        time, region, nodeName, plan);
                            }
                        }
                    }

                    boolean servicePlanChanged = false;
                    boolean overflowPlanChanged = false;

                    if (previousPlan == null & plan != null) {
                        servicePlanChanged = true;
                        overflowPlanChanged = true;
                    }

                    ImmutableMap<NodeIdentifier, ImmutableCollection<ContainerInfo>> prevServicePlan = ImmutableMap
                            .of();
                    ImmutableMap<NodeIdentifier, ImmutableCollection<ContainerInfo>> servicePlan = ImmutableMap.of();
                    ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> prevOverflowPlan = ImmutableMap
                            .of();
                    ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = ImmutableMap
                            .of();

                    if (plan != null) {
                        servicePlan = plan.getServicePlan();
                        overflowPlan = plan.getOverflowPlan();
                    }

                    if (previousPlan != null) {
                        prevServicePlan = previousPlan.getServicePlan();
                        prevOverflowPlan = previousPlan.getOverflowPlan();
                    }

                    if (!servicePlan.equals(prevServicePlan))
                        servicePlanChanged = true;

                    if (!overflowPlan.equals(prevOverflowPlan))
                        overflowPlanChanged = true;

                    if (servicePlanChanged || overflowPlanChanged)
                        printer.printRecord(time, (servicePlanChanged ? 1 : 0), (overflowPlanChanged ? 1 : 0));

                    previousPlan = plan;
                }

                LOGGER.info("Finished writing file: {}", regionFile);
            } catch (IOException e) {
                LOGGER.error("Error writing to CSV file: {}", e);
            }
        });
    }

    private static void processServiceOverflowRLGPlan(long time,
            String nodeName,
            LoadBalancerPlan regionPlan,
            Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<RegionIdentifier, Double>>>>> serviceRegionPlans,
            List<RegionIdentifier> regions) {
        LOGGER.debug("Add region plan for time {} and node '{}': {}", time, nodeName, regionPlan);

        RegionIdentifier fromRegion = regionPlan.getRegion();

        if (!regions.contains(fromRegion))
            regions.add(fromRegion);

        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlans = regionPlan
                .getOverflowPlan();

        servicePlans.forEach((service, shedToRegionPercentages) -> {
            if (!serviceRegionPlans.containsKey(service))
                serviceRegionPlans.put(service, new HashMap<>());

            if (!serviceRegionPlans.get(service).containsKey(time))
                serviceRegionPlans.get(service).put(time, new HashMap<>());

            if (!serviceRegionPlans.get(service).get(time).containsKey(fromRegion))
                serviceRegionPlans.get(service).get(time).put(fromRegion, new HashMap<>());

            Map<RegionIdentifier, Double> shedToRegionPercentagesMap = new HashMap<>();

            shedToRegionPercentages.forEach((shedToRegion, percentRequests) -> {
                if (!regions.contains(shedToRegion))
                    regions.add(shedToRegion);

                shedToRegionPercentagesMap.put(shedToRegion, percentRequests);
            });

            if (serviceRegionPlans.get(service).get(time).get(fromRegion).containsKey(nodeName))
                LOGGER.warn("Replacing plan for service '{}' at time {} for region '{}' from leader node '{}'.",
                        service, time, fromRegion, nodeName);

            serviceRegionPlans.get(service).get(time).get(fromRegion).put(nodeName, shedToRegionPercentagesMap);
        });
    }

    private void outputServicePlanNodeChanges(File outputFolder,
            Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<NodeIdentifier, Integer>>>>> serviceRLGPlans,
            List<Long> times,
            List<NodeIdentifier> nodes) {
        if (!outputFolder.exists()) {
            if (!outputFolder.mkdirs()) {
                throw new RuntimeException("Unable to create directory " + outputFolder);
            }
        }

        serviceRLGPlans.forEach((service, servicePlans) -> {
            File serviceFile = new File(
                    outputFolder + File.separator + "service_" + ChartGenerationUtils.serviceToFilenameString(service)
                            + "-rlg_service_plans" + CSV_FILE_EXTENSION);

            String[] header = new String[1 + nodes.size() + 1];
            header[0] = CSV_HEADER_TIMESTAMP;

            for (int t = 0; t < nodes.size(); t++)
                header[1 + t] = nodes.get(t).getName();

            header[1 + nodes.size()] = "error";

            CSVFormat format = CSVFormat.EXCEL.withHeader(header);

            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(serviceFile), Charset.defaultCharset()), format)) {
                List<Long> times2 = new ArrayList<>();
                times2.addAll(times);
                Collections.sort(times2);

                Object[] prevRowValues = null;

                for (int t = 0; t < times2.size(); t++) {
                    long time = times2.get(t);
                    List<NodeIdentifier> nodesWithoutData = new ArrayList<>();
                    nodesWithoutData.addAll(nodes);

                    int error = 0;
                    Object[] rowValues = new Object[header.length];

                    rowValues[0] = time; // add time to row
                    Arrays.fill(rowValues, 1, nodes.size() + 1, ChartGenerationUtils.EMPTY_CELL_VALUE);

                    Map<RegionIdentifier, Map<String, Map<NodeIdentifier, Integer>>> servicePlan = servicePlans
                            .get(time);

                    if (servicePlan != null) {
                        for (Map.Entry<RegionIdentifier, Map<String, Map<NodeIdentifier, Integer>>> entry : servicePlan
                                .entrySet()) {
                            RegionIdentifier region = entry.getKey();
                            Map<String, Map<NodeIdentifier, Integer>> nodePlans = entry.getValue();

                            if (nodePlans.keySet().size() > 1) {
                                LOGGER.info(
                                        "ERROR: More than one plan found for service '{}' at time {} in region '{}' from nodes '{}'",
                                        service, time, region, nodePlans.keySet());
                                error = 1;
                            } else if (nodePlans.keySet().isEmpty()) {
                                LOGGER.info("ERROR: No plan found for service '{}' at time {} in region '{}'", service,
                                        time, region);
                                error = 1;
                            } else {
                                for (Map.Entry<String, Map<NodeIdentifier, Integer>> entry2 : nodePlans.entrySet()) {
                                    Map<NodeIdentifier, Integer> nodeServiceInstances = entry2.getValue();

                                    for (Map.Entry<NodeIdentifier, Integer> entry3 : nodeServiceInstances.entrySet()) {
                                        NodeIdentifier node = entry3.getKey();

                                        // check if there is data available for
                                        // this node
                                        Integer instances = entry3.getValue();

                                        for (int n = 0; n < nodes.size(); n++) {
                                            if (node.equals(nodes.get(n))) {
                                                rowValues[1 + n] = instances; // add
                                                                              // instances
                                                                              // value
                                                                              // to
                                                                              // row
                                            }
                                        }

                                        // check if data was already found for
                                        // node at this time
                                        if (!nodesWithoutData.contains(node)) {
                                            LOGGER.info(
                                                    "ERROR: For service {}, duplicate data found for node '{}' at time {}",
                                                    service, node, time);
                                            error = 1;
                                        } else {
                                            nodesWithoutData.remove(node);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!nodesWithoutData.isEmpty()) {
                        LOGGER.info("ERROR: For service {}, no data found for nodes {} at time {}.", service,
                                nodesWithoutData, time);
                        error = 1;
                    }

                    rowValues[1 + nodes.size()] = error;

                    // print rowValues if it is the first or last entry or
                    // different from the previous entry
                    if (prevRowValues == null
                            || !ChartGenerationUtils.compareArrays(prevRowValues, rowValues, 1, nodes.size() + 1)
                            || t == times2.size() - 1)
                        printer.printRecord(rowValues);

                    prevRowValues = rowValues;
                }
            } catch (FileNotFoundException e) {
                LOGGER.error("{}", e);
                e.printStackTrace();
            } catch (IOException e) {
                LOGGER.error("Error writing to CSV file: {}", e);
                e.printStackTrace();
            }
        });
    }

    private void outputOverflowServicePlanChangeMatrices(File outputFolder,
            Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<RegionIdentifier, Double>>>>> serviceRegionPlans,
            List<Long> times,
            List<RegionIdentifier> regions) {
        if (!outputFolder.exists()) {
            if (!outputFolder.mkdirs()) {
                throw new RuntimeException("Unable to create directory " + outputFolder);
            }
        }

        Collections.sort(regions, new Comparator<RegionIdentifier>() {
            @Override
            public int compare(RegionIdentifier o1, RegionIdentifier o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });

        serviceRegionPlans.forEach((service, servicePlans) -> {
            File serviceFile = new File(
                    outputFolder + File.separator + "service_" + ChartGenerationUtils.serviceToFilenameString(service)
                            + "-rlg_overflow_plans" + CSV_FILE_EXTENSION);

            String[] header = new String[1 + regions.size() * regions.size() + 1];
            header[0] = CSV_HEADER_TIMESTAMP;

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
                    Map<RegionIdentifier, Map<String, Map<RegionIdentifier, Double>>> servicePlan = servicePlans
                            .get(time);

                    Object[] rowValues = new Object[header.length];

                    rowValues[0] = time; // add time to row
                    Arrays.fill(rowValues, 1, regions.size() * regions.size() + 1,
                            ChartGenerationUtils.EMPTY_CELL_VALUE);

                    for (int f = 0; f < regions.size(); f++) {
                        RegionIdentifier fromRegion = regions.get(f);

                        if (servicePlan != null && servicePlan.containsKey(fromRegion)) {
                            Map<String, Map<RegionIdentifier, Double>> toRegions = servicePlan.get(fromRegion);

                            if (toRegions.keySet().size() > 1) {
                                LOGGER.info(
                                        "ERROR: More than one plan found for service '{}' at time {} in region '{}' from nodes '{}'",
                                        service, time, fromRegion, toRegions.keySet());
                                error = 1;
                            } else if (toRegions.keySet().isEmpty()) {
                                LOGGER.info("ERROR: No region plan found for service '{}' at time {} in region '{}'",
                                        service, time, fromRegion);
                                error = 1;
                            } else {
                                for (final Map.Entry<String, Map<RegionIdentifier, Double>> entry : toRegions
                                        .entrySet()) {
                                    Map<RegionIdentifier, Double> toRegionPercents = entry.getValue();

                                    for (int t = 0; t < regions.size(); t++) {
                                        RegionIdentifier toRegion = regions.get(t);

                                        // check if there is data available for
                                        // this toRegion
                                        if (toRegionPercents.containsKey(toRegion)) {
                                            Double value = toRegionPercents.get(toRegion);
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
}
