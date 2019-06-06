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

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.ServiceConfiguration;
import com.bbn.protelis.networkresourcemanagement.NetworkServerProperties;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

/**
 * Groups DNS entries into separate CSV files according to (source region, destination region) pairs and (source region, service, destination) triples.
 * 
 * @author awald
 *
 */
public class DNSAnalysisTableGenerator
{
    private static final Logger LOGGER = LogManager.getLogger(DNSAnalysisTableGenerator.class);
    
    private static final String OUTPUT_FILE_PREFIX = "dns_";
    
    private static final String CSV_HEADER_TIMESTAMP = "timestamp";
    private static final String CSV_HEADER_CLIENT_ADDRESS = "clientAddress";
    private static final String CSV_HEADER_NAME_TO_RESOLVE = " name_to_resolve";
    private static final String CSV_HEADER_RESOLVED_NAME = "  resolved_name";

    private static final String[] CSV_HEADER = {CSV_HEADER_TIMESTAMP, CSV_HEADER_CLIENT_ADDRESS, CSV_HEADER_NAME_TO_RESOLVE, CSV_HEADER_RESOLVED_NAME};
    
    private static final String CSV_FILE_EXTENSION = ".csv";
    
    private Map<String, String> nodeToRegionMap = new HashMap<>();
    private Map<String, String> ipAddressToNodeMap = new HashMap<>();
    private Map<String, String> domainToServiceNameMap = new HashMap<>();

    
    private Set<String> sourceRegions = new HashSet<>();
    private Set<String> services = new HashSet<>();
    private Set<String> destinationRegions = new HashSet<>();
    
    /**
     * Performs the routine that groups DNS entries into files according to (source region, destination region) pairs and (source region, service, destination) triples.
     * 
     * @param scenarioFolder
     *              the folder containing the scenario configuration
     * @param inputFolder
     *              the folder containing the simulation results
     * @param outputFolder
     *              the folder to output the tables to
     */
    public void processDNSCSVFiles(File scenarioFolder, File inputFolder, File outputFolder)
    {
        LOGGER.info("Scenario folder: '{}'", scenarioFolder);
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
        
        processScenarioConfiguration(scenarioFolder.getName(), scenarioFolder.toPath());
        final Map<ImmutableList<String>, List<CSVRecord>> dnsEntries = new HashMap<>();
        
        final File[] files = inputFolder.listFiles(dnsCSVFileFilter);
        
        if(null == files) {
            LOGGER.error("{} is an invalid directory, cannot process", inputFolder);
            return;
        }
        
        
        
        for (File dnsCSVFile : files)
        {
            LOGGER.info("Reading file: {}", dnsCSVFile);
            readDNSFile(dnsCSVFile, dnsEntries);
        }
        
        // read files for hifi testbed output
        File[] nodeFolders = inputFolder.listFiles();
        
        if (nodeFolders != null)
        {
            for (File nodeFolder : nodeFolders)
            {
                if (nodeFolder.isDirectory())
                {
                    File dnsFolder = new File(nodeFolder.getAbsolutePath() + File.separator + "dns");
                    
                    if (dnsFolder.exists() && dnsFolder.isDirectory())   
                    {
                        File[] dnsCSVFiles = dnsFolder.listFiles(hifiDnsCSVFileFilter);
                        
                        if (dnsCSVFiles != null)
                        {
                            for (File dnsCSVFile : dnsCSVFiles)
                            {
                                LOGGER.info("Reading file: {}", dnsCSVFile);
                                readDNSFile(dnsCSVFile, dnsEntries);
                            }
                        }
                    }
                }
            }
        }
        
        outputDNSLinesToCSV(outputFolder, OUTPUT_FILE_PREFIX, dnsEntries);
    }
    
    private void readDNSFile(File dnsCSVFile, Map<ImmutableList<String>, List<CSVRecord>> dnsEntries)
    {
        try (CSVParser parser = new CSVParser(new InputStreamReader(new FileInputStream(dnsCSVFile), Charset.defaultCharset()), CSVFormat.EXCEL.withHeader(CSV_HEADER).withSkipHeaderRecord()))
        {
            parser.forEach((record) ->
            {
                processDNSLine(record, dnsEntries);
            });
        } catch (FileNotFoundException e)
        {
            LOGGER.error("{}", e);
        } catch (IOException e)
        {
            LOGGER.error("{}", e);
        }
    }
    
    
    private String mapServiceDomainNameToServiceName(String addressToResolve)
    {
        String domainName = addressToResolve.replaceFirst("(\\..+?)?\\.map\\.dcomp\\z", ".map.dcomp");
        
        if (domainToServiceNameMap.containsKey(domainName))
        {
            return domainToServiceNameMap.get(domainName);
        }
        else
        {
            String defaultServiceName = domainName.replaceFirst("\\.map\\.dcomp\\z", "");
            LOGGER.warn("Service address '{}' could not be mapped to service name using service configurations, defaulting to '{}'", addressToResolve, defaultServiceName);
            return defaultServiceName;
        }
    }
    
    private String mapAddressToRegion(String address)
    {      
        // if address refers to a service region, parse the region name from the address
        if (address.matches("\\A.*?\\..+\\.map\\.dcomp"))
        {
            return address.replaceFirst("\\A.*?\\.", "").replaceFirst("\\.map\\.dcomp\\z", "");
        }
        
        // if address refers to a single node or container
        String nodeRegion = mapNodeAddressToRegion(address);
        
        if (nodeRegion != null)
        {
            return nodeRegion;
        }
        
        LOGGER.warn("The address '{}' could not be mapped to a region.", address);
        return "";
    }
    
