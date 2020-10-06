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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.ChartGeneration.log_analysis.MapLogAnalyzer;
import com.bbn.map.utils.MapLoggingConfigurationFactory;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Main class for chart table generation. The class takes in a scenario
 * configuration or output data from the scenario simulation as input. The class
 * then aggregates the data and outputs tables that will be used for charts that
 * represent aspects of the scenario configuration or its simulation results.
 * 
 * @author awald
 *
 */
public final class ChartGenerator {
    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapLoggingConfigurationFactory.class.getName());
    }

    private static final Logger LOGGER = LogManager.getLogger(ChartGenerator.class);

    private static final String CHART_TYPE_ALL = "all";
    private static final int CHART_TYPE_ALL_NUM_ARGS = 6;

    private static final int CHART_TYPE_PARAMETER_LOAD_NUM_ARGS = 5;
    private static final int CHART_TYPE_PARAMETER_LOG_ANALYSIS_NUM_ARGS = 4;
    private static final int CHART_TYPE_PARAMETER_DNS_ANALYSIS_NUM_ARGS = 5;
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
    private static final String CHART_TYPE_LOG_ANALYSIS = "log_analysis";
    private static final String CHART_TYPE_CLIENT_LATENCY_DNS_RESOLUTIONS = "latency_dns";

    private static final String[][] CHART_PARAMETERS = {
            { CHART_TYPE_ALL, "[scenario configuration folder]", "[demand scenario configuration folder]",
                    "[input folder]", "[output folder]", "[data sample interval]" },
            { CHART_TYPE_PARAMETER_LOAD, "[scenario configuration folder]", "[input folder]", "[output folder]",
                    "[data sample interval]" },
            { CHART_TYPE_PARAMETER_LOAD_0, "[scenario configuration folder]", "[input folder]", "[output folder]",
                    "[data sample interval]" },
            { CHART_TYPE_PARAMETER_CLIENT_DEMAND, "[demand scenario configuration folder]", "[output folder]",
                    "[data sample interval]" },
            { CHART_TYPE_PARAMETER_CLIENT_DEMAND_0, "[demand scenario configuration folder]", "[output folder]",
                    "[data sample interval]" },
            { CHART_TYPE_PARAMETER_CLIENT_REQUESTS_RESULTS, "[input folder]", "[output folder]" },
            { CHART_TYPE_PARAMETER_CLIENT_REQUESTS_TO_REGIONS, "[scenario configuration folder]", "[input folder]",
                    "[output folder]" },
            { CHART_TYPE_PARAMETER_DNS_ANALYSIS, "[scenario configuration folder]", "[input folder]", "[output folder]", "[data sample interval]"},
            { CHART_TYPE_CLIENT_LATENCY_DNS_RESOLUTIONS, "[scenario configuration folder]", "[input folder]",
                    "[output folder]" },
            { CHART_TYPE_PARAMETER_DNS_RECORD_COUNT, "[input folder]", "[output folder]" },
            { CHART_TYPE_PARAMETER_DCOP_PLAN_UPDATES, "[input folder]", "[output folder]", "[data sample interval]" },
            { CHART_TYPE_PARAMETER_RLG_PLAN_UPDATES, "[input folder]", "[output folder]", "[data sample interval]" },
            { CHART_TYPE_LOG_ANALYSIS, "[matchers file]", "[input folder]", "[output folder]" } };

    /**
     * The name of the simulation folder within the scenario folder.
     */
    public static final String SIMULATION_FILES_FOLDER_NAME = "simulation";

    /**
     * The number of threads to use for the thread pool, which executes separate
     * modules in parallel.
     */
    public static final int N_WORKER_THREADS = Runtime.getRuntime().availableProcessors();

    private ChartGenerator() {
    }

    /**
     * Selects a chart table generation routine to run according to the given
     * parameters.
     * 
     * @param args [chart type] ...
     */
    @SuppressFBWarnings(value = "SF_SWITCH_FALLTHROUGH", justification = "Intentionally allowing switch fall through when checking parameters")
    public static void main(String[] args) {
        LOGGER.info("Built from git revision {}", getGitVersionInformation());

        boolean startAtZero = true;
        File scenarioFolder = null;
        File demandScenarioFolder = null;
        File inputFolder = null;
        File outputFolder = null;
        long dataSampleInterval = ChartGenerationUtils.DEFAULT_SAMPLE_INTERVAL;

        if (args.length >= 1) {
            int p = 0;
            String chartType = args[p++];

            switch (chartType) {
            case CHART_TYPE_ALL:
                if (args.length == CHART_TYPE_ALL_NUM_ARGS) {
                    scenarioFolder = new File(args[p++]);
                    demandScenarioFolder = new File(args[p++]);
                    inputFolder = new File(args[p++]);
                    outputFolder = new File(args[p++]);
                    dataSampleInterval = Long.parseLong(args[p++]);

                    LOGGER.info("Using thread pool size of {} threads.", N_WORKER_THREADS);
                    ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors
                            .newFixedThreadPool(N_WORKER_THREADS);
                    List<Future<?>> threadPoolSubmissions = new LinkedList<>();

                    Runnable loadChartGeneratorWorker = new ChartGenerationModuleWorker(scenarioFolder,
                            demandScenarioFolder, inputFolder, outputFolder, dataSampleInterval, startAtZero) {
                        @Override
                        public void run() {
                            final LoadChartGenerator cg = new LoadChartGenerator();
                            cg.processScenarioData(scenarioFolder, inputFolder,
                                    new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_LOAD), startAtZero,
                                    dataSampleInterval);

                            // get all loaded services for the scenario
                            final Set<ServiceIdentifier<?>> scenarioServices = cg.getLoadServices();

                            LOGGER.debug("scenarioServices: {}", scenarioServices);

                            if (!demandScenarioFolder.toString().isEmpty()) {
                                final ClientDemandTableGenerator cg2 = new ClientDemandTableGenerator();
                                cg2.setScenarioServices(scenarioServices);
                                cg2.processClientRequestDemandFiles(demandScenarioFolder,
                                        new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_CLIENT_DEMAND),
                                        startAtZero, dataSampleInterval);
                            }

                            final ClientRequestsTableGenerator cg3 = new ClientRequestsTableGenerator();
                            cg3.setScenarioServices(scenarioServices);
                            cg3.processScenarioData(inputFolder, new File(
                                    outputFolder + File.separator + CHART_TYPE_PARAMETER_CLIENT_REQUESTS_RESULTS),
                                    dataSampleInterval);
                        }
                    };
                    threadPoolSubmissions.add(threadPoolExecutor.submit(loadChartGeneratorWorker));

                    Runnable requestsToRegionsTableGeneratorWorker = new ChartGenerationModuleWorker(scenarioFolder,
                            demandScenarioFolder, inputFolder, outputFolder, dataSampleInterval, startAtZero) {
                        @Override
                        public void run() {
                            final RequestsToRegionsTableGenerator cg4 = new RequestsToRegionsTableGenerator();
                            cg4.processScenarioData(scenarioFolder, inputFolder, new File(
                                    outputFolder + File.separator + CHART_TYPE_PARAMETER_CLIENT_REQUESTS_TO_REGIONS));
                        }
                    };
                    threadPoolSubmissions.add(threadPoolExecutor.submit(requestsToRegionsTableGeneratorWorker));

                    Runnable dcopPlanUpdateTableGeneratorWorker = new ChartGenerationModuleWorker(scenarioFolder,
                            demandScenarioFolder, inputFolder, outputFolder, dataSampleInterval, startAtZero) {
                        @Override
                        public void run() {
                            final DCOPPlanUpdateTableGenerator cg6 = new DCOPPlanUpdateTableGenerator();
                            cg6.processScenarioData(inputFolder,
                                    new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_DCOP_PLAN_UPDATES),
                                    dataSampleInterval);

                        }
                    };
                    threadPoolSubmissions.add(threadPoolExecutor.submit(dcopPlanUpdateTableGeneratorWorker));

                    Runnable dnsRecordCountTableGeneratorWorker = new ChartGenerationModuleWorker(scenarioFolder,
                            demandScenarioFolder, inputFolder, outputFolder, dataSampleInterval, startAtZero) {
                        @Override
                        public void run() {
                            final DNSRecordCountTableGenerator cg8 = new DNSRecordCountTableGenerator();
                            cg8.processDNSRecordFiles(inputFolder,
                                    new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_DNS_RECORD_COUNT));
                        }
                    };
                    threadPoolSubmissions.add(threadPoolExecutor.submit(dnsRecordCountTableGeneratorWorker));

                    Runnable rlgPlanUpdateDnsAnalysisTableGeneratorWorker = new ChartGenerationModuleWorker(
                            scenarioFolder, demandScenarioFolder, inputFolder, outputFolder, dataSampleInterval,
                            startAtZero) {
                        @Override
                        public void run() {
                            final RLGPlanUpdateTableGenerator rlgPlanUpdateTG = new RLGPlanUpdateTableGenerator();
                            rlgPlanUpdateTG.processScenarioData(inputFolder,
                                    new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_RLG_PLAN_UPDATES),
                                    dataSampleInterval);

                            final DNSAnalysisTableGenerator cg5 = new DNSAnalysisTableGenerator();
                            cg5.setBinSize(dataSampleInterval);
                            cg5.setServiceRLGROverflowPlans(rlgPlanUpdateTG.getServiceRLGROverflowPlans());
                            cg5.processDNSCSVFiles(scenarioFolder, inputFolder, new File(outputFolder + File.separator + CHART_TYPE_PARAMETER_DNS_ANALYSIS));
                            cg5.processLatencyDNSResolutions(scenarioFolder, inputFolder, new File(
                                    outputFolder + File.separator + CHART_TYPE_CLIENT_LATENCY_DNS_RESOLUTIONS), false);
                        }
                    };
                    threadPoolSubmissions.add(threadPoolExecutor.submit(rlgPlanUpdateDnsAnalysisTableGeneratorWorker));

                    Runnable netGeneratorWorker = new ChartGenerationModuleWorker(scenarioFolder, demandScenarioFolder,
                            inputFolder, outputFolder, dataSampleInterval, startAtZero) {
                        @Override
                        public void run() {
                            final NetworkLoadTable netGenerator = new NetworkLoadTable();
                            netGenerator.processScenarioData(inputFolder.toPath(), outputFolder.toPath());
                        }
                    };
                    threadPoolSubmissions.add(threadPoolExecutor.submit(netGeneratorWorker));

                    for (Future<?> f : threadPoolSubmissions) {
                        try {
                            f.get();
                        } catch (InterruptedException e) {
                            LOGGER.error("Error waiting for computation {} to complete:", f, e);
                            System.exit(1);
                        } catch (ExecutionException e) {
                            LOGGER.error("Error with computation {}:", f, e);
                            System.exit(2);
                        }
                    }

                    System.exit(0);
                }
                break;

            case CHART_TYPE_PARAMETER_LOAD:
                startAtZero = false;
            case CHART_TYPE_PARAMETER_LOAD_0:
                if (args.length == CHART_TYPE_PARAMETER_LOAD_NUM_ARGS) {
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
                if (args.length == 3 || args.length == 4) {
                    demandScenarioFolder = new File(args[p++]);
                    outputFolder = new File(args[p++]);

                    if (p < args.length)
                        dataSampleInterval = Long.parseLong(args[p++]);
                    else
                        dataSampleInterval = -1;

                    ClientDemandTableGenerator cg = new ClientDemandTableGenerator();
                    cg.processClientRequestDemandFiles(demandScenarioFolder, outputFolder, startAtZero,
                            dataSampleInterval);
                    System.exit(0);
                }
                break;

            case CHART_TYPE_PARAMETER_CLIENT_REQUESTS_RESULTS:
                if (args.length == 3) {
                    inputFolder = new File(args[p++]);
                    outputFolder = new File(args[p++]);

                    ClientRequestsTableGenerator cg = new ClientRequestsTableGenerator();
                    cg.processScenarioData(inputFolder, outputFolder, dataSampleInterval);
                    System.exit(0);
                }
                break;

            case CHART_TYPE_PARAMETER_CLIENT_REQUESTS_TO_REGIONS:
                if (args.length == 4) {
                    scenarioFolder = new File(args[p++]);
                    inputFolder = new File(args[p++]);
                    outputFolder = new File(args[p++]);

                    RequestsToRegionsTableGenerator cg = new RequestsToRegionsTableGenerator();
                    cg.processScenarioData(scenarioFolder, inputFolder, outputFolder);
                    System.exit(0);
                }
                break;

            case CHART_TYPE_PARAMETER_DNS_ANALYSIS:
                if (args.length == CHART_TYPE_PARAMETER_DNS_ANALYSIS_NUM_ARGS) {
                    scenarioFolder = new File(args[p++]);
                    inputFolder = new File(args[p++]);
                    outputFolder = new File(args[p++]);
                    dataSampleInterval = Long.parseLong(args[p++]);

                    DNSAnalysisTableGenerator cg = new DNSAnalysisTableGenerator();
                    cg.setBinSize(dataSampleInterval);
                    cg.processDNSCSVFiles(scenarioFolder, inputFolder, outputFolder);
                    System.exit(0);
                }
                break;

            case CHART_TYPE_CLIENT_LATENCY_DNS_RESOLUTIONS:
                if (args.length == 4) {
                    scenarioFolder = new File(args[p++]);
                    inputFolder = new File(args[p++]);
                    outputFolder = new File(args[p++]);

                    DNSAnalysisTableGenerator dnsAnalyzer = new DNSAnalysisTableGenerator();
                    dnsAnalyzer.processLatencyDNSResolutions(scenarioFolder, inputFolder, outputFolder, true);

                    System.exit(0);
                }
                break;

            case CHART_TYPE_PARAMETER_DNS_RECORD_COUNT:
                if (args.length == 3) {
                    inputFolder = new File(args[p++]);
                    outputFolder = new File(args[p++]);

                    DNSRecordCountTableGenerator cg = new DNSRecordCountTableGenerator();
                    cg.processDNSRecordFiles(inputFolder, outputFolder);
                    System.exit(0);
                }
                break;

            case CHART_TYPE_PARAMETER_DCOP_PLAN_UPDATES:
                if (args.length == 4) {
                    inputFolder = new File(args[p++]);
                    outputFolder = new File(args[p++]);
                    dataSampleInterval = Long.parseLong(args[p++]);

                    DCOPPlanUpdateTableGenerator cg = new DCOPPlanUpdateTableGenerator();
                    cg.processScenarioData(inputFolder, outputFolder, dataSampleInterval);
                    System.exit(0);
                }
                break;

            case CHART_TYPE_PARAMETER_RLG_PLAN_UPDATES:
                if (args.length == 4) {
                    inputFolder = new File(args[p++]);
                    outputFolder = new File(args[p++]);
                    dataSampleInterval = Long.parseLong(args[p++]);

                    RLGPlanUpdateTableGenerator cg = new RLGPlanUpdateTableGenerator();
                    cg.processScenarioData(inputFolder, outputFolder, dataSampleInterval);
                    System.exit(0);
                }
                break;

            case CHART_TYPE_LOG_ANALYSIS:
                if (args.length == CHART_TYPE_PARAMETER_LOG_ANALYSIS_NUM_ARGS) {
                    File categoryMatcherFile = new File(args[p++]);
                    File logFileFolder = new File(args[p++]);
                    outputFolder = new File(args[p++]);
//                        dataSampleInterval = Long.parseLong(args[p++]);

                    MapLogAnalyzer mla = new MapLogAnalyzer();

                    boolean error = false;

                    if (logFileFolder != null && (!logFileFolder.exists())) {
                        LOGGER.info("Log file " + logFileFolder + " does not exist.");
                        error = true;
                    }

                    if (categoryMatcherFile != null
                            && (!categoryMatcherFile.exists() || !categoryMatcherFile.isFile())) {
                        LOGGER.info("Match file " + categoryMatcherFile + " does not exist or is not a file.");
                        error = true;
                    }

                    if (!error) {
                        if (logFileFolder.isDirectory()) {
                            List<File> analysisLogFiles = new ArrayList<>();

                            // check for log files immediately inside the specified logFileFolder
                            File[] files = logFileFolder.listFiles(new FileFilter() {
                                @Override
                                public boolean accept(File file) {
                                    return file.getName().matches("(map-.*|map)\\.log") && file.isFile();
                                }
                            });

                            if (files != null) {
                                for (File file : files) {
                                    analysisLogFiles.add(file);
                                }
                            }

                            // check for log files assuming logFileFolder is a node folder
                            analysisLogFiles.addAll(scanNodeFolderForLogs(logFileFolder));

                            // check for log files assuming each subfolder of logFileFolder to be a node
                            // folder
                            File[] nodeFolders = logFileFolder.listFiles(new FileFilter() {
                                @Override
                                public boolean accept(File file) {
                                    return file.isDirectory();
                                }
                            });

                            LOGGER.debug("nodeFolders: {}", Arrays.toString(nodeFolders));

                            if (nodeFolders != null) {
                                for (File nodeFolder : nodeFolders) {
                                    analysisLogFiles.addAll(scanNodeFolderForLogs(nodeFolder));
                                }
                            }

                            // run log analysis on the log files that were found
                            if (!analysisLogFiles.isEmpty()) {
                                LOGGER.info("Log files found for analysis: {}", analysisLogFiles);
                                File[] analysisLogFilesArray = new File[analysisLogFiles.size()];

                                for (int n = 0; n < analysisLogFilesArray.length; n++) {
                                    analysisLogFilesArray[n] = analysisLogFiles.get(n);
                                }

                                mla.run(categoryMatcherFile, outputFolder, analysisLogFilesArray);
                            } else {
                                LOGGER.fatal("No log files were found in '{}'", logFileFolder);
                            }
                        } else if (logFileFolder.isFile()) {
                            mla.run(categoryMatcherFile, outputFolder, logFileFolder);
                        }

                    }

                    System.exit(0);
                }
                break;

            default:
                break;
            }
        }

        outputExpectedArguments();
    }

    private static List<File> scanNodeFolderForLogs(File nodeFolder) {
        List<File> analysisLogFiles = new LinkedList<>();

        File agentLogFile = nodeFolder.toPath().resolve("agent").resolve("map-agent.log").toFile();

        if (agentLogFile.exists() && agentLogFile.isFile()) {
            analysisLogFiles.add(agentLogFile);
        }

        File clientLogFile = nodeFolder.toPath().resolve("client").resolve("map-client.log").toFile();
        File clientPreStartLogFile = nodeFolder.toPath().resolve("client").resolve("map-client_pre_start.log").toFile();

        if (clientLogFile.exists() && clientLogFile.isFile()) {
            analysisLogFiles.add(clientLogFile);
        }

        if (clientPreStartLogFile.exists() && clientPreStartLogFile.isFile()) {
            analysisLogFiles.add(clientPreStartLogFile);
        }

        File dnsLogFile = nodeFolder.toPath().resolve("dns").resolve("map-dns.log").toFile();

        if (dnsLogFile.exists() && dnsLogFile.isFile()) {
            analysisLogFiles.add(dnsLogFile);
        }

        return analysisLogFiles;
    }

    private static void outputExpectedArguments() {
        StringBuilder sb = new StringBuilder();

        for (int a = 0; a < CHART_PARAMETERS.length; a++) {
            if (a > 0)
                sb.append("\n");

            sb.append("[" + a + "]   ");

            for (int b = 0; b < CHART_PARAMETERS[a].length; b++) {
                if (b > 0)
                    sb.append(" ");

                sb.append(CHART_PARAMETERS[a][b]);
            }
        }

        LOGGER.fatal("Expected arguments:\n{}", sb.toString());
    }

    private abstract static class ChartGenerationModuleWorker implements Runnable {
        // CHECKSTYLE:OFF
        final File scenarioFolder;
        final File demandScenarioFolder;
        final File inputFolder;
        final File outputFolder;
        final long dataSampleInterval;
        final boolean startAtZero;
        // CHECKSTYLE:ON

        ChartGenerationModuleWorker(File scenarioFolder, File demandScenarioFolder, File inputFolder, File outputFolder,
                long dataSampleInterval, boolean startAtZero) {
            this.scenarioFolder = scenarioFolder;
            this.demandScenarioFolder = demandScenarioFolder;
            this.inputFolder = inputFolder;
            this.outputFolder = outputFolder;
            this.dataSampleInterval = dataSampleInterval;
            this.startAtZero = startAtZero;
        }
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
