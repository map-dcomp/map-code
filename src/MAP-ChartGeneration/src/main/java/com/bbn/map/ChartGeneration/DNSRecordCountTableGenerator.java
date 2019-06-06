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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
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

import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.Simulation.DnsState;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;



/**
 * Class for counting DNS records to nodes and delegate regions at points in time in the MAP system.
 * 
 * @author awald
 *
 */
public class DNSRecordCountTableGenerator
{
    private static final Logger LOGGER = LogManager.getLogger(DNSRecordCountTableGenerator.class);
    
    private final ObjectMapper mapper;
    
    
    /**
     * Constructs an instance with the modules necessary for JSON deserialization added to the ObjectMapper.
     */
    public DNSRecordCountTableGenerator()
    {
        mapper = JsonUtils.getStandardMapObjectMapper();
    }
    
    
    /**
     * Processes the DNS files in the results of an experiment by counting delegate and node records.
     * 
     * @param inputFolder
     *              the folder containing the results of the scenario simulation
     * @param outputFolder
     *              the folder to output the chart tables to
     */
    public void processDNSRecordFiles(File inputFolder, File outputFolder)
    {
        LOGGER.info("Data input folder: '{}'", inputFolder);
        
        if (!inputFolder.exists())
        {
            LOGGER.error("Input folder does not exist: {}", inputFolder);
            return;
        }
        
        
        Map<ServiceIdentifier<?>, Map<String, Map<Long, Map<RegionIdentifier, Integer>>>> delegateRecordsCounts = new HashMap<>();
        Map<ServiceIdentifier<?>, Map<String, Map<Long, Map<NodeIdentifier, Integer>>>> nameRecordsCounts = new HashMap<>();
        Set<NodeIdentifier> nodes = new HashSet<>();
        Set<RegionIdentifier> regions = new HashSet<>();
        
        
        File simulationFolder = new File(inputFolder + File.separator + ChartGenerationUtils.SCENARIO_SIMULATION_FOLDER_NAME);
        File[] timeFolders = simulationFolder.listFiles();
        
        if (timeFolders != null)
        {
            for (File timeFolder : timeFolders)
            {
                File[] dnsFiles = timeFolder.listFiles(dnsRecordstFileFilter);
                
                if (dnsFiles != null)
                {
                    long time = Long.parseLong(timeFolder.getName());
                    
                    for (File dnsFile : dnsFiles)
                    {
                        String region = dnsFile.getName().split("-|\\.")[1];
                        
                        try
                        {
                            DnsState dnsState = mapper.readValue(dnsFile, DnsState.class);                 
                            
                            processDnsState(region, time, dnsState, delegateRecordsCounts, nameRecordsCounts, regions, nodes);
                            
                            LOGGER.debug("Delegate records counts: {}", delegateRecordsCounts);
                            LOGGER.debug("Delegate records nodes: {}", nameRecordsCounts);
                            
                            outputDNSRecordCountsToCSV(outputFolder, delegateRecordsCounts, nameRecordsCounts, regions, nodes);
                        }  catch (IOException e)
                        {
                            LOGGER.error("Error parsing DNS JSON file {}:\n{}", dnsFile, e);
                        }
                    }
                }
            }
        }
        else
        {
            LOGGER.error("Input simulation folder does not exist or is not a directory: {}", simulationFolder);            
        }
    }
    
    
    
    private void processDnsState(String region, long time, Simulation.DnsState dnsState,
            Map<ServiceIdentifier<?>, Map<String, Map<Long, Map<RegionIdentifier, Integer>>>> delegateRecordsCounts,
            Map<ServiceIdentifier<?>, Map<String, Map<Long, Map<NodeIdentifier, Integer>>>> nameRecordsCounts,
            Set<RegionIdentifier> regions, Set<NodeIdentifier> nodes)
    {
        for (DnsRecord record : dnsState.getEntries())
        {
            processDnsRecord(region, time, record, delegateRecordsCounts, nameRecordsCounts, regions, nodes);
        }
    }
    
    
    private void processDnsRecord(String region, long time, DnsRecord record,
            Map<ServiceIdentifier<?>, Map<String, Map<Long, Map<RegionIdentifier, Integer>>>> delegateRecordsCounts,
            Map<ServiceIdentifier<?>, Map<String, Map<Long, Map<NodeIdentifier, Integer>>>> nameRecordsCounts,
            Set<RegionIdentifier> regions, Set<NodeIdentifier> nodes)
    {
        ServiceIdentifier<?> service = record.getService();
        
        regions.add(new StringRegionIdentifier(region));
        
        LOGGER.debug("Process record at time {} for region '{}' and service '{}'.", time, region, service);
        
        // add keys to delegate record map
        if (!delegateRecordsCounts.containsKey(service)) {
            delegateRecordsCounts.put(service, new HashMap<>());
        }
        
        if (!delegateRecordsCounts.get(service).containsKey(region)) {
            delegateRecordsCounts.get(service).put(region, new HashMap<>());
        }
        
        if (!delegateRecordsCounts.get(service).get(region).containsKey(time)) {
            delegateRecordsCounts.get(service).get(region).put(time, new HashMap<>());
        }
        
        // add keys to name record map
        if (!nameRecordsCounts.containsKey(service)) {
            nameRecordsCounts.put(service, new HashMap<>());
        }
        
        if (!nameRecordsCounts.get(service).containsKey(region)) {
            nameRecordsCounts.get(service).put(region, new HashMap<>());
        }
        
        if (!nameRecordsCounts.get(service).get(region).containsKey(time)) {
            nameRecordsCounts.get(service).get(region).put(time, new HashMap<>());
        }
        
        
        // increment the count either for a delegate record or for a name record
        if (record instanceof DelegateRecord)
        {
            DelegateRecord delegateRecord = (DelegateRecord) record;
            
            RegionIdentifier delegateRegion = delegateRecord.getDelegateRegion();
            delegateRecordsCounts.get(service).get(region).get(time).merge(delegateRegion, 1, (a, b) -> a + b);
            
            regions.add(delegateRegion);
        } else if (record instanceof NameRecord)
        {
            NameRecord nameRecord = (NameRecord) record;
            
            NodeIdentifier nodeIdentifier = nameRecord.getNode();
            nameRecordsCounts.get(service).get(region).get(time).merge(nodeIdentifier, 1, (a, b) -> a + b);
            
            nodes.add(nodeIdentifier);
        }
    }
    