    private String mapNodeAddressToRegion(String nodeAddress)
    {        
        // remove container suffix if it is present
        nodeAddress = nodeAddress.replaceFirst("\\.map\\.dcomp\\z", "").replaceFirst("_c.+\\z", "").toLowerCase();
        return nodeToRegionMap.get(ipAddressToNodeMap.getOrDefault(nodeAddress, nodeAddress).toLowerCase());
    }
    
    private void processScenarioConfiguration(String scenarioName, Path scenarioFolder)
    {
        LOGGER.info("Process scenario configuration for '{}' in folder '{}'.", scenarioName, scenarioFolder);
        
        nodeToRegionMap.clear();
        
        Topology topology;
        try
        {
            topology = NS2Parser.parse(scenarioName, scenarioFolder);
            
            topology.getNodes().forEach((name, node) ->
            {
                String regionName = NetworkServerProperties.parseRegionName(node.getExtraData());
                nodeToRegionMap.put(node.getName().toLowerCase(), regionName);
                
                node.getAllIpAddresses().forEach((link, ipAddress) ->
                {
                    if (ipAddress != null) {
                        ipAddressToNodeMap.put(ipAddress.getHostAddress(), node.getName());
                    }
                });
            });
        } catch (IOException e)
        {
            LOGGER.error("Unable to parse ns2 file: {}", e);
            e.printStackTrace();
        }
        
        LOGGER.debug("Node to region map: {}", nodeToRegionMap);
        LOGGER.debug("IP address to node map: {}", ipAddressToNodeMap);
        
        
        
        Path serviceConfigurationsPath = scenarioFolder.resolve("service-configurations.json");
        
        try
        {
            ImmutableCollection<ServiceConfiguration> serviceConfigurations = ServiceConfiguration.parseServiceConfigurations(serviceConfigurationsPath).values();
            
            for (ServiceConfiguration serviceConfiguration : serviceConfigurations)
            {
                String serviceArtifactName = serviceConfiguration.getService().getArtifact();
                String serviceDomainName = serviceConfiguration.getHostname();
                
                domainToServiceNameMap.put(serviceDomainName, serviceArtifactName);
            }
        } catch (IOException e)
        {
            LOGGER.error("Failed to read service configurations file: {}", serviceConfigurationsPath, e);
        }
        
        LOGGER.debug("Service domain name to service name map: {}", domainToServiceNameMap);
    }
    
    
    private void processDNSLine(CSVRecord dnsLine, Map<ImmutableList<String>, List<CSVRecord>> dnsEntries)
    {
        //Long timestamp = Long.parseLong(dnsLine.get(CSV_HEADER_TIMESTAMP));
        String clientAddress = dnsLine.get(CSV_HEADER_CLIENT_ADDRESS);              // source node
        String nameToResolve = dnsLine.get(CSV_HEADER_NAME_TO_RESOLVE);             // service
        String destinationAddress = dnsLine.get(CSV_HEADER_RESOLVED_NAME);

        String sourceRegion = mapAddressToRegion(clientAddress);
        String serviceName = mapServiceDomainNameToServiceName(nameToResolve);
        String destinationRegion = mapAddressToRegion(destinationAddress);
        
        
        LOGGER.debug("Mapped DNS entry [{}, {}, {}] to [{}, {}, {}]", clientAddress, nameToResolve, destinationAddress, sourceRegion, serviceName, destinationRegion);
        
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
    }
    
    
    private void outputDNSLinesToCSV(File outputFolder, String filenamePrefix, Map<ImmutableList<String>, List<CSVRecord>> dnsEntries)
    {
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
        
        for (int k = 0; k < keyValues.size(); k++)
        {
            if (k > 0)
                b.append("--");
            
            b.append(keyValues.get(k));
        }
        
        b.append(CSV_FILE_EXTENSION);
        
        final File file = new File(outputFolder + File.separator + b.toString());
        outputDNSLinesToCSV(file, entries);
    }
    
    private void outputDNSLinesToCSV(File outputFile, List<CSVRecord> dnsEntries)
    {
        dnsEntries.sort(new Comparator<CSVRecord>()
        {
            @Override
            public int compare(CSVRecord o1, CSVRecord o2)
            {
                long t1 = Long.parseLong(o1.get(0));
                long t2 = Long.parseLong(o2.get(0));
                
                if (t1 < t2)
                    return -1;
                else if (t1 > t2)
                    return 1;
                else
                    return 0;
            }
        });
        

        CSVFormat format = CSVFormat.EXCEL.withHeader(CSV_HEADER);
        
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(new FileOutputStream(outputFile), Charset.defaultCharset()), format))
        {
            for (CSVRecord dnsEntry : dnsEntries)
                printer.printRecord(dnsEntry);
        } catch (IOException e)
        {
            LOGGER.error("Error writing to CSV: {}", e);
            e.printStackTrace();
        }
    }
    
    private FileFilter hifiDnsCSVFileFilter = new FileFilter()
    {
        @Override
        public boolean accept(File file)
        {
            if (file.getName().matches(".*" + Pattern.quote(CSV_FILE_EXTENSION)))
                return true;
            
            return false;
        }
    };
    
    private FileFilter dnsCSVFileFilter = new FileFilter()
    {
        @Override
        public boolean accept(File file)
        {
            if (file.getName().matches("dns-.*" + Pattern.quote(CSV_FILE_EXTENSION)))
                return true;
            
            return false;
        }
    };
}
