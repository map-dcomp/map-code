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
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.CloseableThreadContext;
import org.protelis.lang.datatype.Tuple;
import org.protelis.vm.ProtelisProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AbstractService.Status;
import com.bbn.map.ap.ApLogger;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.dcop.DCOPService;
import com.bbn.map.dcop.DcopInfoProvider;
import com.bbn.map.dcop.DcopSharedInformation;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.dns.PlanTranslator;
import com.bbn.map.rlg.RLGService;
import com.bbn.map.rlg.RlgInfoProvider;
import com.bbn.map.rlg.RlgSharedInformation;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.SimulationRunner;
import com.bbn.map.ta2.OverlayTopology;
import com.bbn.map.utils.JsonUtils;
import com.bbn.map.utils.MAPServices;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan.ContainerInfo;
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

    private final NetworkServices networkServices;
    private final boolean allowDnsChanges;
    private final boolean enableDcop;
    private final boolean enableRlg;

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
     * @see NetworkServer#NetworkServer(NodeLookupService, RegionLookupService,
     *      ProtelisProgram, NodeIdentifier, ResourceManagerFactory, Map)
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
            final boolean enableRlg) {
        super(nodeLookupService, regionLookupService, program, name, manager, extraData);
        this.logger = LoggerFactory.getLogger(Controller.class.getName() + "." + name);
        this.apLogger = LoggerFactory.getLogger("com.bbn.map.ap.program." + name);

        this.dcop = new DCOPService(name.getName(), getRegionIdentifier(), this, AppMgrUtils.getApplicationManager());
        this.rlg = new RLGService(name.getName(), getRegionIdentifier(), this, AppMgrUtils.getApplicationManager());
        this.networkServices = networkServices;
        this.allowDnsChanges = allowDnsChanges;
        this.enableDcop = enableDcop
                && !AgentConfiguration.RlgAlgorithm.NO_MAP.equals(AgentConfiguration.getInstance().getRlgAlgorithm());
        this.enableRlg = enableRlg;
        this.dnsPrevLoadBalancerPlan = null;
        this.dnsPrevRegionServiceState = null;

        setRunDCOP(ControllerProperties.isRunningDcop(extraData));
        setRunRLG(ControllerProperties.isRunningRlg(extraData));
        setHandleDnsChanges(ControllerProperties.isHandlingDnsChanges(extraData));

        setSleepTime(AgentConfiguration.getInstance().getApRoundDuration().toMillis());

        final Thread dumperThread = new Thread(() -> dumperWorker(), String.format("%s dumper", getName()));
        dumperThread.setDaemon(true);
        dumperThread.start();

        logger.info("Running git version {}", SimulationRunner.getGitVersionInformation());
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
            updateDnsInformation();
        }

        if (logger.isTraceEnabled()) {
            logger.trace("{} resource summary compute load: {}", getNodeIdentifier().getName(),
                    getNetworkState().getRegionSummary(EstimationWindow.SHORT).getServerLoad());
        }

        manageContainers();

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

    private void updateDnsInformation() {
        logger.trace("Checking for DNS changes in node: {}", getNodeIdentifier());

        final LoadBalancerPlan newLoadBalancerPlan = getNetworkState().getLoadBalancerPlan();
        final RegionServiceState newRegionServiceState = getRegionServiceState();

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

            final ImmutableCollection<Pair<DnsRecord, Double>> newDnsEntries = networkServices.getPlanTranslator()
                    .convertToDns(newLoadBalancerPlan, newRegionServiceState);
            if (null != newDnsEntries) {

                logger.info("Execution {}, found DNS changes in region {}. Replacing records with: {}",
                        getExecutionCount(), getRegionIdentifier(), newDnsEntries);

                updateLocalNetworkAvailableServices(newDnsEntries);

                final boolean success = networkServices.getDnsUpdateService(getRegionIdentifier())
                        .replaceAllRecords(newDnsEntries);

                if (success) {
                    dnsPrevLoadBalancerPlan = newLoadBalancerPlan;
                    dnsPrevRegionServiceState = newRegionServiceState;

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

    private RegionAvailableServices regionAvailableServices = new MutableRegionAvailableServices(getRegionIdentifier());
    /**
     * The most recently used dns entries.
     */
    private ImmutableCollection<Pair<DnsRecord, Double>> dnsEntries = ImmutableList.of();

    private void updateLocalNetworkAvailableServices(final ImmutableCollection<Pair<DnsRecord, Double>> newDnsEntries) {
        synchronized (availableServicesLock) {
            final MutableRegionAvailableServices serviceInfo = new MutableRegionAvailableServices(
                    getRegionIdentifier());

            newDnsEntries.stream().filter(r -> r.getLeft() instanceof NameRecord)
                    .forEach(r -> serviceInfo.addService((NameRecord) r.getLeft()));

            logger.trace("Created available services {} from {}", serviceInfo, newDnsEntries);

            regionAvailableServices = serviceInfo;
            dnsEntries = newDnsEntries;
        }
    }

    /**
     * 
     * @return the information that this node has about available services in
     *         the region
     */
    @Nonnull
    public NetworkAvailableServices getLocalNetworkAvailableServices() {
        synchronized (availableServicesLock) {
            final NetworkAvailableServices ret = NetworkAvailableServices
                    .convertToNetworkAvailableServices(regionAvailableServices);
            logger.trace("Getting local network available services - Created {} from {}", ret, regionAvailableServices);
            return ret;
        }
    }

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
    private void manageContainers() {
        try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(getName())) {
            final long time = getResourceManager().getClock().getCurrentTime();

            final ResourceManager<?> resMgr = getResourceManager();

            // check if the service plan for this node has changed
            final LoadBalancerPlan rlgPlan = getNetworkState().getLoadBalancerPlan();
            final ImmutableCollection<ContainerInfo> nodeServicePlan = rlgPlan.getServicePlan()
                    .get(getNodeIdentifier());
            if (Objects.equals(prevContainerPlan, nodeServicePlan)) {
                logger.trace(
                        "RLG service plan has not changed for this node, skipping container management. prev: {} new: {} full: {}",
                        prevContainerPlan, nodeServicePlan, rlgPlan);
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
    }

    private final DCOPService dcop;

    private void startDCOP() {
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

    private DcopSharedInformation localDcopSharedInformation = new DcopSharedInformation();

    /**
     * AP uses this to determine what to share with the rest of the network.
     * 
     * @return the DCOP shared information for this node
     */
    @Nonnull
    public DcopSharedInformation getLocalDcopSharedInformation() {
        synchronized (dcopSharedInformationLock) {
            logger.debug("Getting local DCOP information {}", localDcopSharedInformation);
            return localDcopSharedInformation;
        }
    }

    @Override
    public void setLocalDcopSharedInformation(@Nonnull final DcopSharedInformation v) {
        synchronized (dcopSharedInformationLock) {
            logger.debug("Sharing DCOP information {}", v);
            localDcopSharedInformation = v;
        }
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
        synchronized (dcopSharedInformationLock) {
            logger.debug("Returning all DCOP information {}", allDcopSharedInformation);
            return allDcopSharedInformation;
        }
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
            allDcopSharedInformation = builder.build();
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

    @Override
    public boolean isServiceAvailable(final RegionIdentifier region, final ServiceIdentifier<?> service) {
        synchronized (availableServicesLock) {
            return allNetworkAvailableServices.isServiceAvailable(region, service);
        }
    }

    private final Object rlgSharedInformationLock = new Object();

    private RlgSharedInformation localRlgSharedInformation = new RlgSharedInformation();

    /**
     * AP uses this to determine what to share with the rest of the network.
     * 
     * @return the RLG shared information for this node
     */
    @Nonnull
    public RlgSharedInformation getLocalRlgSharedInformation() {
        synchronized (rlgSharedInformationLock) {
            return localRlgSharedInformation;
        }
    }

    @Override
    public void setLocalRlgSharedInformation(@Nonnull final RlgSharedInformation v) {
        synchronized (rlgSharedInformationLock) {
            localRlgSharedInformation = v;
        }
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
        synchronized (rlgSharedInformationLock) {
            return allRlgSharedInformation;
        }
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
                    if (logger.isTraceEnabled()) {
                        logger.trace("Got default RLG shared value, ignoring: " + value);
                    }
                } else {
                    logger.error("Got unexpected tuple as RLG shared value, ignoring: " + value);
                }
            } else {
                logger.error("Got unexpected value as RLG shared value, ignoring: " + value);
            }
        }
        synchronized (rlgSharedInformationLock) {
            allRlgSharedInformation = builder.build();
        }
    }

    @Override
    @Nonnull
    public ResourceSummary getRegionSummary(@Nonnull ResourceReport.EstimationWindow estimationWindow) {
        final long now = getResourceManager().getClock().getCurrentTime();

        ResourceSummary summary = getNetworkState().getRegionSummary(estimationWindow);
        final long minDelay = now - summary.getMinTimestamp();
        final long maxDelay = now - summary.getMaxTimestamp();

        logger.debug(
                "getRegionSummary: Retrieving {} with timestamp range {}-{} at time {} after delay range of {}-{} ms",
                ResourceSummary.class.getSimpleName(), summary.getMinTimestamp(), summary.getMaxTimestamp(), now,
                minDelay, maxDelay);

        return summary;
    }

    @Override
    @Nonnull
    public RegionPlan getDcopPlan() {
        final long now = getResourceManager().getClock().getCurrentTime();

        RegionPlan plan = getNetworkState().getRegionPlan();
        final long delay = now - plan.getTimestamp();

        logger.debug("getDcopPlan: Retrieving {} with timestamp {} at time {} after delay of {} ms",
                RegionPlan.class.getSimpleName(), plan.getTimestamp(), now, delay);

        return getNetworkState().getRegionPlan();
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
    public ImmutableSet<ResourceReport> getNodeResourceReports() {
        return getRegionNodeState().getNodeResourceReports();
    }

    // ---- end RLGInfoProvider

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

                    if (isDumpState()) {
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

}
