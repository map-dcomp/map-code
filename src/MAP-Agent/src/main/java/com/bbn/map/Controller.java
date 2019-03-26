/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018>, <Raytheon BBN Technologies>
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
package com.bbn.map;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.CloseableThreadContext;
import org.protelis.lang.datatype.DeviceUID;
import org.protelis.lang.datatype.Tuple;
import org.protelis.vm.ProtelisProgram;
import org.protelis.vm.impl.AbstractExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AbstractService.Status;
import com.bbn.map.ap.ApplicationManagerUtils;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.dcop.DCOPService;
import com.bbn.map.dcop.DcopInfoProvider;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.PlanTranslator;
import com.bbn.map.rlg.RLGService;
import com.bbn.map.rlg.RlgInfoProvider;
import com.bbn.map.rlg.RlgSharedInformation;
import com.bbn.protelis.networkresourcemanagement.ContainerIdentifier;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeLookupService;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionLookupService;
import com.bbn.protelis.networkresourcemanagement.RegionNodeState;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.RegionServiceState;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceManagerFactory;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.bbn.protelis.utils.VirtualClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;

import java8.util.Objects;

/**
 * The controller for a MAP Agent. This object starts the appropriate services
 * and makes data accessible to the various services.
 */
public class Controller extends NetworkServer implements DcopInfoProvider, RlgInfoProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

    private final NetworkServices networkServices;
    private final boolean allowDnsChanges;
    private final boolean enableDcop;
    private final boolean enableRlg;

    /**
     * Standard constructor for a controller. Sets <code>allowDnsChanges</code>
     * to true as well as <code>enableDcop</code> and <code>enableRlg</code>.
     * 
     * @param nodeLookupService
     *            passed to parent
     * @param regionLookupService
     *            passed to parent
     * @param program
     *            passed to parent
     * @param name
     *            passed to parent
     * @param networkServices
     *            Used to access network services such as DNS updating * @param
     *            managerFactory passed to parent
     * @param extraData
     *            passed to parent
     * @param managerFactory
     *            passed to parent
     * 
     * @see Controller#Controller(NodeLookupService, RegionLookupService,
     *      ProtelisProgram, NodeIdentifier, ResourceManagerFactory, Map,
     *      NetworkServices, boolean, boolean, boolean)
     */
    public Controller(@Nonnull final NodeLookupService nodeLookupService,
            @Nonnull final RegionLookupService regionLookupService,
            @Nonnull final ProtelisProgram program,
            @Nonnull final NodeIdentifier name,
            @Nonnull final ResourceManagerFactory<NetworkServer> managerFactory,
            @Nonnull final Map<String, Object> extraData,
            @Nonnull final NetworkServices networkServices) {
        this(nodeLookupService, regionLookupService, program, name, managerFactory, extraData, networkServices, true,
                true, true);
    }

    /**
     * Full constructor a controller.
     *
     * @param nodeLookupService
     *            passed to parent
     * @param regionLookupService
     *            passed to parent
     * @param program
     *            passed to parent
     * @param name
     *            passed to parent
     * @param networkServices
     *            Used to access network services such as DNS updating
     * @param allowDnsChanges
     *            if true (default) allow changes to the DNS based on the DCOP
     *            and RLG plans. This should only be false during testing.
     * @param enableDcop
     *            false if testing where DCOP should be disabled
     * @param enableRlg
     *            false if testing where RLG should be disabled
     * @param managerFactory
     *            passed to parent
     * @param extraData
     *            passed to parent
     * @see NetworkServer#NetworkServer(NodeLookupService, RegionLookupService,
     *      ProtelisProgram, NodeIdentifier, ResourceManagerFactory, Map)
     */
    public Controller(@Nonnull final NodeLookupService nodeLookupService,
            @Nonnull final RegionLookupService regionLookupService,
            @Nonnull final ProtelisProgram program,
            @Nonnull final NodeIdentifier name,
            @Nonnull final ResourceManagerFactory<NetworkServer> managerFactory,
            @Nonnull final Map<String, Object> extraData,
            @Nonnull final NetworkServices networkServices,
            final boolean allowDnsChanges,
            final boolean enableDcop,
            final boolean enableRlg) {
        super(nodeLookupService, regionLookupService, program, name, managerFactory, extraData);
        this.dcop = new DCOPService(name.getName(), this, this, AppMgrUtils.getApplicationManager());
        this.rlg = new RLGService(name.getName(), this, this, AppMgrUtils.getApplicationManager(), this, this);
        this.networkServices = networkServices;
        this.allowDnsChanges = allowDnsChanges;
        this.enableDcop = enableDcop;
        this.enableRlg = enableRlg;
        this.prevLoadBalancerPlan = null;
        this.prevRegionServiceState = null;

        setRunDCOP(ControllerProperties.isRunningDcop(extraData));
        setRunRLG(ControllerProperties.isRunningRlg(extraData));
        setHandleDnsChanges(ControllerProperties.isHandlingDnsChanges(extraData));

        setSleepTime(AgentConfiguration.getInstance().getApRoundDuration().toMillis());

        final Thread dumperThread = new Thread(() -> dumperWorker(), String.format("%s dumper", getName()));
        dumperThread.setDaemon(true);
        dumperThread.start();

    }

    private LoadBalancerPlan prevLoadBalancerPlan;
    private RegionServiceState prevRegionServiceState;

    @Override
    protected void postRunCycle() {
        if (!isExecuting()) {
            // don't do anything if we're not executing
            return;
        }

        if (runDCOP && !isDCOPRunning()) {
            startDCOP();
        } else if (!runDCOP && isDCOPRunning()) {
            stopDCOP();
        }

        if (runRLG && !isRLGRunning()) {
            startRLG();
        } else if (!runRLG && isRLGRunning()) {
            stopRLG();
        }

        if (allowDnsChanges && isHandleDnsChanges()) {
            LOGGER.trace("Checking for DNS changes in node: {}", getNodeIdentifier());

            final LoadBalancerPlan newLoadBalancerPlan = getNetworkState().getLoadBalancerPlan();
            final RegionServiceState newRegionServiceState = getRegionServiceState();

            // update the DNS if something has changed
            if (!newLoadBalancerPlan.equals(prevLoadBalancerPlan)
                    || !newRegionServiceState.equals(prevRegionServiceState)) {

                final ImmutableCollection<DnsRecord> newDnsEntries = networkServices.getPlanTranslator()
                        .convertToDns(newLoadBalancerPlan, newRegionServiceState);

                LOGGER.info("Found DNS changes in region {}. Replacing records with: {}", getRegionIdentifier(),
                        newDnsEntries);

                networkServices.getDnsUpdateService(getRegionIdentifier()).replaceAllRecords(newDnsEntries);
                prevLoadBalancerPlan = newLoadBalancerPlan;
                prevRegionServiceState = newRegionServiceState;

                LOGGER.trace("Storing LBPlan: {} services: {}", prevLoadBalancerPlan, prevRegionServiceState);
            } else {
                LOGGER.trace("No DNS update needed prevLBPlan: {} newLBPlan: {} prevServices: {} newServices: {}",
                        prevLoadBalancerPlan, newLoadBalancerPlan, prevRegionServiceState, newRegionServiceState);
            }
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("{} resource summary compute load: {}", getNodeIdentifier().getName(),
                    getNetworkState().getRegionSummary(EstimationWindow.SHORT).getServerLoad());
        }

        manageContainers();
    }

    /**
     * Start and stop containers as necessary.
     */
    private void manageContainers() {
        try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(getName())) {

            final ResourceManager resMgr = getResourceManager();

            final LoadBalancerPlan loadBalancerPlan = getNetworkState().getLoadBalancerPlan();

            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, Integer>> servicePlan = loadBalancerPlan
                    .getServicePlan();

            // stop services that are to stop
            loadBalancerPlan.getStopContainers().entrySet().stream().filter(e -> e.getKey().equals(getNodeIdentifier()))
                    .map(Map.Entry::getValue).flatMap(l -> l.stream()).forEach(container -> {

                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Stopping container {}", container);
                        }

                        if (!resMgr.stopService(container)) {
                            LOGGER.warn("Unable to stop service on container: {}", container);
                        }
                    });

            // check if need to start new instances of services
            final ServiceReport serviceReport = resMgr.getServiceReport();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Updating container to match {}", serviceReport);
            }

            // count current number of instances of each service running
            final Map<ServiceIdentifier<?>, Integer> numInstanceOfService = new HashMap<>();
            serviceReport.getServiceState().forEach((container, serviceState) -> {
                final ServiceState.Status status = serviceState.getStatus();
                if (ServiceState.Status.STARTING == status || ServiceState.Status.RUNNING == status) {
                    numInstanceOfService.merge(serviceState.getService(), 1, Integer::sum);
                }
            });

            // start services when there aren't enough running
            servicePlan.forEach((service, plan) -> {
                if (null == service) {
                    LOGGER.warn(
                            "Got null service in load balancer plan, this is invalid and this portion of it's plan will be ignored: {}",
                            plan);
                } else {
                    final int requestedInstances = plan.getOrDefault(getNodeIdentifier(), 0);
                    final int currentInstances = numInstanceOfService.getOrDefault(service, 0);
                    if (requestedInstances > currentInstances) {
                        final int numToAdd = requestedInstances - currentInstances;

                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Attempting to add {} instances of {} to {}. Requested: {} current: {}",
                                    numToAdd, service, getName(), requestedInstances, currentInstances);
                        }

                        for (int i = 0; i < numToAdd; ++i) {
                            final ContainerIdentifier cid = resMgr.startService(service,
                                    ApplicationManagerUtils.getContainerParameters(service));
                            if (null == cid) {
                                LOGGER.warn("Unable to allocate/start container for service {} on node {}", service,
                                        getNodeIdentifier());
                            }
                        }
                    }
                } // valid service
            });

        } // logger context
    }

    @Override
    protected void preStopExecuting() {
        stopDCOP();
        stopRLG();
    }

    private final DCOPService dcop;

    private void startDCOP() {
        if (enableDcop) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Starting DCOP");
            }
            dcop.startService();
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Attempt to start DCOP ignored because DCOP is disabled");
            }
        }
    }

    private void stopDCOP() {
        if (isDCOPRunning() && LOGGER.isInfoEnabled()) {
            LOGGER.info("Stopping DCOP");
        }
        dcop.stopService(AgentConfiguration.getInstance().getServiceShutdownWaitTime().toMillis());
    }

    /**
     * @return true if DCOP is to run on this controller and it is executing
     */
    public boolean isDCOPRunning() {
        return Status.RUNNING == dcop.getStatus();
    }

    private final RLGService rlg;

    private void startRLG() {
        if (enableRlg) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Starting RLG");
            }

            rlg.startService();
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Attempt to start RLG ignored because RLG is disabled");
            }
        }
    }

    private void stopRLG() {
        if (isRLGRunning() && LOGGER.isInfoEnabled()) {
            LOGGER.info("Stopping RLG");
        }

        rlg.stopService(AgentConfiguration.getInstance().getServiceShutdownWaitTime().toMillis());
    }

    /**
     * @return true if RLG is to run on this controller and it is executing
     */
    public boolean isRLGRunning() {
        return Status.RUNNING == rlg.getStatus();
    }

    private boolean runDCOP = false;

    /**
     * Defaults to false.
     * 
     * @param v
     *            should this controller run DCOP?
     */
    public void setRunDCOP(final boolean v) {
        runDCOP = v;
    }

    /**
     * @return If this controller should run DCOP
     */
    public boolean isRunDCOP() {
        return runDCOP;
    }

    private boolean runRLG = false;

    /**
     * Defaults to false.
     * 
     * @param v
     *            Should this controller run RLG?
     */
    public void setRunRLG(final boolean v) {
        runRLG = v;
    }

    /**
     * @return If this controller should run RLG
     */
    public boolean isRunRLG() {
        return runRLG;
    }

    /**
     * Allow AP to write debug messages to the logger.
     * 
     * @param str
     *            the string to write
     */
    public void debug(final String str) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(getName() + ": " + str);
        }
    }

    // ---- DCOP shared information
    private DcopSharedInformation localDcopSharedInformation = new DcopSharedInformation();

    /**
     * AP uses this to determine what to share with the rest of the network.
     * 
     * @return the DCOP shared information for this node
     */
    @Nonnull
    public DcopSharedInformation getLocalDcopSharedInformation() {
        return localDcopSharedInformation;
    }

    @Override
    public void setLocalDcopSharedInformation(@Nonnull final DcopSharedInformation v) {
        localDcopSharedInformation = v;
    }

    private ImmutableMap<RegionIdentifier, DcopSharedInformation> allDcopSharedInformation = ImmutableMap.of();

    /**
     * This method should be used by DCOP to get the most recent shared state.
     * 
     * @return the DCOP shared information
     */
    @Override
    @Nonnull
    public ImmutableMap<RegionIdentifier, DcopSharedInformation> getAllDcopSharedInformation() {
        return allDcopSharedInformation;
    }

    /**
     * This method is to be used by AP to set the new shared state.
     * 
     * @param newInfo
     *            the new DCOP shared information
     */
    public void setAllDcopSharedInformation(final Tuple newInfo) {
        ImmutableMap.Builder<RegionIdentifier, DcopSharedInformation> builder = ImmutableMap.builder();
        // TODO: this conversion seems super-awkward.
        for (Object entry : newInfo) {
            final Tuple pair = (Tuple) entry;
            final RegionIdentifier sourceRegion = (RegionIdentifier) pair.get(0);
            final Object value = pair.get(1);
            if (value instanceof DcopSharedInformation) {
                builder.put(sourceRegion, (DcopSharedInformation) value);
            } else if (value instanceof Tuple) {
                final Tuple valueT = (Tuple) value;
                if (valueT.isEmpty()) {
                    // ignore
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Got default DCOP shared value, ignoring: " + value);
                    }
                } else {
                    LOGGER.error("Got unexpected tuple as DCOP shared value, ignoring: " + value);
                }
            } else {
                LOGGER.error("Got unexpected value as DCOP shared value, ignoring: " + value);
            }
        }
        allDcopSharedInformation = builder.build();
    }

    // ---- end DCOP shared information

    // ---- RLG shared information

    private RlgSharedInformation localRlgSharedInformation = new RlgSharedInformation();

    /**
     * AP uses this to determine what to share with the rest of the network.
     * 
     * @return the RLG shared information for this node
     */
    @Nonnull
    public RlgSharedInformation getLocalRlgSharedInformation() {
        return localRlgSharedInformation;
    }

    @Override
    public void setLocalRlgSharedInformation(@Nonnull final RlgSharedInformation v) {
        localRlgSharedInformation = v;
    }

    private ImmutableMap<RegionIdentifier, RlgSharedInformation> allRlgSharedInformation = ImmutableMap.of();

    /**
     * This method should be used by RLG to get the most recent shared state.
     * 
     * @return the RLG shared information
     */
    @Override
    @Nonnull
    public ImmutableMap<RegionIdentifier, RlgSharedInformation> getAllRlgSharedInformation() {
        return allRlgSharedInformation;
    }

    /**
     * This method is to be used by AP to set the new shared state.
     * 
     * @param newInfo
     *            the new RLG shared information
     */
    public void setAllRlgSharedInformation(final Tuple newInfo) {
        ImmutableMap.Builder<RegionIdentifier, RlgSharedInformation> builder = ImmutableMap.builder();
        // TODO: this conversion seems super-awkward.
        for (Object entry : newInfo) {
            final Tuple pair = (Tuple) entry;
            final RegionIdentifier sourceRegion = (RegionIdentifier) pair.get(0);
            final Object value = pair.get(1);
            if (value instanceof RlgSharedInformation) {
                builder.put(sourceRegion, (RlgSharedInformation) value);
            } else if (value instanceof Tuple) {
                final Tuple valueT = (Tuple) value;
                if (valueT.isEmpty()) {
                    // ignore
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Got default RLG shared value, ignoring: " + value);
                    }
                } else {
                    LOGGER.error("Got unexpected tuple as RLG shared value, ignoring: " + value);
                }
            } else {
                LOGGER.error("Got unexpected value as RLG shared value, ignoring: " + value);
            }
        }
        allRlgSharedInformation = builder.build();
    }

    // ---- end RLG shared information

    private boolean handleDnsChanges = false;

    private void setHandleDnsChanges(final boolean v) {
        handleDnsChanges = v;
    }

    /**
     * The node handling DNS changes will watch for changes in the
     * {@link RegionPlan} and the {@link LoadBalancerPlan} and then use
     * {@link PlanTranslator} to convert the plans to {@link DnsRecord}s and
     * then push the changes into the DNS. <i>There needs to be at least 1 node
     * in each region handling DNS.</i>
     * 
     * @return if this node is handling DNS changes
     */
    public boolean isHandleDnsChanges() {
        return handleDnsChanges;
    }

    /**
     * Child context used for {@link NetworkServer#instance()}.
     */
    public class ChildContext extends AbstractExecutionContext {
        private Controller parent;

        /**
         * Create a child context.
         * 
         * @param parent
         *            the parent environment to get information from.
         */
        public ChildContext(final Controller parent) {
            super(parent.getExecutionEnvironment(), parent.getNetworkManager());
            this.parent = parent;
        }

        @Override
        public Number getCurrentTime() {
            return parent.getCurrentTime();
        }

        @Override
        public DeviceUID getDeviceUID() {
            return parent.getDeviceUID();
        }

        @Override
        public double nextRandomDouble() {
            return parent.nextRandomDouble();
        }

        @Override
        protected AbstractExecutionContext instance() {
            return new ChildContext(parent);
        }

        /**
         * Used by AP to access the methods in the controller. Simplifies the
         * workaround for how ChildContexts work.
         * 
         * @return parent
         * @see Controller#getController()
         */
        public Controller getController() {
            return parent;
        }
    }

    @Override
    protected AbstractExecutionContext instance() {
        return new ChildContext(this);
    }

    /**
     * Used by AP to access the methods in this object. Simplifies the
     * workaround for how ChildContexts work.
     * 
     * @return this
     */
    public Controller getController() {
        return this;
    }

    private Path baseOutputDirectory = null;

    /**
     * 
     * @return the base directory to write to, the node will create a directory
     *         within this directory to put it's state. May be null, in which
     *         case data will not be dumped, but an error will be logged.
     */
    public Path getBaseOutputDirectory() {
        synchronized (dumpPropertyLock) {
            return baseOutputDirectory;
        }
    }

    /**
     * 
     * @param v
     *            see {@link #getBaseOutputDirectory()}
     */
    public void setBaseOutputDirectory(final Path v) {
        synchronized (dumpPropertyLock) {
            baseOutputDirectory = v;
        }
    }

    /**
     * When dumping state the default interval to use.
     */
    public static final int DEFAULT_DUMP_INTERVAL_SECONDS = 10;
    private Duration dumpInterval = Duration.ofSeconds(DEFAULT_DUMP_INTERVAL_SECONDS);

    /**
     * 
     * @return the interval to dump state at
     * @see #isDumpState()
     */
    @Nonnull
    public Duration getDumpInterval() {
        synchronized (dumpPropertyLock) {
            return this.dumpInterval;
        }
    }

    /**
     * 
     * @param v
     *            see {@link #getDumpInterval()}
     */
    public void setDumpInterval(final Duration v) {
        synchronized (dumpPropertyLock) {
            this.dumpInterval = v;
        }
    }

    private boolean dumpState = false;
    // lock for the dump properties
    private final Object dumpPropertyLock = new Object();

    /**
     * 
     * @param v
     *            see {@link #isDumpState()}
     */
    public void setDumpState(final boolean v) {
        synchronized (dumpPropertyLock) {
            this.dumpState = v;
        }
    }

    /**
     * 
     * @return true if the current state of the node should be written out at
     *         regular intervals
     * @see #getDumpInterval()
     */
    public boolean isDumpState() {
        synchronized (dumpPropertyLock) {
            return this.dumpState;
        }
    }

    /**
     * 
     * @return an object writer that is configured for dumping state as JSON
     */
    public static ObjectWriter createDumpWriter() {
        final ObjectWriter mapper = new ObjectMapper()//
                .registerModule(new GuavaModule())//
                .registerModule(new ParameterNamesModule())//
                .registerModule(new Jdk8Module()) //
                .registerModule(new JavaTimeModule())//
                .writer()//
                .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)//
                .withDefaultPrettyPrinter();

        return mapper;
    }

    private void dumperWorker() {
        try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(getName())) {
            final VirtualClock clock = getResourceManager().getClock();
            clock.waitForClockStart();

            final ObjectWriter mapper = createDumpWriter();

            while (!clock.isShutdown()) {
                final Duration lDumpInterval = getDumpInterval();

                if (isDumpState()) {
                    final Path baseOutput = getBaseOutputDirectory();
                    if (null != baseOutput) {
                        final Path nodeOutputDirectory = baseOutput.resolve(getName());

                        final long now = clock.getCurrentTime();

                        final String timeDir = String.format("%06d", now);
                        final Path outputDir = nodeOutputDirectory.resolve(timeDir);

                        // create directory
                        final File outputDirFile = outputDir.toFile();
                        if (!outputDirFile.exists()) {
                            if (!outputDir.toFile().mkdirs()) {
                                LOGGER.error("Unable to create output directory {}, skipping output", outputDir);
                                continue;
                            }
                        }

                        if (outputDirFile.exists()) {
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("Dumping state to {}", outputDir);
                            }

                            try {
                                dumpCurrentState(outputDir, mapper);
                            } catch (final IOException e) {
                                LOGGER.error("Error writing current state, may have partial output", e);
                            }
                        } else {
                            LOGGER.error("'{}' was not created and does not exist. Skipping output.", outputDir);
                        }
                    } else {
                        LOGGER.error(
                                "Base output directory is null, yet dump state is true, skipping output of node state");
                    }

                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Waiting for {} ms", lDumpInterval.toMillis());
                    }
                    clock.waitForDuration(lDumpInterval.toMillis());
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Done waiting");
                    }

                } // if dumping state
            } // while running

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Dumper thread for {} exiting", getName());
            }
        } // end logger context
    }

    private void dumpCurrentState(@Nonnull final Path outputDir, @Nonnull final ObjectWriter mapper)
            throws IOException {

        // node state
        final Path nodeStateFilename = outputDir.resolve("state.json");
        try (BufferedWriter writer = Files.newBufferedWriter(nodeStateFilename, Charset.defaultCharset())) {
            final NodeState state = new NodeState(this);
            mapper.writeValue(writer, state);
        }

        final ResourceManager manager = getResourceManager();
        // resource report
        for (final EstimationWindow window : EstimationWindow.values()) {
            final Path resourceReportFilename = outputDir.resolve(String.format("resourceReport-%s.json", window));
            try (BufferedWriter writer = Files.newBufferedWriter(resourceReportFilename, Charset.defaultCharset())) {
                final ResourceReport report = manager.getCurrentResourceReport(window);
                mapper.writeValue(writer, report);
            }
        }

        if (isDCOPRunning()) {
            // ResourceSummary(LONG)
            final Path resourceSummaryFilename = outputDir
                    .resolve(String.format("resourceSummary-%s.json", EstimationWindow.LONG));
            try (BufferedWriter writer = Files.newBufferedWriter(resourceSummaryFilename, Charset.defaultCharset())) {
                final ResourceSummary summary = getNetworkState().getRegionSummary(EstimationWindow.LONG);
                mapper.writeValue(writer, summary);
            }

            // RegionPlan
            final Path regionPlanFilename = outputDir.resolve("regionPlan.json");
            try (BufferedWriter writer = Files.newBufferedWriter(regionPlanFilename, Charset.defaultCharset())) {
                final RegionPlan plan = getNetworkState().getRegionPlan();
                mapper.writeValue(writer, plan);
            }
        }

        if (isRLGRunning()) {
            // ResourceSummary(SHORT)
            final Path resourceSummaryFilename = outputDir
                    .resolve(String.format("resourceSummary-%s.json", EstimationWindow.SHORT));
            try (BufferedWriter writer = Files.newBufferedWriter(resourceSummaryFilename, Charset.defaultCharset())) {
                final ResourceSummary summary = getNetworkState().getRegionSummary(EstimationWindow.SHORT);
                mapper.writeValue(writer, summary);
            }

            // RegionPlan
            final Path regionPlanFilename = outputDir.resolve("regionPlan.json");
            try (BufferedWriter writer = Files.newBufferedWriter(regionPlanFilename, Charset.defaultCharset())) {
                final RegionPlan plan = getNetworkState().getRegionPlan();
                mapper.writeValue(writer, plan);
            }

            // RegionNodeState
            final Path regionNodeStateFilename = outputDir.resolve("regionNodeState.json");
            try (BufferedWriter writer = Files.newBufferedWriter(regionNodeStateFilename, Charset.defaultCharset())) {
                final RegionNodeState nodeState = getRegionNodeState();
                mapper.writeValue(writer, nodeState);
            }

            // LoadBalancerPlan
            final Path loadBalancerPlanFilename = outputDir.resolve("loadBalancerPlan.json");
            try (BufferedWriter writer = Files.newBufferedWriter(loadBalancerPlanFilename, Charset.defaultCharset())) {
                final LoadBalancerPlan plan = getNetworkState().getLoadBalancerPlan();
                mapper.writeValue(writer, plan);
            }
        }
    }

    /**
     * Used to output the basic node state to JSON. This class wraps a
     * {@link Controller} object and exposes only the properties that should be
     * output.
     * 
     * @author jschewe
     *
     */
    private static final class NodeState {
        private final Controller controller;

        /**
         * 
         * @param controller
         *            the controller to output data for
         */
        /* package */ NodeState(final Controller controller) {
            this.controller = controller;
        }

        /**
         * @return the region of the node
         */
        @SuppressWarnings("unused") // used by JSON output
        public RegionIdentifier getRegion() {
            return controller.getRegionIdentifier();
        }

        /**
         * @return the name of the node
         */
        @SuppressWarnings("unused") // used by JSON output
        public String getName() {
            return controller.getName();
        }

        /**
         * 
         * @return see {@link Controller#isRLGRunning()}
         */
        @SuppressWarnings("unused") // used by JSON output
        public boolean isRLG() {
            return controller.isRLGRunning();
        }

        /**
         * 
         * @return see {@link Controller#isDCOPRunning()}
         */
        @SuppressWarnings("unused") // used by JSON output
        public boolean isDCOP() {
            return controller.isDCOPRunning();
        }

        /**
         * 
         * @return see {@link Controller#isHandlingDnsChanges()}
         */
        @SuppressWarnings("unused") // used by JSON output
        public boolean isHandlingDnsChanges() {
            return controller.isHandleDnsChanges();
        }

        /**
         * 
         * @return the current value of the Protelis VM
         */
        @SuppressWarnings("unused") // used by JSON output
        public String getApDebugValue() {
            return Objects.toString(controller.getVM().getCurrentValue());
        }

    }

}