    private void outputDNSRecordCountsToCSV(File outputFolder,
            Map<ServiceIdentifier<?>, Map<String, Map<Long, Map<RegionIdentifier, Integer>>>> delegateRecordsCounts,
            Map<ServiceIdentifier<?>, Map<String, Map<Long, Map<NodeIdentifier, Integer>>>> nameRecordsCounts,
            Set<RegionIdentifier> regions, Set<NodeIdentifier> nodes)
    {
        if (!outputFolder.exists()) {
            if (!outputFolder.mkdirs()) {
                throw new RuntimeException("Unable to create directory " + outputFolder);
            }
        }
        
        
        List<RegionIdentifier> regionsList = new ArrayList<>();
        regionsList.addAll(regions);
        Collections.sort(regionsList, new Comparator<RegionIdentifier>()
        {
            @Override
            public int compare(RegionIdentifier o1, RegionIdentifier o2)
            {
                return o1.toString().compareTo(o2.toString());
            }
        });

        List<NodeIdentifier> nodesList = new ArrayList<>();
        nodesList.addAll(nodes);
        Collections.sort(nodesList, new Comparator<NodeIdentifier>()
        {
            @Override
            public int compare(NodeIdentifier o1, NodeIdentifier o2)
            {
                return o1.toString().compareTo(o2.toString());
            }
        });
        
        
        String[] header = new String[1 + regionsList.size() + nodesList.size()];
        header[0] = ChartGenerationUtils.CSV_TIME_COLUMN_LABEL;
        
        for (int n = 0; n < regionsList.size(); n++)
            header[1 + n] = regionsList.get(n).toString();
        
        for (int n = 0; n < nodesList.size(); n++)
            header[1 + regionsList.size() + n] = nodesList.get(n).toString();
        
        CSVFormat format = CSVFormat.EXCEL.withHeader(header);
        
        
        for (ServiceIdentifier<?> service : Sets.union(delegateRecordsCounts.keySet(), nameRecordsCounts.keySet()))  
        {
            for (String region : Sets.union(delegateRecordsCounts.get(service).keySet(), nameRecordsCounts.get(service).keySet()))
            {
                List<Long> times = new ArrayList<>();
                times.addAll(Sets.union(delegateRecordsCounts.get(service).get(region).keySet(), nameRecordsCounts.get(service).get(region).keySet()));
                Collections.sort(times);
                
                File outputFile = new File(outputFolder + File.separator + "DNS_" + region + "-" + ChartGenerationUtils.serviceToFilenameString(service) + ChartGenerationUtils.CSV_FILE_EXTENSION);
                
                try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(new FileOutputStream(outputFile), Charset.defaultCharset()), format))
                {
                    LOGGER.trace("Started DNS record count file: {}", outputFile);
                    
                    for (Long time : times)
                    {
                        List<Long> record = new ArrayList<>(header.length);
                        record.add(0, time);
                        
                        for (int n = 0; n < regionsList.size(); n++)
                        {
                            Integer count =  delegateRecordsCounts.get(service).get(region).get(time).get(regionsList.get(n));
                            record.add(1 + n, (count != null ? count : 0L));
                        }
                        
                        for (int n = 0; n < nodesList.size(); n++)
                        {
                            Integer count =  nameRecordsCounts.get(service).get(region).get(time).get(nodesList.get(n));
                            record.add(1 + regionsList.size() + n, (count != null ? count : 0L));
                        }
                        
                        printer.printRecord(record);
                    }                    
                } catch (IOException e)
                {
                    LOGGER.error("Error writing to CSV: {}", e);
                    e.printStackTrace();
                }
            }
        }
        
    }
    
    
    private final FileFilter dnsRecordstFileFilter = new FileFilter()
    {
        @Override
        public boolean accept(File pathname)
        {
            return pathname.getName().matches("dns-.*\\.json");
        }
    };
}
