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
package com.bbn.map.rlg;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AbstractPeriodicService;
import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.ContainerWeightAlgorithm;
import com.bbn.map.AgentConfiguration.RlgPriorityPolicy;
import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.rlg.RlgUtils.LoadPercentages;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlanBuilder;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceStatus;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The main entry point for RLG. The {@link Controller} will use this class to
 * interact with RLG. The {@link Controller} will start this service as
 * appropriate for the node.
 */
public class RLGService extends AbstractPeriodicService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RLGService.class);

    /**
     * 10 second delay between stopping traffic and shutting down a container
     */
    private static final Duration SERVICE_UNDERLOADED_TO_CONTAINER_SHUTDOWN_DELAY = AgentConfiguration.getInstance()
            .getRlgServiceUnderloadedToContainerShutdownDelay();

    /**
     * The maximum number of containers to schedule for shutdown at one time.
     */
    private static final int MAXIMUM_SIMULTANEOUS_SHUTDOWNS = AgentConfiguration.getInstance()
            .getRlgMaxShutdownsPerRoundPerService();

    /**
     * The maximum load percentage that should be reached after scheduled
     * shutdowns occur. This is intended to ensure that shutdowns do not cause
     * the load percentage to exceed the overload threshold, triggering an
     * immediate reallocation.
     */
    private static final double TARGET_MAXIMUM_SHUTDOWN_LOAD_PERCENTAGE = AgentConfiguration.getInstance()
            .getRlgLoadThreshold() - 0.1;

    /**
     * The threshold at which load prediction in packing is supposed to balance
     * load up to. That is, at the end of the iteration, all containers will
     * have load no more than this threshold percentage.
     */
    private static final double PACKING_LOADPRED_UPPER_THRESHOLD = AgentConfiguration.getInstance()
            .getRlgLoadThreshold();

    /**
     * Same as MAXIMUM_SIMULTANEOUS_SHUTDOWNS, except that packing is more
     * aggressive with container shutdown.
     */
    private static final int PACKING_MAXIMUM_SIMULTANEOUS_SHUTDOWNS = 20;

    /**
     * The percentage of total region capacity that packing heuristic uses to
     * decide number of containers to start.
     */
    private static final double PACKING_MAXIMUM_STARTUP_PERCENTAGE = 0.1;

    /**
     * The scheduled container shutdowns.
     */
    private Map<ServiceIdentifier<?>, Map<NodeIdentifier, LocalDateTime>> scheduledContainerShutdowns = new HashMap<>();

    /**
     * Construct an RLG service.
     * 
     * @param region
     *            the region this service is for
     * @param applicationManager
     *            source of information about applications, including
     *            specifications and profiles
     * @param nodeName
     *            the name of the node that this service is running on (for
     *            logging)
     * @param rlgInfoProvider
     *            how to access information
     */
    public RLGService(@Nonnull final String nodeName,
            @Nonnull final RegionIdentifier region,
            @Nonnull final RlgInfoProvider rlgInfoProvider,
            @Nonnull final ApplicationManagerApi applicationManager) {
        super("RLG-" + nodeName, AgentConfiguration.getInstance().getRlgRoundDuration());
        this.region = region;
        this.applicationManager = applicationManager;
        this.rlgInfoProvider = rlgInfoProvider;
        this.rlgBins = new BinPacking();
        this.prevLoads = new HashMap<>();
        this.prevContainers = new HashMap<>();

        LOGGER.info("Using RLG load input setting: {}", AgentConfiguration.getInstance().getRlgAlgorithmLoadInput());

        final RlgPriorityPolicy rlgPriorityPolicy = AgentConfiguration.getInstance().getRlgPriorityPolicy();
        switch (rlgPriorityPolicy) {
        case FIXED_TARGET:
            servicePriorityManager = new FixedTargetServicePriorityManager(MapUtils.COMPUTE_ATTRIBUTE);
            break;
        case GREEDY_GROUP:
            servicePriorityManager = new GreedyGroupServicePriorityManager(MapUtils.COMPUTE_ATTRIBUTE);
            break;
        case NO_PRIORITY:
            servicePriorityManager = new NullServicePriorityManager();
            break;
        default:
            throw new IllegalArgumentException("Unknown RLG priority policy: " + rlgPriorityPolicy);
        }
    }

    private final RegionIdentifier region;

    private final RlgInfoProvider rlgInfoProvider;

    private final ServicePriorityManager servicePriorityManager;

    /**
     * Where to get application specifications and profiles from.
     */
    private final ApplicationManagerApi applicationManager;

    private final BinPacking rlgBins;
    private Map<ServiceIdentifier<?>, Double> prevLoads;
    private Map<ServiceIdentifier<?>, Integer> prevContainers;

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "Cannot guarantee that RLG will compute a non-null plan. Appears to be a bug in FindBugs")
    @Override
    protected void execute() {
        try {
            final LoadBalancerPlan previousPlan = rlgInfoProvider.getRlgPlan();

            final LoadBalancerPlan newPlan = computePlan();
            if (null == newPlan) {
                LOGGER.warn("RLG produced a null plan, ignoring");
            } else if (!newPlan.equals(previousPlan)) {
                LOGGER.info("Publishing RLG plan: {}", newPlan);

                rlgInfoProvider.publishRlgPlan(newPlan);
            }
        } catch (final Throwable t) {
            LOGGER.error("Got error computing RLG plan. Skipping this round and will try again next round", t);
        }
    }

    private LoadBalancerPlan computePlan() {
        final AgentConfiguration.RlgAlgorithm rlgAlgorithm = AgentConfiguration.getInstance().getRlgAlgorithm();
        LOGGER.info("Using {} RLG algorithm", rlgAlgorithm);

        if (AgentConfiguration.RlgAlgorithm.STUB.equals(rlgAlgorithm)) {
            return stubComputePlan();
        } else if (AgentConfiguration.RlgAlgorithm.BIN_PACKING.equals(rlgAlgorithm)) {
            return realComputePlan();
        } else if (AgentConfiguration.RlgAlgorithm.NO_MAP.equals(rlgAlgorithm)) {
            return noMapPlan();
        } else {
            throw new IllegalArgumentException("Unknown RLG algorithm: " + rlgAlgorithm);
        }
    }

    private void initializeServicePriorityManager(ServicePriorityManager servicePriorityManager,
            LoadBalancerPlanBuilder newServicePlan,
            RegionPlan regionPlan,
            ImmutableSet<ResourceReport> resourceReports,
            LoadPercentages loadPercentages) {
        // create set of services with services that are currently in the plan
        final Set<ServiceIdentifier<?>> priorityServices = newServicePlan.getPlan().entrySet().stream()
                .map(e -> e.getValue()).flatMap(cis -> cis.stream()).map(ci -> ci.getService()).distinct()
                .collect(Collectors.toSet());

        // add all services that default to this region
        final Collection<ApplicationCoordinates> allServices = applicationManager.getAllCoordinates();
        for (final ApplicationCoordinates service : allServices) {
            final ApplicationSpecification spec = AppMgrUtils.getApplicationSpecification(applicationManager, service);
            if (Objects.equals(this.region, spec.getServiceDefaultRegion())) {
                priorityServices.add(service);
            }
        }

        // add all services that are in the DCOP plan
        regionPlan.getPlan().forEach((service, regionTraffic) -> {
            Double trafficToThisRegion = regionTraffic.get(this.region);

            if (trafficToThisRegion != null && trafficToThisRegion > 0.0) {
                priorityServices.add(service);
            }
        });

        servicePriorityManager.beginIteration(priorityServices, resourceReports, loadPercentages);
    }

    private Map<NodeIdentifier, ResourceReport> filterResourceReports(final Set<ResourceReport> resourceReports) {
        // keep the most recent ResourceReport for each node
        // only consider ResourceReports with a short estimation window, should
        // already be filtered by AP, but let's be sure.
        final Map<NodeIdentifier, ResourceReport> reports = new HashMap<>();
        resourceReports.forEach(report -> {
            reports.merge(report.getNodeName(), report, (oldValue, value) -> {
                if (oldValue.getTimestamp() < value.getTimestamp()) {
                    return value;
                } else {
                    return oldValue;
                }
            });
        });

        LOGGER.debug("Filtered reports: {}", reports);

        return reports;
    }

    /**
     * Compute a new plan.
     * 
     * @return the plan or null if no new plan should be sent out
     */
    private LoadBalancerPlan stubComputePlan() {
        LOGGER.debug("---- stubComputePlan ----");

        final RegionIdentifier thisRegion = region;
        final LocalDateTime currentTime = LocalDateTime.now();

        // Acquire information from the RlgInfoProvider
        final ImmutableSet<ResourceReport> resourceReports = rlgInfoProvider.getNodeResourceReports();
        final RegionPlan dcopPlan = rlgInfoProvider.getDcopPlan();
        final LoadBalancerPlan prevPlan = rlgInfoProvider.getRlgPlan();

        // get and set the shared RLG information through rlgInfoProvider
        final ImmutableMap<RegionIdentifier, RlgSharedInformation> infoFromAllRlgs = rlgInfoProvider
                .getAllRlgSharedInformation();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("shared rlg information: " + infoFromAllRlgs);
        }

        LOGGER.info("Resource reports: {}", resourceReports);
        LOGGER.trace("DCOP plan: {}", dcopPlan);
        LOGGER.trace("ApplicationManager: {}", applicationManager);

        final Map<NodeIdentifier, ResourceReport> reports = filterResourceReports(resourceReports);

        final Map<NodeIdentifier, NodeIdentifier> containerToNodeMap = createContainerToNodeMap(resourceReports);
        final Map<NodeIdentifier, ServiceStatus> containerServiceStatus = resourceReports.stream()
                .map(ResourceReport::getContainerReports)//
                .map(Map::entrySet).flatMap(Set::stream) //
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getServiceStatus()));

        // track which services are running and on which node
        final RlgSharedInformation newRlgShared = new RlgSharedInformation();

        final LoadBalancerPlanBuilder newServicePlan = createBasePlan(resourceReports, prevPlan);
        LOGGER.trace("Plan after doing base create: {}", newServicePlan);

        final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity = findNodesWithAvailableContainerCapacity(
                reports, newServicePlan);
        LOGGER.trace("Nodes with available capacity: {}", nodesWithAvailableCapacity);

        final RlgUtils.LoadPercentages loadPercentages = RlgUtils.computeServiceLoadPercentages(reports);

        // initialize the servicePriorityManager for this iteration according to
        // the DCOP plan services and serviceReports
        // add the initial set of services in newServicePlan to the Set
        // final Set<ServiceIdentifier<?>> priorityServices =
        // newServicePlan.getPlan().entrySet().stream().map(e -> e.getValue())
        // .flatMap(cis -> cis.stream()).map(ci ->
        // ci.getService()).distinct().collect(Collectors.toSet());

        // servicePriorityManager.beginIteration(priorityServices,
        // resourceReports, loadPercentages);
        initializeServicePriorityManager(servicePriorityManager, newServicePlan, dcopPlan, resourceReports,
                loadPercentages);

        ensureServicesInDefaultRegion(newServicePlan, nodesWithAvailableCapacity, loadPercentages);

        // make sure there is at least one instance of each service that DCOP
        // wants running in this region
        if (!resourceReports.isEmpty()) {
            startServicesForDcop(dcopPlan, nodesWithAvailableCapacity, thisRegion, newServicePlan);
        } else {
            LOGGER.warn("No resource reports, RLG cannot do much of anything");
        }

        LOGGER.trace("Service plan after creating instances for DCOP: " + newServicePlan);

        // find services that are overloaded, underloaded, and underload ended
        final double serviceOverloadThreshold = AgentConfiguration.getInstance().getRlgLoadThreshold();
        final double serviceUnderloadEndedThreshold = AgentConfiguration.getInstance().getRlgUnderloadEndedThreshold();
        final double serviceUnderloadThreshold = AgentConfiguration.getInstance().getRlgUnderloadThreshold();

        final List<ServiceIdentifier<?>> overloadedServices = new LinkedList<>();
        final List<ServiceIdentifier<?>> underloadedServices = new LinkedList<>();
        final List<ServiceIdentifier<?>> underloadedEndedServices = new LinkedList<>();

        StringBuilder serviceLoadAndThresholdInfo = new StringBuilder();

        for (final Map.Entry<ServiceIdentifier<?>, Map<NodeAttribute, Double>> entry : loadPercentages.allocatedLoadPercentagePerService
                .entrySet()) {
            final ServiceIdentifier<?> service = entry.getKey();
            final double containerLoad = entry.getValue().getOrDefault(MapUtils.COMPUTE_ATTRIBUTE, 0D);
            serviceLoadAndThresholdInfo.append(" [").append(service).append(" : ").append(containerLoad).append("]");

            if (containerLoad >= serviceOverloadThreshold) {
                final ApplicationSpecification spec = AppMgrUtils.getApplicationSpecification(applicationManager,
                        service);

                if (spec.isReplicable()) {
                    LOGGER.trace("Service {} is overloaded and has load {} which is greater than or equal to {}",
                            service, containerLoad, serviceOverloadThreshold);
                    overloadedServices.add(service);
                } else {
                    LOGGER.debug("Overloaded service {} is not replicable", service);
                }
            }

            if (containerLoad >= serviceUnderloadEndedThreshold) {
                LOGGER.trace("Service {} is underload ended and has load {} which is greater than or equal to {}",
                        service, containerLoad, serviceUnderloadEndedThreshold);
                underloadedEndedServices.add(service);
            } else if (containerLoad < serviceUnderloadThreshold) {
                LOGGER.trace("Service {} is underloaded and has load {} which less than {}", service, containerLoad,
                        serviceUnderloadThreshold);
                underloadedServices.add(service);
            }
        }

        LOGGER.debug("RLG service thresholds and loads: OL: {}, ULE: {}, UL: {}, Service {}: {}",
                serviceOverloadThreshold, serviceUnderloadEndedThreshold, serviceUnderloadThreshold,
                MapUtils.COMPUTE_ATTRIBUTE.getName(), serviceLoadAndThresholdInfo.toString());
        LOGGER.debug(
                "RLG Service categorization: overloadedServices: {}, underloadedEndedServices: {}, underloadedServices: {}",
                overloadedServices, underloadedEndedServices, underloadedServices);
        LOGGER.debug("RLG plan before handling overloads: {}", newServicePlan);

        StubFunctions.allocateContainersForOverloadedServices(servicePriorityManager, resourceReports, newServicePlan,
                nodesWithAvailableCapacity, overloadedServices, loadPercentages);

        // Beginning of container down scaling part of RLG stub
        LOGGER.debug("---- start container shutdown iteration ----");
        LOGGER.debug("RLG plan before handling shutdowns: {}", newServicePlan);
        LOGGER.debug("scheduledContainerShutdowns: {}", scheduledContainerShutdowns);

        // initial computations
        final Set<ServiceIdentifier<?>> downscaleableServices = getDownscaleableServices(resourceReports);
        LOGGER.debug("Found downscaleable services: {} ", downscaleableServices);

        Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute, Double>>> totalContainerLoadByService = getTotalContainerLoadByService(
                resourceReports, MapUtils.COMPUTE_ATTRIBUTE);

        final Map<ServiceIdentifier<?>, List<NodeIdentifier>> smallestLoadServiceContainers = sortServiceContainersByAscendingLoadLoad(
                totalContainerLoadByService, MapUtils.COMPUTE_ATTRIBUTE);

        // cancel any shutdowns for services that are underload ended
        underloadedEndedServices.forEach((serviceId) -> {
            final Map<NodeIdentifier, LocalDateTime> containers = scheduledContainerShutdowns.get(serviceId);

            if (containers != null) {
                final Set<NodeIdentifier> cancelableContainers = new HashSet<>();

                // prepare to cancel only containers that are not yet scheduled
                // to shut down and are not yet STOPPED or STOPPING
                containers.forEach((container, shutdownTime) -> {
                    if (currentTime.isBefore(shutdownTime)) {
                        final ServiceStatus status = containerServiceStatus.get(container);

                        // if a container's state isn't known or is known to be
                        // stopping/stopped, don't cancel the shutdown
                        if (null != status && !ServiceStatus.STOPPING.equals(status)
                                && !ServiceStatus.STOPPED.equals(status)) {
                            cancelableContainers.add(container);
                        }
                    }
                });

                LOGGER.debug(
                        "Limited cancelable containers from {} to {}. Based on currentTime = {} and containers: {}",
                        containers.keySet(), cancelableContainers, currentTime, containers);

                LOGGER.debug(" *** Canceling scheduled shutdown of containers for service {}: {}", serviceId,
                        cancelableContainers);

                for (NodeIdentifier containerId : cancelableContainers) {
                    NodeIdentifier nodeId = containerToNodeMap.get(containerId);

                    if (nodeId != null) {
                        // cancel scheduled shutdown
                        Optional<Double> weight = newServicePlan.getPlan().get(nodeId).stream()
                                .filter(c -> containerId.equals(c.getId())).findFirst().map(c -> c.getWeight());

                        if (weight.isPresent()) {
                            if (weight.get() > 0.0) {
                                newServicePlan.allowTrafficToContainer(nodeId, containerId);
                            } else {
                                LOGGER.debug("While canceling shutdown, cannot allowTrafficToContainer({}, {})"
                                        + "only because container weight is 0.0.", nodeId, containerId);
                            }
                        }

                        scheduledContainerShutdowns.get(serviceId).remove(containerId);
                    } else {
                        LOGGER.error(
                                "Unable to allowTrafficToContainer for container '{}' because the container could not be mapped to a node.",
                                containerId);
                    }
                }
            }
        });

        final List<ServiceIdentifier<?>> deallocationServices = servicePriorityManager
                .getPriorityServiceDeallocationList().stream().filter(s -> downscaleableServices.contains(s))
                .collect(Collectors.toList());

        for (ServiceIdentifier<?> serviceId : deallocationServices) {
            double totalServiceLoad = 0.0;
            Map<NodeIdentifier, Double> containerCapacities = new HashMap<>();

            for (Map<NodeAttribute, Double> load : totalContainerLoadByService.get(serviceId).values()) {
                totalServiceLoad += load.getOrDefault(MapUtils.COMPUTE_ATTRIBUTE, 0.0);
            }

            reports.forEach((node, report) -> {
                report.getContainerReports().forEach((container, creport) -> {
                    containerCapacities.put(container, creport.getComputeCapacity().get(MapUtils.COMPUTE_ATTRIBUTE));
                });
            });

            final Map<NodeIdentifier, LocalDateTime> serviceStopContainers = scheduledContainerShutdowns
                    .computeIfAbsent(serviceId, (cs) -> new HashMap<>());

            final List<NodeIdentifier> shutdownContainerIds = smallestLoadServiceContainers.getOrDefault(serviceId,
                    new LinkedList<>());
            final Set<NodeIdentifier> containerIds = new HashSet<>(shutdownContainerIds);

            // remove containers that are already scheduled for shutdown from
            // the list
            shutdownContainerIds.removeAll(serviceStopContainers.keySet());

            // remove a container from containerIds so that the last container
            // cannot be stopped
            if (!shutdownContainerIds.isEmpty()) {
                shutdownContainerIds.remove(shutdownContainerIds.size() - 1);
            }

            // schedule priority shutdowns
            int priorityShutdowns = (int) Math
                    .floor(servicePriorityManager.getRemainingPriorityDeallocations(serviceId));

            // change existing scheduled shutdowns to immediate priority
            // shutdowns
            for (Iterator<Map.Entry<NodeIdentifier, LocalDateTime>> iter = serviceStopContainers.entrySet()
                    .iterator(); priorityShutdowns > 0 && iter.hasNext(); priorityShutdowns--) {
                Map.Entry<NodeIdentifier, LocalDateTime> entry = iter.next();
                entry.setValue(currentTime);
            }

            // add additional priority shutdowns if needed
            while (priorityShutdowns > 0 && !shutdownContainerIds.isEmpty()) {
                serviceStopContainers.put(shutdownContainerIds.remove(0), currentTime);
                priorityShutdowns--;
            }

            // check if a container shutdown should be scheduled for reasons
            // other than priority
            Map<NodeIdentifier, LocalDateTime> scheduledShutdowns = scheduledContainerShutdowns.get(serviceId);
            if (scheduledShutdowns == null || scheduledShutdowns.size() < MAXIMUM_SIMULTANEOUS_SHUTDOWNS) {
                if (underloadedServices.contains(serviceId)) {

                    LOGGER.debug("Load per allocated capacity for service '{}' is below {}.", serviceId,
                            serviceUnderloadThreshold);

                    // limit the number of containers that can be scheduled
                    // for shutdown in this round
                    while (shutdownContainerIds.size() > MAXIMUM_SIMULTANEOUS_SHUTDOWNS) {
                        shutdownContainerIds.remove(shutdownContainerIds.size() - 1);
                    }

                    // predict the remaining container capacity and limit the
                    // number of containers to be stopped
                    // so that capacity does not drop low enough to trigger an
                    // immediate reallocation
                    final double minStableCapacity = Math.max(1.0,
                            totalServiceLoad / TARGET_MAXIMUM_SHUTDOWN_LOAD_PERCENTAGE);
                    double predictedRemainingCapacity = containerIds.stream()
                            .filter(c -> !shutdownContainerIds.contains(c)).map(c -> containerCapacities.get(c))
                            .collect(Collectors.summingDouble(cap -> cap));
                    LOGGER.debug(
                            "*** For service '{}', containerIds = {}, shutdownContainerIds = {}, "
                                    + "totalServiceLoad = {}, minStableCapacity = {}, predictedRemainingCapacity = {}, "
                                    + "containerCapacities = {}",
                            serviceId, containerIds, shutdownContainerIds, totalServiceLoad, minStableCapacity,
                            predictedRemainingCapacity, containerCapacities);
                    while (predictedRemainingCapacity < minStableCapacity && !shutdownContainerIds.isEmpty()) {
                        NodeIdentifier container = shutdownContainerIds.remove(shutdownContainerIds.size() - 1);
                        predictedRemainingCapacity += containerCapacities.get(container);
                        LOGGER.debug("For service '{}', limited containers planned for shutdown to {} by removing {} "
                                + "to avoid immediate reallocation. totalServiceLoad = {}, minStableCapacity = {}, "
                                + "predictedRemainingCapacity = {}", serviceId, shutdownContainerIds, container,
                                totalServiceLoad, minStableCapacity, predictedRemainingCapacity);
                    }

                    LocalDateTime shutdownTime = currentTime.plus(SERVICE_UNDERLOADED_TO_CONTAINER_SHUTDOWN_DELAY);

                    for (NodeIdentifier container : shutdownContainerIds) {
                        serviceStopContainers.put(container, shutdownTime);
                    }

                    LOGGER.debug(
                            " *** Scheduling shutdown of containers '{}' for service '{}' "
                                    + "(scheduled shutdown time: {}) at time {}",
                            shutdownContainerIds, serviceId, shutdownTime, currentTime);
                }
            }
        }

        LOGGER.debug("scheduledContainerShutdowns: {}", scheduledContainerShutdowns);

        // shutdown any containers that were scheduled to be shutdown by now
        final Set<ServiceIdentifier<?>> scheduledContainerShutdownServices = new HashSet<>(
                scheduledContainerShutdowns.keySet());

        for (ServiceIdentifier<?> serviceId : scheduledContainerShutdownServices) {
            final Map<NodeIdentifier, LocalDateTime> potentialShutdownContainers = new HashMap<>(
                    scheduledContainerShutdowns.get(serviceId));

            for (final Map.Entry<NodeIdentifier, LocalDateTime> entry : potentialShutdownContainers.entrySet()) {
                final NodeIdentifier containerId = entry.getKey();
                final LocalDateTime shutdownTime = entry.getValue();
                final NodeIdentifier nodeId = containerToNodeMap.get(containerId);

                LOGGER.warn(
                        "While traversing potentialShutdownContainers ({}), could not find node for container '{}'"
                                + "in containerToNodeMap: {}. Assuming container is STOPPED.",
                        potentialShutdownContainers, containerId, containerToNodeMap);

                // remove a container from scheduledContainerShutdowns if the
                // container is no longer supplying a service status or if its
                // status is STOPPED, or it no longer appears on a node
                if (!containerServiceStatus.containsKey(containerId)
                        || ServiceStatus.STOPPED.equals(containerServiceStatus.get(containerId)) || nodeId == null) {
                    LOGGER.debug(" *** Removing STOPPED container '{}' for service '{}' from scheduled shutdowns.",
                            containerId, serviceId);
                    scheduledContainerShutdowns.get(serviceId).remove(containerId);
                } else {
                    newServicePlan.stopTrafficToContainer(nodeId, containerId);

                    if (currentTime.isAfter(shutdownTime) || currentTime.isEqual(shutdownTime)) {
                        LOGGER.debug(
                                " *** Stopping container '{}' for service '{}' (scheduled shutdown time: {}) at time {}.",
                                containerId, serviceId, shutdownTime, currentTime);

                        newServicePlan.stopContainer(nodeId, containerId);
                        servicePriorityManager.notifyDeallocation(serviceId, 1.0);
                    }
                }
            }
        }

        // remove each service that has no containers scheduled for shutdown
        // from scheduledContainerShutdowns
        for (Iterator<Map.Entry<ServiceIdentifier<?>, Map<NodeIdentifier, LocalDateTime>>> iter = scheduledContainerShutdowns
                .entrySet().iterator(); iter.hasNext();) {
            if (iter.next().getValue().isEmpty())
                iter.remove();
        }

        LOGGER.debug("RLG plan after handling shutdowns: {}", newServicePlan);

        LOGGER.debug("---- end container shutdown iteration ----");

        // ensure that all containers not scheduled for cancellation allow
        // traffic
        resourceReports.forEach(resourceReport -> {
            final NodeIdentifier node = resourceReport.getNodeName();
            resourceReport.getContainerReports().forEach((container, containerReport) -> {
                final ServiceIdentifier<?> service = containerReport.getService();
                final ServiceStatus status = containerReport.getServiceStatus();

                if (!scheduledContainerShutdowns.containsKey(service)
                        || !scheduledContainerShutdowns.get(service).containsKey(container)) {

                    if (!ServiceStatus.STOPPING.equals(status) && !ServiceStatus.STOPPED.equals(status)) {
                        Optional<Double> weight = newServicePlan.getPlan().get(node).stream()
                                .filter(c -> container.equals(c.getId())).findFirst().map(c -> c.getWeight());

                        if (weight.isPresent()) {
                            if (weight.get() > 0.0) {
                                newServicePlan.allowTrafficToContainer(node, container);
                            } else {
                                LOGGER.debug(
                                        "Cannot allowTrafficToContainer({}, {}) only because container weight is 0.0.",
                                        node, container);
                            }
                        }
                    } else {
                        LOGGER.warn(
                                "Unexpectedly found container '{}' in the STOPPING or STOPPED state when trying to allow traffic.",
                                container);
                    }
                }
            });
        });

        // update container weights
        runContainerWeightAlgorithm(newServicePlan, reports, containerToNodeMap);

        // End of down scaling part of RLG stub

        // build up the final plan
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = computeOverflowPlan(
                dcopPlan);

        final LoadBalancerPlan plan = newServicePlan.toLoadBalancerPlan(resourceReports, overflowPlan);

        LOGGER.debug("RLG currentTime: {}, RLG plan to be published: {}, RLG scheduledContainerShutdowns: {} ",
                currentTime, plan, scheduledContainerShutdowns);

        // tell other RLGs about the services running in this region
        LOGGER.trace("Sharing information about services: {}", newRlgShared);
        rlgInfoProvider.setLocalRlgSharedInformation(newRlgShared);

        return plan;
    }

    /**
     * 
     * @return plan that keeps all services where they are currently running and
     *         makes no changes
     */
    private LoadBalancerPlan noMapPlan() {
        LOGGER.debug("---- no MAP plan ----");

        final ImmutableSet<ResourceReport> resourceReports = rlgInfoProvider.getNodeResourceReports();
        final LoadBalancerPlan prevPlan = rlgInfoProvider.getRlgPlan();

        final Map<NodeIdentifier, ResourceReport> reports = filterResourceReports(resourceReports);
        final Map<NodeIdentifier, NodeIdentifier> containerToNodeMap = createContainerToNodeMap(resourceReports);

        // track which services are running and on which node
        final RlgSharedInformation newRlgShared = new RlgSharedInformation();

        // start new service plan
        final LoadBalancerPlanBuilder newServicePlan = createBasePlan(resourceReports, prevPlan);
        LOGGER.trace("noMapPlan: Plan after doing base create: {}", newServicePlan);

        // compute container weights
        runContainerWeightAlgorithm(newServicePlan, reports, containerToNodeMap);

        // build up the final plan
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = ImmutableMap
                .of();

        final LoadBalancerPlan plan = newServicePlan.toLoadBalancerPlan(resourceReports, overflowPlan);

        // tell other RLGs about the services running in this region
        LOGGER.trace("noMapPlan: Sharing information about services: {}", newRlgShared);
        rlgInfoProvider.setLocalRlgSharedInformation(newRlgShared);

        return plan;
    }

    private static final double CONTAINER_WEIGHT_EXPONENTIAL_DECAY_BASE = 16.0;

    private void runContainerWeightAlgorithm(LoadBalancerPlanBuilder newServicePlan,
            Map<NodeIdentifier, ResourceReport> reports,
            Map<NodeIdentifier, NodeIdentifier> containerToNodeMap) {
        final AgentConfiguration.ContainerWeightAlgorithm containerWeightAlgorithm = AgentConfiguration.getInstance()
                .getContainerWeightAlgorithm();

        if (ContainerWeightAlgorithm.ROUND_ROBIN.equals(containerWeightAlgorithm)) {
            LOGGER.debug("containerWeightAlgorithm: {}, Using standard round robin container weights.",
                    containerWeightAlgorithm);
            return;
        } else {
            Map<NodeIdentifier, Map<NodeAttribute, Double>> containerWeights = null;

            if (ContainerWeightAlgorithm.PROPORTIONAL.equals(containerWeightAlgorithm)) {
                // container proportional weights
                containerWeights = RlgUtils.computeContainerWeights(reports, (capacity, load) -> {
                    Map<NodeAttribute, Double> remainingCapacity = new HashMap<>();

                    capacity.forEach((attr, value) -> {
                        remainingCapacity.merge(attr, value, Double::sum);
                    });

                    load.forEach((attr, value) -> {
                        remainingCapacity.merge(attr, -value, Double::sum);
                    });

                    return remainingCapacity;
                }, false);

            } else if (ContainerWeightAlgorithm.EXPONENTIAL_DECAY.equals(containerWeightAlgorithm)) {
                // container exponential decay weights
                containerWeights = RlgUtils.computeContainerWeights(reports, (capacity, load) -> {
                    Map<NodeAttribute, Double> weights = new HashMap<>();

                    load.forEach((attr, value) -> {
                        weights.put(attr, Math.pow(CONTAINER_WEIGHT_EXPONENTIAL_DECAY_BASE,
                                -(value / capacity.getOrDefault(attr, 1.0)) - 1));
                    });

                    return weights;
                }, false);
            } else {
                LOGGER.error("Unimplemented containerWeightAlgorithm: {}", containerWeightAlgorithm);
                return;
            }

            // assign the weights to the containers in the RLG plan
            if (containerWeights != null) {
                LOGGER.debug("containerWeightAlgorithm: {}, containerWeights: {}", containerWeightAlgorithm,
                        containerWeights);

                containerWeights.forEach((container, weights) -> {
                    if (container != null && weights != null) {
                        Double weight = weights.get(MapUtils.COMPUTE_ATTRIBUTE);

                        if (weight != null) {
                            NodeIdentifier node = containerToNodeMap.get(container);

                            // stop traffic to container if it has a weight <= 0
                            if (weight <= 0.0) {
                                newServicePlan.stopTrafficToContainer(node, container);
                            }

                            newServicePlan.setContainerWeight(node, container, weight);
                        } else {
                            LOGGER.error(
                                    "weights.get(MapUtils.COMPUTE_ATTRIBUTE) returned a null value,"
                                            + "MapUtils.COMPUTE_ATTRIBUTE = {}, weights: {}",
                                    MapUtils.COMPUTE_ATTRIBUTE, weights);
                        }
                    } else {
                        LOGGER.error("container: {} or weights: {} is null", container, weights);
                    }
                });
            }
        }
    }

    /**
     * Ensure that any services that default to this region have at least 1
     * instance of the service running.
     */
    private void ensureServicesInDefaultRegion(final LoadBalancerPlanBuilder newServicePlan,
            final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            final RlgUtils.LoadPercentages loadPercentages) {
        final Collection<ApplicationCoordinates> allServices = applicationManager.getAllCoordinates();
        for (final ApplicationCoordinates service : allServices) {
            final ApplicationSpecification spec = AppMgrUtils.getApplicationSpecification(applicationManager, service);
            if (Objects.equals(this.region, spec.getServiceDefaultRegion())) {
                // need to have at least 1 instance running
                if (!planHasContainerForService(newServicePlan, service)) {
                    // allocate a node
                    final NodeIdentifier newNode = StubFunctions.chooseNode(service, newServicePlan,
                            nodesWithAvailableCapacity, loadPercentages);

                    if (null == newNode) {
                        LOGGER.error(
                                "{} defaults to this region and has no containers in the plan and there are no available nodes to start the service",
                                service);
                    } else if (!nodesWithAvailableCapacity.containsKey(newNode)
                            || null == nodesWithAvailableCapacity.get(newNode)) {
                        LOGGER.error(
                                "{} defaults to this region and has no containers in the plan {}, however the chosen node has no container capacity {} available nodes: {}",
                                service, newServicePlan, newNode, nodesWithAvailableCapacity);
                    } else {
                        final int availCap = nodesWithAvailableCapacity.get(newNode);
                        if (availCap < 1) {
                            LOGGER.error(
                                    "{} defaults to this region and has no containers in the plan {}, however the chosen node has no container capacity {} available nodes: {}",
                                    service, newServicePlan, newNode, nodesWithAvailableCapacity);
                        } else {
                            StubFunctions.allocateContainers(servicePriorityManager, service, newNode, 1,
                                    newServicePlan, nodesWithAvailableCapacity);
                        }
                    }
                }
            }
        }
    }

    /**
     * 
     * @param newServicePlan
     *            the plan to check
     * @param service
     *            the service to look for
     * @return true if newServicePlan has at least 1 instance of the specified
     *         service
     */
    private static boolean planHasContainerForService(final LoadBalancerPlanBuilder newServicePlan,
            final ServiceIdentifier<?> service) {
        final Map<NodeIdentifier, Collection<LoadBalancerPlan.ContainerInfo>> plan = newServicePlan.getPlan();
        return plan.entrySet().stream().map(Map.Entry::getValue).flatMap(Collection::stream)
                .anyMatch(info -> Objects.equals(service, info.getService()));
    }

    private Set<ServiceIdentifier<?>> getDownscaleableServices(final ImmutableSet<ResourceReport> resourceReports) {
        final Set<ServiceIdentifier<?>> downscaleableServices = new HashSet<>();
        final Map<ServiceIdentifier<?>, Integer> serviceInstances = new HashMap<>();

        resourceReports.forEach((report) -> {
            report.getContainerReports().forEach((containerId, containerReport) -> {
                serviceInstances.merge(containerReport.getService(), 1, Integer::sum);
            });
        });

        LOGGER.debug("serviceInstances: {}", serviceInstances);

        serviceInstances.forEach((serviceId, instances) -> {
            if (instances > 1) {
                downscaleableServices.add(serviceId);
            }
        });

        LOGGER.debug("getDownscaleableServices(): {}", downscaleableServices);
        return downscaleableServices;
    }

    private Map<NodeIdentifier, NodeIdentifier> createContainerToNodeMap(ImmutableSet<ResourceReport> resourceReports) {
        Map<NodeIdentifier, NodeIdentifier> containerToNodeMap = new HashMap<>();

        for (ResourceReport resourceReport : resourceReports) {
            NodeIdentifier nodeId = resourceReport.getNodeName();

            resourceReport.getContainerReports().forEach((containerId, containerReport) -> {
                containerToNodeMap.put(containerId, nodeId);
            });
        }

        LOGGER.debug("containerToNodeMap: {}", containerToNodeMap);

        return containerToNodeMap;
    }

    private Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute, Double>>> getTotalContainerLoadByService(
            final ImmutableSet<ResourceReport> resourceReports,
            final NodeAttribute loadAttribute) {
        // determine the total amount of load on each container
        final Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute, Double>>> totalContainerLoadByService = new HashMap<>();

        for (final ResourceReport resourceReport : resourceReports) {

            resourceReport.getContainerReports().forEach((containerId, containerReport) -> {
                final ServiceIdentifier<?> serviceId = containerReport.getService();

                if (serviceId != null) {
                    if (ServiceStatus.RUNNING.equals(containerReport.getServiceStatus())) {
                        final Map<NodeIdentifier, Map<NodeAttribute, Double>> totalLoadByContainer = totalContainerLoadByService
                                .computeIfAbsent(containerReport.getService(), (service) -> new HashMap<>());

                        final Map<NodeAttribute, Double> totalLoad = totalLoadByContainer.computeIfAbsent(containerId,
                                (container) -> new HashMap<>());

                        RlgUtils.getConfiguredLoadInput(containerReport).forEach((clientNodeId, loadFromNode) -> {
                            loadFromNode.forEach((attr, value) -> {
                                totalLoad.merge(attr, value, Double::sum);
                            });
                        });
                    } else {
                        LOGGER.debug(
                                "findServiceContainerWithSmallestLoad: Container '{}' was not considered for smallest load because it has service state '{}'.",
                                containerId, containerReport.getServiceStatus());
                    }
                } else {
                    LOGGER.debug("findServiceContainerWithSmallestLoad: Container '{}' has report with null service.",
                            containerId);
                }

            });
        }

        LOGGER.debug("totalContainerLoadByService: {}", totalContainerLoadByService);

        return totalContainerLoadByService;
    }

    private Map<ServiceIdentifier<?>, List<NodeIdentifier>> sortServiceContainersByAscendingLoadLoad(
            Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute, Double>>> totalContainerLoadByService,
            NodeAttribute loadAttribute) {

        final Map<ServiceIdentifier<?>, List<NodeIdentifier>> ascendingLoadContainersByService = new HashMap<>();

        // create a list of containers ascending by load for each service
        totalContainerLoadByService.forEach((service, totalContainerLoads) -> {
            List<NodeIdentifier> ascendingLoadContainers = new LinkedList<>(totalContainerLoads.keySet());

            // sort containers in ascending order by load for loadAttribute
            Collections.sort(ascendingLoadContainers, new Comparator<NodeIdentifier>() {
                @Override
                public int compare(NodeIdentifier a, NodeIdentifier b) {
                    return (int) Math.signum(totalContainerLoads.get(a).getOrDefault(loadAttribute, 0.0)
                            - totalContainerLoads.get(b).getOrDefault(loadAttribute, 0.0));
                }
            });

            ascendingLoadContainersByService.put(service, ascendingLoadContainers);
        });

        LOGGER.debug("ascendingLoadContainers: {}", ascendingLoadContainersByService);

        return ascendingLoadContainersByService;
    }

    /**
     * Take the DCOP plan and filter out overflow to regions that don't already
     * have the specified service running. Also filter out this region.
     */
    private ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> computeOverflowPlan(
            final RegionPlan dcopPlan) {

        if (AgentConfiguration.getInstance().isRlgNullOverflowPlan()) {
            return ImmutableMap.of();
        }

        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> dcopOverflowPlan = dcopPlan
                .getPlan();

        final ImmutableMap.Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = ImmutableMap
                .builder();
        dcopOverflowPlan.forEach((service, dcopServiceOverflowPlan) -> {
            final ImmutableMap.Builder<RegionIdentifier, Double> serviceOverflowPlanBuilder = ImmutableMap.builder();

            dcopServiceOverflowPlan.forEach((region, weight) -> {
                if (weight > 0) {
                    // check if service is running in region
                    if (rlgInfoProvider.isServiceAvailable(region, service)) {
                        serviceOverflowPlanBuilder.put(region, weight);
                    } else {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("No instance of {} in region {}, skipping delegation", service, region);
                        }
                    }
                }
            });

            final ImmutableMap<RegionIdentifier, Double> serviceOverflowPlan = serviceOverflowPlanBuilder.build();
            if (!serviceOverflowPlan.isEmpty()) {
                overflowPlan.put(service, serviceOverflowPlan);
            }
        });

        return overflowPlan.build();
    }

    private static boolean infoSpecifiesRunningService(final ServiceIdentifier<?> service,
            final Collection<LoadBalancerPlan.ContainerInfo> infos) {
        return infos.stream()
                .filter(info -> !info.isStop() && !info.isStopTrafficTo() && service.equals(info.getService()))
                .findAny().isPresent();
    }

    /**
     * Start any services that DCOP plans to send to.
     */
    private void startServicesForDcop(final RegionPlan dcopPlan,
            Map<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            final RegionIdentifier thisRegion,
            final LoadBalancerPlanBuilder newServicePlan) {
        if (AgentConfiguration.getInstance().isRlgNullOverflowPlan()) {
            // ignore the overflow plan from DCOP
            return;
        }

        // TODO: stop services that are not on the DCOP plan

        dcopPlan.getPlan().forEach((service, servicePlan) -> {
            if (servicePlan.containsKey(thisRegion)) {
                final Optional<?> runningService = newServicePlan.getPlan().entrySet().stream()
                        .filter(e -> infoSpecifiesRunningService(service, e.getValue())).findAny();

                if (!runningService.isPresent()) {
                    // need to start 1 instance of service
                    final Optional<Map.Entry<NodeIdentifier, Integer>> newNodeAndCap = nodesWithAvailableCapacity
                            .entrySet().stream().findAny();
                    if (newNodeAndCap.isPresent()) {
                        final NodeIdentifier newNode = newNodeAndCap.get().getKey();
                        newServicePlan.addService(newNode, service, 1);
                        // subtract 1 from available container capacity on
                        // newNode
                        nodesWithAvailableCapacity.merge(newNode, -1, Integer::sum);
                        if (nodesWithAvailableCapacity.get(newNode) <= 0) {
                            nodesWithAvailableCapacity.remove(newNode);
                        }
                    }
                }
            } // if dcop is using this region for the service
        }); // foreach dcop service
    }

    /**
     * Plan that keeps all of the running containers and changes nothing else.
     * 
     * @return builder with the base plan
     */
    private LoadBalancerPlanBuilder createBasePlan(final ImmutableSet<ResourceReport> resourceReports,
            final LoadBalancerPlan prevPlan) {

        final LoadBalancerPlanBuilder newServicePlan = new LoadBalancerPlanBuilder(prevPlan, resourceReports);

        LOGGER.debug("Base plan {} from {}", newServicePlan, resourceReports);

        return newServicePlan;
    }

    /**
     * 
     * @return nodeid -> available containers
     */
    private SortedMap<NodeIdentifier, Integer> findNodesWithAvailableContainerCapacity(
            final Map<NodeIdentifier, ResourceReport> reports,
            final LoadBalancerPlanBuilder newServicePlan) {

        final Map<NodeIdentifier, Integer> servicePlanUsedContainers = new HashMap<>();
        newServicePlan.getPlan().forEach((node, containers) -> {
            final int runningContainers = containers.stream().mapToInt(info -> !info.isStop() ? 1 : 0).sum();
            servicePlanUsedContainers.put(node, runningContainers);
        });
        LOGGER.trace("servicePlanUsedContainers: {} from: {}", servicePlanUsedContainers, newServicePlan);

        final SortedMap<NodeIdentifier, Integer> availableNodeCapacity = new TreeMap<>();

        reports.forEach((nodeid, report) -> {
            final int containerCapacity = report.getMaximumServiceContainers();
            LOGGER.trace("{} container capacity: {}", nodeid, containerCapacity);

            final int serviceReportContainersInUse = report.getAllocatedServiceContainers();

            // check the plan in case not all containers have started
            final int planContainersInUse = servicePlanUsedContainers.getOrDefault(nodeid, 0);

            final int availableContainers = containerCapacity
                    - Math.max(serviceReportContainersInUse, planContainersInUse);

            LOGGER.trace("{} serviceReport in use: {} plan in use: {} available: {}", nodeid,
                    serviceReportContainersInUse, planContainersInUse, availableContainers);

            if (availableContainers > 0) {
                availableNodeCapacity.put(nodeid, availableContainers);
            } else {
                LOGGER.trace("No available capacity on {}", nodeid);
            }
        });

        LOGGER.trace("Final available node capacity: {}", availableNodeCapacity);

        return availableNodeCapacity;
    }

    private LoadBalancerPlan realComputePlan() {
        final RegionPlan dcopPlan = rlgInfoProvider.getDcopPlan();
        final ImmutableSet<ResourceReport> resourceReports = rlgInfoProvider.getNodeResourceReports();
        final LoadBalancerPlan prevPlan = rlgInfoProvider.getRlgPlan();
        final RegionIdentifier thisRegion = rlgInfoProvider.getRegion();

        // get and set the shared RLG information through rlgInfoProvider
        final ImmutableMap<RegionIdentifier, RlgSharedInformation> infoFromAllRlgs = rlgInfoProvider
                .getAllRlgSharedInformation();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("shared rlg information: " + infoFromAllRlgs);
        }

        // LOGGER.trace("Resource reports: {}", resourceReports);
        LOGGER.info("Resource reports: {}", resourceReports);
        LOGGER.trace("DCOP plan: {}", dcopPlan);
        LOGGER.trace("ApplicationManager: {}", applicationManager);

        final LoadBalancerPlanBuilder newServicePlan = createBasePlan(resourceReports, prevPlan);
        LOGGER.trace("Plan after doing base create: {}", newServicePlan);

        // keep the most recent ResourceReport for each node
        // only consider ResourceReports with a short estimation window, should
        // already be filtered by AP, but let's be sure.
        final Map<NodeIdentifier, ResourceReport> reports = new HashMap<>();
        resourceReports.forEach(report -> {
            reports.merge(report.getNodeName(), report, (oldValue, value) -> {
                if (oldValue.getTimestamp() < value.getTimestamp()) {
                    return value;
                } else {
                    return oldValue;
                }
            });
        });

        LOGGER.trace("Filtered reports: {}", reports);

        final Map<NodeIdentifier, Integer> nodesWithAvailableCapacity = findNodesWithAvailableContainerCapacity(reports,
                newServicePlan);

        // make sure there is at least one instance of each service that DCOP
        // wants running in this region
        if (!resourceReports.isEmpty()) {
            startServicesForDcop(dcopPlan, nodesWithAvailableCapacity, thisRegion, newServicePlan);
        } else {
            LOGGER.warn("No resource reports, RLG cannot do much of anything");
        }

        LOGGER.trace("Service plan after creating instances for DCOP: {}", newServicePlan);

        // packing code here
        LOGGER.info("RLG load balance test");
        // MessageDigest md = null;
        // try {
        // md = MessageDigest.getInstance("MD5");
        // } catch (Exception e) {
        // e.printStackTrace();
        // }

        final ArrayList<Server> serverCollection = new ArrayList<Server>();
        final ArrayList<Service> serviceCollection = new ArrayList<Service>();

        final Map<ServiceIdentifier<?>, Double> serviceLoads = new HashMap<>();
        final Map<ServiceIdentifier<?>, Integer> serviceContainers = new HashMap<>();
        final Map<ServiceIdentifier<?>, Integer> serviceContToAdd = new HashMap<>();

        // final ArrayList<Service> overloadedServices = new
        // ArrayList<Service>();

        resourceReports.forEach(report -> {
            final NodeIdentifier serverName = report.getNodeName();
            final int serverCapacity = report.getMaximumServiceContainers();

            Server server = new Server(serverName, serverCapacity);
            serverCollection.add(server);

            // this is to get list of all services
            LOGGER.trace("Compute demand for {} is {}", report.getNodeName(), report.getComputeDemand());
            for (Map.Entry<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>> entry : RlgUtils
                    .getConfiguredLoadInput(report).entrySet()) {
                LOGGER.info("Service name actual: " + Objects.toString(entry.getKey()));
                ServiceIdentifier<?> serviceName = entry.getKey();

                Double serviceLoad = 0.0;

                LOGGER.info("entry value: " + Objects.toString(entry.getValue()));
                for (Map.Entry<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> entry2 : entry.getValue()
                        .entrySet()) {
                    serviceLoad += entry2.getValue().getOrDefault(MapUtils.COMPUTE_ATTRIBUTE, 0D);
                    // for (Map.Entry<NodeAttribute, Double> entry3 :
                    // entry2.getValue().entrySet()) {
                    // serviceLoad = entry3.getValue();
                    // }
                }

                LOGGER.info("Service load actual: " + serviceLoad);

                Service dummyService = new Service(serviceName, serviceLoad, 1);
                serviceCollection.add(dummyService);

                if (!serviceLoads.containsKey(serviceName)) {
                    serviceLoads.put(serviceName, serviceLoad);
                    serviceContainers.put(serviceName, 1);
                } else {
                    double prevLoad = serviceLoads.get(serviceName);
                    int prevContainers = serviceContainers.get(serviceName);
                    serviceLoads.replace(serviceName, prevLoad + serviceLoad);
                    serviceContainers.replace(serviceName, prevContainers + 1);
                }

                // final double overloadThreshold =
                // AgentConfiguration.getInstance().getRlgLoadThreshold();
                // if (serviceLoad > overloadThreshold) {
                // Service dummyService2 = new Service(serviceName,
                // serviceLoad*0.99, 1);
                // overloadedServices.add(dummyService2);
                // }

                LOGGER.info("Server load: " + Objects.toString(report.getComputeLoad()));
                LOGGER.info("Server demand: " + Objects.toString(report.getComputeDemand()));
            }
        });

        for (Map.Entry<ServiceIdentifier<?>, Double> entry : serviceLoads.entrySet()) {
            final ServiceIdentifier<?> serviceName = entry.getKey();
            final double curLoad = entry.getValue();
            final int curContainers = serviceContainers.get(serviceName);

            final double avgLoad = curLoad / curContainers;

            double prevLoad = 0;
            int prevCont = 0;
            double prevAvg = 0;
            if (prevLoads.containsKey(serviceName)) {
                prevLoad = prevLoads.get(serviceName);
                prevCont = prevContainers.get(serviceName);
                prevAvg = prevLoad / prevCont;
            }

            double predictedLoad = curLoad + 0.5 * (curLoad - prevLoad);
            double predictedAvg = predictedLoad / (curContainers + prevCont);

            double predicted = avgLoad * curContainers + 0.5 * (avgLoad - prevAvg);

            double required = predictedLoad / PACKING_LOADPRED_UPPER_THRESHOLD;
            int delta = (int) Math.ceil(required - curContainers);

            if (delta > 0) {
                serviceContToAdd.put(serviceName, delta);
            }

            // double predictedAvg = avgLoad + 0.5 * (avgLoad - prevAvg);
            // predictedAvg /= 1.5;

            // if (predictedAvg > 0.75) {
            // int newContainers = (int) Math.ceil(predictedLoad/0.75);
            // int contToAdd = newContainers - curContainers;
            // serviceContToAdd.put(serviceName, contToAdd);
            // }

            // if (avgLoad > PACKING_LOADPRED_UPPER_THRESHOLD) {
            // int newContainers = (int) Math.ceil(curLoad /
            // PACKING_LOADPRED_UPPER_THRESHOLD);
            // System.out.println("New containers:" + newContainers);
            // int contToAdd = newContainers - curContainers;
            // System.out.println("contToAdd:" + contToAdd);
            // serviceContToAdd.put(serviceName, contToAdd);
            // }
        }

        prevLoads = serviceLoads;
        prevContainers = serviceContainers;

        System.out.println("New heuristic debug, serviceLoads: " + serviceLoads);
        System.out.println("New heuristic debug, serviceContainers: " + serviceContainers);
        System.out.println("New heuristic debug, serviceContToAdd: " + serviceContToAdd);

        // add new servers at this point
        for (Server server : serverCollection) {
            // System.out.println("Checking server " +
            // Objects.toString(server.getName()));
            if (!rlgBins.hasServer(server)) {
                rlgBins.addServer(server);
            }
        }
        // BinPacking bins = new BinPacking(serverCollection);
        // ConsistentHash circle = new ConsistentHash(md, 1, serverCollection);

        // Sohaib: Merging previous and new plans

        // services to add = allservices - previousServicePlan
        // BinPacking object should be created only once. Have methods to add
        // servers and services

        final LoadBalancerPlan previousServicePlan = rlgInfoProvider.getRlgPlan();

        // create a list of old services
        final Set<ServiceIdentifier<?>> oldServices = previousServicePlan.getServicePlan().entrySet().stream()
                .map(Map.Entry::getValue).flatMap(Collection::stream).map(LoadBalancerPlan.ContainerInfo::getService)
                .collect(Collectors.toSet());

        // this is for adding new services only
        for (Service service : serviceCollection) {
            // if service is not already present, then add it using best fit
            if (!oldServices.contains(service.getName())) {
                LOGGER.info("RLG adding new service:" + service.toString());
                rlgBins.addBestFit(service.getName(), service.getLoad());
                // System.out.println("RLG: New service added.");
            }
            // else {
            // System.out.println("RLG: Old service, not added.");
            // }
        }

        // // handle overloaded -- alternate method
        // for (Service service : overloadedServices) {
        // LOGGER.info("RLG adding new container for overloaded service:" +
        // service.toString());
        // rlgBins.addBestFit(service.getName(), service.getLoad());
        // }

        // -- start overloaded and underloaded service estimation
        // now we determine overloaded services and handle them -- we use the
        // STUB implementation for this currently

        final RlgUtils.LoadPercentages loadPercentages = RlgUtils.computeServiceLoadPercentages(reports);

        // find services that are overloaded, underloaded, and underload ended
        final double serviceOverloadThreshold = AgentConfiguration.getInstance().getRlgLoadThreshold();
        final double serviceUnderloadEndedThreshold = AgentConfiguration.getInstance().getRlgUnderloadEndedThreshold();
        final double serviceUnderloadThreshold = AgentConfiguration.getInstance().getRlgUnderloadThreshold();

        final List<ServiceIdentifier<?>> overloadedServices = new LinkedList<>();
        final List<ServiceIdentifier<?>> underloadedServices = new LinkedList<>();
        final List<ServiceIdentifier<?>> underloadedEndedServices = new LinkedList<>();

        for (final Map.Entry<ServiceIdentifier<?>, Map<NodeAttribute, Double>> entry : loadPercentages.allocatedLoadPercentagePerService
                .entrySet()) {
            final ServiceIdentifier<?> service = entry.getKey();
            final double containerLoad = entry.getValue().getOrDefault(MapUtils.COMPUTE_ATTRIBUTE, 0D);

            if (containerLoad >= serviceOverloadThreshold) {
                final ApplicationSpecification spec = AppMgrUtils.getApplicationSpecification(applicationManager,
                        service);

                if (spec.isReplicable()) {
                    overloadedServices.add(service);
                } else {
                    LOGGER.debug("Overloaded service {} is not replicable", service);
                }
            }

            if (containerLoad >= serviceUnderloadEndedThreshold) {
                LOGGER.trace("Service {} is underload ended and has load {} which is greater than or equal to {}",
                        entry.getKey(), containerLoad, serviceUnderloadEndedThreshold);
                underloadedEndedServices.add(entry.getKey());
            } else if (containerLoad < serviceUnderloadThreshold) {
                LOGGER.trace("Service {} is underloaded and has load {} which less than {}", entry.getKey(),
                        containerLoad, serviceUnderloadThreshold);
                underloadedServices.add(entry.getKey());
            }
        }

        LOGGER.info("Services above threshold: {}", overloadedServices);
        // LOGGER.info("Plan before handling overloads: {}", newServicePlan);

        // // -- Start heuristic 2
        // // for each overloaded service, start new containers
        // double totalCap = rlgBins.totalCapacity();
        // double remaining = rlgBins.remainingCapacity();
        // int numOverloaded = overloadedServices.size();
        // int containersToStart = (int) Math
        // .ceil(Math.min(remaining / numOverloaded, totalCap *
        // PACKING_MAXIMUM_STARTUP_PERCENTAGE));
        // for (ServiceIdentifier<?> serviceName : overloadedServices) {
        // for (int i = 0; i < containersToStart; i++) {
        // // start a new container
        // LOGGER.info("RLG adding new container for service: " +
        // Objects.toString(serviceName));
        // rlgBins.addBestFit(serviceName, 1);
        // }
        // }
        // // -- End heuristic 2
        // // -- end overloaded and underloaded service estimation

        // -- Start heuristic 3 --
        final double remaining = rlgBins.remainingCapacity();
        final int numOverloaded = overloadedServices.size();
        serviceContToAdd.forEach((serviceName, value) -> {
            int containersToStart = (int) Math.ceil(Math.min(remaining / numOverloaded, value));
            for (int i = 0; i < containersToStart; i++) {
                rlgBins.addBestFit(serviceName, 1);
            }
        });
        // -- End heuristic 3

        // For container shutdown, we take the RLG stub code as template and
        // modify it

        // Beginning of container down scaling part of RLG stub
        LOGGER.debug("---- start container shutdown iteration ----");
        LOGGER.debug(" *** newServicePlan: {}", newServicePlan);

        // initial computations
        final LocalDateTime currentTime = LocalDateTime.now();
        final Set<ServiceIdentifier<?>> downscaleableServices = getDownscaleableServices(resourceReports);

        if (!downscaleableServices.isEmpty())
            LOGGER.debug("Found downscaleable services: {} ", downscaleableServices);

        final Map<NodeIdentifier, NodeIdentifier> containerToNodeMap = createContainerToNodeMap(resourceReports);
        final Map<NodeIdentifier, ServiceStatus> containerServiceStatus = resourceReports.stream()
                .map(ResourceReport::getContainerReports)//
                .map(Map::entrySet).flatMap(Set::stream) //
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getServiceStatus()));

        Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute, Double>>> totalContainerLoadByService = getTotalContainerLoadByService(
                resourceReports, MapUtils.COMPUTE_ATTRIBUTE);

        final Map<ServiceIdentifier<?>, List<NodeIdentifier>> smallestLoadServiceContainers = sortServiceContainersByAscendingLoadLoad(
                totalContainerLoadByService, MapUtils.COMPUTE_ATTRIBUTE);

        // cancel any shutdowns for services that are underload ended
        underloadedEndedServices.forEach((serviceId) -> {
            Map<NodeIdentifier, LocalDateTime> containers = scheduledContainerShutdowns.get(serviceId);

            if (containers != null) {
                LOGGER.debug(" *** Canceling scheduled shutdown of containers for service {}: {}", serviceId,
                        containers);

                for (NodeIdentifier containerId : containers.keySet()) {
                    NodeIdentifier nodeId = containerToNodeMap.get(containerId);

                    if (nodeId != null) {
                        LOGGER.trace("Calling allowTrafficToContainer({}, {})", nodeId, containerId);

                        Optional<Double> weight = newServicePlan.getPlan().get(nodeId).stream()
                                .filter(c -> containerId.equals(c.getId())).findFirst().map(c -> c.getWeight());

                        if (weight.isPresent() && weight.get() > 0.0) {
                            newServicePlan.allowTrafficToContainer(nodeId, containerId);
                        }
                    } else {
                        LOGGER.error(
                                "Unable to allowTrafficToContainer for container '{}' because the container could not be mapped to a node.",
                                containerId);
                    }
                }

                // cancel scheduled shutdown
                scheduledContainerShutdowns.remove(serviceId);
            }
        });

        for (ServiceIdentifier<?> serviceId : downscaleableServices) {

            double totalServiceLoad = 0.0;

            for (Map<NodeAttribute, Double> load : totalContainerLoadByService.get(serviceId).values()) {
                totalServiceLoad += load.getOrDefault(MapUtils.COMPUTE_ATTRIBUTE, 0.0);
            }

            // check if a container shutdown should be scheduled
            if (!scheduledContainerShutdowns.containsKey(serviceId)) {
                if (underloadedServices.contains(serviceId)) {

                    LOGGER.debug("Load per allocated capacity for service '{}' is below {}.", serviceId,
                            serviceUnderloadThreshold);
                    final Map<NodeIdentifier, LocalDateTime> serviceStopContainers = scheduledContainerShutdowns
                            .computeIfAbsent(serviceId, (cs) -> new HashMap<>());

                    final List<NodeIdentifier> containerIds = smallestLoadServiceContainers.get(serviceId);

                    if (containerIds != null) {

                        // remove containers that are already scheduled for
                        // shutdown from the list
                        containerIds.removeAll(serviceStopContainers.keySet());

                        // determine an upper limit on the number of containers
                        // that can be shutdown without triggering an immediate
                        // reallocation
                        // TODO: Currently this assumes a capacity of 1
                        // TASK_CONTAINER per container. Add code to sum actual
                        // capacities of each container
                        int availableContainersForShutdown = containerIds.size();
                        int maxStableShutdowns = (int) Math.floor(availableContainersForShutdown
                                - totalServiceLoad / TARGET_MAXIMUM_SHUTDOWN_LOAD_PERCENTAGE);

                        // remove a container from containerIds so that the last
                        // container cannot be stopped
                        if (!containerIds.isEmpty()) {
                            containerIds.remove(containerIds.size() - 1);
                        }

                        // limit the number of containers that can be scheduled
                        // for shutdown in this round
                        while (containerIds.size() > PACKING_MAXIMUM_SIMULTANEOUS_SHUTDOWNS) {
                            containerIds.remove(containerIds.size() - 1);
                        }

                        if (containerIds.size() > maxStableShutdowns) {
                            LOGGER.debug(
                                    " *** For service '{}', limiting number of containers being scheduled for shutdown"
                                            + "from {} to {} to prevent an immediate reallocation. containerIds before limit: {},"
                                            + "availableContainersForShutdown: {}, totalServiceLoad: {}",
                                    serviceId, containerIds.size(), maxStableShutdowns, containerIds,
                                    availableContainersForShutdown, totalServiceLoad);

                            while (containerIds.size() > maxStableShutdowns) {
                                containerIds.remove(containerIds.size() - 1);
                            }
                        }

                        LocalDateTime shutdownTime = currentTime.plus(SERVICE_UNDERLOADED_TO_CONTAINER_SHUTDOWN_DELAY);

                        for (NodeIdentifier container : containerIds) {
                            serviceStopContainers.put(container, shutdownTime);
                        }

                        // int plannedNumberOfShutdowns =
                        // Math.min(serviceStopContainers.size() +
                        // MAXIMUM_SIMULTANEOUS_SHUTDOWNS, containerIds.size());
                        //
                        // for (int n = 0; serviceStopContainers.size() <
                        // plannedNumberOfShutdowns && n < containerIds.size();
                        // n++)
                        // {
                        // serviceStopContainers.computeIfAbsent(containerIds.get(n),
                        // k -> shutdownTime);
                        // }

                        LOGGER.debug(
                                " *** Scheduling shutdown of containers '{}' for service '{}' "
                                        + "(scheduled shutdown time: {}) at time {}",
                                containerIds, serviceId, shutdownTime, currentTime);
                    } else {
                        LOGGER.debug("No container with smallest load found for service '{}'.", serviceId);
                    }
                }
            }

            // // start/continue withholding traffic from containers
            // final Set<NodeIdentifier> containers =
            // scheduledContainerShutdowns.getOrDefault(serviceId, new
            // HashMap<>())
            // .keySet();
            //
            // containers.forEach((container) -> {
            // final NodeIdentifier nodeId = containerToNodeMap.get(container);
            //
            // if (nodeId != null) {
            // newServicePlan.stopTrafficToContainer(nodeId, container);
            // } else {
            // LOGGER.warn("containerToNodeMap does not have node mapping for
            // container '{}'", container);
            // }
            // });
        }

        // // start out by copying stop and stopTrafficTo from the previous
        // // plan
        // rlgInfoProvider.getRlgPlan().getServicePlan().forEach((node,
        // containerInfos) -> {
        // containerInfos.forEach(info -> {
        //
        // scheduledContainerShutdowns
        //
        // if (null != info.getId()) {
        // try {
        // if (info.isStop()) {
        // newServicePlan.stopContainer(node, info.getId());
        // }
        // if (info.isStopTrafficTo()) {
        // newServicePlan.stopTrafficToContainer(node, info.getId());
        // }
        // } catch (final IllegalArgumentException e) {
        // LOGGER.trace("Container {} is no longer running, skipping copy of
        // state to new plan",
        // info.getId());
        // }
        // }
        // });
        // });

        LOGGER.debug("scheduledContainerShutdowns: {}", scheduledContainerShutdowns);

        // shutdown any containers that were scheduled to be shutdown by now
        final Set<ServiceIdentifier<?>> scheduledContainerShutdownServices = new HashSet<>(
                scheduledContainerShutdowns.keySet());

        for (ServiceIdentifier<?> serviceId : scheduledContainerShutdownServices) {
            final Map<NodeIdentifier, LocalDateTime> potentialShutdownContainers = new HashMap<>(
                    scheduledContainerShutdowns.get(serviceId));

            for (final Map.Entry<NodeIdentifier, LocalDateTime> entry : potentialShutdownContainers.entrySet()) {
                final NodeIdentifier containerId = entry.getKey();
                final LocalDateTime shutdownTime = entry.getValue();
                final NodeIdentifier nodeId = containerToNodeMap.get(containerId);

                // remove a container from scheduledContainerShutdowns if the
                // container is no longer supplying a service status or if its
                // status is STOPPED
                if (!containerServiceStatus.containsKey(containerId)
                        || containerServiceStatus.get(containerId).equals(ServiceStatus.STOPPED)) {
                    LOGGER.debug(" *** Removing STOPPED container '{}' for service '{}' from scheduled shutdowns.",
                            containerId, serviceId);
                    scheduledContainerShutdowns.get(serviceId).remove(containerId);

                    // // allow traffic to containerId
                    // newServicePlan.allowTrafficToContainer(nodeId,
                    // containerId);
                    // newServicePlan.unstopContainer(nodeId, containerId);
                } else {
                    newServicePlan.stopTrafficToContainer(nodeId, containerId);

                    if (currentTime.isAfter(shutdownTime)) {
                        LOGGER.debug(
                                " *** Stopping container '{}' for service '{}' (scheduled shutdown time: {}) at time {}.",
                                containerId, serviceId, shutdownTime, currentTime);

                        newServicePlan.stopContainer(nodeId, containerId);

                        System.out.println("DEBUG. Service to shut down: " + serviceId);
                        System.out.println("DEBUG. Container to shut down: " + containerId);
                        System.out.println("DEBUG. NCP for shut down: " + nodeId);

                        // remove container from BinPacking object
                        // if it is the only container for the service in the
                        // entire region, don't shut it down
                        rlgBins.shutdownContainer(nodeId, serviceId);
                    }
                }
            }

            // remove a service from the Map if it has no containers scheduled
            // for shutdown
            scheduledContainerShutdowns.compute(serviceId,
                    (key, containers) -> (containers.isEmpty() ? null : containers));
        }

        LOGGER.debug("newPlan after deallocation: {}", newServicePlan);

        LOGGER.debug("---- end container shutdown iteration ----");

        // End of down scaling part of RLG stub

        rlgBins.print();

        rlgBins.constructRLGPlan(newServicePlan);

        LOGGER.info("New service plan:" + newServicePlan);

        // create the final plan
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = computeOverflowPlan(
                dcopPlan);

        final LoadBalancerPlan plan = newServicePlan.toLoadBalancerPlan(resourceReports, overflowPlan);

        return plan;
    }

}
