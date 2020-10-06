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
package com.bbn.map.ChartGeneration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.Controller.NodeState;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.diffplug.common.base.Errors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import java8.util.Lists;

/**
 * Generates a CSV file listing plan update times for each leader in each
 * region.
 * 
 * @author awald
 *
 */
public class DCOPPlanUpdateTableGenerator {
    private static final Logger LOGGER = LogManager.getLogger(DCOPPlanUpdateTableGenerator.class);

    private static final String REGION_PLAN_FILENAME = "regionPlan.json";
    private static final String NODE_STATE_FILENAME = "state.json";

    private final ObjectMapper mapper;

    /**
     * Constructs an instance with the modules necessary for JSON
     * deserialization added to the ObjectMapper.
     */
    public DCOPPlanUpdateTableGenerator() {
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
                throw new RuntimeException("Unable to create directory " + outputFolder);
            }
        }

        // region name -> time -> node name -> RegionPlan
        Map<String, Map<Long, Map<String, RegionPlan>>> regionsPlans = new HashMap<>();

        // service -> time -> from region -> node name -> to region -> percent
        // requests
        Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<RegionIdentifier, Double>>>>> serviceRegionPlans = new HashMap<>();
        List<RegionIdentifier> regions = new ArrayList<>();

        ChartGenerationUtils.visitTimeFolders(inputFolder.toPath(), (nodeName, timeFolder, folderTime) -> {

            final Path nodeStateFile = timeFolder.resolve(NODE_STATE_FILENAME);

            try (BufferedReader nodeStateReader = Files.newBufferedReader(nodeStateFile)) {
                final NodeState nodeState = mapper.readValue(nodeStateReader, NodeState.class);
                LOGGER.debug("Read state for node '{}': {}", nodeState.getName(), nodeState.isDCOP());

                if (nodeState.isDCOP()) {
                    final Path regionPlanFile = timeFolder.resolve(REGION_PLAN_FILENAME);

                    if (Files.exists(regionPlanFile)) {
                        try (BufferedReader regionPlanReader = Files.newBufferedReader(regionPlanFile)) {
                            final RegionPlan regionPlan = mapper.readValue(regionPlanReader, RegionPlan.class);                            
                            LOGGER.debug("Read region plan for time {}: {}", folderTime, regionPlan);
                            processRegionPlan(folderTime, nodeState.getRegion().getName(), nodeState.getName(), regionPlan,
                                    regionsPlans);
                        } catch (final IOException e) {
                            LOGGER.error("Error parsing JSON file: {}", regionPlanFile, e);
                        }
                    } else {
                        LOGGER.info("WARNING: At time {} DCOP is running but RegionPlan file '{}' not found.", folderTime,
                                regionPlanFile);
                    }
                } else {
                    processRegionPlan(folderTime, nodeState.getRegion().getName(), nodeState.getName(), null, regionsPlans);
                }

            } catch (final IOException e) {
                LOGGER.error("Error parsing JSON file: {}", nodeStateFile, e);
            }
        });

        final List<Long> times = new ArrayList<>();
        times.addAll(ChartGenerationUtils.getSecondOrderKeys(regionsPlans));
        Collections.sort(times);

        final long firstBinCenter = (times.size() > 0 ? times.get(0) : 0);

        LOGGER.debug("Regions plans: {}", regionsPlans);
        final Map<String, Map<Long, Map<String, RegionPlan>>> timeBinnedRegionsPlans = timeBinRegionPlans(regionsPlans,
                firstBinCenter, binSize);
        LOGGER.debug("Time binnned regions plans: {}", timeBinnedRegionsPlans);

        ChartGenerationUtils.outputRegionLeadersToCSV(outputFolder, "-dcop_leaders", timeBinnedRegionsPlans);
        outputRegionPlanChangeToCSV(outputFolder, timeBinnedRegionsPlans);
        outputRegionPlanChangeByServiceToCSV(outputFolder, timeBinnedRegionsPlans);

        timeBinnedRegionsPlans.forEach((region, regionPlans) -> {
            regionPlans.forEach((time, leaderNodePlans) -> {
                leaderNodePlans.forEach((nodeName, regionPlan) -> {
                    if (regionPlan != null)
                        processServiceRegions(time, nodeName, regionPlan, serviceRegionPlans, regions);
                });
            });
        });

        LOGGER.debug("Service region plans: {}", serviceRegionPlans);
        outputServicePlanChangeMatrices(outputFolder, serviceRegionPlans,
                Lists.copyOf(ChartGenerationUtils.getSecondOrderKeys(timeBinnedRegionsPlans)), regions);
        outputServicePlanChangeGnuPlotFiles(outputFolder, serviceRegionPlans, regions);

        try {
            outputDcopPlans(outputFolder.toPath(), timeBinnedRegionsPlans, regions);
        } catch (final IOException e) {
            LOGGER.error("Unable to print DCOP plans", e);
        }
    }

    private void outputDcopPlans(final Path outputFolder,
            final Map<String, Map<Long, Map<String, RegionPlan>>> timeBinnedRegionsPlans,
            final List<RegionIdentifier> regions) throws IOException {

        final Map<RegionIdentifier, Map<Long, RegionPlan>> plansPerRegion = new HashMap<>();
        timeBinnedRegionsPlans.forEach((regionName, regionData) -> {
            regionData.forEach((time, timeData) -> {
                timeData.forEach((nodeName, plan) -> {
                    if (null != plan) {
                        plansPerRegion.computeIfAbsent(plan.getRegion(), k -> new HashMap<>()).put(time, plan);
                    }
                });
            });
        });

        final int numNonRegionColumns = 3;
        final String[] header = new String[numNonRegionColumns + regions.size()];
        header[0] = ChartGenerationUtils.CSV_TIME_COLUMN_LABEL;
        header[1] = "service";
        header[2] = "plan_region";
        for (int i = 0; i < regions.size(); ++i) {
            header[numNonRegionColumns + i] = regions.get(i).getName();
        }

        final CSVFormat format = CSVFormat.EXCEL.withHeader(header);

        final Path outputFile = outputFolder.resolve("all_dcop_plans" + ChartGenerationUtils.CSV_FILE_EXTENSION);
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, Charset.defaultCharset());
                CSVPrinter printer = new CSVPrinter(writer, format)) {

            // use Object[] so that printer.printRecord handles the
            // variable arguments correctly
            final Object[] line = new String[header.length];

            plansPerRegion.entrySet().forEach(Errors.rethrow().wrap(regionEntry -> {
                final RegionIdentifier region = regionEntry.getKey();
                line[2] = region.getName();

                regionEntry.getValue().entrySet().forEach(Errors.rethrow().wrap(timeEntry -> {
                    final long time = timeEntry.getKey();
                    final RegionPlan plan = timeEntry.getValue();
                    line[0] = Long.toString(time);

                    final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlan = plan
                            .getPlan();

                    servicePlan.entrySet().forEach(Errors.rethrow().wrap(serviceEntry -> {
                        final ServiceIdentifier<?> service = serviceEntry.getKey();

                        // mark all overflow regions as unknown values by
                        // default
                        Arrays.fill(line, numNonRegionColumns, line.length, ChartGenerationUtils.EMPTY_CELL_VALUE);

                        line[1] = ChartGenerationUtils.serviceToFilenameString(service);
                        
                        final double overflowSum = serviceEntry.getValue().entrySet().stream().mapToDouble(Map.Entry::getValue).sum();

                        serviceEntry.getValue().entrySet().forEach(planEntry -> {
                            final RegionIdentifier overflowRegion = planEntry.getKey();
                            final double value = planEntry.getValue();
                            final int regionIndex = regions.indexOf(overflowRegion);
                            if (-1 == regionIndex) {
                                throw new RuntimeException(
                                        "Cannot find index of " + overflowRegion + " in regions: " + regions);
                            }

                            // divide by sum to ensure that we have a common base of 1
                            line[regionIndex + numNonRegionColumns] = Double.toString(value / overflowSum);
                        }); // foreach region plan entry

                        printer.printRecord(line);
                    })); // foreach service
                })); // foreach time

            })); // foreach region

        } // allocate csv writer
    }

    /* package */ static <V>
            Map<String, Map<Long, Map<String, V>>>
            timeBinRegionPlans(Map<String, Map<Long, Map<String, V>>> regionsPlans, long firstBinCenter, long binSize) {
        Map<String, Map<Long, Map<String, V>>> result = new HashMap<>();

        regionsPlans.forEach((region, regionPlans) -> {
            if (!result.containsKey(region))
                result.put(region, new HashMap<>());

            regionPlans.forEach((time, plans) -> {
                long binTime = ChartGenerationUtils.getBinForTime(time, firstBinCenter, binSize);

                if (!result.get(region).containsKey(binTime))
                    result.get(region).put(binTime, new HashMap<>());

                plans.forEach((node, plan) -> {
                    result.get(region).get(binTime).put(node, plan);
                });
            });

            result.get(region).forEach((binTime, plans) -> {
                List<String> planNodes = new ArrayList<>();

                plans.forEach((node, plan) -> {
                    if (plan != null)
                        planNodes.add(node);
                });

                if (planNodes.isEmpty())
                    LOGGER.info("WARNING: No plans found for region '{}' at time {}", region, binTime);
                else if (planNodes.size() > 1)
                    LOGGER.info("WARNING: More than one plan found for region '{}' at time {} from nodes {}", region,
                            binTime, planNodes);
            });
        });

        return result;
    }

    private static void processServiceRegions(long time,
            String nodeName,
            RegionPlan regionPlan,
            Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<RegionIdentifier, Double>>>>> serviceRegionPlans,
            List<RegionIdentifier> regions) {
        LOGGER.debug("Add region plan for time {} and node '{}': {}", time, nodeName, regionPlan);

        RegionIdentifier fromRegion = regionPlan.getRegion();

        if (!regions.contains(fromRegion))
            regions.add(fromRegion);

        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> servicePlans = regionPlan.getPlan();

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
                LOGGER.info(
                        "WARNING: Replacing plan for service '{}' at time {} for region '{}' from leader node '{}'.",
                        service, time, fromRegion, nodeName);

            serviceRegionPlans.get(service).get(time).get(fromRegion).put(nodeName, shedToRegionPercentagesMap);
        });
    }

    private void processRegionPlan(long time,
            String regionName,
            String nodeName,
            RegionPlan regionPlan,
            Map<String, Map<Long, Map<String, RegionPlan>>> regionPlans) {
        LOGGER.debug("Process plan at time {} for node '{}' in region '{}': {}", time, nodeName, regionName,
                regionPlan);

        if (!regionPlans.containsKey(regionName))
            regionPlans.put(regionName, new HashMap<>());

        if (!regionPlans.get(regionName).containsKey(time))
            regionPlans.get(regionName).put(time, new HashMap<>());

        regionPlans.get(regionName).get(time).put(nodeName, regionPlan);
    }

    private void outputRegionPlanChangeToCSV(File outputFolder,
            Map<String, Map<Long, Map<String, RegionPlan>>> regionsPlans) {
        regionsPlans.forEach((region, regionPlans) -> {
            File regionFile = new File(outputFolder + File.separator + "region_" + region + "-dcop_plan_change_times"
                    + ChartGenerationUtils.CSV_FILE_EXTENSION);
            LOGGER.debug("Start file for region {}: {}", region, regionFile);

            CSVFormat format = CSVFormat.EXCEL.withHeader(ChartGenerationUtils.CSV_TIME_COLUMN_LABEL);

            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(regionFile), Charset.defaultCharset()), format)) {
                List<Long> times = new ArrayList<>();
                times.addAll(regionPlans.keySet());
                Collections.sort(times);

                RegionPlan previousPlan = null;

                for (long time : times) {
                    RegionPlan plan = null;

                    for (String nodeName : regionPlans.get(time).keySet()) {
                        RegionPlan rp = regionPlans.get(time).get(nodeName);

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

                    // if there is a plan published in the region at this time
                    if (plan != null) {
                        if (previousPlan == null || !previousPlan.equals(plan))
                            printer.printRecord(time);
                    }

                    previousPlan = plan;
                }

                LOGGER.info("Finished writing file: {}", regionFile);
            } catch (IOException e) {
                LOGGER.error("Error writing to CSV file: {}", e);
            }

        });
    }

    private void outputRegionPlanChangeByServiceToCSV(File outputFolder,
            Map<String, Map<Long, Map<String, RegionPlan>>> regionsPlans) {
        regionsPlans.forEach((region, regionPlans) -> {
            File regionFile = new File(outputFolder + File.separator + "region_" + region
                    + "-dcop_plan_changes_by_service" + ChartGenerationUtils.CSV_FILE_EXTENSION);
            LOGGER.debug("Start file for region {}: {}", region, regionFile);

            Set<ServiceIdentifier<?>> serviceSet = new HashSet<>();
            regionPlans.forEach((time, nodePlans) -> {
                nodePlans.forEach((nodeName, plan) -> {
                    if (plan != null)
                        serviceSet.addAll(plan.getPlan().keySet());
                });
            });

            LOGGER.debug("Services: {}", serviceSet);

            List<ServiceIdentifier<?>> services = new ArrayList<>();
            services.addAll(serviceSet);
            Collections.sort(services, new Comparator<ServiceIdentifier<?>>() {
                @Override
                public int compare(ServiceIdentifier<?> o1, ServiceIdentifier<?> o2) {
                    return o1.toString().compareTo(o2.toString());
                }
            });

            String[] header = new String[services.size() + 1];
            header[0] = ChartGenerationUtils.CSV_TIME_COLUMN_LABEL;

            for (int n = 0; n < services.size(); n++)
                header[n + 1] = services.get(n).toString();

            CSVFormat format = CSVFormat.EXCEL.withHeader(header);

            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(regionFile), Charset.defaultCharset()), format)) {
                List<Long> times = new ArrayList<>();
                times.addAll(regionPlans.keySet());
                Collections.sort(times);

                RegionPlan previousPlan = null;

                for (long time : times) {
                    RegionPlan plan = null;

                    for (String nodeName : regionPlans.get(time).keySet()) {
                        RegionPlan rp = regionPlans.get(time).get(nodeName);

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

                    // if there is a plan published in the region at this time
                    Map<ServiceIdentifier<?>, Boolean> difference = compareRegionPlansPerService(previousPlan, plan);

                    List<Long> row = new ArrayList<>();
                    row.add(time);

                    for (ServiceIdentifier<?> service : services) {
                        Boolean changed = difference.get(service);

                        if (changed == null)
                            changed = false;

                        row.add(changed ? 1L : 0L);
                    }

                    printer.printRecord(row);

                    previousPlan = plan;
                }

                LOGGER.info("Finished writing file: {}", regionFile);
            } catch (IOException e) {
                LOGGER.error("Error writing to CSV file: {}", e);
            }

        });
    }

    private void outputServicePlanChangeGnuPlotFiles(File outputFolder,
            Map<ServiceIdentifier<?>, Map<Long, Map<RegionIdentifier, Map<String, Map<RegionIdentifier, Double>>>>> serviceRegionPlans,
            List<RegionIdentifier> regions) {
        Map<ServiceIdentifier<?>, Map<RegionIdentifier, Map<Long, Map<RegionIdentifier, Double>>>> serviceRegionPlans2 = new HashMap<>();
        Map<RegionIdentifier, Map<Long, Map<ServiceIdentifier<?>, Map<RegionIdentifier, Double>>>> serviceRegionPlans3 = new HashMap<>();
        List<Long> allTimes = new ArrayList<>();

        LOGGER.debug("serviceRegionPlans: {}", serviceRegionPlans);

        serviceRegionPlans.forEach((service, regionsPlans) -> {
            serviceRegionPlans2.put(service, new HashMap<>());

            regionsPlans.forEach((time, fromRegionPlans) -> {
                allTimes.add(time);

                fromRegionPlans.forEach((fromRegion, leaderPlans) -> {
                    if (!serviceRegionPlans2.get(service).containsKey(fromRegion))
                        serviceRegionPlans2.get(service).put(fromRegion, new HashMap<>());

                    if (!serviceRegionPlans3.containsKey(fromRegion))
                        serviceRegionPlans3.put(fromRegion, new HashMap<>());

                    if (!serviceRegionPlans3.get(fromRegion).containsKey(time))
                        serviceRegionPlans3.get(fromRegion).put(time, new HashMap<>());

                    leaderPlans.forEach((leader, plan) -> {
                        serviceRegionPlans2.get(service).get(fromRegion).put(time, plan);
                        serviceRegionPlans3.get(fromRegion).get(time).put(service, plan);
                    });
                });
            });

            for (RegionIdentifier region : regions) {
                if (!serviceRegionPlans2.get(service).containsKey(region)) {
                    serviceRegionPlans2.get(service).put(region, new HashMap<>());
                }

                if (!serviceRegionPlans3.containsKey(region)) {
                    serviceRegionPlans3.put(region, new HashMap<>());
                }
            }
        });

        Collections.sort(allTimes);
        LOGGER.debug("serviceRegionPlans2: {}", serviceRegionPlans2);

        serviceRegionPlans2.forEach((service, regionsPlans) -> {
            regionsPlans.forEach((fromRegion, timePlans) -> {
                File serviceRegionGnuPlotFile = new File(
                        outputFolder + File.separator + "dcop-" + ChartGenerationUtils.serviceToFilenameString(service)
                                + "_" + fromRegion.toString() + "-gnu.txt");

                List<Long> times = new ArrayList<>();
                times.addAll(timePlans.keySet());
                Collections.sort(times);

                long endTime = (allTimes.isEmpty() ? 0 : allTimes.get(allTimes.size() - 1));

                if (!times.isEmpty())
                    endTime = times.get(times.size() - 1);

                for (int n = 1; n < times.size();) {
                    if (timePlans.get(times.get(n)).equals(timePlans.get(times.get(n - 1)))) {
                        times.remove(n);
                    } else {
                        n++;
                    }
                }

                try (Writer writer = Files.newBufferedWriter(serviceRegionGnuPlotFile.toPath(),
                        Charset.defaultCharset())) {
                    try (GnuPlotPrinter printer = new GnuPlotPrinter(writer, "Region Plan:\\n", endTime)) {
                        printer.printEntry(0, (times.isEmpty() ? endTime : times.get(0)), new HashMap<>());

                        for (int n = 0; n < times.size(); n++) {
                            long time = times.get(n);
                            long nextTime = (n + 1 < times.size() ? times.get(n + 1) : endTime);

                            try {
                                printer.printEntry(time, nextTime, timePlans.get(time));
                            } catch (IOException e) {
                                LOGGER.error("Unable to print GNU plot entry: {}", e);
                            }
                        }
                    }
                } catch (IOException e) {
                    LOGGER.error("Unable to print GNU plot entries: {}", e);
                }
            });
        });

        serviceRegionPlans3.forEach((fromRegion, timeServicePlans) -> {
            List<Long> times = new ArrayList<>();
            times.addAll(timeServicePlans.keySet());
            Collections.sort(times);

            long endTime = (allTimes.isEmpty() ? 0 : allTimes.get(allTimes.size() - 1));

            if (!times.isEmpty())
                endTime = times.get(times.size() - 1);

            for (int n = 1; n < times.size();) {
                if (timeServicePlans.get(times.get(n)).equals(timeServicePlans.get(times.get(n - 1)))) {
                    times.remove(n);
                } else {
                    n++;
                }
            }

            // timeServicePlans.forEach((time, servicePlans) ->
            // {
            File serviceRegionGnuPlotFile = new File(
                    outputFolder + File.separator + "dcop-" + fromRegion.toString() + "-gnu.txt");

            try (Writer writer = Files.newBufferedWriter(serviceRegionGnuPlotFile.toPath(), Charset.defaultCharset())) {
                try (GnuPlotPrinter printer = new GnuPlotPrinter(writer, "Region Plan:", endTime)) {
                    printer.printEntry(0, (times.isEmpty() ? endTime : times.get(0)), new HashMap<>());

                    for (int n = 0; n < times.size(); n++) {
                        long time = times.get(n);
                        long nextTime = (n + 1 < times.size() ? times.get(n + 1) : endTime);

                        try {
                            printer.printServiceEntry(time, nextTime, timeServicePlans.get(time));
                        } catch (IOException e) {
                            LOGGER.error("Unable to print GNU plot entry: {}", e);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Unable to print GNU plot entries: {}", e);
            }
        });
        // });
    }

    private void outputServicePlanChangeMatrices(File outputFolder,
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
                            + "-dcop_plan_request_distribution" + ChartGenerationUtils.CSV_FILE_EXTENSION);

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

    private Map<ServiceIdentifier<?>, Boolean> compareRegionPlansPerService(RegionPlan regionPlan1,
            RegionPlan regionPlan2) {
        LOGGER.debug("Comparing by service: {} :: {}", regionPlan1, regionPlan2);

        Map<ServiceIdentifier<?>, Boolean> difference = new HashMap<>();

        ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> plan1, plan2;

        if (regionPlan1 != null)
            plan1 = regionPlan1.getPlan();
        else
            plan1 = ImmutableMap.copyOf(new HashMap<>());

        if (regionPlan2 != null)
            plan2 = regionPlan2.getPlan();
        else
            plan2 = ImmutableMap.copyOf(new HashMap<>());

        Set<ServiceIdentifier<?>> services = new HashSet<>();
        services.addAll(plan1.keySet());
        services.addAll(plan2.keySet());

        services.forEach((service) -> {
            boolean changed = false;
            Map<RegionIdentifier, Double> servicePlan1 = plan1.get(service);
            Map<RegionIdentifier, Double> servicePlan2 = plan2.get(service);

            if (servicePlan1 == null)
                changed = (servicePlan2 != null);
            else
                changed = !servicePlan1.equals(servicePlan2);

            difference.put(service, changed);
        });

        return difference;
    }
}
