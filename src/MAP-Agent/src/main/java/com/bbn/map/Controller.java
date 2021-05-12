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
package com.bbn.map;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.CloseableThreadContext;
import org.protelis.lang.datatype.Tuple;
import org.protelis.lang.datatype.impl.ArrayTupleImpl;
import org.protelis.vm.ProtelisProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AbstractService.Status;
import com.bbn.map.AgentConfiguration.LinkDelayAlgorithm;
import com.bbn.map.ap.ApLogger;
import com.bbn.map.ap.ImmutableDcopSharedInformation;
import com.bbn.map.ap.TotalDemand;
import com.bbn.map.ap.dcop.DcopDirectCommunicator;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.dcop.DCOPService;
import com.bbn.map.dcop.DcopInfoProvider;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.dns.PlanTranslator;
import com.bbn.map.rlg.RLGService;
import com.bbn.map.rlg.RlgInfoProvider;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.SimulationRunner;
import com.bbn.map.ta2.OverlayTopology;
import com.bbn.map.ta2.RegionalTopology;
import com.bbn.map.utils.JsonUtils;
import com.bbn.map.utils.MAPServices;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan.ContainerInfo;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeLookupService;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionLookupService;
import com.bbn.protelis.networkresourcemanagement.RegionNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.RegionServiceState;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceManagerFactory;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceReport;
import com.bbn.protelis.utils.ImmutableUtils;
import com.bbn.protelis.utils.VirtualClock;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The controller for a MAP Agent. This object starts the appropriate services
 * and makes data accessible to the various services.
 */
public class Controller extends NetworkServer implements DcopInfoProvider, RlgInfoProvider, ApLogger {

    /**
     * Event types that can show up in the event log.
     * 
     * @author jschewe
     *
     */
    public enum EventTypes {
        /**
         * A new DCOP plan has been published.
         */
        DCOP_PLAN_PLUBLIED,
        /**
         * A new RLG plan has been published.
         */
        RLG_PLAN_PUBLISHED,
        /**
         * The DNS has been updated successfully.
         */
        DNS_UPDATE_SUCCEEDED,
        /**
         * The DNS update failed.
         */
        DNS_UPDATE_FAILED;
    }

    private final Logger logger;
    private final Logger apLogger;
    private static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

    private final NetworkServices networkServices;
    private final boolean allowDnsChanges;
    private final boolean enableDcop;
    private final boolean enableRlg;

    private final DnsManagementThread dnsManagementThread;
    private final ContainerManagementThread containerManagementThread;

    private final ApplicationManagerApi applicationManager;

    private final NodeLookupService dcopNodeLookup;

