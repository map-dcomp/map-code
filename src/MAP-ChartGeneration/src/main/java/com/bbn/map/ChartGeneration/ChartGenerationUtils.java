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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.ServiceConfiguration;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringServiceIdentifier;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Class containing utility functions for producing chart tables.
 * 
 * @author awald
 *
 */
public final class ChartGenerationUtils {
    private static final Logger LOGGER = LogManager.getLogger(ChartGenerationUtils.class);

    /**
     * File extension to use for CSV output files.
     * Includes the dot.
     */
    public static final String CSV_FILE_EXTENSION = ".csv";

    /**
     * Column header for timestamp.
     */
    public static final String CSV_TIME_COLUMN_LABEL = "time";

    /**
     * Value to use for empty data cells in CSV output.
     */
    public static final Object EMPTY_CELL_VALUE = "?";

    /**
     * The name of the simulation folder in low fidelity test bed output.
     */
    public static final String SCENARIO_SIMULATION_FOLDER_NAME = "simulation";
    
    /**
     * The default bin size to use for binning.
     */
    public static final long DEFAULT_BIN_SIZE = 10000;
    
    /**
     * The default sample interval, usually the same as bin size.
     */
    public static final long DEFAULT_SAMPLE_INTERVAL = 10000;
    
    /**
     * The default first bin center to use for binning.
     */
    public static final long DEFAULT_FIRST_BIN_CENTER = 0;
    
    /**
     * The suffix for domain names of nodes, containers, or services in the MAP system.
     */
    public static final String MAP_DOMAIN_NAME_SUFFIX = ".map.dcomp";
    
    
    
    
    private ChartGenerationUtils() {}

    /**
     * Outputs the given table information to a CSV file for all NodeAttribute
     * attributes in the given table.
     * 
     * @param <V>
     *            the type of values that are stored in the data table
     * @param outputFolder
     *            the folder to output the table information to
     * @param filenamePrefix
     *            the prefix to use for the names of all files being written out
     * @param extractor
     *            converts a Value object into a piece of data to output
     * @param table
     *            the data to output
     */
    public static <V> void outputDataToCSV(File outputFolder,
            String filenamePrefix,
            ValueExtractor<V> extractor, 
            Map<Long, Map<String, Map<NodeAttribute, V>>> table) {
        Set<NodeAttribute> attributes = new HashSet<>();
        attributes.addAll(getAttributesFromTableMap(table));

        outputDataToCSV(outputFolder, filenamePrefix, attributes, extractor, table);
    }

    /**
     * Outputs the given table information to a CSV file for each NodeAttribute
     * attribute.
     * 
     * @param <V>
     *            the type of values that are stored in the data table
     * @param outputFolder
     *            the folder to output the table information to
     * @param filenamePrefix
     *            the prefix to use for the names of all files being written out
     * @param attributes
     *            a list of NodeAttributes to output files for
     * @param extractor
     *            converts a Value object into a piece of data to output
     * @param table
     *            the data to output
     */
    public static <V> void outputDataToCSV(File outputFolder,
            String filenamePrefix,
            Set<NodeAttribute> attributes,
            ValueExtractor<V> extractor,
            Map<Long, Map<String, Map<NodeAttribute, V>>> table) {
        if (!outputFolder.exists()) {
            if (!outputFolder.mkdirs()) {
                throw new RuntimeException("Unable to create " + outputFolder);
            }
        }

        for (NodeAttribute attr : attributes) {
            final String filename = outputFolder.getPath() + File.separator + filenamePrefix + "-"
                    + ((NodeAttribute) attr).getName() + ".csv";
            final File file = new File(filename);
            outputDataToCSV(file, attr, extractor, table);
        }
    }

