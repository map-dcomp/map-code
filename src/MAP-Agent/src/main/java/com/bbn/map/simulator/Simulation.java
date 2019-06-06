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
package com.bbn.map.simulator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.protelis.lang.datatype.DeviceUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.map.NetworkServices;
import com.bbn.map.ServiceConfiguration;
import com.bbn.map.ap.MapNetworkFactory;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.map.dns.DNSUpdateService;
import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.dns.PlanTranslator;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.DelegateRegionLookup;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NetworkClient;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeLookupService;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionLookupService;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;
import com.bbn.protelis.networkresourcemanagement.testbed.LocalNodeLookupService;
import com.bbn.protelis.networkresourcemanagement.testbed.Scenario;
import com.bbn.protelis.networkresourcemanagement.testbed.ScenarioRunner;
import com.bbn.protelis.utils.VirtualClock;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.algorithms.shortestpath.DistanceStatistics;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java8.util.Objects;

/**
 * Simulate client demand for a network. A {@link ScenarioRunner} should not be
 * used when this class is used as it does the same work (and more).
 * 
 */
public class Simulation implements NetworkServices, AutoCloseable, RegionLookupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(Simulation.class);

    private static final int MAX_NUM_CHARACTERS_IN_TIMESTAMP = String.valueOf(Integer.MAX_VALUE).length();
    /**
     * Format for time directories.
     */
    public static final String TIME_DIR_FORMAT = String.format("%%0%dd", MAX_NUM_CHARACTERS_IN_TIMESTAMP);

    private static final int BASE_AP_COM_PORT = 20000;

    /**
     * Name of the file to read inside the scenario for hardware configuration
     * information. If more than 1 hardware configuration has the same name, the
     * last one is used.
     */
    public static final String HARDWARE_CONFIG_FILENAME = "hardware-configurations.json";

    /**
     * Name of the file to read inside the scenario for service configuration
     * information.
     */
    public static final String SERVICE_CONFIGURATIONS_FILENAME = "service-configurations.json";

    /**
     * Name of the file to read inside the scenario for application
     * dependencies.
     */
    public static final String SERVICE_DEPENDENCIES_FILENAME = "service-dependencies.json";

    private Graph<NetworkNode, NetworkLink> graph = new SparseMultigraph<>();

    private final Scenario<Controller, NetworkLink, NetworkClient> scenario;

    /**
     * 
     * @return the scenario that is being run
     */
    @Nonnull
    public Scenario<Controller, NetworkLink, NetworkClient> getScenario() {
        return scenario;
    }

    private final Map<NodeIdentifier, Controller> controllerCache = new HashMap<>();
    private final Map<NodeIdentifier, NetworkClient> clientCache = new HashMap<>();
    private final ImmutableList<ClientSim> clientSimulators;

    /**
     * 
     * @return the simulators, used for display purposes
     */
    public ImmutableList<ClientSim> getClientSimulators() {
        return clientSimulators;
    }

    private final DijkstraShortestPath<NetworkNode, NetworkLink> pathFinder;
    private final ImmutableMap<String, HardwareConfiguration> hardwareConfigs;
    private final SimResourceManagerFactory managerFactory;

    private final boolean allowDnsChanges;
    private final boolean enableDcop;
    private final boolean enableRlg;

    private final Map<Controller, SimResourceManager> resourceManagers = new HashMap<>();

    private final List<NodeFailure> nodeFailures;

    private Thread nodeFailureThread = null;

    /**
     * Used to populate the map for {@link #getResourceManager(NetworkServer)}.
     * 
     * @param controller
     *            the node being managed
     * @param manager
     *            the manager for the node
     */
    public void addResourceManagerMapping(@Nonnull final Controller controller,
            @Nonnull final SimResourceManager manager) {
        resourceManagers.put(controller, manager);
    }

    /**
     * Get the {@link SimResourceManager} for a node. This can be used to add
     * demand to the node.
     * 
     * @param node
     *            the node to get the manager for
     * @return the manager for a node or null if the manager cannot be found
     */
    public SimResourceManager getResourceManager(@Nonnull final NetworkServer node) {
        return resourceManagers.get(node);
    }

    /**
     * Constructor to be used in non-testing cases. Dns changes are allowed and
     * DCOP and RLG are enabled.
     * 
     * @param name
     *            see
     *            {@link #Simulation(String, Path, Path, VirtualClock, long, int, boolean, boolean, boolean, Function)
     * @param scenarioDirectory
     *            see
     *            {@link #Simulation(String, Path, Path, VirtualClock, long, int, boolean, boolean, boolean, Function)
     * @param demandPath
     *            see
     *            {@link #Simulation(String, Path, Path, VirtualClock, long, int, boolean, boolean, boolean, Function)
     * @param clock
     *            see
     *            {@link #Simulation(String, Path, Path, VirtualClock, long, int, boolean, boolean, boolean, Function)
     * @param pollingInterval
     *            see
     *            {@link #Simulation(String, Path, Path, VirtualClock, long, int, boolean, boolean, boolean, Function)
     * @param ttl
     *            see
     *            {@link #Simulation(String, Path, Path, VirtualClock, long, int, boolean, boolean, boolean, Function)
     * @param serviceContainerParmeterLookup
     *            see
     *            {@link #Simulation(String, Path, Path, VirtualClock, long, int, boolean, boolean, boolean, Function)
     * @throws IOException
     *             if there is an error reading the simulation files
     */
    public Simulation(@Nonnull final String name,
            @Nonnull final Path scenarioDirectory,
            final Path demandPath,
            @Nonnull final VirtualClock clock,
            final long pollingInterval,
            final int ttl,
            final Function<ServiceIdentifier<?>, ContainerParameters> serviceContainerParmeterLookup)
            throws IOException {
        this(name, scenarioDirectory, demandPath, clock, pollingInterval, ttl, true, true, true,
                serviceContainerParmeterLookup);
    }

    /**
     * Load a simulation from the specified base directory.
     * 
     * @param name
     *            the name of the simulation
     * @param scenarioDirectory
     *            all scenario resources will be found relative to this.
     * @param demandPath
     *            where to find the files containing the client demand, may be
     *            null to have no demand
     * @param clock
     *            the clock to use for the simulation
     * @param pollingInterval
     *            milliseconds between generating new {@link ResourceReport}
     *            objects
     * @param ttl
     *            passed to
     *            {@link PlanTranslator#PlanTranslator(int, ImmutableMap)}
     * @param allowDnsChanges
     *            Should be true unless in testing where the DNS should not
     *            change during the simulation run
     * @param enableDcop
     *            false if DCOP should be disabled, this is for testing
     * @param enableRlg
     *            false if RLG shoudl be disabled, this is for testing
     * @param serviceContainerParmeterLookup
     *            function to map from services to the container parameters to
     *            run the service. This is used for starting the initial
     *            services.
     * @throws IOException
     *             if there is an error reading the simulation files
     */
    public Simulation(@Nonnull final String name,
            @Nonnull final Path scenarioDirectory,
            final Path demandPath,
            @Nonnull final VirtualClock clock,
            final long pollingInterval,
            final int ttl,
            final boolean allowDnsChanges,
            final boolean enableDcop,
            final boolean enableRlg,
            final Function<ServiceIdentifier<?>, ContainerParameters> serviceContainerParmeterLookup)
            throws IOException {
        this.managerFactory = new SimResourceManagerFactory(this, pollingInterval);
        this.clock = clock;
        this.globalDNS = new DNSSim(this, "GLOBAL");
        this.graph = new SparseMultigraph<>();
        this.allowDnsChanges = allowDnsChanges;
        this.enableDcop = enableDcop;
        this.enableRlg = enableRlg;
        this.hardwareConfigs = parseHardwareConfigurations(scenarioDirectory);

        this.scenario = parseScenario(name, scenarioDirectory, managerFactory);

        // setup regional DNS and servers in the graph
        for (final Map.Entry<DeviceUID, Controller> entry : scenario.getServers().entrySet()) {
            final Controller controller = entry.getValue();
            graph.addVertex(controller);
            controllerCache.put(controller.getNodeIdentifier(), controller);

            ensureRegionalDNSExists(controller.getRegionIdentifier());
        }

        // setup links
        scenario.getLinks().forEach(link -> {
            final LinkResourceManager lmgr = new LinkResourceManager(link);
            addLinkResMgr(lmgr);
        });

        checkDnsUpdateHandlers();

        final Path serviceConfigurationPath = scenarioDirectory.resolve(SERVICE_CONFIGURATIONS_FILENAME);
        final Path applicationDependenciesPath = scenarioDirectory.resolve(SERVICE_DEPENDENCIES_FILENAME);
        final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations = AppMgrUtils
                .loadApplicationManager(serviceConfigurationPath, applicationDependenciesPath);

        final ApplicationManagerApi appManager = AppMgrUtils.getApplicationManager();

        planTranslator = new PlanTranslator(ttl);

        // setup clients
        final ImmutableList.Builder<ClientSim> clientSimBuilder = ImmutableList.builder();
        for (final Map.Entry<DeviceUID, NetworkClient> entry : scenario.getClients().entrySet()) {
            final NetworkClient client = entry.getValue();
            graph.addVertex(client);
            clientCache.put(client.getNodeIdentifier(), entry.getValue());

            final ClientSim sim = new ClientSim(this, entry.getValue(), demandPath);
            clientSimBuilder.add(sim);

            ensureRegionalDNSExists(client.getRegionIdentifier());
        }
        clientSimulators = clientSimBuilder.build();

        // setup links
        for (final NetworkLink l : scenario.getLinks()) {
            graph.addEdge(l, l.getLeft(), l.getRight());
        }

        final Path nodeFailuresPath = scenarioDirectory.resolve(NODE_FAILURES_FILENAME);
        nodeFailures = loadNodeFailures(nodeFailuresPath);
        if (!verifyNodeFailures()) {
            // cannot execute, failures were logged by the verify function
            throw new IllegalArgumentException(
                    "There were errors with the node failures. See previous log messages for the details.");
        }

        // start initial services
        final Map<ApplicationCoordinates, Set<RegionIdentifier>> serviceDefaultRegions = new HashMap<>();
        serviceConfigurations.forEach((service, config) -> {
            LOGGER.debug("Processing service: {} config: {}", service, config);
            
            final ApplicationSpecification appSpec = appManager.getApplicationSpecification(service);

            final ImmutableMap<NodeIdentifier, Integer> defaultNodes = config.getDefaultNodes();
            final RegionIdentifier serviceDefaultRegion = appSpec.getServiceDefaultRegion();

            boolean foundNodeInDefaultRegion = false;
            for (final Map.Entry<NodeIdentifier, Integer> entry : defaultNodes.entrySet()) {
                final NodeIdentifier nodeId = entry.getKey();
                final int initialInstances = entry.getValue();

                LOGGER.debug("Default node: {} instances: {}", nodeId, initialInstances);

                final Controller controller = getControllerById(nodeId);
                Objects.requireNonNull(controller, "Cannot find controller for node: " + nodeId);

                if (serviceDefaultRegion.equals(controller.getRegionIdentifier())) {
                    foundNodeInDefaultRegion = true;
                }

                final DNSSim regionalDns = getRegionalDNS(serviceDefaultRegion);

                final ResourceManager<?> mgr = controller.getResourceManager();
                for (int i = 0; i < initialInstances; ++i) {
                    final ContainerParameters parameters = serviceContainerParmeterLookup.apply(service);
                    final NodeIdentifier id = mgr.startService(service, parameters);
                    Objects.requireNonNull(id, "Unable to start initial service " + service + " instance number "
                            + (i + 1) + " on " + nodeId);

                    LOGGER.info("Started initial service {} on {}", service, id);

                    // add entry for the container into the regional DNS
                    final DnsRecord record = new NameRecord(null, ttl, service, id);
                    regionalDns.addRecord(record, 1D);
                }

                final Set<RegionIdentifier> regions = serviceDefaultRegions.computeIfAbsent(service,
                        k -> new HashSet<>());
                regions.add(serviceDefaultRegion);
            }

            if (!foundNodeInDefaultRegion) {
                throw new RuntimeException(
                        "No default nodes defined for service " + service + " in region " + serviceDefaultRegion);
            }
        });

        // setup delegate records for all service default regions in the global
        // DNS
        serviceDefaultRegions.forEach((service, regions) -> {
            final ApplicationSpecification appSpec = appManager.getApplicationSpecification(service);
            if (null == appSpec) {
                throw new RuntimeException("Unable to find application specification configuration for " + service);
            }

            regions.forEach(region -> {
                final DnsRecord record = new DelegateRecord(null, ttl, service, region);
                getGlobalDNS().addRecord(record, 1D);
            });
        });

        pathFinder = new DijkstraShortestPath<>(graph);

    }

    /** Check that each region has at least 1 node handling DNS updates */
    private void checkDnsUpdateHandlers() {
        final Map<RegionIdentifier, Boolean> dnsExists = new HashMap<>();
        regionalDNS.forEach((region, dns) -> dnsExists.put(region, false));

        synchronized (controllerCache) {
            controllerCache.forEach((k, controller) -> {
                if (controller.isHandleDnsChanges()) {
                    dnsExists.put(controller.getRegionIdentifier(), true);
                }
            });
        }

        dnsExists.forEach((region, exists) -> {
            if (!exists) {
                throw new RuntimeException("There is no DNS handler defined for region: " + region);
            }
        });
    }

    private static ImmutableMap<String, HardwareConfiguration> parseHardwareConfigurations(
            @Nonnull final Path scenarioPath) throws IOException {
        final Path path = scenarioPath.resolve(HARDWARE_CONFIG_FILENAME);
        if (!Files.exists(path)) {
            // no hardware configs
            return ImmutableMap.of();
        }

        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper().registerModule(new GuavaModule());

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            final ImmutableList<HardwareConfiguration> list = mapper.readValue(reader,
                    new TypeReference<ImmutableList<HardwareConfiguration>>() {
                    });
            final ImmutableMap.Builder<String, HardwareConfiguration> map = ImmutableMap.builder();
            list.forEach(config -> map.put(config.getName(), config));

            return map.build();
        }
    }

    /**
     * @param name
     *            the name of the hardware configuration to find
     * @return the hardware configuration for the name or null if not found
     */
    public HardwareConfiguration getHardwareConfiguration(@Nonnull final String name) {
        return hardwareConfigs.get(name);
    }

    /**
     * @param scenarioName
     * @param baseDirectory
     * @return
     * @throws IOException
     */
    private Scenario<Controller, NetworkLink, NetworkClient> parseScenario(@Nonnull final String scenarioName,
            @Nonnull final Path baseDirectory,
            @Nonnull final SimResourceManagerFactory managerFactory) throws IOException {

        final DelegateRegionLookup regionLookupService = new DelegateRegionLookup();
        final NodeLookupService nodeLookupService = new LocalNodeLookupService(BASE_AP_COM_PORT);

        final MapNetworkFactory factory = new MapNetworkFactory(nodeLookupService, regionLookupService, managerFactory,
                AgentConfiguration.getInstance().getApProgram(),
                AgentConfiguration.getInstance().isApProgramAnonymous(), this, allowDnsChanges, enableDcop, enableRlg);

        final Topology topology = NS2Parser.parse(scenarioName, baseDirectory);
        final Scenario<Controller, NetworkLink, NetworkClient> scenario = new Scenario<>(topology, factory,
                name -> new DnsNameIdentifier(name));

        regionLookupService.setDelegate(this);

        return scenario;
    }

    /**
     * Get the {@link NetworkLink}s to traverse to get from source to dest.
     * 
     * @param source
     *            the source node
     * @param dest
     *            the destination node
     * @return a non-null list, the list is empty if there is no path
     */
    @Nonnull
    public List<NetworkLink> getPath(@Nonnull final NetworkNode source, @Nonnull final NetworkNode dest) {
        synchronized (graph) {
            if (!graph.containsVertex(source) || !graph.containsVertex(dest)) {
                return Collections.emptyList();
            } else {
                final List<NetworkLink> path = pathFinder.getPath(source, dest);
                return path;
            }
        }
    }

    /**
     * 
     * @return the diameter of the network
     * @see DistanceStatistics#diameter(edu.uci.ics.jung.graph.Hypergraph)
     */
    public double getNetworkDiameter() {
        synchronized (graph) {
            return DistanceStatistics.diameter(graph);
        }
    }

    private final DNSSim globalDNS;

    /**
     * Public for visualization only.
     * 
     * @return the global DNS
     */
    public DNSSim getGlobalDNS() {
        return globalDNS;
    }

    private final Map<RegionIdentifier, DNSSim> regionalDNS = new HashMap<>();

    /**
     * Called from the constructor only.
     * 
     * Package visible for testing.
     * 
     * @param region
     *            the region to make sure a DNS exists for.
     */
    /* package */ void ensureRegionalDNSExists(final RegionIdentifier region) {
        regionalDNS.computeIfAbsent(region, k -> new DNSSim(this, region.getName(), getGlobalDNS()));
    }

    /**
     * @param region
     *            the region to find the DNS for
     * @return the DNS for the region
     * @throws IllegalArgumentException
     *             if the region is not known
     */
    @Nonnull
    public DNSSim getRegionalDNS(@Nonnull final RegionIdentifier region) {
        final DNSSim dns = regionalDNS.get(region);

        if (null == dns) {
            throw new IllegalArgumentException("Region " + region + " is not known to the simulation");
        }

        return dns;
    }

    /**
     * @return the list of all regions
     */
    @Nonnull
    public ImmutableSet<RegionIdentifier> getAllRegions() {
        return ImmutableSet.copyOf(regionalDNS.keySet());
    }

    /**
     * Lookup a container in DNS by name.
     * 
     * @param clientId
     *            the client doing the lookup
     * @param clientRegion
     *            the region that the client is in
     * @param service
     *            the service to find
     * @return the container
     * @throws UnknownHostException
     *             if the host cannot be found
     */
    @Nonnull
    public ContainerSim getContainerForService(@Nonnull final NodeIdentifier clientId,
            @Nonnull final RegionIdentifier clientRegion,
            @Nonnull final ServiceIdentifier<?> service) throws UnknownHostException {
        final DNSSim dns = getRegionalDNS(clientRegion);

        final NodeIdentifier containerName = dns.resolveService(clientId.getName(), service);
        if (null == containerName) {
            LOGGER.error("Unable to find '" + service + "' in dns: " + dns);
            throw new UnknownHostException("Host '" + service + "' is not found in the DNS");
        }

        synchronized (controllerCache) {
            for (final Map.Entry<?, Controller> entry : controllerCache.entrySet()) {
                final SimResourceManager resMgr = getResourceManager(entry.getValue());
                final ContainerSim container = resMgr.getContainerById(containerName);
                if (null != container) {
                    return container;
                }
            }
        }

        throw new UnknownHostException("Service '" + service + "' was found in DNS to point to " + containerName
                + ", but that container cannot be found");
    }

    /**
     * Find a particular controller by it's ID.
     * 
     * @param name
     *            the controller ID to look for
     * @return the controller or null if not found
     */
    public Controller getControllerById(@Nonnull final NodeIdentifier name) {
        synchronized (controllerCache) {
            return controllerCache.get(name);
        }
    }

    /**
     * Find a particular client by it's ID.
     * 
     * @param name
     *            the clinet ID to look for
     * @return the client or null if not found
     */
    public NetworkClient getClientById(@Nonnull final NodeIdentifier name) {
        return clientCache.get(name);
    }

    /**
     * 
     * @return an unmodifiable collection of the controllers in the simulation
     */
    public Collection<Controller> getAllControllers() {
        synchronized (controllerCache) {
            return Collections.unmodifiableCollection(controllerCache.values());
        }
    }

    private final VirtualClock clock;

    /**
     * 
     * @return the clock used to control this simulation
     */
    @Nonnull
    public VirtualClock getClock() {
        return clock;
    }

    private AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Start running the simulation.
     */
    public void startSimulation() {
        if (running.compareAndSet(false, true)) {

            synchronized (controllerCache) {
                // make sure there is a global leader, if there isn't, pick one
                final Optional<Controller> globalLeaderSet = controllerCache.entrySet().stream()
                        .map(Map.Entry::getValue).filter(Controller::isGlobalLeader).findAny();
                if (!globalLeaderSet.isPresent()) {
                    controllerCache.entrySet().stream().findFirst().get().getValue().setGlobalLeader(true);
                }

                controllerCache.forEach((k, controller) -> {
                    controller.startExecuting();
                    getResourceManager(controller).startSimulation();
                });
            }

            nodeFailureThread = new Thread(() -> simulateFailures(), "Simulate node failures");
            nodeFailureThread.start();

            clock.startClock();
        }
    }

    /**
     * Start the clients. One may want to start the clients after the simulation
     * has started up.
     */
    public void startClients() {
        clientSimulators.forEach((sim) -> sim.startSimulator());
    }

    /**
     * Once the simulation has been stopped it cannot be started again.
     */
    public void stopSimulation() {
        running.set(false);

        clock.shutdown();

        if (null != nodeFailureThread) {
            nodeFailureThread.interrupt();
        }

        synchronized (controllerCache) {
            controllerCache.forEach((k, controller) -> getResourceManager(controller).stopSimulation());
        }

        clientSimulators.forEach((sim) -> sim.shutdownSimulator());

        // shutdown AP communication
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(
                    "Stopping AP execution on all NetworkServers. Errors about streams being closed after this point can be safely ignored.");
        }
        scenario.getServers().forEach((k, server) -> server.stopExecuting());

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Waiting for all daemons to stop");
        }
        while (!daemonsQuiescent()) {
            try {
                Thread.sleep(scenario.getTerminationPollFrequency());
            } catch (final InterruptedException e) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("sleep interrupted, waiting again", e);
                }
                // ignore interruptions - we're just waiting in any case
            }
        }

        scenario.getServers().forEach((k, server) -> server.accessNetworkManager().stop());

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Stopped AP communication");
        }
    }

    private boolean daemonsQuiescent() {
        boolean alldead = true;
        for (final Map.Entry<DeviceUID, ? extends NetworkServer> entry : scenario.getServers().entrySet()) {
            if (entry.getValue().isExecuting()) {
                alldead = false;
                break;
            }
        }
        return alldead;
    }

    // NetworkServices interface
    private final PlanTranslator planTranslator;

    @Override
    @Nonnull
    public PlanTranslator getPlanTranslator() {
        return planTranslator;
    }

    @Override
    @Nonnull
    public DNSUpdateService getDnsUpdateService(@Nonnull final RegionIdentifier region) {
        return getRegionalDNS(region);
    }
    // end NetworkServices interface

    /**
     * Write the current state of the simulation to the specified output
     * directory.
     * 
     * @param outputDir
     *            where to write the state, this directory must already exist
     * @param mapper
     *            configured to write data out nicely
     * @throws IOException
     *             if there is an error writing one of the files
     */
    public void dumpCurrentState(@Nonnull final Path outputDir, @Nonnull final ObjectWriter mapper) throws IOException {

        // DNS state
        final Path globalDnsFilename = outputDir.resolve(String.format("dns-%s.json", "GLOBAL"));
        try (BufferedWriter writer = Files.newBufferedWriter(globalDnsFilename, Charset.defaultCharset())) {
            final DnsState state = new DnsState(getGlobalDNS());
            mapper.writeValue(writer, state);
        }
        for (final Map.Entry<RegionIdentifier, DNSSim> entry : regionalDNS.entrySet()) {
            final RegionIdentifier region = entry.getKey();
            final DNSSim dns = entry.getValue();

            final Path dnsFilename = outputDir.resolve(String.format("dns-%s.json", region.getName()));
            try (BufferedWriter writer = Files.newBufferedWriter(dnsFilename, Charset.defaultCharset())) {
                final DnsState state = new DnsState(dns);
                mapper.writeValue(writer, state);
            }
        }

        // client state
        for (final ClientSim client : getClientSimulators()) {
            final Path dnsFilename = outputDir.resolve(String.format("client-%s.json", client.getClientName()));
            try (BufferedWriter writer = Files.newBufferedWriter(dnsFilename, Charset.defaultCharset())) {
                mapper.writeValue(writer, client.getSimulationState());
            }
        }

    }

    /**
     * Used to output a DNS to JSON. This class wraps {@link DNSSim} and exposes
     * the properties that should be output.
     * 
     * @author jschewe
     *
     */
    public static final class DnsState {

        /**
         * 
         * @param entries
         *            see {@link #getEntries()}
         * @param cache
         *            see {@link #getCache()}
         */
        public DnsState(@JsonProperty("entries") @Nonnull final ImmutableList<DnsRecord> entries,
                @JsonProperty("cache") @Nonnull final ImmutableList<DnsRecord> cache) {
            this.entries = entries;
        }

        /**
         * 
         * @param dns
         *            the DNS to output data for
         */
        /* package */ DnsState(@Nonnull final DNSSim dns) {
            final ImmutableList.Builder<DnsRecord> entriesBuilder = ImmutableList.builder();
            dns.foreachRecord((record, weight) -> {
                entriesBuilder.add(record);
            });
            entries = entriesBuilder.build();
        }

        private final ImmutableList<DnsRecord> entries;

        /**
         * @return see
         *         {@link DNSSim#foreachRecord(com.bbn.map.simulator.DNSSim.RecordVisitor)}
         */
        public ImmutableList<DnsRecord> getEntries() {
            return entries;
        }

    }

    /**
     * Get a map of the number of requests from each source region to the
     * specified destination region.
     * 
     * @param destRegion
     *            the destination region of interest
     * @return mapping of source region to number of requests
     */
    @Nonnull
    public Map<RegionIdentifier, Integer> getNumRequestsForRegion(@Nonnull final RegionIdentifier destRegion) {
        final Map<RegionIdentifier, Integer> result = new HashMap<>();
        synchronized (controllerCache) {
            controllerCache.forEach((k, controller) -> {
                if (destRegion.equals(controller.getRegionIdentifier())) {
                    final SimResourceManager resMgr = getResourceManager(controller);
                    resMgr.getNumRequestsPerRegion().forEach((srcRegion, count) -> {
                        result.merge(srcRegion, count, Integer::sum);
                    });
                }
            });
        }
        return result;
    }

    /**
     * 
     * @param node
     *            the node to get the server capacity for
     * @return the capacity of the specified node. This is based on the hardware
     *         configuration of the node.
     */
    public ImmutableMap<NodeAttribute<?>, Double> getServerCapacity(final Controller node) {
        final String serverHardware = node.getHardware();
        final HardwareConfiguration hardwareConfig = getHardwareConfiguration(serverHardware);
        if (null == hardwareConfig) {
            return ImmutableMap.of();
        } else {

            final ImmutableMap.Builder<NodeAttribute<?>, Double> builder = ImmutableMap.builder();
            hardwareConfig.getCapacity().forEach((k, v) -> builder.put(k, v));

            return builder.build();
        }
    }

    /**
     * Get the capacity of a region for the specified attribute.
     * 
     * @param region
     *            the region compute the capacity for
     * @param attribute
     *            the attribute to compute the capacity of
     * @return the capacity
     */
    public double getRegionCapacity(@Nonnull final RegionIdentifier region, @Nonnull final NodeMetricName attribute) {
        synchronized (controllerCache) {
            final double capacity = controllerCache.entrySet().stream().map(Map.Entry::getValue)
                    .filter(c -> c.getRegionIdentifier().equals(region))
                    .mapToDouble(c -> getServerCapacity(c).getOrDefault(attribute, 0D)).sum();
            return capacity;
        }
    }

    /**
     * Compute the current load across a region for an attribute.
     * 
     * @param region
     *            the region to compute the load for
     * @param attribute
     *            the attribute to compute the load of
     * @return the load
     */
    public double computeRegionLoad(@Nonnull final RegionIdentifier region, @Nonnull final NodeMetricName attribute) {
        synchronized (controllerCache) {
            // complicated bit of streaming logic, but this will sum all load
            // values
            // for the specified attribute across source regions and services.
            final Stream<Controller> controllersStream = controllerCache.entrySet().stream().map(Map.Entry::getValue);
            final Stream<Controller> regionControllersStream = controllersStream
                    .filter(c -> region.equals(c.getRegionIdentifier()));
            final Stream<ResourceReport> reportStream = regionControllersStream
                    .map(c -> c.getResourceReport(EstimationWindow.SHORT));
            final Stream<ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>>> serverLoadStream = reportStream
                    .map(r -> r.getComputeLoad());
            final double load = serverLoadStream
                    .mapToDouble(
                            serviceEntry -> serviceEntry.entrySet().stream()
                                    .mapToDouble(regionEntry -> regionEntry.getValue().entrySet().stream()
                                            .mapToDouble(e -> e.getValue().getOrDefault(attribute, 0D)).sum())
                                    .sum())
                    .sum();
            return load;
        }
    }

    /**
     * Compute the current load percentage across a region for an attribute
     * separated by source region.
     * 
     * @param region
     *            the region to compute the load percentage for
     * @param attribute
     *            the attribute to compute the load percentage of
     * @return key is the source region, value is the load percentage (between 0
     *         and 1)
     */
    public Map<RegionIdentifier, Double> computeRegionLoadPercentageBySource(@Nonnull final RegionIdentifier region,
            @Nonnull final NodeMetricName attribute) {
        double totalLoad = 0;

        final Map<RegionIdentifier, Double> load = new HashMap<>();
        synchronized (controllerCache) {
            for (final Map.Entry<NodeIdentifier, Controller> controllerEntry : controllerCache.entrySet()) {
                final Controller controller = controllerEntry.getValue();
                if (region.equals(controller.getRegionIdentifier())) {
                    final ResourceReport report = controller.getResourceReport(EstimationWindow.SHORT);
                    final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> serverLoad = report
                            .getComputeLoad();
                    for (final Map.Entry<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> serviceEntry : serverLoad
                            .entrySet()) {
                        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> serviceLoad = serviceEntry
                                .getValue();
                        for (Map.Entry<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> srcRegionEntry : serviceLoad
                                .entrySet()) {
                            final NodeIdentifier srcNode = srcRegionEntry.getKey();
                            final RegionIdentifier srcRegion;
                            if (controllerCache.containsKey(srcNode)) {
                                srcRegion = controllerCache.get(srcNode).getRegionIdentifier();
                            } else if (clientCache.containsKey(srcNode)) {
                                srcRegion = clientCache.get(srcNode).getRegionIdentifier();
                            } else {
                                srcRegion = null;
                                LOGGER.warn("Unable to find region for {}", srcNode);
                            }

                            if (null != srcRegion) {
                                final double value = srcRegionEntry.getValue().getOrDefault(attribute, 0D);
                                load.merge(srcRegion, value, Double::sum);
                                totalLoad += value;
                            } // found source region
                        } // foreach source region
                    } // foreach service
                } // if correct region
            } // foreach controller
        } // lock

        if (totalLoad > 0) {
            final double finalTotalLoad = totalLoad;
            final Map<RegionIdentifier, Double> loadPercentage = load.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / finalTotalLoad));

            return loadPercentage;
        } else {
            return Collections.emptyMap();
        }

    }

    /**
     * Compute the neighbors of a region.
     * 
     * @param region
     *            the region to find the neighbors of
     * @return a newly created set of the neighbors
     */
    public Set<RegionIdentifier> computeNeighborRegions(@Nonnull final RegionIdentifier region) {
        final Set<RegionIdentifier> neighbors = new HashSet<>();

        synchronized (controllerCache) {
            controllerCache.forEach((k, controller) -> {
                if (controller.getRegionIdentifier().equals(region)) {
                    controller.getNeighbors().forEach(nodeId -> {
                        final Controller neighbor = getControllerById(nodeId);
                        if (null != neighbor) {
                            if (!region.equals(neighbor.getRegionIdentifier())) {
                                neighbors.add(neighbor.getRegionIdentifier());
                            }
                        }
                    });
                }
            });
        }

        return neighbors;
    }

    @Override
    public void close() {
        stopSimulation();
    }

    private final Map<NodeIdentifier, Map<NodeIdentifier, LinkResourceManager>> linkResourceManagers = new HashMap<>();

    /* package */ LinkResourceManager getLinkResourceManager(@Nonnull final NetworkLink link)
            throws IllegalArgumentException {
        return getLinkResourceManager(link.getLeft().getNodeIdentifier(), link.getRight().getNodeIdentifier());
    }

    /**
     * 
     * @param one
     *            one end of the link
     * @param two
     *            the other end of the link
     * @throws IllegalArgumentException
     *             if the two identifiers are equal
     * 
     * @return the manager or null if it cannot be found
     */
    /* package */ LinkResourceManager getLinkResourceManager(@Nonnull final NodeIdentifier one,
            @Nonnull final NodeIdentifier two) throws IllegalArgumentException {
        final NodeIdentifier left;
        final NodeIdentifier right;
        final int compareResult = one.getName().compareTo(two.getName());
        if (compareResult < 0) {
            left = one;
            right = two;
        } else if (compareResult > 0) {
            left = two;
            right = one;
        } else {
            throw new IllegalArgumentException(
                    String.format("Links cannot go from a node to itself %s == %s", one, two));
        }

        return linkResourceManagers.getOrDefault(left, Collections.emptyMap()).getOrDefault(right, null);
    }

    /* package */ void addLinkResMgr(@Nonnull final LinkResourceManager lmgr) {
        linkResourceManagers.computeIfAbsent(lmgr.getReceiver(), k -> new HashMap<>()).put(lmgr.getTransmitter(), lmgr);
    }

    private final Map<NodeIdentifier, ContainerSim> containers = new HashMap<NodeIdentifier, ContainerSim>();

    /**
     * Find a particular container by it's ID.
     * 
     * @param name
     *            the container ID to look for
     * @return the container simulator or null if not found
     */
    public ContainerSim getContainerById(@Nonnull final NodeIdentifier name) {
        synchronized (containers) {
            return containers.get(name);
        }
    }

    /**
     * Register a container starting. This allows it to be looked up with
     * {@link #getContainerById(NodeIdentifier)}.
     * 
     * @param containerId
     *            the id to register
     * @param container
     *            the container simulator to register with the id
     * @throws IllegalArgumentException
     *             when the container ID is already used
     */
    public void registerContainer(@Nonnull final NodeIdentifier containerId, @Nonnull final ContainerSim container) {
        synchronized (containers) {
            LOGGER.trace("registerContainer '{}': {}", containerId, container);

            if (containers.containsKey(containerId)) {
                throw new IllegalArgumentException("The container ID " + containerId + " is already registered.");
            }

            containers.put(containerId, container);
        }
    }

    /**
     * Inverse of {@link #registerContainer(NodeIdentifier, ContainerSim)}.
     * 
     * @param containerId
     *            the id of the container to unregister
     */
    public void unregisterContainer(@Nonnull final NodeIdentifier containerId) {
        synchronized (containers) {
            LOGGER.trace("unregisterContainer '{}'", containerId);
            containers.remove(containerId);
        }
    }

    /**
     * Set the directory to output to.
     * 
     * @param outputDirectory
     *            the directory to output logs to, null to not output anything
     */
    public void setBaseOutputDirectory(final Path outputDirectory) {
        // Note: this assumes that ensureRegionalDNSExists() is never called
        // outside the constructor in a normal run

        globalDNS.setBaseOutputDirectory(outputDirectory);
        regionalDNS.forEach((k, sim) -> {
            sim.setBaseOutputDirectory(outputDirectory);
        });

    }

    private static final int NUM_ROUNDS_BETWEEN_NEIGHBOR_CONNECT_ATTEMPTS = 1;

    /**
     * Block until all {@link NetworkServer}s in the simulation have connected
     * to their neighbors.
     * 
     */
    public void waitForAllNodesToConnectToNeighbors() {
        boolean done = false;
        while (!done) {
            done = getAllControllers().stream().allMatch(c -> c.isApConnectedToAllNeighbors());
            if (!done) {
                getClock().waitForDuration(NUM_ROUNDS_BETWEEN_NEIGHBOR_CONNECT_ATTEMPTS);
            }
        }
    }

    // RegionLookupService
    @Override
    public RegionIdentifier getRegionForNode(@Nonnull final NodeIdentifier nodeId) {
        // check if it's a container first
        final ContainerSim container = getContainerById(nodeId);
        if (null != container) {
            return container.getParentNode().getRegionIdentifier();
        } else {
            return scenario.getRegionForNode(nodeId);
        }
    }
    // end RegionLookupService

    private void simulateFailures() {
        for (final NodeFailure failure : nodeFailures) {
            LOGGER.trace("Waiting for failure time {}", failure.time);
            getClock().waitUntilTime(failure.time);

            if (!running.get()) {
                LOGGER.debug("Exiting failure thread due to simulation shutdown");
                return;
            }

            final DnsNameIdentifier nodeId = new DnsNameIdentifier(failure.node);
            final Controller controller = getControllerById(nodeId);
            if (null != controller) {
                LOGGER.info("Stopping node {}", failure.node);
                getResourceManager(controller).stopSimulation();
                controller.stopExecuting();
                synchronized (graph) {
                    graph.removeVertex(controller);
                }
                synchronized (controllerCache) {
                    controllerCache.remove(nodeId);
                }
                LOGGER.info("Finished stopping node {}", failure.node);
            } else {
                LOGGER.info("Stopping container {}", failure.node);
                synchronized (containers) {
                    containers.remove(nodeId);
                    unregisterContainer(nodeId);
                }
                LOGGER.info("Finished stopping container {}", failure.node);
            }

            if (!running.get()) {
                LOGGER.debug("Exiting failure thread due to simulation shutdown");
                return;
            }
        }
    }

    private static final String NODE_FAILURES_FILENAME = "node-failures.json";

    @Nonnull
    private List<NodeFailure> loadNodeFailures(@Nonnull final Path path) throws IOException {
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }

        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            final List<NodeFailure> list = mapper.readValue(reader, new TypeReference<List<NodeFailure>>() {
            });

            Collections.sort(list, NodeFailureTimeCompare.INSTANCE);
            return list;
        }
    }

    /**
     * Check that the node failures are sane.
     */
    private boolean verifyNodeFailures() {
        boolean retval = true;
        final Set<String> seen = new HashSet<>();
        for (final NodeFailure failure : nodeFailures) {
            if (seen.contains(failure.node)) {
                LOGGER.error("The node {} shows up multiple times in the failures list", failure.node);
                retval = false;
            }

            final DnsNameIdentifier nodeId = new DnsNameIdentifier(failure.node);
            final Controller controller = getControllerById(nodeId);
            if (null == controller) {
                final ContainerSim container = getContainerById(nodeId);
                if (null == container) {
                    LOGGER.error("The node {} is not known to the simulation. Checked both node and container.",
                            failure.node);
                    retval = false;
                }
            } else if (controller.isRunDCOP()) {
                // can be removed later on when we have a way to handle failure
                // of our leaders
                LOGGER.error("The node {} is running DCOP, it cannot be marked as a failure.", failure.node);
                retval = false;
            } else if (controller.isRunRLG()) {
                // can be removed later on when we have a way to handle failure
                // of our leaders
                LOGGER.error("The node {} is running RLG, it cannot be marked as a failure.", failure.node);
                retval = false;
            } else if (controller.isHandleDnsChanges()) {
                // can be removed later on when we have a way to handle failure
                // of our leaders
                LOGGER.error("The node {} is running DNS, it cannot be marked as a failure.", failure.node);
                retval = false;
            } else if (controller.isGlobalLeader() && !AgentConfiguration.getInstance().isUseLeaderElection()) {
                LOGGER.error(
                        "The node {} is running the global leader and leader election is not enabled. Therefore it cannot be marked as a failure.",
                        failure.node);
                retval = false;
            }

            seen.add(failure.node);
        }

        return retval;
    }

    private static final class NodeFailureTimeCompare implements Comparator<NodeFailure> {
        public static final NodeFailureTimeCompare INSTANCE = new NodeFailureTimeCompare();

        @Override
        public int compare(final NodeFailure o1, final NodeFailure o2) {
            return Long.compare(o1.time, o2.time);
        }

    }

    // CHECKSTYLE:OFF JSON serialized value class
    /**
     * A simulated node failure.
     * 
     * @author jschewe
     *
     */
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD", justification = "Written by JSON parser")
    private static final class NodeFailure {
        /**
         * When should the node fail.
         */
        public long time;
        /**
         * Which node or container to make fail.
         */
        public String node;
    }
    // CHECKSTYLE:ON
}
