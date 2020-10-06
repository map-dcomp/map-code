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
package com.bbn.map.simulator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import javax.annotation.Nonnull;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.protelis.lang.datatype.DeviceUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.utils.LogExceptionHandler;
import com.bbn.map.utils.MapLoggingConfigurationFactory;
import com.bbn.protelis.common.testbed.termination.TerminationCondition;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.diffplug.common.base.Errors;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;

/**
 * Execute a simulation from the command line.
 * 
 * @author jschewe
 *
 */
public class SimulationRunner {
    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapLoggingConfigurationFactory.class.getName());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationRunner.class);

    /**
     * Print out the usage information. Does not exit, that is up to the caller.
     * 
     * @param options
     *            the options for the command line parser
     */
    private static void printUsage(final Options options) {
        new HelpFormatter().printHelp("SimulationRunner", options);
    }

    private static final String SCENARIO_OPT = "scenario";
    private static final String DEMAND_OPT = "demand";
    private static final String RUNTIME_OPT = "runtime";
    private static final String OUTPUT_OPT = "output";
    private static final String DUMP_INTERVAL_OPT = "dumpInterval";
    private static final String HELP_OPT = "help";
    private static final String SLOW_NETWORK_THRESHOLD_OPT = "slowNetworkThreshold";
    private static final String SLOW_SERVER_THRESHOLD_OPT = "slowServerThreshold";

    private static final String AGENT_CONFIGURATION_OPT = "agentConfiguration";

    /**
     * Parse the option as a duration. First check if it's a number, if so, then
     * treat it as a number of seconds. Otherwise parse with
     * {@link Duration#parse(CharSequence)}.
     * 
     * @param options
     *            the specified options
     * @param cmd
     *            the parse options
     * @param optionName
     *            the option to read
     * @return the duration, null on an error
     */
    private static Duration parseDuration(@Nonnull final Options options,
            @Nonnull final CommandLine cmd,
            @Nonnull final String optionName) {
        final String str = cmd.getOptionValue(optionName);
        final Duration value = parseDuration(str);
        if (null == value) {
            LOGGER.error("'{}' could not be parsed as an integer or a duration for option {}", str, optionName);
        }
        return value;
    }

    /**
     * Parse the string as a duration. First check if it's a number, if so, then
     * treat it as a number of seconds. Otherwise parse with
     * {@link Duration#parse(CharSequence)}.
     *
     * @param str
     *            the string to parse
     * @return the duration, null on an error
     */
    public static Duration parseDuration(final String str) {
        try {
            final long runtimeSeconds = Long.parseLong(str);
            return Duration.ofSeconds(runtimeSeconds);
        } catch (final NumberFormatException e) {
            // not a number, parse as a duration
            try {
                final Duration duration = Duration.parse(str);
                return duration;
            } catch (final DateTimeParseException de) {
                return null;
            }
        }
    }

    /**
     * Get the git version information for the build.
     * 
     * @return the version or a string stating what the problem was
     */
    public static String getGitVersionInformation() {
        final URL url = SimulationRunner.class.getResource("git.properties");
        if (null == url) {
            return "UNKNOWN";
        }
        try (InputStream is = url.openStream()) {
            final Properties props = new Properties();
            props.load(is);
            return props.getProperty("git.commit.id", "MISSING-PROPERTY");
        } catch (final IOException e) {
            LOGGER.error("Unable to read git.properties", e);
            return "ERROR-READING-VERSION";
        }
    }

    /**
     * 
     * @param args
     *            run without arguments to see all options
     */
    public static void main(final String[] args) {
        LogExceptionHandler.registerExceptionHandler();

        LOGGER.info("Git version: {}", getGitVersionInformation());

        final Options options = new Options();
        options.addRequiredOption("s", SCENARIO_OPT, true, "The directory where the scenario is stored (required)");
        options.addOption("d", DEMAND_OPT, true, "The directory where the demand is stored");
        options.addOption("r", RUNTIME_OPT, true,
                "The amount of time to run for, either a number of seconds or a string compatible with Duration.parse (Defaults to the end of the last client load)");
        options.addOption("o", OUTPUT_OPT, true,
                "Directory to write the output to, if not specified no output is written");
        options.addOption(null, DUMP_INTERVAL_OPT, true,
                "The number of seconds between dumps of the data to the output directory (default: "
                        + DEFAULT_DUMP_INTERVAL_SECONDS + " seconds)");
        options.addOption("h", HELP_OPT, false, "Show the help");

        options.addOption(null, SLOW_NETWORK_THRESHOLD_OPT, true,
                "The percentage above which a client request across a network link is labeled as slow. Value must be in the range [0, 1]. Default is "
                        + SimulationConfiguration.getInstance().getSlowNetworkThreshold());
        options.addOption(null, SLOW_SERVER_THRESHOLD_OPT, true,
                "The percentage above which a client request to a server is labeled as slow. Value must be in the range [0, 1]. Default is "
                        + SimulationConfiguration.getInstance().getSlowServerThreshold());

        options.addOption(null, AGENT_CONFIGURATION_OPT, true, "Read the agent configuration from the specified file.");

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(options, args);
            final SimulationRunner runner = new SimulationRunner();
            final Path scenarioPath = Paths.get(cmd.getOptionValue("s"));
            runner.setScenarioPath(scenarioPath);

            if (cmd.hasOption(RUNTIME_OPT)) {
                final Duration runtime = parseDuration(options, cmd, RUNTIME_OPT);
                if (null == runtime) {
                    printUsage(options);
                    System.exit(1);
                }
                runner.setRuntime(runtime);
            }

            if (cmd.hasOption(HELP_OPT)) {
                printUsage(options);
                System.exit(0);
            }

            if (cmd.hasOption(AGENT_CONFIGURATION_OPT)) {
                final Path path = Paths.get(cmd.getOptionValue(AGENT_CONFIGURATION_OPT));
                try {
                    AgentConfiguration.readFromFile(path);
                } catch (final IOException e) {
                    LOGGER.error("Error reading agent configuration from {}: {}", path, e.getMessage(), e);
                }
            }

            if (cmd.hasOption(OUTPUT_OPT)) {
                final Path outputDirectory = Paths.get(cmd.getOptionValue(OUTPUT_OPT));
                runner.setOutputDirectory(outputDirectory);
            }

            if (cmd.hasOption(DEMAND_OPT)) {
                final Path demandPath = Paths.get(cmd.getOptionValue(DEMAND_OPT));
                runner.setDemandPath(demandPath);
            }

            if (cmd.hasOption(DUMP_INTERVAL_OPT)) {
                final String str = cmd.getOptionValue(DUMP_INTERVAL_OPT);
                try {
                    final long dumpInterval = Long.parseLong(str);
                    final Duration interval = Duration.ofSeconds(dumpInterval);
                    runner.setDumpInterval(interval);
                } catch (final NumberFormatException e) {
                    LOGGER.error("'{}' could not be parsed as an integer", str);
                    printUsage(options);
                    System.exit(1);
                }
            }

            if (cmd.hasOption(SLOW_NETWORK_THRESHOLD_OPT)) {
                final String str = cmd.getOptionValue(SLOW_NETWORK_THRESHOLD_OPT);
                try {
                    final double value = Double.parseDouble(str);
                    SimulationConfiguration.getInstance().setSlowNetworkThreshold(value);
                } catch (final NumberFormatException e) {
                    LOGGER.error("'{}' could not be parsed as a double", str);
                    printUsage(options);
                    System.exit(1);
                } catch (final IllegalArgumentException e) {
                    LOGGER.error("Illegal value '{}' for slow network threshold", str);
                    printUsage(options);
                    System.exit(1);
                }
            }

            if (cmd.hasOption(SLOW_SERVER_THRESHOLD_OPT)) {
                final String str = cmd.getOptionValue(SLOW_SERVER_THRESHOLD_OPT);
                try {
                    final double value = Double.parseDouble(str);
                    SimulationConfiguration.getInstance().setSlowServerThreshold(value);
                } catch (final NumberFormatException e) {
                    LOGGER.error("'{}' could not be parsed as a double", str);
                    printUsage(options);
                    System.exit(1);
                } catch (final IllegalArgumentException e) {
                    LOGGER.error("Illegal value '{}' for slow server threshold", str);
                    printUsage(options);
                    System.exit(1);
                }
            }

            if (null == runner.getDemandPath() && null == runner.getRuntime()) {
                LOGGER.error(
                        "If the runtime is not specified, then the demand path must be specified to determine when to end the simulation");
                System.exit(1);
            }

            LOGGER.info("Starting the simulation");
            runner.run();

            LOGGER.info("The simulation has finished");

            System.exit(0);

        } catch (final ParseException e) {
            LOGGER.error("Error parsing the command line: {}", e.getMessage());
            new HelpFormatter().printHelp("SimulationRunner", options);
            System.exit(1);
        }
    }

    /**
     * Default constructor, all properties are at default values. They will need
     * to be set before executing {@link #run()}.
     */
    public SimulationRunner() {
    }

    private Path outputDirectory = null;

    /**
     * 
     * @return the directory to output data to, if null no outputs will be
     *         created
     */
    public Path getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * 
     * @param v
     *            see {@link #getOutputDirectory()}
     */
    public void setOutputDirectory(final Path v) {
        outputDirectory = v;
    }

    private Path scenarioPath = null;

    /**
     * @return the path to the scenario, may be null if not yet set
     */
    public Path getScenarioPath() {
        return scenarioPath;
    }

    /**
     * Must be set to run the simulation.
     * 
     * @param p
     *            see {@link #getScenarioPath()}
     */
    public void setScenarioPath(@Nonnull final Path p) {
        scenarioPath = p;
    }

    private Path demandPath = null;

    /**
     * 
     * @return where to read the demand from, may be null
     */
    public Path getDemandPath() {
        return demandPath;
    }

    /**
     * 
     * @param v
     *            see {@link #getDemandPath()}
     */
    public void setDemandPath(final Path v) {
        demandPath = v;
    }

    private Duration runtime = null;

    /**
     * Must be set to run the simulation.
     * 
     * @return how long the simulation is to run for
     */
    public Duration getRuntime() {
        return runtime;
    }

    /**
     * 
     * @param v
     *            see {@link #getRuntime()}
     */
    public void setRuntime(@Nonnull final Duration v) {
        LOGGER.info("setting runtime to {}", v);
        if (v.isNegative()) {
            throw new IllegalArgumentException("Runtime cannot be negative");
        } else if (v.isZero()) {
            throw new IllegalArgumentException("Runtime must be greater than 0");
        }

        runtime = v;
    }

    /**
     * Default dump interval.
     */
    public static final int DEFAULT_DUMP_INTERVAL_SECONDS = 10;
    private Duration dumpInterval = Duration.ofSeconds(DEFAULT_DUMP_INTERVAL_SECONDS);

    /**
     * @return how long between dumping state, default is 10 seconds
     */
    @Nonnull
    public Duration getDumpInterval() {
        return dumpInterval;
    }

    /**
     * 
     * @param v
     *            see {@link #getDumpInterval()}
     */
    public void setDumpInterval(@Nonnull final Duration v) {
        if (v.isNegative()) {
            throw new IllegalArgumentException("Dump interval cannot be negative");
        } else if (v.isZero()) {
            throw new IllegalArgumentException("Dump interval must be greater than 0");
        }

        dumpInterval = v;
    }

    /**
     * DNS TTL in seconds.
     */
    public static final int TTL = 1;

    /**
     * Create the output directory if needed.
     * 
     * @return success or output is null
     */
    private boolean createOutputDirectory() {
        final Path outputDirectory = getOutputDirectory();
        if (null != outputDirectory) {
            final File f = outputDirectory.toFile();
            if (!f.exists()) {
                if (!f.mkdirs()) {
                    LOGGER.error("Unable to create directory '{}'", outputDirectory);
                    return false;
                }
                return f.exists();
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    /**
     * Run the simulation.
     * 
     * @throws NullPointerException
     *             if a required parameter is unset
     */
    public void run() {
        Objects.requireNonNull(getScenarioPath(), "Scenario path must be specified");

        if (!createOutputDirectory()) {
            LOGGER.error("Unable to create output directory: {}", getOutputDirectory());
            return;
        }

        try {
            final VirtualClock clock = new SimpleClock();
            final Simulation sim = new Simulation(getScenarioPath().toString(), getScenarioPath(), getDemandPath(),
                    clock, AgentConfiguration.getInstance().getApRoundDuration().toMillis(), TTL,
                    AppMgrUtils::getContainerParameters);

            // set global leader, this can fail if the node cannot be found
            if (!AgentConfiguration.getInstance().isUseLeaderElection()) {
                final String leaderName = getGlobalLeaderName();
                if (null == leaderName) {
                    // just pick a node
                    sim.getAllControllers().stream().findFirst().get().setGlobalLeader(true);
                } else {
                    final NodeIdentifier id = new DnsNameIdentifier(leaderName);
                    final Controller c = sim.getControllerById(id);
                    if (null == c) {
                        throw new IllegalArgumentException("The node " + leaderName
                                + " is not known to the simulation and cannot be set as the global leader");
                    } else {
                        c.setGlobalLeader(true);
                    }
                }
            }

            if (null != getOutputDirectory()) {
                sim.setBaseOutputDirectory(getOutputDirectory());

                sim.getAllControllers().forEach(controller -> {
                    controller.setBaseOutputDirectory(getOutputDirectory());
                    controller.setDumpInterval(getDumpInterval());
                    controller.setDumpState(true);
                });
            }

            final Thread dumperThread = new Thread(() -> dumperWorker(clock, sim), "Dumper");

            sim.getScenario().setTerminationCondition(termination);

            stopScenario = false;

            if (null != getOutputDirectory()) {
                dumperThread.start();
            }

            sim.startSimulation();

            LOGGER.info("Waiting for all nodes to connect to their neighbors for AP");
            sim.waitForAllNodesToConnectToNeighbors();
            LOGGER.info("All nodes are connected for AP");

            final int numApRoundsToStabilize = SimUtils.computeRoundsToStabilize(sim);
            LOGGER.info("Waiting {} AP rounds to ensure that it is stable", numApRoundsToStabilize);
            SimUtils.waitForApRounds(sim, numApRoundsToStabilize);

            LOGGER.info("Starting clients");
            sim.startClients();

            if (null == getRuntime()) {
                LOGGER.info("Waiting for all client simulators to exit");
                sim.getClientSimulators().stream().forEach(Errors.rethrow().wrap(client -> {
                    client.join();
                }));
                LOGGER.info("All clients have finished");
            } else {
                LOGGER.info("Waiting {} ms for the simulation to finish", getRuntime().toMillis());
                clock.waitForDuration(getRuntime().toMillis());
            }

            LOGGER.info("Stopping the simulation");
            stopScenario = true;
            sim.stopSimulation();
        } catch (final IOException e) {
            LOGGER.error("Error reading one of the input files", e);
        }

    }

    private void dumperWorker(final VirtualClock clock, final Simulation sim) {
        clock.waitForClockStart();

        final ObjectWriter mapper = Controller.createDumpWriter();

        final Path baseOutput = getOutputDirectory();
        final Path nodeOutputDirectory = baseOutput.resolve("simulation");

        if (!Files.exists(nodeOutputDirectory)) {
            if (!nodeOutputDirectory.toFile().mkdirs()) {
                LOGGER.error("Unable to create output directory {}", nodeOutputDirectory);
            }
        }

        if (Files.exists(nodeOutputDirectory)) {
            try {

                // write out configuration
                final Path agentConfigurationFilename = getOutputDirectory().resolve("agent-configuration.json");
                try (BufferedWriter writer = Files.newBufferedWriter(agentConfigurationFilename,
                        Charset.defaultCharset())) {
                    mapper.writeValue(writer, AgentConfiguration.getInstance());
                }
                final Path simulationConfigurationFilename = nodeOutputDirectory
                        .resolve("simulation-configuration.json");
                try (BufferedWriter writer = Files.newBufferedWriter(simulationConfigurationFilename,
                        Charset.defaultCharset())) {
                    mapper.writeValue(writer, SimulationConfiguration.getInstance());
                }

                // write out all client requests
                for (final ClientSim client : sim.getClientSimulators()) {
                    client.setBaseOutputDirectory(baseOutput);
                    final Path clientFilename = nodeOutputDirectory
                            .resolve(String.format("client-%s-requests.json", client.getSimName()));
                    try (BufferedWriter writer = Files.newBufferedWriter(clientFilename, Charset.defaultCharset())) {
                        final ImmutableList<ClientLoad> requests = client.getClientRequests();
                        mapper.writeValue(writer, requests);
                    }
                }
            } catch (final IOException e) {
                LOGGER.error("Unable to write out client request information", e);
            }
        } else {
            LOGGER.error("'{}' was not created and does not exist. Skipping output.", nodeOutputDirectory);
        }

        while (!clock.isShutdown()) {
            final long now = clock.getCurrentTime();

            final String timeDir = String.format(Simulation.TIME_DIR_FORMAT, now);
            final Path outputDir = nodeOutputDirectory.resolve(timeDir);

            // create directory
            final File outputDirFile = outputDir.toFile();
            if (!outputDirFile.exists()) {
                if (!outputDir.toFile().mkdirs()) {
                    LOGGER.error("Unable to create output directory {}", outputDir);
                }
            }

            if (outputDirFile.exists()) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Dumping state to {}", outputDir);
                }

                try {
                    sim.dumpCurrentState(outputDir, mapper);
                } catch (final IOException e) {
                    LOGGER.error("Error writing current state, may have partial output", e);
                }
            } else {
                LOGGER.error("'{}' was not created and does not exist. Skipping output.", outputDir);
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Waiting for {} ms", getDumpInterval().toMillis());
            }
            clock.waitForDuration(getDumpInterval().toMillis());
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Done waiting");
            }
        }

        try {
            // write out all final client values
            for (final ClientSim client : sim.getClientSimulators()) {
                final Path clientFilename = nodeOutputDirectory
                        .resolve(String.format("client-%s-final-state.json", client.getSimName()));
                try (BufferedWriter writer = Files.newBufferedWriter(clientFilename, Charset.defaultCharset())) {
                    mapper.writeValue(writer, client.getSimulationState());
                }
            }
        } catch (final IOException e) {
            LOGGER.error("Unable to write out final client state", e);
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Dumper thread exiting");
        }
    }

    private boolean stopScenario;

    private final transient TerminationCondition<Map<DeviceUID, Controller>> termination = new TerminationCondition<Map<DeviceUID, Controller>>() {
        @Override
        public boolean shouldTerminate(final Map<DeviceUID, Controller> ignored) {
            return stopScenario;
        }
    };

    private String globalLeaderName = null;

    /**
     * 
     * @param v
     *            see {@link #getGlobalLeaderName()}
     */
    public void setGlobalLeaderName(final String v) {
        globalLeaderName = v;
    }

    /**
     * 
     * @return the name of the global leader, may be null
     */
    public String getGlobalLeaderName() {
        return globalLeaderName;
    }

}