    /**
     * Outputs multiple CSV files, each with a specified set of columns to
     * include from the given data.
     * 
     * @param <V>
     *            the type of values that are stored in the data table
     * @param outputFolder
     *            the folder to output the table information to
     * @param columnLabels
     *            the labels of the columns to include in the CSV files
     * @param filenameSuffix
     *            the suffix to use for the names of all files being written out
     * @param data
     *            the Map (time -> label -> column label -> value) of data to
     *            output to CSV format. One CSV file will be output per label in
     *            the Map.
     */
    public static <V> void outputDataToCSV2(File outputFolder,
            List<String> columnLabels,
            String filenameSuffix,
            Map<Long, Map<String, Map<String, V>>> data) {
        if (!outputFolder.exists()) {
            if (!outputFolder.mkdirs()) {
                throw new RuntimeException("Unable to create directory " + outputFolder);
            }
        }

        for (String label : getSecondOrderKeys(data)) {
            final String filename = outputFolder.getPath() + File.separator + label + "-" + filenameSuffix + ".csv";
            final File file = new File(filename);
            outputDataToCSV2(file, label, columnLabels, data);
        }
    }

    /**
     * Outputs a single CSV file using information under a {@link label} key and
     * consisting of columns specified by {@link columnLabels} in the given data
     * Map.
     * 
     * @param <V>
     *            the type of values that are stored in the data table
     * @param outputFile
     *            a File to output CSV data to
     * @param label
     *            the label to reference in the Map when accessing data to write
     *            to the CSV file
     * @param columnLabels
     *            the labels of the columns to include in the CSV file
     * @param data
     *            the Map (time -> label -> column label -> value) of data to
     *            output to CSV format.
     */
    public static <V> void outputDataToCSV2(File outputFile,
            String label,
            List<String> columnLabels,
            Map<Long, Map<String, Map<String, V>>> data) {
        String[] header = new String[1 + columnLabels.size()];
        int n = 0;
        header[n] = CSV_TIME_COLUMN_LABEL;

        for (String column : columnLabels) {
            n++;
            header[n] = column;
        }

        CSVFormat format = CSVFormat.EXCEL.withHeader(header);

        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(new FileOutputStream(outputFile), Charset.defaultCharset()), format)) {
            List<Long> times = new ArrayList<>();

            for (long time : data.keySet())
                times.add(time);

            Collections.sort(times);

            for (long time : times) {
                List<Object> row = new ArrayList<>();
                row.add(time);

                for (int c = 1; c < header.length; c++) {
                    String columnLabel = header[c];

                    if (data.get(time).get(label).containsKey(columnLabel))
                        row.add(data.get(time).get(label).get(columnLabel));
                    else
                        row.add(EMPTY_CELL_VALUE);
                }

                printer.printRecord(row);
            }

            LOGGER.info("Outputted data to file: {}", outputFile);

        } catch (IOException e) {
            LOGGER.error("Error writing to CSV file: {}", e);
        }
    }

    /**
     * Outputs the given table data information to a CSV file for a single
     * attribute.
     * 
     * @param <A>
     *            the type of the attribute for which to output data
     * @param <V>
     *            the type of values that are stored in the data table
     * @param outputFile
     *            the file to output data to
     * @param attribute
     *            an attribute in the Map for which to output data to the given
     *            file
     * @param extractor
     *            converts a Value object into a piece of data to output
     * @param data
     *            the data to output
     */
    public static <A, V> void outputDataToCSV(File outputFile, A attribute, ValueExtractor<V> extractor, Map<Long, Map<String, Map<A, V>>> data) {
        List<String> labels = new ArrayList<>();
        labels.addAll(getSecondOrderKeys(data));
        Collections.sort(labels);

        LOGGER.debug("outputDataToCSV: labels: {}", labels);

        String[] header = new String[1 + labels.size()];
        int n = 0;
        header[n] = CSV_TIME_COLUMN_LABEL;

        for (String label : labels) {
            n++;
            header[n] = label;
        }

        CSVFormat format = CSVFormat.EXCEL.withHeader(header);

        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(new FileOutputStream(outputFile), Charset.defaultCharset()), format)) {
            List<Long> times = new ArrayList<>();

            for (long time : data.keySet())
                times.add(time);

            Collections.sort(times);

            for (long time : times) {
                List<Object> row = new ArrayList<>();
                row.add(time);

                for (String label : labels) {                    
                    if (data.get(time).containsKey(label) && data.get(time).get(label).containsKey(attribute)) {
                        row.add(extractor.extract(data.get(time).get(label).get(attribute)));
                    } else {
                        row.add(EMPTY_CELL_VALUE);
                    }
                }

                printer.printRecord(row);
            }

            LOGGER.info("Outputted data to file: {}", outputFile);

        } catch (IOException e) {
            LOGGER.error("Error writing to CSV file: {}", e);
        }
    }
    
    /**
     * Used to pass into function for extracting a piece of data from a Value. 
     *
     * @param <V>
     *          the type of the value to extract a piece of data from
     */
    public interface ValueExtractor<V>
    {
        /**
         * Extracts data from Value.
         * 
         * @param v
         *          input Value
         * @return a data Object extracted from value
         */
        Object extract(V v);
    }
    

    /**
     * Collects attributes (the innermost key of type {@link A}) from the given
     * Map and returns the attributes as a Set.
     * 
     * @param <A>
     *            the type of attributes in the Map
     * @param <V>
     *            the type of values in the Map
     * @param table
     *            the Map to collect attributes from
     * @return the attributes found in the Map
     */
    public static <A, V> Set<A> getAttributesFromTableMap(Map<Long, Map<String, Map<A, V>>> table) {
        Set<A> attributes = new HashSet<>();

        table.forEach((time, labelMap) -> {
            labelMap.forEach((label, attributeValues) -> {
                attributes.addAll(attributeValues.keySet());
            });
        });

        return attributes;
    }

    /**
     * 
     * @param <T>
     *            the type of the keys
     * @param <AV>
     *            the value type of the internal map
     * @param map
     *            source map
     * @return Get all keys from the internal map
     */
    public static <T, AV> Set<T> getSecondOrderKeys(Map<?, Map<T, AV>> map) {
        Set<T> keys = new HashSet<>();

        map.forEach((key1, map2) -> {
            keys.addAll(map2.keySet());
        });

        return keys;
    }

    /**
     * Takes in a node name and returns the part of the name before the first
     * '.' or the full name if no '.' is found.
     * 
     * @param nodeName
     *            the name of a node, either the plain name or the full domain
     *            name
     * @return the plain name of the node
     */
    public static String extractNodeName(String nodeName) {
        int index = nodeName.indexOf('.');
        return (index >= 0 ? nodeName.substring(0, index) : nodeName);
    }

    /**
     * Determines the center of the bin for the given time.
     * 
     * @param time
     *            a time to determine the bin for
     * @param firstBinCenter
     *            the center in time of the first bin
     * @param binSize
     *            the time span of each bin
     * @return the center of the bin that the given time was placed into
     */
    public static long getBinForTime(long time, long firstBinCenter, long binSize) {
        long firstBinStart = firstBinCenter - binSize / 2;
        long binIndex = (time - firstBinStart) / binSize;
        return (binIndex * binSize + firstBinCenter);
    }

    /**
     * Find the node folder under search base. This is the folder that contains
     * all of the timestamp folders.
     * 
     * @param searchBase
     *            the base directory to search in, this directory MUST contain
     *            only files for 1 NCP
     * @return the folder or null if one is not found
     * @throws IOException
     *             if there was a problem walking the directory structure
     */
    public static Path findNcpFolder(final Path searchBase) throws IOException {
        final Path firstLongResourceReportPath = Files.walk(searchBase).filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals(LoadChartGenerator.RESOURCE_REPORT_LONG_FILENAME))
                .findFirst().orElse(null);
        LOGGER.trace("Found resource report at {}", firstLongResourceReportPath);
        if (null == firstLongResourceReportPath) {
            return null;
        } else {
            final Path firstTimeDir = firstLongResourceReportPath.getParent();
            LOGGER.trace("First time dir is {}", firstTimeDir);
            if (null == firstTimeDir) {
                return null;
            }

            final Path nodeDir = firstTimeDir.getParent();
            LOGGER.trace("Node dir is {}", nodeDir);

            return nodeDir;
        }
    }

    /**
     * Extracts the node name from the path of a node folder.
     * 
     * @param nodeFolder
     *            the file for the node folder
     * @return the name of the node corresponding to the node folder
     */
    public static String getNodeNameFromNodeFolder(final Path nodeFolder) {
        if (null == nodeFolder || null == nodeFolder.getFileName()) {
            return null;
        } else {
            final Path fname = nodeFolder.getFileName();
            if (null == fname) {
                return null;
            } else {
                return fname.toString().replace(".map.dcomp", "");
            }
        }
    }

    /**
     * For use with {@link ChartGenerationUtils#visitTimeFolders(Path)}.
     * 
     * @author jschewe
     *
     */
    public interface TimeFolderVisitor {
        /**
         * 
         * @param nodeName
         *            the name of the node
         * @param timeFolder
         *            the time folder
         * @param time
         *            the time based on the folder name
         */
        void visit(String nodeName, Path timeFolder, long time);
    }

    /**
     * 
     * @param inputFolder
     *            base folder to find node folders and eventually time folders
     *            in
     * @param visitor
     *            called for each time folder
     */
    public static void visitTimeFolders(final Path inputFolder, final TimeFolderVisitor visitor) {
        try (DirectoryStream<Path> inputFolderStream = Files.newDirectoryStream(inputFolder)) {
            for (final Path nodeBaseFolder : inputFolderStream) {
                if (Files.isDirectory(nodeBaseFolder)) {
                    try {
                        final Path nodeFolder = ChartGenerationUtils.findNcpFolder(nodeBaseFolder);
                        LOGGER.trace("Found NCP directory at {} from {}", nodeFolder, nodeBaseFolder);
                        if (null == nodeFolder) {
                            continue;
                        }

                        final Path nodeFilename = nodeFolder.getFileName();
                        if (null == nodeFilename) {
                            continue;
                        }

                        // if the folder is for an NCP
                        if (!nodeFilename.toString().equals(ChartGenerator.SIMULATION_FILES_FOLDER_NAME)) {
                            final String nodeName = ChartGenerationUtils.getNodeNameFromNodeFolder(nodeFolder); // nodeFolder.getName();

                            try (DirectoryStream<Path> stream = Files.newDirectoryStream(nodeFolder)) {
                                for (final Path timeFolder : stream) {
                                    // check if timeFolder is in fact a folder
                                    if (Files.isDirectory(timeFolder)) {
                                        final Path timeFilename = timeFolder.getFileName();
                                        if (null == timeFilename) {
                                            continue;
                                        }

                                        try {
                                            final long time = Long.parseLong(timeFilename.toString());
                                            visitor.visit(nodeName, timeFolder, time);
                                        } catch (final NumberFormatException e) {
                                            LOGGER.debug("Skipping folder '{}', which is not a time folder.",
                                                    timeFolder, e);
                                        }
                                    } // time directory
                                }
                            } catch (final NotDirectoryException e) {
                                LOGGER.error("Node folder {} is not a directory, skipping", nodeFolder);
                                continue;
                            } catch (final IOException e) {
                                LOGGER.error("Error closing timeFolder directory stream", e);
                                continue;
                            }
                        } // NCP folder
                    } catch (final IOException e) {
                        LOGGER.error("Error finding first long resource report", e);
                    }
                } // node folder
            } // foreach node folder

        } catch (final NotDirectoryException e) {
            LOGGER.error("Node folder {} is not a directory", inputFolder);
        } catch (final IOException e) {
            LOGGER.error("Error closing inputFolder directory stream", e);
        }
    }

    /**
     * Compares the the contents of two arrays for a given index range.
     * 
     * @param a
     *            the first array
     * @param b
     *            the second array
     * @param start
     *            the initial index to compare (inclusive)
     * @param end
     *            the ending index to compare (exclusive)
     * @return true if the arrays have all equal elements in the given range and
     *         false otherwise
     */
    public static boolean compareArrays(Object[] a, Object[] b, int start, int end) {
        for (int n = start; n < end; n++) {
            if (!a[n].equals(b[n]))
                return false;
        }

        return true;
    }

    /**
     * Creates a CSV file, one for each region, that indicates which nodes are
     * leaders at each time.
     * 
     * @param <P>
     *            The type of plan.
     * @param outputFolder
     *            The folder to output files to.
     * @param fileSuffix
     *            The filename suffix, which is the same for each file that is
     *            output.
     * @param regionsPlans
     *            The plans from each node for each region at each time (region
     *            name -> time -> node name -> plan).
     */
    public static <P> void outputRegionLeadersToCSV(File outputFolder,
            String fileSuffix,
            Map<String, Map<Long, Map<String, P>>> regionsPlans) {
        regionsPlans.forEach((region, regionPlans) -> {
            File regionFile = new File(
                    outputFolder + File.separator + "region_" + region + fileSuffix + CSV_FILE_EXTENSION);
            LOGGER.debug("Start file for region {}: {}", region, regionFile);

            List<String> nodeNames = new ArrayList<>();
            nodeNames.addAll(ChartGenerationUtils.getSecondOrderKeys(regionPlans));
            Collections.sort(nodeNames);

            String[] headers = new String[nodeNames.size() + 1];
            headers[0] = CSV_TIME_COLUMN_LABEL;

            for (int n = 1; n < headers.length; n++)
                headers[n] = nodeNames.get(n - 1);

            CSVFormat format = CSVFormat.EXCEL.withHeader(headers);

            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(regionFile), Charset.defaultCharset()), format)) {
                List<Long> times = new ArrayList<>();
                times.addAll(regionPlans.keySet());
                Collections.sort(times);

                for (Long binTime : times) {
                    Map<String, P> nodeRegionPlans = regionPlans.get(binTime);
                    List<String> values = new ArrayList<>();
                    values.add(Long.toString(binTime));

                    for (int n = 1; n < headers.length; n++)
                        values.add(Integer.toString(nodeRegionPlans.get(headers[n]) != null ? 1 : 0));

                    try {
                        printer.printRecord(values);
                    } catch (IOException e) {
                        LOGGER.error("Error writing to CSV file: {}", e);
                    }
                }

                LOGGER.info("Finished writing file: {}", regionFile);
            } catch (IOException e) {
                LOGGER.error("Error writing to CSV file: {}", e);
            }
        });
    }
    
    /**
     * Converts a service into a service string for filenames.
     * 
     * @param service
     *          the service
     * @return the service string for filenames
     */    
    public static String serviceToFilenameString(ServiceIdentifier<?> service)
    {
        if (service instanceof ApplicationCoordinates)
            return ((ApplicationCoordinates) service).getArtifact();
        else if (service instanceof StringServiceIdentifier)
            return ((StringServiceIdentifier) service).getName();
        else return serviceToFilenameString(service.toString());
    }
    
    /**
     * Converts full service name string into a service string for filenames.
     * 
     * @param service
     *          the service string
     * @return the service string for filenames
     */
    public static String serviceToFilenameString(String service)
    {
        if (service.matches("AppCoordinates \\{.*,.*,.*\\}"))
        {
            String list = service.replaceFirst("\\AAppCoordinates \\{", "").replaceFirst("\\}\\z", "");
            String[] listParts = list.split(", ");
            
            String artifact = listParts[1];
            String version = listParts[2];
            
            return (artifact + "_" + version);
        }
        
        return service;
    }
    
    /**
     * Reads the service configurations file for a scenario to find the services.
     * 
     * @param scenarioFolder
     *          the folder containing the scenario configuration
     * @return the set of services in the scenario
     */
    public static Set<ServiceIdentifier<?>> getScenarioServices(File scenarioFolder)
    {
        Set<ServiceIdentifier<?>> services = new HashSet<>();
        Path serviceConfigurationsPath = scenarioFolder.toPath().resolve("service-configurations.json");
        
        ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();
        
        try (BufferedReader reader = Files.newBufferedReader(serviceConfigurationsPath)) {
            final ServiceConfiguration[] serviceConfigurations = mapper.readValue(reader, ServiceConfiguration[].class);
            
            for (ServiceConfiguration sc : serviceConfigurations)
            {
                services.add(sc.getService());
            }
            
            return services;
        } catch (IOException e)
        {
            LOGGER.error("Error reading service configurations for scenario.", e);
        }
        
        return null;
    }
}