    /**
     * Not null when running DCOP and using direct communication.
     */
    private DcopDirectCommunicator dcopDirect = null;

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
     * @param manager
     *            passed to parent
     * @param extraData
     *            passed to parent
     * @param dcopNodeLookup
     *            used to lookup nodes for DCOP direct communication
     * @see NetworkServer#NetworkServer(NodeLookupService, ProtelisProgram,
     *      NodeIdentifier, ResourceManagerFactory, Map)
     */
    @SuppressFBWarnings(value = "SC_START_IN_CTOR", justification = "starting dumper thread in the constructor is intentional")
    public Controller(@Nonnull final NodeLookupService nodeLookupService,
            @Nonnull final RegionLookupService regionLookupService,
            @Nonnull final ProtelisProgram program,
            @Nonnull final NodeIdentifier name,
            @Nonnull final ResourceManager<Controller> manager,
            @Nonnull final Map<String, Object> extraData,
            @Nonnull final NetworkServices networkServices,
            final boolean allowDnsChanges,
            final boolean enableDcop,
            final boolean enableRlg,
            final NodeLookupService dcopNodeLookup) {
        super(nodeLookupService, program, name, manager, extraData);
        this.regionLookupService = regionLookupService;
        this.logger = LoggerFactory.getLogger(Controller.class.getName() + "." + name);
        this.apLogger = LoggerFactory.getLogger("com.bbn.map.ap.program." + name);
        this.applicationManager = AppMgrUtils.getApplicationManager();

        this.dcop = new DCOPService(name.getName(), getRegionIdentifier(), this, this.applicationManager);
        this.rlg = new RLGService(name.getName(), getRegionIdentifier(), this, this.applicationManager);
        this.networkServices = networkServices;
        this.allowDnsChanges = allowDnsChanges;
        this.enableDcop = enableDcop
                && !AgentConfiguration.RlgAlgorithm.NO_MAP.equals(AgentConfiguration.getInstance().getRlgAlgorithm());
        this.enableRlg = enableRlg;
        this.dnsPrevLoadBalancerPlan = null;
        this.dnsPrevRegionServiceState = null;
        this.dcopNodeLookup = dcopNodeLookup;

        this.containerManagementThread = new ContainerManagementThread(this);
        this.containerManagementThread.start();

        this.dnsManagementThread = new DnsManagementThread(this);
        this.dnsManagementThread.start();

        setRunDCOP(ControllerProperties.isRunningDcop(extraData));
        setRunRLG(ControllerProperties.isRunningRlg(extraData));
        setHandleDnsChanges(ControllerProperties.isHandlingDnsChanges(extraData));

        setSleepTime(AgentConfiguration.getInstance().getApRoundDuration().toMillis());

        final Thread dumperThread = new Thread(() -> dumperWorker(), String.format("%s dumper", getName()));
        dumperThread.setDaemon(true);
        dumperThread.start();

        logger.info("Running git version {}", SimulationRunner.getGitVersionInformation());

        // setup starting state for DCOP messages. There MUST be an entry for
        // the local region
        setLocalDcopSharedInformation(new DcopSharedInformation());
    }

    private boolean globalLeader = false;

    /**
     * 
     * @return is this node the global leader
     */
    public boolean isGlobalLeader() {
        synchronized (this) {
            return globalLeader;
        }
    }

    /**
     * 
     * @param v
     *            see {@link #isGlobalLeader()}
     */
    public void setGlobalLeader(final boolean v) {
        synchronized (this) {
            globalLeader = v;
        }
    }

    /**
     * This is here for access from protelis.
     * 
     * @return {@link AgentConfiguration#isUseLeaderElection()}
     */
    public boolean isUseLeaderElection() {
        return AgentConfiguration.getInstance().isUseLeaderElection();
    }

    private LoadBalancerPlan dnsPrevLoadBalancerPlan;
    private RegionServiceState dnsPrevRegionServiceState;
    private ImmutableCollection<Pair<DnsRecord, Double>> prevDnsEntries;

    private final Object algorithmStartLock = new Object();

    private long algorithmStartTime = -1;
    private boolean algorithmsRunning = false;

    /**
     * Specify the time that the DCOP and RLG algorithms should start running.
     * 
     * @param time
     *            a time based on {@link ResourceManager#getClock()}
     */
    public void startAlgorithmsAt(final long time) {
        synchronized (algorithmStartLock) {
            algorithmStartTime = time;
        }
    }

    /**
     * 
     * @return {@link #startAlgorithmsAt(long)}
     */
    public long getAlgorithmStartTime() {
        synchronized (algorithmStartLock) {
            return algorithmStartTime;
        }
    }

    @Override
    protected void postRunCycle() {
        if (!isExecuting()) {
            // don't do anything if we're not executing
            return;
        }

        if (!algorithmsRunning) {
            final long algorithmStartTime = getAlgorithmStartTime();
            if (algorithmStartTime >= 0) {
                final long now = getResourceManager().getClock().getCurrentTime();
                if (algorithmStartTime <= now) {
                    // use this boolean to avoid needing to check the clock once
                    // the algorithms have started
                    algorithmsRunning = true;
                }
            }
        }

        if (algorithmsRunning) {
            // only run RLG and DCOP once told to start

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
        }

        if (allowDnsChanges && isHandleDnsChanges()) {
            this.dnsManagementThread.updateData(getNetworkState().getLoadBalancerPlan(), getRegionServiceState());
        }

        if (logger.isTraceEnabled()) {
            logger.trace("{} resource summary compute load: {}", getNodeIdentifier().getName(),
                    getDcopResourceSummary().getServerLoad());
        }

        this.containerManagementThread.updateData(getNetworkState().getLoadBalancerPlan());

        fetchServiceImages();
    }

    private void fetchServiceImages() {
        if (AgentConfiguration.ImageFetchAlgorithm.DCOP
                .equals(AgentConfiguration.getInstance().getImageFetchAlgorithm())) {
            final RegionPlan dcopPlan = getNetworkState().getRegionPlan();
            final ResourceManager<?> resMgr = getResourceManager();
            final RegionIdentifier region = getRegionIdentifier();

            dcopPlan.getPlan().entrySet().stream().filter(e -> e.getValue().getOrDefault(region, 0D) > 0)
                    .map(Map.Entry::getKey).forEach(service -> resMgr.fetchImage(service));
        }
    }

    private void updateDnsInformation(final LoadBalancerPlan newLoadBalancerPlan,
            final RegionServiceState newRegionServiceState) {
        logger.trace("Checking for DNS changes in node: {}", getNodeIdentifier());

        if (null == newLoadBalancerPlan || null == newRegionServiceState) {
            logger.warn("Got null rlg plan or service state, skipping DNS information update");
            return;
        }

        // update the DNS if something has changed
        if (!newLoadBalancerPlan.equals(dnsPrevLoadBalancerPlan)
                || !newRegionServiceState.equals(dnsPrevRegionServiceState)) {

            if (logger.isTraceEnabled()) {
                final ObjectWriter mapper = createDumpWriter();

                String prevLoadStr = null;
                try (StringWriter writer = new StringWriter()) {
                    mapper.writeValue(writer, dnsPrevLoadBalancerPlan);
                    prevLoadStr = writer.toString();
                } catch (final IOException e) {
                    logger.error("Unable to write out debug state", e);
                }

                String newLoadStr = null;
                try (StringWriter writer = new StringWriter()) {
                    mapper.writeValue(writer, newLoadBalancerPlan);
                    newLoadStr = writer.toString();
                } catch (final IOException e) {
                    logger.error("Unable to write out debug state", e);
                }

                String prevServiceStr = null;
                try (StringWriter writer = new StringWriter()) {
                    mapper.writeValue(writer, dnsPrevRegionServiceState);
                    prevServiceStr = writer.toString();
                } catch (final IOException e) {
                    logger.error("Unable to write out debug state", e);
                }

                String newServiceStr = null;
                try (StringWriter writer = new StringWriter()) {
                    mapper.writeValue(writer, newRegionServiceState);
                    newServiceStr = writer.toString();
                } catch (final IOException e) {
                    logger.error("Unable to write out debug state", e);
                }

                logger.trace("Differences prevLoad: {} newLoad: {} equal? {} prevService: {} newService: {} equal? {}",
                        prevLoadStr, //
                        newLoadStr, //
                        newLoadBalancerPlan.equals(dnsPrevLoadBalancerPlan), //
                        prevServiceStr, //
                        newServiceStr, //
                        !newRegionServiceState.equals(dnsPrevRegionServiceState));
            }

            // only recompute DNS records if an input has changed
            final ImmutableCollection<Pair<DnsRecord, Double>> newDnsEntries = networkServices.getPlanTranslator()
                    .convertToDns(newLoadBalancerPlan, newRegionServiceState);

            // only publish DNS record changes if the new records are different
            // than last published
            if (null != newDnsEntries && !newDnsEntries.equals(prevDnsEntries)) {

                logger.info("Execution {}, found DNS changes in region {}. Replacing records with: {}",
                        getExecutionCount(), getRegionIdentifier(), newDnsEntries);

                updateLocalNetworkAvailableServices(newDnsEntries);

                final boolean success = networkServices.getDnsUpdateService(getRegionIdentifier())
                        .replaceAllRecords(newDnsEntries);

                if (success) {
                    dnsPrevLoadBalancerPlan = newLoadBalancerPlan;
                    dnsPrevRegionServiceState = newRegionServiceState;
                    prevDnsEntries = newDnsEntries;

                    logger.trace("Storing LBPlan: {} services: {}", dnsPrevLoadBalancerPlan, dnsPrevRegionServiceState);
                } else {
                    logger.warn("Unable to update DNS, load balancer plan not instantiated.");
                }

                final String message = String.format("%s",
                        success ? EventTypes.DNS_UPDATE_SUCCEEDED : EventTypes.DNS_UPDATE_FAILED);
                writeEventLog(message);
            }

        } else {
            logger.trace("No DNS update needed prevLBPlan: {} newLBPlan: {} prevServices: {} newServices: {}",
                    dnsPrevLoadBalancerPlan, newLoadBalancerPlan, dnsPrevRegionServiceState, newRegionServiceState);
        }
    }

    /**
     * Protects regionAvailableServices and dnsEntries.
     */
    private final Object availableServicesLock = new Object();

    private NetworkAvailableServices localAvailableServices = new NetworkAvailableServices();
    /**
     * The most recently used dns entries.
     */
    private ImmutableCollection<Pair<DnsRecord, Double>> dnsEntries = ImmutableList.of();

    private void updateLocalNetworkAvailableServices(final ImmutableCollection<Pair<DnsRecord, Double>> newDnsEntries) {
        synchronized (availableServicesLock) {
            final MutableNetworkAvailableServices serviceInfo = new MutableNetworkAvailableServices();

            newDnsEntries.stream().filter(r -> r.getLeft() instanceof NameRecord)
                    .forEach(r -> serviceInfo.addService((NameRecord) r.getLeft()));

            logger.trace("Created available services {} from {}", serviceInfo, newDnsEntries);

            localAvailableServices = new NetworkAvailableServices(serviceInfo);
            dnsEntries = newDnsEntries;
        }
    }

    /**
     * Used by Protelis when sharing information.
     * 
     * @return the information that this node has about available services in
     *         the region
     */
    @Nonnull
    public NetworkAvailableServices getLocalNetworkAvailableServices() {
        synchronized (availableServicesLock) {
            return localAvailableServices;
        }
    }

    @GuardedBy("availableServicesLock")
    private NetworkAvailableServices allNetworkAvailableServices = new NetworkAvailableServices();

    /* package for testing */ NetworkAvailableServices getAllNetworkAvailableServices() {
        synchronized (availableServicesLock) {
            return allNetworkAvailableServices;
        }
    }

    /**
     * 
     * @param newData
     *            the information about what services are running in the network
     */
    public void setAllNetworkAvailableServices(@Nonnull final NetworkAvailableServices newData) {
        logger.trace("Received new network available services data: {}", newData);
        synchronized (availableServicesLock) {
            allNetworkAvailableServices = newData;
        }
    }

    /**
     * 
     * @param node
     *            the node to find the service running
     * @return the service or null if not known
     * @see NetworkAvailableServices#getServiceForNode(NodeIdentifier)
     */
    public ServiceIdentifier<?> getServiceForNode(final NodeIdentifier node) {
        synchronized (availableServicesLock) {
            return allNetworkAvailableServices.getServiceForNode(node);
        }
    }

    /**
     * The most recent RLG plan that was executed by manageContainers. Only the
     * portion for this node is stored.
     */
    private ImmutableCollection<ContainerInfo> prevContainerPlan = null;

    /**
     * Start and stop containers as necessary.
     */
    private void manageContainers(final LoadBalancerPlan rlgPlan) {

        try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(getName())) {
            if (null == rlgPlan) {
                logger.warn("Got null RLG plan, skipping container management");
                return;
            }

            final long time = getResourceManager().getClock().getCurrentTime();

            final ResourceManager<?> resMgr = getResourceManager();

            // check if the service plan for this node has changed
            final ImmutableCollection<ContainerInfo> nodeServicePlan = rlgPlan.getServicePlan()
                    .get(getNodeIdentifier());
            if (Objects.equals(prevContainerPlan, nodeServicePlan)) {
                return;
            }

            final long delay = time - rlgPlan.getTimestamp();

            logger.debug("manageContainers: Acting on new {} with timestamp {} at time {} after a delay of {} ms.",
                    LoadBalancerPlan.class.getSimpleName(), rlgPlan.getTimestamp(), time, delay);

            logger.trace("Manging containers RLG service plan for this node: {}", nodeServicePlan);

            if (null != nodeServicePlan) {
                final Map<ServiceIdentifier<?>, Collection<LoadBalancerPlan.ContainerInfo>> containersToStart = new HashMap<>();
                nodeServicePlan.forEach(containerInfo -> {
                    if (null == containerInfo.getId() && !containerInfo.isStop()) {
                        containersToStart.computeIfAbsent(containerInfo.getService(), k -> new LinkedList<>())
                                .add(containerInfo);
                    }
                });

                logger.trace("Containers to start: {}", containersToStart);

                // check if need to start new instances of services
                final ServiceReport serviceReport = resMgr.getServiceReport();
                serviceReport.getServiceState().forEach((container, serviceState) -> {
                    final Optional<LoadBalancerPlan.ContainerInfo> inPlan = nodeServicePlan.stream()
                            .filter(info -> container.equals(info.getId())).findAny();
                    if (!inPlan.isPresent()) {
                        // container is not in the plan, remove from the list of
                        // containers to start for the same service
                        final Collection<LoadBalancerPlan.ContainerInfo> toStart = containersToStart
                                .getOrDefault(serviceState.getService(), Collections.emptySet());
                        if (!toStart.isEmpty()) {
                            logger.trace(
                                    "{} is not in the plan and is running service {}. Removing from containers to start",
                                    container, serviceState.getService());

                            toStart.remove(toStart.stream().findAny().get());
                        }
                    } else {
                        // check that the service matches
                        if (!Objects.equals(inPlan.get().getService(), serviceState.getService())) {
                            logger.error(
                                    "Container {} is currently running service {}, however the RLG plan states that it should be running service {}. The service of a container cannot change.",
                                    container, serviceState.getService(), inPlan.get().getService());
                        }
                    }
                });
                logger.trace("After filtering for running containers: {}", containersToStart);

                // start the needed containers
                containersToStart.forEach((service, containerInfos) -> {
                    containerInfos.forEach(containerInfo -> {
                        final NodeIdentifier cid = resMgr.startService(containerInfo.getService(),
                                AppMgrUtils.getContainerParameters(containerInfo.getService()));
                        if (null == cid) {
                            logger.warn("Unable to allocate/start container for service {} on node {}",
                                    containerInfo.getService(), getNodeIdentifier());
                        } else {
                            logger.trace("Started container {}", cid, containerInfo.getService());
                        }
                    });
                });

                // stop containers requested to stop
                nodeServicePlan.forEach(containerInfo -> {
                    if (containerInfo.isStop()) {

                        if (logger.isTraceEnabled()) {
                            logger.trace("Stopping container {}", containerInfo.getId());
                        }

                        if (!resMgr.stopService(containerInfo.getId())) {
                            logger.warn("Unable to stop service on container: {}", containerInfo.getId());
                        }

                    }
                });
            } // service plan exists for this node

            // record that this plan has been executed
            prevContainerPlan = nodeServicePlan;
        } // logger node context
    }

    @Override
    protected void preStopExecuting() {
        stopDCOP();
        stopRLG();
        this.dnsManagementThread.stopExecuting();
        this.containerManagementThread.stopExecuting();
    }

    private final DCOPService dcop;

    private void startDCOP() {
        if (getDcopShareDirect()) {
            if (null == dcopDirect) {
                // execute even when DCOP is disabled to allow tests that depend
                // on
                // DCOP sharing without DCOP running.
                dcopDirect = new DcopDirectCommunicator(this, dcopNodeLookup, networkServices.getTA2Interface(),
                        networkServices.getMapOracle());
                dcopDirect.start();
            }
        }

        if (enableDcop) {
            logger.info("Starting DCOP");
            dcop.startService();
            logger.info("DCOP started");
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Attempt to start DCOP ignored because DCOP is disabled");
            }
        }
    }

    private void stopDCOP() {
        logger.info("Stopping DCOP");
        dcop.stopService(AgentConfiguration.getInstance().getServiceShutdownWaitTime().toMillis());
        if (null != dcopDirect) {
            dcopDirect.stop();
        }
        logger.info("DCOP stopped");
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
            logger.info("Starting RLG");

            rlg.startService();
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Attempt to start RLG ignored because RLG is disabled");
            }
        }
    }

    private void stopRLG() {
        logger.info("Stopping RLG");
        rlg.stopService(AgentConfiguration.getInstance().getServiceShutdownWaitTime().toMillis());
        logger.info("RLG stopped");
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

    // ----- ApLogger
    @Override
    public void apErrorMessage(final String str) {
        apLogger.error("{} ({}): {}", getName(), getExecutionCount(), str);
    }

    @Override
    public void apWarnMessage(final String str) {
        apLogger.warn("{} ({}): {}", getName(), getExecutionCount(), str);
    }

    @Override
    public void apInfoMessage(final String str) {
        apLogger.info("{} ({}): {}", getName(), getExecutionCount(), str);
    }

    @Override
    public void apDebugMessage(final String str) {
        apLogger.debug("{} ({}): {}", getName(), getExecutionCount(), str);
    }

    @Override
    public void apTraceMessage(final String str) {
        apLogger.trace("{} ({}): {}", getName(), getExecutionCount(), str);
    }
    // ----- end ApLogger

    // ---- DcopInfoProvider
    private final Object dcopSharedInformationLock = new Object();

    private ImmutableDcopSharedInformation localDcopSharedInformation;

    /**
     * AP uses this to determine what to share with the rest of the network.
     * 
     * @return the DCOP shared information for this node
     */
    @Nonnull
    public ImmutableDcopSharedInformation getLocalDcopSharedInformation() {
        synchronized (dcopSharedInformationLock) {
            logger.debug("Getting local DCOP information {}", localDcopSharedInformation);
            return localDcopSharedInformation;
        }
    }

    @Override
    public void setLocalDcopSharedInformation(@Nonnull final DcopSharedInformation v) {
        synchronized (dcopSharedInformationLock) {
            if (null != localDcopSharedInformation && localDcopSharedInformation.equivalentTo(v)) {
                logger.debug("Ignoring extra set of duplicate DCOP shared information. prev: {} new: {}",
                        localDcopSharedInformation, v);
                return;
            }
            logger.debug("New dcop shared information {} old: {}", v, localDcopSharedInformation);
            localDcopSharedInformation = new ImmutableDcopSharedInformation(v);
            logger.debug("Sharing DCOP information {}", localDcopSharedInformation);
        }
        if (getDcopShareDirect()) {
            // a controller should see it's own messages, this is consistent
            // with the AP implementation
            setDcopSharedInformation(getRegion(), getLocalDcopSharedInformation());

            if (null == dcopDirect) {
                logger.error(
                        "Using DCOP direct communication, but dcopDirect variable is null. Message will not be sent");
            } else {
                dcopDirect.shareDcopMessage(getLocalDcopSharedInformation());
            }
        }
    }

    private Map<RegionIdentifier, ImmutableDcopSharedInformation> allDcopSharedInformation = new HashMap<>();

    /**
     * This method should be used by DCOP to get the most recent shared state.
     * 
     * @return the DCOP shared information
     */
    @Override
    @Nonnull
    public ImmutableMap<RegionIdentifier, DcopSharedInformation> getAllDcopSharedInformation() {
        synchronized (dcopSharedInformationLock) {
            logger.debug("Returning all DCOP information {}", allDcopSharedInformation);

            final ImmutableMap.Builder<RegionIdentifier, DcopSharedInformation> builder = ImmutableMap.builder();
            allDcopSharedInformation.forEach((region, shared) -> {
                builder.put(region, shared.getMessage());
            });
            return builder.build();
        }
    }

    /**
     * Used by {@link DcopDirectCommunicator} to record shared DCOP information.
     * 
     * @param region
     *            the region that the data was received from
     * @param data
     *            the data received
     */
    public void setDcopSharedInformation(final RegionIdentifier region, final ImmutableDcopSharedInformation data) {
        synchronized (dcopSharedInformationLock) {
            allDcopSharedInformation.put(region, data);
        }
    }

    /**
     * This method is to be used by AP to set the new shared state.
     * 
     * @param newInfo
     *            the new DCOP shared information
     */
    public void setAllDcopSharedInformation(final Tuple newInfo) {
        final Map<RegionIdentifier, ImmutableDcopSharedInformation> builder = new HashMap<>();
        // TODO: this conversion seems super-awkward.
        for (Object entry : newInfo) {
            final Tuple pair = (Tuple) entry;
            final RegionIdentifier sourceRegion = (RegionIdentifier) pair.get(0);
            final Object value = pair.get(1);
            if (value instanceof ImmutableDcopSharedInformation) {
                // no need to copy as AP doesn't modify the object
                final ImmutableDcopSharedInformation msg = (ImmutableDcopSharedInformation) value;
                builder.put(sourceRegion, msg);
            } else if (value instanceof Tuple) {
                final Tuple valueT = (Tuple) value;
                if (valueT.isEmpty()) {
                    // ignore
                    if (logger.isTraceEnabled()) {
                        logger.trace("Got default DCOP shared value, ignoring: " + value);
                    }
                } else {
                    logger.error("Got unexpected tuple as DCOP shared value, ignoring: " + value);
                }
            } else {
                logger.error("Got unexpected value as DCOP shared value, ignoring: " + value);
            }
        }
        synchronized (dcopSharedInformationLock) {
            allDcopSharedInformation = builder;
            logger.debug("Set new DCOP shared information {}", allDcopSharedInformation);
        }
    }

    private RegionPlan prevDcopPlan = null;

    @Override
    public void publishDcopPlan(@Nonnull RegionPlan plan) {
        final long time = getResourceManager().getClock().getCurrentTime();
        plan.setTimestamp(time);

        getNetworkState().setRegionPlan(plan);
        notifyDcopPlanListeners(plan);
        if (!plan.equals(prevDcopPlan)) {
            validateDcopPlan(plan);

            final String message = String.format("%s", EventTypes.DCOP_PLAN_PLUBLIED);
            writeEventLog(message);

            logger.debug("Published new DCOP plan with time {} at round {}: {}", plan.getTimestamp(),
                    getExecutionCount(), plan);

            prevDcopPlan = plan;
        }
    }

    private static final double WEIGHT_SUM_TOLERANCE = 1E-6;

    @Override
    @Nonnull
    public RegionalTopology getRegionTopology() {
        return networkServices.getTA2Interface().getRegionTopology();
    }
    // ---- end DcopInfoProvider

    /**
     * Log warnings if the plan doesn't look sane.
     */
    private void validateDcopPlan(@Nonnull final RegionPlan plan) {
        plan.getPlan().forEach((service, servicePlan) -> {
            final double serviceWeightSum = servicePlan.entrySet().stream().mapToDouble(e -> e.getValue()).sum();
            if (Math.abs(1 - serviceWeightSum) > WEIGHT_SUM_TOLERANCE) {
                logger.warn("DCOP plan for region {} service {} has weight sum of {}, which is not 1",
                        plan.getRegion().getName(), service.getIdentifier(), serviceWeightSum);
            }
            if (MAPServices.UNPLANNED_SERVICES.contains(service)) {
                logger.warn("DCOP plan for region {} specifies the service {} which MAP does not control",
                        plan.getRegion().getName(), service.getIdentifier());
            }
        });
        if (plan.getPlan().isEmpty()) {
            logger.warn("DCOP plan for region {} is empty", plan.getRegion().getName());
        }
    }

    /**
     * Log warnings if the plan doesn't look sane.
     */
    private void validateRlgPlan(@Nonnull final LoadBalancerPlan plan) {
        // don't validate the overflow plan as that is a DCOP plan that has been
        // filtered based on available services.

        if (plan.getServicePlan().isEmpty()) {
            logger.warn("RLG plan for region {} is empty", plan.getRegion().getName());
        } else {
            final ImmutableMap<NodeIdentifier, ImmutableCollection<LoadBalancerPlan.ContainerInfo>> servicePlan = plan
                    .getServicePlan();
            servicePlan.forEach((node, containerInfos) -> {
                containerInfos.forEach(info -> {
                    if (null == info.getService()) {
                        logger.warn("RLG plan for node {} container {} has null service", node.getName(), info.getId());
                    } else if (MAPServices.UNPLANNED_SERVICES.contains(info.getService())) {
                        logger.warn(
                                "RLG plan for node {} container {} specifies the service {} which MAP does not control",
                                plan.getRegion().getName(), info.getService().getIdentifier());
                    }
                }); // foreach info
            }); // foreach node
        }
    }

    private final Object dcopListenerLock = new Object();
    private final List<Consumer<RegionPlan>> dcopListeners = new LinkedList<>();

    /**
     * Used to allow tests to know when a plan has been published.
     * 
     * @param callback
     *            executed when a plan is published
     */
    public void addDcopPlanListener(final Consumer<RegionPlan> callback) {
        synchronized (dcopListenerLock) {
            dcopListeners.add(callback);
        }
    }

    /**
     * @param callback
     *            the listener to remove
     */
    public void removeDcopPlanListener(final Consumer<RegionPlan> callback) {
        synchronized (dcopListenerLock) {
            dcopListeners.remove(callback);
        }
    }

    private void notifyDcopPlanListeners(final RegionPlan plan) {
        synchronized (dcopListenerLock) {
            dcopListeners.forEach(callback -> callback.accept(plan));
        }
    }

    // ----- RLGInfoProvider
    @Override
    @Nonnull
    public RegionIdentifier getRegion() {
        return getRegionIdentifier();
    }

    private final Object dcopDataLock = new Object();

    private ResourceSummary dcopResourceSummary = null;

    @Override
    @Nonnull
    public ResourceSummary getDcopResourceSummary() {
        synchronized (dcopDataLock) {
            if (null == dcopResourceSummary) {
                final ImmutableSet<ResourceReport> reports = getDcopResourceReports();
                if (reports.isEmpty()) {
                    dcopResourceSummary = getNullSummary(getRegion(), EstimationWindow.LONG);
                } else {
                    dcopResourceSummary = computeResourceSummary(getRegion(), regionLookupService,
                            getDcopResourceReports());
                }
            }

            logResourceSummaryDelay("DCOP", dcopResourceSummary);

            return dcopResourceSummary;
        }
    }

    private void logResourceSummaryDelay(final String label, final ResourceSummary summary) {
        if (logger.isDebugEnabled()) {
            final long now = getResourceManager().getClock().getCurrentTime();

            final long minDelay = now - summary.getMinTimestamp();
            final long maxDelay = now - summary.getMaxTimestamp();

            logger.debug(
                    "ResourceSummary {}: Retrieving {} with timestamp range {}-{} at time {} after delay range of {}-{} ms",
                    label, ResourceSummary.class.getSimpleName(), summary.getMinTimestamp(), summary.getMaxTimestamp(),
                    now, minDelay, maxDelay);
        }
    }

    @Override
    @Nonnull
    public RegionPlan getDcopPlan() {
        final long now = getResourceManager().getClock().getCurrentTime();

        final RegionPlan plan = getNetworkState().getRegionPlan();
        final long delay = now - plan.getTimestamp();

        logger.debug("getDcopPlan: Retrieving {} with timestamp {} at time {} after delay of {} ms",
                RegionPlan.class.getSimpleName(), plan.getTimestamp(), now, delay);

        return plan;
    }

    @Override
    @Nonnull
    public LoadBalancerPlan getRlgPlan() {
        return getNetworkState().getLoadBalancerPlan();
    }

    private LoadBalancerPlan prevRlgPlan = null;

    @Override
    public void publishRlgPlan(@Nonnull LoadBalancerPlan plan) {
        final long time = getResourceManager().getClock().getCurrentTime();
        plan.setTimestamp(time);

        getNetworkState().setLoadBalancerPlan(plan);

        notifyRlgPlanListeners(plan);

        if (!plan.equals(prevRlgPlan)) {
            validateRlgPlan(plan);

            final String message = String.format("%s", EventTypes.RLG_PLAN_PUBLISHED);

            writeEventLog(message);

            logger.debug("Published new RLG plan with timestamp {} at round {}: {}", plan.getTimestamp(),
                    getExecutionCount(), plan);

            prevRlgPlan = plan;
        }
    }

    @Override
    @Nonnull
    public OverlayTopology getCurrentRegionTopology() {
        return networkServices.getTA2Interface().getOverlay(getRegionIdentifier());
    }

    @Override
    @Nonnull
    public ImmutableSet<ResourceReport> getRlgResourceReports() {
        synchronized (rlgDataLock) {
            return rlgResourceReports;
        }
    }

    private final Object rlgDataLock = new Object();
    private ResourceSummary rlgResourceSummary = null;

    @Override
    @Nonnull
    public ResourceSummary getRlgResourceSummary() {
        synchronized (rlgDataLock) {
            if (null == rlgResourceSummary) {
                final ImmutableSet<ResourceReport> reports = getRlgResourceReports();
                if (reports.isEmpty()) {
                    rlgResourceSummary = getNullSummary(getRegion(), EstimationWindow.SHORT);
                } else {
                    rlgResourceSummary = computeResourceSummary(getRegion(), regionLookupService, reports);
                }
            }

            logResourceSummaryDelay("RLG", rlgResourceSummary);

            return rlgResourceSummary;
        }
    }

    // ---- end RLGInfoProvider

    private ImmutableSet<ResourceReport> rlgResourceReports = ImmutableSet.of();

    /**
     * Called from AP.
     * 
     * @param tuple
     *            the resource reports
     */
    public void setRlgResourceReports(final Tuple tuple) {
        synchronized (rlgDataLock) {
            rlgResourceReports = apResourceReportsToImmutableSet(tuple);
            rlgResourceSummary = null;
        }
    }

    private ImmutableSet<ResourceReport> dcopResourceReports = ImmutableSet.of();

    /**
     * Called from AP.
     * 
     * @param tuple
     *            the resource reports
     */
    public void setDcopResourceReports(final Tuple tuple) {
        synchronized (dcopDataLock) {
            dcopResourceReports = apResourceReportsToImmutableSet(tuple);
            localTotalDemand = null;
            dcopResourceSummary = null;
        }
    }

    private ImmutableSet<ResourceReport> getDcopResourceReports() {
        synchronized (dcopDataLock) {
            return dcopResourceReports;
        }
    }

    private ImmutableSet<ResourceReport> apResourceReportsToImmutableSet(final Tuple tuple) {
        final long time = getResourceManager().getClock().getCurrentTime();

        if (logger.isTraceEnabled()) {
            logger.trace("Setting region resource reports. Region: " + getRegionIdentifier());
        }

        final Map<NodeIdentifier, ResourceReport> reportMap = new HashMap<>();
        for (final Object entry : tuple) {
            final ResourceReport report = (ResourceReport) entry;
            final long propagationDelay = time - report.getTimestamp();

            logger.debug(
                    "Received ResourceReport with timestamp {} from {} at time {} after a propagation delay of {} ms.",
                    report.getTimestamp(), report.getNodeName(), time, propagationDelay);

            if (reportMap.containsKey(report.getNodeName())) {
                final long oneTimestamp = reportMap.get(report.getNodeName()).getTimestamp();
                final long twoTimestamp = report.getTimestamp();
                logger.warn(
                        "Saw duplicate resource report from {}, using the one with the latest timestamp. timestamp one: {} timestamp two: {}",
                        report.getNodeName(), oneTimestamp, twoTimestamp);
                if (oneTimestamp < twoTimestamp) {
                    reportMap.put(report.getNodeName(), report);
                }
            } else {
                reportMap.put(report.getNodeName(), report);
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Adding report for " + report.getNodeName());
            }
        }

        return ImmutableSet.copyOf(reportMap.values());
    }

    private final Object rlgListenerLock = new Object();
    private final List<Consumer<LoadBalancerPlan>> rlgListeners = new LinkedList<>();

    /**
     * Used to allow tests to know when a plan has been published.
     * 
     * @param callback
     *            executed when a plan is published
     */
    public void addRlgPlanListener(final Consumer<LoadBalancerPlan> callback) {
        synchronized (rlgListenerLock) {
            rlgListeners.add(callback);
        }
    }

    /**
     * @param callback
     *            the listener to remove
     */
    public void removeRlgPlanListener(final Consumer<LoadBalancerPlan> callback) {
        synchronized (rlgListenerLock) {
            rlgListeners.remove(callback);
        }
    }

    private void notifyRlgPlanListeners(final LoadBalancerPlan plan) {
        synchronized (rlgListenerLock) {
            rlgListeners.forEach(callback -> callback.accept(plan));
        }
    }

    private final RegionLookupService regionLookupService;

    /**
     * Public visibility for testing only.
     * 
     * @param thisRegion
     *            the region for the summary
     * @param regionLookupService
     *            how to convert nodes to regions
     * @param resourceReports
     *            the reports to summarize
     * @return the summary
     * @throws IllegalArgumentException
     *             if {@code resourceReports} is empty or contains reports with
     *             different windows
     */
    @Nonnull
    public static ResourceSummary computeResourceSummary(final RegionIdentifier thisRegion,
            final RegionLookupService regionLookupService,
            final ImmutableSet<ResourceReport> resourceReports) {

        if (resourceReports.isEmpty()) {
            throw new IllegalArgumentException("No reports to summarize");
        }

        final Set<EstimationWindow> windows = resourceReports.stream().map(ResourceReport::getDemandEstimationWindow)
                .collect(Collectors.toSet());
        if (windows.size() > 1) {
            throw new IllegalArgumentException("All resource reports must have the same estimation window");
        }
        final EstimationWindow window = windows.iterator().next();

        long minTimestamp = Long.MAX_VALUE;
        long maxTimestamp = Long.MIN_VALUE;

        final Map<NodeAttribute, Double> serverCapacity = new HashMap<>();
        final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Map<NodeAttribute, Double>>> serverLoad = new HashMap<>();
        final Map<ServiceIdentifier<?>, Map<RegionIdentifier, Map<NodeAttribute, Double>>> serverDemand = new HashMap<>();
        final Map<ServiceIdentifier<?>, Integer> serverAverageProcessingTimeCount = new HashMap<>();
        final Map<ServiceIdentifier<?>, Double> serverAverageProcessingTimeSum = new HashMap<>();
        final Map<RegionIdentifier, Map<RegionNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> networkLoad = new HashMap<>();
        final Map<RegionIdentifier, Map<RegionNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> networkDemand = new HashMap<>();
        int maximumServiceContainers = 0;
        int allocatedServiceContainers = 0;

        final Map<RegionIdentifier, Double> linkDelayAvgSum = new HashMap<>();
        final Map<RegionIdentifier, Integer> linkDelayAvgCount = new HashMap<>();
        final Map<RegionIdentifier, Double> linkDelayMin = new HashMap<>();
        final Map<RegionIdentifier, Map<LinkAttribute, Double>> networkCapacity = new HashMap<>();

        for (final ResourceReport report : resourceReports) {
            minTimestamp = Math.min(minTimestamp, report.getTimestamp());
            maxTimestamp = Math.max(maxTimestamp, report.getTimestamp());

            // serverCapacity
            report.getNodeComputeCapacity().forEach((attr, value) -> {
                serverCapacity.merge(attr, value, Double::sum);
            });

            // serverLoad
            report.getComputeLoad().forEach((service, serviceData) -> {
                final Map<RegionIdentifier, Map<NodeAttribute, Double>> mergedServiceData = serverLoad
                        .computeIfAbsent(service, k -> new HashMap<>());
                serviceData.forEach((sourceNode, nodeData) -> {
                    final RegionIdentifier sourceRegion = regionLookupService.getRegionForNode(sourceNode);

                    final Map<NodeAttribute, Double> mergedNodeData = mergedServiceData.computeIfAbsent(sourceRegion,
                            k -> new HashMap<>());
                    nodeData.forEach((attr, value) -> {
                        mergedNodeData.merge(attr, value, Double::sum);
                    });
                });
            });

            // serverDemand
            report.getComputeDemand().forEach((service, serviceData) -> {
                final Map<RegionIdentifier, Map<NodeAttribute, Double>> mergedServiceData = serverDemand
                        .computeIfAbsent(service, k -> new HashMap<>());
                serviceData.forEach((sourceNode, nodeData) -> {
                    final RegionIdentifier sourceRegion = regionLookupService.getRegionForNode(sourceNode);

                    final Map<NodeAttribute, Double> mergedNodeData = mergedServiceData.computeIfAbsent(sourceRegion,
                            k -> new HashMap<>());
                    nodeData.forEach((attr, value) -> {
                        mergedNodeData.merge(attr, value, Double::sum);
                    });
                });
            });

            // average processing time
            report.getAverageProcessingTime().forEach((service, avg) -> {
                serverAverageProcessingTimeSum.merge(service, avg, Double::sum);
                serverAverageProcessingTimeCount.merge(service, 1, Integer::sum);
            });

            // use node network capacity as the summaries don't care about the
            // containers
            convertInterfaceToRegion(thisRegion, regionLookupService, report.getNetworkCapacity(), networkCapacity,
                    linkDelayAvgSum, linkDelayAvgCount, linkDelayMin);

            final Set<NodeIdentifier> containersOnReportNode = getContainersRunningOnReportNode(report);

            // network load
            mergeNetworkLoadDemand(thisRegion, report.getNodeName(), containersOnReportNode, regionLookupService,
                    report.getNetworkLoad(), networkLoad);

            // network demand
            mergeNetworkLoadDemand(thisRegion, report.getNodeName(), containersOnReportNode, regionLookupService,
                    report.getNetworkDemand(), networkDemand);

            maximumServiceContainers += report.getMaximumServiceContainers();
            allocatedServiceContainers += report.getAllocatedServiceContainers();
        }

        // add in link delay attribute
        final LinkDelayAlgorithm linkDelayAlgorithm = AgentConfiguration.getInstance().getLinkDelayAlgorithm();
        networkCapacity.forEach((region, regionData) -> {
            if (linkDelayAvgCount.containsKey(region)) {
                final double linkDelay;
                switch (linkDelayAlgorithm) {
                case AVERAGE:
                    linkDelay = linkDelayAvgSum.getOrDefault(region, 0D) / linkDelayAvgCount.getOrDefault(region, 0);
                    break;
                case MINIMUM:
                    linkDelay = linkDelayMin.getOrDefault(region, 0D);
                    break;
                default:
                    throw new RuntimeException("Unknown link delay algorithm " + linkDelayAlgorithm);
                }

                regionData.put(LinkAttribute.DELAY, linkDelay);
            }
        });

        // compute after processing time
        final ImmutableMap.Builder<ServiceIdentifier<?>, Double> serverAverageProcessingTime = ImmutableMap.builder();
        serverAverageProcessingTimeCount.forEach((service, count) -> {
            final double sum = serverAverageProcessingTimeSum.getOrDefault(service, 0D);
            if (sum > 0 && count > 0) {
                final double average = sum / count;
                serverAverageProcessingTime.put(service, average);
            }
        });

        final ResourceSummary summary = new ResourceSummary(thisRegion, minTimestamp, maxTimestamp, window, //
                ImmutableMap.copyOf(serverCapacity), ImmutableUtils.makeImmutableMap3(serverLoad),
                ImmutableUtils.makeImmutableMap3(serverDemand), serverAverageProcessingTime.build(), //
                ImmutableUtils.makeImmutableMap2(networkCapacity), ImmutableUtils.makeImmutableMap4(networkLoad),
                ImmutableUtils.makeImmutableMap4(networkDemand), //
                maximumServiceContainers, allocatedServiceContainers);
        return summary;

    }

    /**
     * Create a null summary object. Is used when there is no resource reports
     * to summarize. Uses {@link ResourceReport#NULL_TIMESTAMP} as the minimum
     * and maximum timestamps.
     * 
     * @param region
     *            the region
     * @param estimationWindow
     *            the window over which demand is estimated
     * @return empty summary for a region
     */
    public static ResourceSummary getNullSummary(@Nonnull final RegionIdentifier region,
            @Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, ImmutableMap<NodeAttribute, Double>>> serverLoad = ImmutableMap
                .of();
        final ImmutableMap<NodeAttribute, Double> serverCapacity = ImmutableMap.of();
        final ImmutableMap<RegionIdentifier, ImmutableMap<LinkAttribute, Double>> networkCapacity = ImmutableMap.of();
        final ImmutableMap<RegionIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> networkLoad = ImmutableMap
                .of();

        return new ResourceSummary(region, ResourceReport.NULL_TIMESTAMP, ResourceReport.NULL_TIMESTAMP,
                estimationWindow, serverCapacity, serverLoad, serverLoad, ImmutableMap.of(), networkCapacity,
                networkLoad, networkLoad, 0, 0);
    }

    private static Set<NodeIdentifier> getContainersRunningOnReportNode(final ResourceReport report) {
        return report.getContainerReports().entrySet().stream() //
                .map(Map.Entry::getValue) //
                .map(ContainerResourceReport::getContainerName) //
                .collect(Collectors.toSet());
    }

    private static void mergeNetworkLoadDemand(RegionIdentifier thisRegion,
            final NodeIdentifier reportNode,
            final Set<NodeIdentifier> containersOnReportNode,
            final RegionLookupService nodeToRegion,
            final ImmutableMap<InterfaceIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> source,
            final Map<RegionIdentifier, Map<RegionNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> dest) {

        source.forEach((neighborInterface, neighborData) -> {

            final Set<RegionIdentifier> regions = neighborInterface.getNeighbors().stream()
                    .map(nodeToRegion::getRegionForNode).collect(Collectors.toSet());

            regions.forEach(neighborRegion -> {
                if (null != neighborRegion) {
                    final Map<RegionNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> destNeighborData = dest
                            .computeIfAbsent(neighborRegion, k -> new HashMap<>());

                    neighborData.forEach((regionFlow, sourceData) -> {
                        final RegionIdentifier regionSource = regionFlow.getSource();
                        final RegionIdentifier regionDest = regionFlow.getDestination();
                        final RegionIdentifier serverRegion = regionFlow.getServer();

                        final Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>> destSourceData = destNeighborData
                                .computeIfAbsent(regionFlow, k -> new HashMap<>());

                        final boolean includeBandwidth = true; // handled in
                                                               // collection now
                                                               // that we
                                                               // collect at the
                                                               // region level

                        sourceData.forEach((service, serviceData) -> {
                            final Map<LinkAttribute, Double> destServiceData = destSourceData.computeIfAbsent(service,
                                    k -> new HashMap<>());

                            serviceData.forEach((attr, value) -> {
                                if (includeBandwidth || (!LinkAttribute.DATARATE_TX.equals(attr)
                                        && !LinkAttribute.DATARATE_RX.equals(attr))) {
                                    destServiceData.merge(attr, value, Double::sum);
                                }
                            }); // foreach attribute

                        }); // foreach service
                    }); // foreach source
                } else {
                    LOGGER.warn("Unable to find region for neighbor a node in {}, found {}",
                            neighborInterface.getNeighbors(), regions);
                }
            });

        }); // foreach neighbor
    }

    /**
     * Convert the map of InterfaceIdentifiers to RegionIdentifiers.
     */
    private static <T> void convertInterfaceToRegion(final RegionIdentifier thisRegion,
            final RegionLookupService nodeToRegion,
            final ImmutableMap<InterfaceIdentifier, ImmutableMap<T, Double>> source,
            final Map<RegionIdentifier, Map<T, Double>> dest,
            final Map<RegionIdentifier, Double> linkDelayAvgSum,
            final Map<RegionIdentifier, Integer> linkDelayAvgCount,
            final Map<RegionIdentifier, Double> linkDelayMin) {

        source.forEach((ifce, v) -> {
            final Set<RegionIdentifier> regions = ifce.getNeighbors().stream().map(nodeToRegion::getRegionForNode)
                    .collect(Collectors.toSet());

            regions.forEach(region -> {
                if (null != region) {
                    final Map<T, Double> values = dest.computeIfAbsent(region, k -> new HashMap<>());
                    v.forEach((attr, value) -> {

                        if (LinkAttribute.DELAY.equals(attr)) {
                            if (!thisRegion.equals(region)) {
                                // consider link delay only when it's
                                // with another region
                                linkDelayAvgCount.merge(region, 1, Integer::sum);
                                linkDelayAvgSum.merge(region, value, Double::sum);
                                linkDelayMin.merge(region, value, Double::min);
                            }
                        } else {
                            values.merge(attr, value, Double::sum);
                        }
                    });
                } else {
                    LOGGER.warn("Unable to find region for a node in {} found: {}", ifce.getNeighbors(), regions);
                }
            });

        });
    }

    /**
     * Write to the event log if output is enabled. The current time and ap
     * execution round will be prepended to the message.
     * 
     * @param message
     *            the message to output
     */
    private void writeEventLog(final String message) {
        final String line = String.format("%d,%d,%s", getResourceManager().getClock().getCurrentTime(),
                getExecutionCount(), message);

        final Path nodeOutputDirectory = getNodeOutputDirectory();
        if (null != nodeOutputDirectory) {
            if (!Files.exists(nodeOutputDirectory)) {
                if (!nodeOutputDirectory.toFile().mkdirs()) {
                    logger.error("Unable to create output directory {}, skipping output", line);
                    return;
                }
            }

            final Path eventLogFile = nodeOutputDirectory.resolve("events.csv");

            try (BufferedWriter writer = Files.newBufferedWriter(eventLogFile, Charset.defaultCharset(),
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
                writer.write(line);
                writer.newLine();
            } catch (final IOException e) {
                logger.error("Error writing event '{}': {}", line, e.getMessage(), e);
            }
        } else {
            logger.debug("Not writing to event log. No output directory.");
        }
    }

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
        final ObjectWriter mapper = JsonUtils.getStandardMapObjectMapper()//
                .writer()//
                .withDefaultPrettyPrinter();

        return mapper;
    }

    /**
     * 
     * @return the path or null if no output should be generated
     */
    public Path getNodeOutputDirectory() {
        if (isDumpState()) {
            final Path baseOutput = getBaseOutputDirectory();
            if (null != baseOutput) {
                final Path nodeOutputDirectory = baseOutput.resolve(getName());

                return nodeOutputDirectory;
            } else {
                logger.error("Base output directory is null, yet dump state is true, skipping output of node state");
                return null;
            }
        } else {
            return null;
        }

    }

    private void dumpAgentConfiguration(final ObjectWriter mapper) {
        final Path nodeOutputDirectory = getNodeOutputDirectory();
        if (null != nodeOutputDirectory) {
            if (!Files.exists(nodeOutputDirectory)) {
                if (!nodeOutputDirectory.toFile().mkdirs()) {
                    logger.error("Unable to create output directory {}, skipping output of agent configuration",
                            nodeOutputDirectory);
                    return;
                }
            }

            // write out configuration
            final Path agentConfigurationFilename = nodeOutputDirectory.resolve("agent-configuration.json");
            try (BufferedWriter writer = Files.newBufferedWriter(agentConfigurationFilename,
                    Charset.defaultCharset())) {
                mapper.writeValue(writer, AgentConfiguration.getInstance());
            } catch (final IOException e) {
                logger.error("Error writing agent configuration", e);
            }
        }
    }

    private void dumperWorker() {
        try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(getName())) {
            final VirtualClock clock = getResourceManager().getClock();
            clock.waitForClockStart();

            final ObjectWriter mapper = createDumpWriter();

            boolean agentConfigWritten = false;

            while (!clock.isShutdown()) {
                if (isExecuting()) {
                    final Duration lDumpInterval = getDumpInterval();

                    if (isDumpState() && algorithmsRunning) {
                        final Path nodeOutputDirectory = getNodeOutputDirectory();
                        if (null != nodeOutputDirectory) {
                            if (!agentConfigWritten) {
                                dumpAgentConfiguration(mapper);
                                agentConfigWritten = true;
                            }

                            final long now = clock.getCurrentTime();

                            final String timeDir = String.format(Simulation.TIME_DIR_FORMAT, now);
                            final Path outputDir = nodeOutputDirectory.resolve(timeDir);

                            // create directory
                            final File outputDirFile = outputDir.toFile();
                            if (!outputDirFile.exists()) {
                                if (!outputDir.toFile().mkdirs()) {
                                    logger.error("Unable to create output directory {}, skipping output", outputDir);
                                    continue;
                                }
                            }

                            if (outputDirFile.exists()) {
                                if (logger.isTraceEnabled()) {
                                    logger.trace("Dumping state to {}", outputDir);
                                }

                                try {
                                    dumpCurrentState(outputDir, mapper);
                                } catch (final IOException e) {
                                    logger.error("Error writing current state, may have partial output", e);
                                }
                            } else {
                                logger.error("'{}' was not created and does not exist. Skipping output.", outputDir);
                            }
                        } else {
                            logger.warn("Told to dump state and dump directory is null");
                        }

                        if (logger.isTraceEnabled()) {
                            logger.trace("Waiting for {} ms", lDumpInterval.toMillis());
                        }
                        clock.waitForDuration(lDumpInterval.toMillis());
                        if (logger.isTraceEnabled()) {
                            logger.trace("Done waiting");
                        }
                    } // isDumpState
                } // is executing
            } // while running

            if (logger.isTraceEnabled()) {
                logger.trace("Dumper thread for {} exiting", getName());
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

        // available services for debugging issues looking up which services are
        // running where
        final Path availableServicesPath = outputDir.resolve("availableServices.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(availableServicesPath)) {
            synchronized (availableServicesLock) {
                writer.write(String.valueOf(allNetworkAvailableServices));
            }
        }

        final ResourceManager<?> manager = getResourceManager();
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
                final ResourceSummary summary = getDcopResourceSummary();
                mapper.writeValue(writer, summary);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(
                    outputDir.resolve(String.format("regionResourceReports-%s.json", EstimationWindow.LONG)),
                    Charset.defaultCharset())) {
                mapper.writeValue(writer, getDcopResourceReports());
            }

            // RegionPlan
            final Path regionPlanFilename = outputDir.resolve("regionPlan.json");
            try (BufferedWriter writer = Files.newBufferedWriter(regionPlanFilename, Charset.defaultCharset())) {
                final RegionPlan plan = getNetworkState().getRegionPlan();
                mapper.writeValue(writer, plan);
            }

            final Path totalDemandFilename = outputDir.resolve("totalDemand.json");
            try (BufferedWriter writer = Files.newBufferedWriter(totalDemandFilename, Charset.defaultCharset())) {
                mapper.writeValue(writer, globalTotalDemand);
            }

        }

        if (isRLGRunning()) {
            // ResourceSummary(SHORT)
            final Path resourceSummaryFilename = outputDir
                    .resolve(String.format("resourceSummary-%s.json", EstimationWindow.SHORT));
            try (BufferedWriter writer = Files.newBufferedWriter(resourceSummaryFilename, Charset.defaultCharset())) {
                final ResourceSummary summary = getRlgResourceSummary();
                mapper.writeValue(writer, summary);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(
                    outputDir.resolve(String.format("regionResourceReports-%s.json", EstimationWindow.SHORT)),
                    Charset.defaultCharset())) {
                mapper.writeValue(writer, getRlgResourceReports());
            }

            // RegionPlan
            final Path regionPlanFilename = outputDir.resolve("regionPlan.json");
            try (BufferedWriter writer = Files.newBufferedWriter(regionPlanFilename, Charset.defaultCharset())) {
                final RegionPlan plan = getNetworkState().getRegionPlan();
                mapper.writeValue(writer, plan);
            }
        }

        // LoadBalancerPlan
        final Path loadBalancerPlanFilename = outputDir.resolve("loadBalancerPlan.json");
        try (BufferedWriter writer = Files.newBufferedWriter(loadBalancerPlanFilename, Charset.defaultCharset())) {
            final LoadBalancerPlan plan = getNetworkState().getLoadBalancerPlan();
            mapper.writeValue(writer, plan);
        }

        if (isHandleDnsChanges()) {
            synchronized (availableServicesLock) {
                final Path filename = outputDir.resolve("dns-records.json");
                try (BufferedWriter writer = Files.newBufferedWriter(filename, Charset.defaultCharset())) {
                    mapper.writeValue(writer, dnsEntries);
                }
            }

            final Path filename = outputDir.resolve("regionServiceState.json");
            try (BufferedWriter writer = Files.newBufferedWriter(filename, Charset.defaultCharset())) {
                mapper.writeValue(writer, getRegionServiceState());
            }

        }
    }

    /**
     * Used to output the basic node state to JSON. This class wraps a
     * {@link Controller} object and exposes only the properties that should be
     * output. The properties are copied and will not change after an instance
     * is constructed.
     * 
     * @author jschewe
     *
     */
    public static final class NodeState {
        private final RegionIdentifier region;
        private final String name;
        private final boolean rlg; // if RLG is runnning
        private final boolean dcop; // if DCOP is running
        private final boolean handlingDnsChanges;
        private final String apDebugValue;
        private final long apExecutionCount;
        private final boolean globalLeader;

        /**
         * 
         * @param controller
         *            the controller to output data for. Properties are copied
         *            from the {@link Controller} and stored for later output.
         */
        /* package */ NodeState(final Controller controller) {
            region = controller.getRegionIdentifier();
            name = controller.getName();
            rlg = controller.isRLGRunning();
            dcop = controller.isDCOPRunning();
            handlingDnsChanges = controller.isHandleDnsChanges();
            apDebugValue = Objects.toString(controller.getVM().getCurrentValue());
            apExecutionCount = controller.getExecutionCount();
            globalLeader = controller.isGlobalLeader();
        }

        /**
         * 
         * @param region
         *            the region of the node
         * @param name
         *            the name of the node
         * @param rlg
         *            see {@link Controller#isRLGRunning()}
         * @param dcop
         *            see {@link Controller#isDCOPRunning()}
         * @param handlingDnsChanges
         *            see {@link Controller#isHandlingDnsChanges()}
         * @param apDebugValue
         *            see {@link #getApDebugValue()}
         * @param apExecutionCount
         *            see {@link #getApExecutionCount()}
         * @param globalLeader
         *            see {@link #isGlobalLeader()}
         */
        public NodeState(@JsonProperty("region") final RegionIdentifier region,
                @JsonProperty("name") final String name,
                @JsonProperty("rlg") final boolean rlg,
                @JsonProperty("dcop") final boolean dcop,
                @JsonProperty("handlingDnsChanges") final boolean handlingDnsChanges,
                @JsonProperty("apDebugValue") final String apDebugValue,
                @JsonProperty("apExecutionCount") final long apExecutionCount,
                @JsonProperty("globalLeader") final boolean globalLeader) {
            this.region = region;
            this.name = name;
            this.rlg = rlg;
            this.dcop = dcop;
            this.handlingDnsChanges = handlingDnsChanges;
            this.apDebugValue = apDebugValue;
            this.apExecutionCount = apExecutionCount;
            this.globalLeader = globalLeader;
        }

        /**
         * @return the region of the node
         */
        @SuppressWarnings("unused") // used by JSON output
        public RegionIdentifier getRegion() {
            return region;
        }

        /**
         * @return the name of the node
         */
        @SuppressWarnings("unused") // used by JSON output
        public String getName() {
            return name;
        }

        /**
         * 
         * @return see {@link Controller#isRLGRunning()}
         */
        @SuppressWarnings("unused") // used by JSON output
        public boolean isRLG() {
            return rlg;
        }

        /**
         * 
         * @return see {@link Controller#isDCOPRunning()}
         */
        @SuppressWarnings("unused") // used by JSON output
        public boolean isDCOP() {
            return dcop;
        }

        /**
         * 
         * @return see {@link Controller#isHandlingDnsChanges()}
         */
        @SuppressWarnings("unused") // used by JSON output
        public boolean isHandlingDnsChanges() {
            return handlingDnsChanges;
        }

        /**
         * 
         * @return the current value of the Protelis VM
         */
        @SuppressWarnings("unused") // used by JSON output
        public String getApDebugValue() {
            return apDebugValue;
        }

        /**
         * 
         * @return the AP execution count
         */
        @SuppressWarnings("unused") // used by JSON output
        public long getApExecutionCount() {
            return apExecutionCount;
        }

        /**
         * 
         * @return see {@link Controller#isGlobalLeader()}
         */
        public boolean isGlobalLeader() {
            return globalLeader;
        }

    }

    private abstract static class ManagementThread extends Thread {
        private static final Logger LOGGER = LoggerFactory.getLogger(ManagementThread.class);
        private boolean newData;
        private final Object lock = new Object();

        protected final Object getLock() {
            return lock;
        }

        private boolean done = false;

        /* package */ ManagementThread(final String name) {
            super(name);
            this.newData = false;
        }

        /** must hold {@code lock} */
        protected final void notifyNewData() {
            this.newData = true;
            this.lock.notifyAll();
        }

        /**
         * Notify the thread to stop executing.
         */
        public final void stopExecuting() {
            synchronized (lock) {
                done = true;
                this.lock.notifyAll();
            }
        }

        @Override
        public final void run() {
            while (!done) {
                synchronized (lock) {
                    while (!done && !newData) {
                        try {
                            lock.wait();
                        } catch (final InterruptedException e) {
                            LOGGER.debug("Got interrupted, checking for done at the top of the loop", e);
                        }
                    }

                    newData = false;
                }

                if (!done) {
                    doWork();
                }
            }
        }

        protected abstract void doWork();

    }

    private static final class DnsManagementThread extends ManagementThread {

        private LoadBalancerPlan newLoadBalancerPlan;
        private RegionServiceState newRegionServiceState;
        private final Controller controller;

        /* package */ DnsManagementThread(final Controller controller) {
            super("DnsMangement for " + controller.getName());
            this.controller = controller;
            this.newLoadBalancerPlan = null;
            this.newRegionServiceState = null;
        }

        /**
         * @param newLoadBalancerPlan
         *            new plan to act on
         * @param newRegionServiceState
         *            new service state
         */
        public void updateData(final LoadBalancerPlan newLoadBalancerPlan,
                final RegionServiceState newRegionServiceState) {
            synchronized (getLock()) {
                this.newLoadBalancerPlan = newLoadBalancerPlan;
                this.newRegionServiceState = newRegionServiceState;
                notifyNewData();
            }
        }

        @Override
        protected void doWork() {
            this.controller.updateDnsInformation(newLoadBalancerPlan, newRegionServiceState);
        }
    }

    private static final class ContainerManagementThread extends ManagementThread {

        private LoadBalancerPlan newLoadBalancerPlan;
        private final Controller controller;

        /* package */ ContainerManagementThread(final Controller controller) {
            super("ContainerMangement for " + controller.getName());
            this.controller = controller;
            this.newLoadBalancerPlan = null;
        }

        /**
         * @param newLoadBalancerPlan
         *            new plan to act on
         */
        public void updateData(final LoadBalancerPlan newLoadBalancerPlan) {
            synchronized (getLock()) {
                this.newLoadBalancerPlan = newLoadBalancerPlan;
                notifyNewData();
            }
        }

        @Override
        protected void doWork() {
            this.controller.manageContainers(newLoadBalancerPlan);
        }
    }

    /**
     * Used by Protelis to get the list of services. This list must contain the
     * same elements in the same order across all nodes.
     * 
     * @return {@link ApplicationCoordinates} for all known services in sorted
     *         order
     */
    public Tuple getAllServices() {
        // get all planned services
        return new ArrayTupleImpl(applicationManager.getAllApplicationSpecifications().stream() //
                .map(ApplicationSpecification::getCoordinates)//
                .filter(c -> !MAPServices.UNPLANNED_SERVICES.contains(c)) //
                .sorted().collect(Collectors.toList()).toArray());
    }

    private ImmutableMap<ServiceIdentifier<?>, TotalDemand> localTotalDemand = null;

    /**
     * Local information about total demand. This uses the
     * {@link EstimationWindow#LONG} estimation window. This is based on the
     * latest {@link ResourceSummary} and is used by AP to compute
     * {@link #getTotalDemandForService(ServiceIdentifier)}.
     * 
     * @param service
     *            the service to get the demand for
     * @return demand for the service, {@link TotalDemand#nullTotalDemand()} if
     *         there is no known demand for the service
     */
    public TotalDemand getLocalTotalDemandForService(final @Nonnull ServiceIdentifier<?> service) {
        synchronized (rlgDataLock) {
            if (null == localTotalDemand) {
                localTotalDemand = TotalDemand.fromSummary(getDcopResourceSummary());
            }
            return localTotalDemand.getOrDefault(service, TotalDemand.nullTotalDemand());
        }
    }

    /**
     * @param service
     *            the service we are interested in
     * @return is this node the leader for AP sharing of {@link TotalDemand} for
     *         the specified service
     */
    public boolean isTotalDemandLeaderForService(final @Nonnull ServiceIdentifier<?> service) {
        if (isRunDCOP()) {
            final ApplicationSpecification appSpec = AppMgrUtils.getApplicationSpecification(applicationManager,
                    service);
            if (null == appSpec.getServiceDefaultRegion()) {
                logger.error("Got null default region for service {}", service);
                return false;
            } else {
                return appSpec.getServiceDefaultRegion().equals(getRegion());
            }
        } else {
            return false;
        }
    }

    private final Map<ServiceIdentifier<?>, TotalDemand> globalTotalDemand = new HashMap<>();

    @Override
    public TotalDemand getTotalDemandForService(final @Nonnull ServiceIdentifier<?> service) {
        return globalTotalDemand.getOrDefault(service, TotalDemand.nullTotalDemand());
    }

    /**
     * 
     * @param service
     *            the service
     * @param totalDemand
     *            the new total demand for the service in the topology
     * @see #getTotalDemandForService(ServiceIdentifier)
     */
    public void setTotalDemandForService(final @Nonnull ServiceIdentifier<?> service,
            final @Nonnull TotalDemand totalDemand) {
        globalTotalDemand.put(service, totalDemand);
    }

    /**
     * This makes it easy for AP to check the parameter.
     * 
     * @return {@link AgentConfiguration#getDcopShareDirect()}
     */
    public boolean getDcopShareDirect() {
        return AgentConfiguration.getInstance().getDcopShareDirect();
    }
}
