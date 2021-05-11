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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.diffplug.common.base.Errors;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Output tables of network data for each node.
 * 
 * @author jschewe
 *
 */
public class NetworkLoadTable {
    private static final Logger LOGGER = LogManager.getLogger(NetworkLoadTable.class);

    private final ObjectMapper mapper;

    /** node -> time -> node data */
    private final Map<NodeIdentifier, SortedMap<Long, NodeData>> data = new HashMap<>();

    /**
     * Constructs an object to generate tables for projected client demand.
     */
    public NetworkLoadTable() {
        mapper = JsonUtils.getStandardMapObjectMapper();
    }

    /**
     * Processes data for a scenario to produce compute load tables.
     * 
     * @param inputFolder
     *            the folder containing the results of the scenario simulation
     * @param outputFolder
     *            the folder to output the chart tables to
     */
    public void processScenarioData(final Path inputFolder, final Path outputFolder) {

        if (!Files.exists(inputFolder)) {
            LOGGER.error("Input folder does not exist: {}", inputFolder);
            return;
        }
        ChartGenerationUtils.visitTimeFolders(inputFolder, (nodeName, timeFolder, folderTime) -> {
            final Path resourceReportLongFile = timeFolder.resolve(LoadChartGenerator.RESOURCE_REPORT_LONG_FILENAME);
            try (BufferedReader reader = Files.newBufferedReader(resourceReportLongFile)) {
                final ResourceReport report = mapper.readValue(reader, ResourceReport.class);
                final long reportTime = report.getTimestamp();

                if (reportTime > 0) {
                    final SortedMap<Long, NodeData> nodeTimeData = data.computeIfAbsent(report.getNodeName(),
                            k -> new TreeMap<>());
                    final NodeData nodeData = nodeTimeData.computeIfAbsent(reportTime, k -> new NodeData());

                    gatherNodeData(reportTime, report, nodeData);
                } else {
                    LOGGER.warn("Found data with time ({}) not > 0. Ignoring: {}", reportTime, report);
                }
            } catch (JsonParseException | JsonMappingException e) {
                LOGGER.error("Error parsing JSON file {}", resourceReportLongFile, e);
            } catch (NoSuchFileException | FileNotFoundException e) {
                LOGGER.warn("Could not find resource report file: {}", resourceReportLongFile, e);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });

        try {
            writeTableData(outputFolder);
        } catch (final IOException e) {
            LOGGER.error("Error writing table data", e);
        }
    }

    private void writeTableData(final Path outputFolder) throws IOException {
        final Path networkFolder = outputFolder.resolve("network");
        if (!Files.exists(networkFolder)) {
            Files.createDirectories(networkFolder);
        }

        data.entrySet().forEach(Errors.rethrow().wrap(e -> {
            final NodeIdentifier node = e.getKey();
            final SortedMap<Long, NodeData> timeData = e.getValue();
            writeNodeData(networkFolder, node, timeData);
            writeContainerData(networkFolder, node, timeData);
        }));
    }

    private void
            writeNodeData(final Path networkFolder, final NodeIdentifier node, final SortedMap<Long, NodeData> timeData)
                    throws FileNotFoundException, IOException {
        final String[] header = { ChartGenerationUtils.CSV_TIME_COLUMN_LABEL, SERVICE_COLUMN_HEADER, RX_COLUMN_HEADER,
                TX_COLUMN_HEADER };

        CSVFormat format = CSVFormat.EXCEL.withHeader(header);

        final Path outputFile = networkFolder
                .resolve(node.getName() + "_node-data" + ChartGenerationUtils.CSV_FILE_EXTENSION);
        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(new FileOutputStream(outputFile.toFile()), Charset.defaultCharset()), format)) {
            timeData.forEach((time, ndata) -> {

                ndata.serviceData.entrySet().forEach(Errors.rethrow().wrap(e -> {
                    final String service = e.getKey();
                    final NetworkData netData = e.getValue();

                    final List<Object> row = new LinkedList<>();
                    row.add(time);
                    row.add(service);
                    row.add(netData.rx);
                    row.add(netData.tx);
                    printer.printRecord(row);
                }));

            });
        }
    }

    private static final String SERVICE_COLUMN_HEADER = "service";
    private static final String RX_COLUMN_HEADER = "RX";
    private static final String TX_COLUMN_HEADER = "TX";

    private void writeContainerData(final Path networkFolder,
            final NodeIdentifier node,
            final SortedMap<Long, NodeData> timeData) throws FileNotFoundException, IOException {
        final String[] header = { "container", ChartGenerationUtils.CSV_TIME_COLUMN_LABEL, SERVICE_COLUMN_HEADER,
                RX_COLUMN_HEADER, TX_COLUMN_HEADER };

        CSVFormat format = CSVFormat.EXCEL.withHeader(header);

        final Path outputFile = networkFolder
                .resolve(node.getName() + "_container-data" + ChartGenerationUtils.CSV_FILE_EXTENSION);
        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(new FileOutputStream(outputFile.toFile()), Charset.defaultCharset()), format)) {
            timeData.forEach((time, ndata) -> {

                ndata.containerData.entrySet().forEach(Errors.rethrow().wrap(ce -> {
                    final NodeIdentifier container = ce.getKey();
                    final ContainerData cData = ce.getValue();

                    cData.serviceData.entrySet().forEach(Errors.rethrow().wrap(se -> {
                        final String service = se.getKey();
                        final NetworkData netData = se.getValue();

                        final List<Object> row = new LinkedList<>();
                        row.add(container.getName());
                        row.add(time);
                        row.add(service);
                        row.add(netData.rx);
                        row.add(netData.tx);
                        printer.printRecord(row);
                    }));

                }));

            });
        }

    }

    private void gatherNodeData(final long time, final ResourceReport report, final NodeData nodeData) {
        report.getNetworkLoad().forEach((neighbor, neighborLoad) -> {
            neighborLoad.forEach((source, sourceLoad) -> {
                sourceLoad.forEach((service, serviceLoad) -> {
                    final String serviceArtifact;
                    if (service instanceof ApplicationCoordinates) {
                        serviceArtifact = ((ApplicationCoordinates) service).getArtifact();
                    } else {
                        serviceArtifact = service.toString();
                    }

                    final NetworkData serviceData = nodeData.serviceData.computeIfAbsent(serviceArtifact,
                            k -> new NetworkData());
                    serviceData.rx += serviceLoad.getOrDefault(LinkAttribute.DATARATE_RX, 0D);
                    serviceData.tx += serviceLoad.getOrDefault(LinkAttribute.DATARATE_TX, 0D);
                });
            });
        });
    }

    // CHECKSTYLE:OFF data class
    private static final class NetworkData {
        public double rx = 0;
        public double tx = 0;
    }

    private static final class NodeData {
        /**
         * service artifact -> network data
         */
        public final Map<String, NetworkData> serviceData = new HashMap<>();
        public final Map<NodeIdentifier, ContainerData> containerData = new HashMap<>();
    }

    private static final class ContainerData {
        /**
         * service artifact -> network data
         */
        public final Map<String, NetworkData> serviceData = new HashMap<>();
    }
    // CHECKSTYLE:ON
}
