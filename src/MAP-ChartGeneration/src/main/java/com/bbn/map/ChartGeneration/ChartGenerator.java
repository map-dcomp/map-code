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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.utils.MapLoggingConfigurationFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Main class for chart table generation. The class takes in a scenario configuration or output data from the scenario simulation as input.
 * The class then aggregates the data and outputs tables that will be used for charts that represent aspects
 * of the scenario configuration or its simulation results.
 * 
 * @author awald
 *
 */
public final class ChartGenerator
{
    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapLoggingConfigurationFactory.class.getName());
    }

    private static final Logger LOGGER = LogManager.getLogger(ChartGenerator.class);
    
    private static final long DEFAULT_DATA_SAMPLE_INTERVAL = 10000;
    

    private static final String CHART_TYPE_ALL = "all";
    private static final int CHART_TYPE_ALL_NUM_ARGS = 6;
    
    private static final int CHART_TYPE_PARAMETER_LOAD_NUM_ARGS = 5;
    private static final String CHART_TYPE_PARAMETER_LOAD = "load";
    private static final String CHART_TYPE_PARAMETER_LOAD_0 = "load_0";
    private static final String CHART_TYPE_PARAMETER_CLIENT_DEMAND = "client_demand";
    private static final String CHART_TYPE_PARAMETER_CLIENT_DEMAND_0 = "client_demand_0";
    private static final String CHART_TYPE_PARAMETER_CLIENT_REQUESTS_RESULTS = "requests_results";
    private static final String CHART_TYPE_PARAMETER_CLIENT_REQUESTS_TO_REGIONS = "requests_to_regions";
    private static final String CHART_TYPE_PARAMETER_DNS_ANALYSIS = "dns";
    private static final String CHART_TYPE_PARAMETER_DNS_RECORD_COUNT = "dns_record_count";
    private static final String CHART_TYPE_PARAMETER_DCOP_PLAN_UPDATES = "dcop_plan_updates";
    private static final String CHART_TYPE_PARAMETER_RLG_PLAN_UPDATES = "rlg_plan_updates";
    
    private static final String[][] CHART_PARAMETERS = 
    {
            {CHART_TYPE_ALL, "[scenario configuration folder]", "[demand scenario configuration folder]", "[input folder]", "[output folder]", "[data sample interval]"},
            {CHART_TYPE_PARAMETER_LOAD, "[scenario configuration folder]", "[input folder]", "[output folder]", "[data sample interval]"},
            {CHART_TYPE_PARAMETER_LOAD_0, "[scenario configuration folder]", "[input folder]", "[output folder]", "[data sample interval]"},
            {CHART_TYPE_PARAMETER_CLIENT_DEMAND, "[demand scenario configuration folder]", "[output folder]", "[data sample interval]"},
            {CHART_TYPE_PARAMETER_CLIENT_DEMAND_0, "[demand scenario configuration folder]", "[output folder]", "[data sample interval]"},
            {CHART_TYPE_PARAMETER_CLIENT_REQUESTS_RESULTS, "[input folder]", "[output folder]"},
            {CHART_TYPE_PARAMETER_CLIENT_REQUESTS_TO_REGIONS, "[scenario configuration folder]", "[input folder]", "[output folder]"},
            {CHART_TYPE_PARAMETER_DNS_ANALYSIS, "[scenario configuration folder]", "[input folder]", "[output folder]"},
            {CHART_TYPE_PARAMETER_DNS_RECORD_COUNT, "[input folder]", "[output folder]"},
            {CHART_TYPE_PARAMETER_DCOP_PLAN_UPDATES, "[input folder]", "[output folder]", "[data sample interval]"},
            {CHART_TYPE_PARAMETER_RLG_PLAN_UPDATES, "[input folder]", "[output folder]", "[data sample interval]"}
    };
    
    /**
     * The name of the simulation folder within the scenario folder.
     */
    public static final String SIMULATION_FILES_FOLDER_NAME = "simulation";
    
    
    private ChartGenerator()
    {
    }   
    
    
    /**
     * Selects a chart table generation routine to run according to the given parameters.
     * 
     * @param args
     *          [chart type] ...
     */
    @SuppressFBWarnings(value = "SF_SWITCH_FALLTHROUGH", justification = "Intentionally allowing switch fall through when checking parameters")
    public static void main(String[] args)
    {
        LOGGER.info("Built from git revision {}", getGitVersionInformation());

        boolean startAtZero = true;
        File scenarioFolder = null;
        File demandScenarioFolder = null;
        File inputFolder = null;
        File outputFolder = null;
        long dataSampleInterval = DEFAULT_DATA_SAMPLE_INTERVAL;
        
        
        if (args.length >= 1)
        {
            int p = 0;
            String chartType = args[p++];
            
            
            switch(chartType)
            {
                case CHART_TYPE_ALL:
                    if (args.length == CHART_TYPE_ALL_NUM_ARGS)
                    {
                        scenarioFolder = new File(args[p++]);
                        demandScenarioFolder = new File(args[p++]);
                        inputFolder = new File(args[p++]);
                        outputFolder = new File(args[p++]);
                        dataSampleInterval = Long.parseLong(args[p++]);
                        
                        LoadChartGenerator cg = new LoadChartGenerator();
                        cg.processScenarioData(scenarioFolder, inputFolder, new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_LOAD), startAtZero, dataSampleInterval);
                        
                        if (!demandScenarioFolder.toString().isEmpty())
                        {
                            ClientDemandTableGenerator cg2 = new ClientDemandTableGenerator();
                            cg2.processClientRequestDemandFiles(demandScenarioFolder, new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_CLIENT_DEMAND), startAtZero, dataSampleInterval);
                        }
                        
                        ClientRequestsTableGenerator cg3 = new ClientRequestsTableGenerator();
                        cg3.processScenarioData(inputFolder, new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_CLIENT_REQUESTS_RESULTS), dataSampleInterval);
                        
                        RequestsToRegionsTableGenerator cg4 = new RequestsToRegionsTableGenerator();
                        cg4.processScenarioData(scenarioFolder, inputFolder, new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_CLIENT_REQUESTS_TO_REGIONS));
                        
                        DNSAnalysisTableGenerator cg5 = new DNSAnalysisTableGenerator();
                        cg5.processDNSCSVFiles(scenarioFolder, inputFolder, new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_DNS_ANALYSIS));
                        
                        DCOPPlanUpdateTableGenerator cg6 = new DCOPPlanUpdateTableGenerator();
                        cg6.processScenarioData(inputFolder, new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_DCOP_PLAN_UPDATES), dataSampleInterval);                      
                        
                        RLGPlanUpdateTableGenerator cg7 = new RLGPlanUpdateTableGenerator();
                        cg7.processScenarioData(inputFolder, new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_RLG_PLAN_UPDATES), dataSampleInterval);  
                        
                        DNSRecordCountTableGenerator cg8 = new DNSRecordCountTableGenerator();
                        cg8.processDNSRecordFiles(inputFolder, new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_DNS_RECORD_COUNT));
                        
                        System.exit(0);
                    }
                    break;
            
                case CHART_TYPE_PARAMETER_LOAD:
                    startAtZero = false;
                case CHART_TYPE_PARAMETER_LOAD_0:
                    if (args.length == CHART_TYPE_PARAMETER_LOAD_NUM_ARGS)
                    {
                        scenarioFolder = new File(args[p++]);
                        inputFolder = new File(args[p++]);
                        outputFolder = new File(args[p++]);
                        dataSampleInterval = Long.parseLong(args[p++]);
                        
                        LoadChartGenerator cg = new LoadChartGenerator();
                        cg.processScenarioData(scenarioFolder, inputFolder, outputFolder, startAtZero, dataSampleInterval);                     
                        System.exit(0);
                    }
                    break;
                    

                case CHART_TYPE_PARAMETER_CLIENT_DEMAND:
                    startAtZero = false;
                case CHART_TYPE_PARAMETER_CLIENT_DEMAND_0:
                    if (args.length == 3 || args.length == 4)
                    {
                        demandScenarioFolder = new File(args[p++]);
                        outputFolder = new File(args[p++]);
                       
                        if (p < args.length)
                            dataSampleInterval = Long.parseLong(args[p++]);
                        else
                            dataSampleInterval = -1;
                        
                        ClientDemandTableGenerator cg = new ClientDemandTableGenerator();
                        cg.processClientRequestDemandFiles(demandScenarioFolder, outputFolder, startAtZero, dataSampleInterval);
                        System.exit(0);
                    }
                    break;
                    
                case CHART_TYPE_PARAMETER_CLIENT_REQUESTS_RESULTS:
                    if (args.length == 3)
                    {
                        inputFolder = new File(args[p++]);
                        outputFolder = new File(args[p++]);
                        
                        ClientRequestsTableGenerator cg = new ClientRequestsTableGenerator();
                        cg.processScenarioData(inputFolder, outputFolder, dataSampleInterval);
                        System.exit(0);
                    }
                    break;
                    
                case CHART_TYPE_PARAMETER_CLIENT_REQUESTS_TO_REGIONS:
                    if (args.length == 4)
                    {
                        scenarioFolder = new File(args[p++]);
                        inputFolder = new File(args[p++]);
                        outputFolder = new File(args[p++]);
                        
                        RequestsToRegionsTableGenerator cg = new RequestsToRegionsTableGenerator();
                        cg.processScenarioData(scenarioFolder, inputFolder, outputFolder);
                        System.exit(0);
                    }
                    break;
                    
                case CHART_TYPE_PARAMETER_DNS_ANALYSIS:
                    if (args.length == 4)
                    {
                        scenarioFolder = new File(args[p++]);
                        inputFolder = new File(args[p++]);
                        outputFolder = new File(args[p++]);
                        
                        DNSAnalysisTableGenerator cg = new DNSAnalysisTableGenerator();
                        cg.processDNSCSVFiles(scenarioFolder, inputFolder, outputFolder);
                        System.exit(0);
                    }
                    break;
                    
                case CHART_TYPE_PARAMETER_DNS_RECORD_COUNT:
                    if (args.length == 3)
                    {
                        inputFolder = new File(args[p++]);
                        outputFolder = new File(args[p++]);
                        
                        DNSRecordCountTableGenerator cg = new DNSRecordCountTableGenerator();
                        cg.processDNSRecordFiles(inputFolder, outputFolder);
                        System.exit(0);
                    }
                    break;
                    
                case CHART_TYPE_PARAMETER_DCOP_PLAN_UPDATES:
                    if (args.length == 4)
                    {
                        inputFolder = new File(args[p++]);
                        outputFolder = new File(args[p++]);
                        dataSampleInterval = Long.parseLong(args[p++]);
                        
                        DCOPPlanUpdateTableGenerator cg = new DCOPPlanUpdateTableGenerator();
                        cg.processScenarioData(inputFolder, outputFolder, dataSampleInterval);
                        System.exit(0);
                    }                   
                    break;
 
                case CHART_TYPE_PARAMETER_RLG_PLAN_UPDATES:
                    if (args.length == 4)
                    {
                        inputFolder = new File(args[p++]);
                        outputFolder = new File(args[p++]);
                        dataSampleInterval = Long.parseLong(args[p++]);
                        
                        RLGPlanUpdateTableGenerator cg = new RLGPlanUpdateTableGenerator();
                        cg.processScenarioData(inputFolder, outputFolder, dataSampleInterval);
                        System.exit(0);
                    }                   
                    break;
                    
                default:
                    break;
            }
        }
        
        outputExpectedArguments();
    }
    
    private static void outputExpectedArguments()
    {
        StringBuilder sb = new StringBuilder();
        
        for (int a = 0; a < CHART_PARAMETERS.length; a++)
        {
            if (a > 0)
                sb.append("\n");
            
            sb.append("[" + a + "]   ");
            
            for (int b = 0; b < CHART_PARAMETERS[a].length; b++)
            {
                if (b > 0)
                    sb.append(" ");
                
                sb.append(CHART_PARAMETERS[a][b]);
            }
        }
        
        LOGGER.fatal("Expected arguments:\n{}", sb.toString());
    }
    

    /**
     * Get the git version information for the build.
     * 
     * @return the version or a string stating what the problem was
     */
    public static String getGitVersionInformation() {
        final URL url = ChartGenerator.class.getResource("git.properties");
        if (null == url) {
            return "UNKNOWN";
        }
        try (InputStream is = url.openStream()) {
            final Properties props = new Properties();
            props.load(is);
            return props.getProperty("git.commit.id", "MISSING-PROPERTY");
        } catch (final IOException e) {
            LOGGER.error("Unable to read version properties", e);
            return "ERROR-READING-VERSION";
        }
    }
}
