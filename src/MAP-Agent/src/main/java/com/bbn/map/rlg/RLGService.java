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
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
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
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
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
            final LoadBalancerPlan newPlan = computePlan();
            if (null == newPlan) {
                LOGGER.warn("RLG produced a null plan, ignoring");
            } else {
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

    private void initializeServicePriorityManager(final ServicePriorityManager servicePriorityManager,
            final LoadBalancerPlanBuilder newServicePlan,
            final RegionPlan regionPlan,
            final ImmutableSet<ResourceReport> resourceReports,
            final LoadPercentages loadPercentages) {

        final Set<ServiceIdentifier<?>> priorityServices = new HashSet<>();

        // add all services that are in the DCOP plan and have traffic to this
        // region > 0
        regionPlan.getPlan().forEach((service, regionTraffic) -> {
            Double trafficToThisRegion = regionTraffic.get(this.region);

            if (trafficToThisRegion != null && trafficToThisRegion > 0.0) {
                priorityServices.add(service);
            }
        });

        // add all services that default to this region
        final Collection<ApplicationCoordinates> allServices = applicationManager.getAllCoordinates();
        for (final ApplicationCoordinates service : allServices) {
            final ApplicationSpecification spec = AppMgrUtils.getApplicationSpecification(applicationManager, service);
            if (Objects.equals(this.region, spec.getServiceDefaultRegion())) {
                priorityServices.add(service);
            }
        }

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

        final LocalDateTime currentTime = LocalDateTime.now();

        // Acquire information from the RlgInfoProvider
        final ImmutableSet<ResourceReport> resourceReports = rlgInfoProvider.getRlgResourceReports();
        final RegionPlan dcopPlan = rlgInfoProvider.getDcopPlan();
        final LoadBalancerPlan prevPlan = rlgInfoProvider.getRlgPlan();

        LOGGER.info("Resource reports: {}", resourceReports);
        LOGGER.debug("DCOP plan: {}", dcopPlan);
        LOGGER.trace("ApplicationManager: {}", applicationManager);

        final Map<NodeIdentifier, ResourceReport> reports = filterResourceReports(resourceReports);

        final Map<NodeIdentifier, NodeIdentifier> containerToNodeMap = createContainerToNodeMap(resourceReports);
        final Map<NodeIdentifier, ServiceStatus> containerServiceStatus = resourceReports.stream()
                .map(ResourceReport::getContainerReports)//
                .map(Map::entrySet).flatMap(Set::stream) //
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getServiceStatus()));

        // track which services are running and on which node
        final LoadBalancerPlanBuilder newServicePlan = createBasePlan(resourceReports, prevPlan);
        LOGGER.trace("Plan after doing base create: {}", newServicePlan);

        final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity = findNodesWithAvailableContainerCapacity(
                reports, newServicePlan);
        LOGGER.trace("Nodes with available capacity: {}", nodesWithAvailableCapacity);

        final RlgUtils.LoadPercentages loadPercentages = RlgUtils.computeServiceLoadPercentages(reports);

        final Map<ServiceIdentifier<?>, Integer> containersPerService = computeContainersPerService(reports);

        // initialize the servicePriorityManager for this iteration according to
        // the DCOP plan services and serviceReports
        initializeServicePriorityManager(servicePriorityManager, newServicePlan, dcopPlan, resourceReports,
                loadPercentages);

        final Set<ServiceIdentifier<?>> servicesDefaultingToThisRegion = ensureServicesInDefaultRegion(newServicePlan,
                nodesWithAvailableCapacity, loadPercentages, containersPerService, reports);

        // make sure there is at least one instance of each service that DCOP
        // wants running in this region
        if (!resourceReports.isEmpty()) {
            startAndStopServicesForDcop(dcopPlan, newServicePlan, nodesWithAvailableCapacity, loadPercentages,
                    containersPerService, reports, servicesDefaultingToThisRegion);
        } else {
            LOGGER.warn("No resource reports, RLG cannot do much of anything");
        }

        LOGGER.trace("Service plan after creating instances for DCOP: {}", newServicePlan);

        // find services that are overloaded, underloaded, and underload ended
        final double serviceOverloadThreshold = AgentConfiguration.getInstance().getRlgLoadThreshold();
        final double serviceUnderloadEndedThreshold = AgentConfiguration.getInstance().getRlgUnderloadEndedThreshold();
        final double serviceUnderloadThreshold = AgentConfiguration.getInstance().getRlgUnderloadThreshold();

        final List<ServiceIdentifier<?>> overloadedServices = new LinkedList<>();
        final List<ServiceIdentifier<?>> underloadedServices = new LinkedList<>();
        final List<ServiceIdentifier<?>> underloadedEndedServices = new LinkedList<>();

        final StringBuilder serviceLoadAndThresholdInfo = new StringBuilder();

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
        final Set<ServiceIdentifier<?>> downscaleableServices = getDownscaleableServices(containersPerService);
        LOGGER.debug("Found downscaleable services: {} ", downscaleableServices);

        Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute, Double>>> totalContainerLoadByService = getTotalContainerLoadByService(
                resourceReports, MapUtils.COMPUTE_ATTRIBUTE);

        final Map<ServiceIdentifier<?>, List<NodeIdentifier>> smallestLoadServiceContainers = sortServiceContainersByAscendingLoadLoad(
                totalContainerLoadByService, MapUtils.COMPUTE_ATTRIBUTE);

        LOGGER.trace("totalContainerLoadByService: {}, smallestLoadServiceContainers: {}", totalContainerLoadByService,
                smallestLoadServiceContainers);

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

            for (Map<NodeAttribute, Double> load : totalContainerLoadByService
                    .getOrDefault(serviceId, Collections.emptyMap()).values()) {
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

            LOGGER.trace("shutdownContainerIds = {}", shutdownContainerIds);

            // remove containers that are already scheduled for shutdown from
            // the list
            shutdownContainerIds.removeAll(serviceStopContainers.keySet());

            LOGGER.trace("after remove containers that are already scheduled for shutdown, shutdownContainerIds = {}",
                    shutdownContainerIds);

            // remove a container from containerIds so that the last container
            // cannot be stopped
            if (!shutdownContainerIds.isEmpty()) {
                shutdownContainerIds.remove(shutdownContainerIds.size() - 1);
            }

            LOGGER.trace("after remove last container, shutdownContainerIds = {}", shutdownContainerIds);

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

            LOGGER.debug(
                    "For service '{}', preparing to stop {} containers for priority reasons: {}, shutdownContainerIds = {}",
                    serviceId, serviceStopContainers.size(), serviceStopContainers, shutdownContainerIds);

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

                    LOGGER.trace("after limit number of containers for shutdown, shutdownContainerIds = {}",
                            shutdownContainerIds);

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

                if (nodeId == null) {
                    LOGGER.warn(
                            "While traversing potentialShutdownContainers ({}), could not find node for container '{}'"
                                    + "in containerToNodeMap: {}. Assuming container is STOPPED.",
                            potentialShutdownContainers, containerId, containerToNodeMap);
                }

                // remove a container from scheduledContainerShutdowns if the
                // container is no longer supplying
                // a service status or if its status is STOPPED, or it no longer
                // appears on a node
                if (!containerServiceStatus.containsKey(containerId)
                        || ServiceStatus.STOPPED.equals(containerServiceStatus.get(containerId)) || nodeId == null) {
                    LOGGER.debug(" *** Removing STOPPED container '{}' for service '{}' from scheduled shutdowns.",
                            containerId, serviceId);
                    scheduledContainerShutdowns.get(serviceId).remove(containerId);
                } else {
                    try {
                        newServicePlan.stopTrafficToContainer(nodeId, containerId);

                        if (currentTime.isAfter(shutdownTime) || currentTime.isEqual(shutdownTime)) {
                            LOGGER.debug(
                                    " *** Stopping container '{}' for service '{}' (scheduled shutdown time: {}) at time {}.",
                                    containerId, serviceId, shutdownTime, currentTime);

                            newServicePlan.stopContainer(nodeId, containerId);
                        }
                    } catch (final IllegalArgumentException e) {
                        LOGGER.debug("Could not find container that we planned to stop, assuming this is ok", e);
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
                                "Unexpectedly found container '{}' for service '{}' in the STOPPING or STOPPED state "
                                        + "when trying to allow traffic. This may be a result of the DCOP plan specifiying no "
                                        + "traffic for the service.",
                                container, service);
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

        validatePlan(reports, plan);

        return plan;
    }

    private void validatePlan(final Map<NodeIdentifier, ResourceReport> reports, final LoadBalancerPlan plan) {
        final StringBuilder message = new StringBuilder();

        plan.getServicePlan().forEach((node, containerInfos) -> {
            final int plannedContainers = countNotStoppedContainers(containerInfos);
            if (reports.containsKey(node)) {
                final int containerLimit = reports.get(node).getMaximumServiceContainers();
                if (plannedContainers > containerLimit) {
                    message.append(String.format("Planning %d containers for node %s when it's limit is %d: %s; ",
                            plannedContainers, node.getName(), containerLimit, containerInfos));
                }
            } else {
                message.append(
                        String.format("Planning containers for node %s that is not in the list of resource reports; ",
                                node.getName()));
            }
        });

        if (message.length() > 0) {
            LOGGER.error("Invalid RLG plan: {}", message);
        }
    }

    /**
     * 
     * @return plan that keeps all services where they are currently running and
     *         makes no changes
     */
    private LoadBalancerPlan noMapPlan() {
        LOGGER.debug("---- no MAP plan ----");

        final ImmutableSet<ResourceReport> resourceReports = rlgInfoProvider.getRlgResourceReports();
        final LoadBalancerPlan prevPlan = rlgInfoProvider.getRlgPlan();

        final Map<NodeIdentifier, ResourceReport> reports = filterResourceReports(resourceReports);
        final Map<NodeIdentifier, NodeIdentifier> containerToNodeMap = createContainerToNodeMap(resourceReports);

        // start new service plan
        final LoadBalancerPlanBuilder newServicePlan = createBasePlan(resourceReports, prevPlan);
        LOGGER.trace("noMapPlan: Plan after doing base create: {}", newServicePlan);

        // compute container weights
        runContainerWeightAlgorithm(newServicePlan, reports, containerToNodeMap);

        // build up the final plan
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = ImmutableMap
                .of();

        final LoadBalancerPlan plan = newServicePlan.toLoadBalancerPlan(resourceReports, overflowPlan);

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

    private static int countNotStoppedContainers(final Collection<LoadBalancerPlan.ContainerInfo> infos) {
        return (int) infos.stream() //
                .filter(c -> !c.isStop()) //
                .count();
    }

    /**
     * This is used by
     * {@link #ensureServicesInDefaultRegion(LoadBalancerPlanBuilder, SortedMap, LoadPercentages)}
     * and
     * {@link #startServicesForDcop(RegionPlan, LoadBalancerPlanBuilder, Map, LoadPercentages)}
     * to allocate required containers.
     */
    private boolean allocateRequiredContainer(final LoadBalancerPlanBuilder newServicePlan,
            final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            final LoadPercentages loadPercentages,
            final ServiceIdentifier<?> service,
            final Map<ServiceIdentifier<?>, Integer> containersPerService,
            final Map<NodeIdentifier, ResourceReport> reports) {
        // allocate a node
        final NodeIdentifier newNode = StubFunctions.chooseNode(service, newServicePlan, nodesWithAvailableCapacity,
                loadPercentages);

        if (null == newNode) {
            LOGGER.error("There is no capacity to allocate a container for the service {}", service);

            // need to stop a container to have an NCP to start a
            // new one on
            final Set<ServiceIdentifier<?>> servicesWithMultipleContainers = containersPerService.entrySet().stream()
                    .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());
            if (servicesWithMultipleContainers.isEmpty()) {
                LOGGER.warn(
                        "All services running in region only have a single container, nothing can be stopped to start service {} for DCOP",
                        service);
                return false;
            } else {
                // find service with the lowest priority
                final ApplicationManagerApi appMgr = AppMgrUtils.getApplicationManager();
                final OrderServicesByPriority comparator = new OrderServicesByPriority(appMgr);

                final ServiceIdentifier<?> serviceToReduce = servicesWithMultipleContainers.stream().min(comparator)
                        .get();

                final Pair<NodeIdentifier, NodeIdentifier> result = findLeastLoadedContainerForService(reports,
                        serviceToReduce, newServicePlan);

                final NodeIdentifier leastLoadedContainer = result.getRight();
                final NodeIdentifier leastLoadedContainerNcp = result.getLeft();
                if (null == leastLoadedContainer || null == leastLoadedContainerNcp) {
                    throw new RuntimeException("Cannot find least loaded container for service: " + serviceToReduce);
                }
                newServicePlan.stopContainer(leastLoadedContainerNcp, leastLoadedContainer);
                servicePriorityManager.notifyDeallocation(serviceToReduce, 1);
                containersPerService.merge(serviceToReduce, -1, Integer::sum);

                newServicePlan.addService(leastLoadedContainerNcp, service, 1);
                servicePriorityManager.requestAllocation(service, 1);
                containersPerService.merge(service, 1, Integer::sum);
                return true;
            }
        } else if (!nodesWithAvailableCapacity.containsKey(newNode)
                || null == nodesWithAvailableCapacity.get(newNode)) {
            LOGGER.error(
                    "The chosen node ({}) is not in nodesWithAvailableCapacity. This is an internal error. Service {} available nodes: {}",
                    newNode, service, nodesWithAvailableCapacity);
            return false;
        } else {
            final int availCap = nodesWithAvailableCapacity.get(newNode);
            if (availCap < 1) {
                LOGGER.error("The chosen node has no container capacity {} available nodes: {}", service,
                        newServicePlan, newNode, nodesWithAvailableCapacity);
                return false;
            } else {
                StubFunctions.allocateContainers(servicePriorityManager, service, newNode, 1, newServicePlan,
                        nodesWithAvailableCapacity);
                return true;
            }
        }
    }

    /**
     * 
     * @param newPlan
     *            TODO
     * @return NCP, container
     */
    private Pair<NodeIdentifier, NodeIdentifier> findLeastLoadedContainerForService(
            final Map<NodeIdentifier, ResourceReport> reports,
            final ServiceIdentifier<?> service,
            final LoadBalancerPlanBuilder newPlan) {
        NodeIdentifier ncp = null;
        NodeIdentifier container = null;
        double minimumLoad = Double.NaN;
        for (final Map.Entry<NodeIdentifier, ResourceReport> entry : reports.entrySet()) {
            final NodeIdentifier candidateNcp = entry.getKey();
            final ResourceReport report = entry.getValue();

            for (final Map.Entry<NodeIdentifier, ContainerResourceReport> centry : report.getContainerReports()
                    .entrySet()) {
                final NodeIdentifier candidateContainer = centry.getKey();
                final ContainerResourceReport creport = centry.getValue();
                if (service.equals(creport.getService())) {

                    final boolean planToStopCandidateContainer;
                    if (newPlan.getPlan().containsKey(candidateNcp) && null != newPlan.getPlan().get(candidateNcp)) {
                        final Optional<LoadBalancerPlan.ContainerInfo> candidateContainerPlan = newPlan.getPlan()
                                .get(candidateNcp).stream()
                                .filter(i -> null != i && Objects.equals(candidateContainer, i.getId())).findAny();
                        planToStopCandidateContainer = candidateContainerPlan.isPresent()
                                && candidateContainerPlan.get().isStop();
                    } else {
                        planToStopCandidateContainer = false;
                    }

                    if (!ServiceStatus.STOPPED.equals(creport.getServiceStatus())
                            && !ServiceStatus.STOPPING.equals(creport.getServiceStatus())
                            && !planToStopCandidateContainer) {
                        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> creportLoad = RlgUtils
                                .getConfiguredLoadInput(creport);
                        final Stream<ImmutableMap<NodeAttribute, Double>> s1 = creportLoad.entrySet().stream()
                                .map(Map.Entry::getValue);
                        final Stream<Map.Entry<NodeAttribute, Double>> s2 = s1.map(Map::entrySet)
                                .flatMap(Collection::stream);
                        final Stream<Double> s3 = s2.filter(e -> MapUtils.COMPUTE_ATTRIBUTE.equals(e.getKey()))
                                .map(Map.Entry::getValue);
                        final double load = s3.mapToDouble(i -> i).sum();

                        if (Double.isNaN(minimumLoad) || load < minimumLoad) {
                            ncp = candidateNcp;
                            container = candidateContainer;
                            minimumLoad = load;
                        }
                    } // the container is running
                } // the service we care about
            } // foreach container report
        }

        return Pair.of(ncp, container);
    }

    private static final class OrderServicesByPriority implements Comparator<ServiceIdentifier<?>> {

        private final ApplicationManagerApi appMgr;

        private OrderServicesByPriority(final ApplicationManagerApi appMgr) {
            this.appMgr = appMgr;
        }

        @Override
        public int compare(final ServiceIdentifier<?> o1, final ServiceIdentifier<?> o2) {
            final int o1Priority = AppMgrUtils.getApplicationSpecification(appMgr, o1).getPriority();
            final int o2Priority = AppMgrUtils.getApplicationSpecification(appMgr, o2).getPriority();
            return Integer.compare(o1Priority, o2Priority);
        }

    }

    /**
     * Ensure that any services that default to this region have at least 1
     * instance of the service running.
     * 
     * @return the services that default to this region
     */
    private Set<ServiceIdentifier<?>> ensureServicesInDefaultRegion(final LoadBalancerPlanBuilder newServicePlan,
            final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            final RlgUtils.LoadPercentages loadPercentages,
            final Map<ServiceIdentifier<?>, Integer> containersPerService,
            final Map<NodeIdentifier, ResourceReport> reports) {
        final Set<ServiceIdentifier<?>> servicesDefaultingToThisRegion = new HashSet<>();

        final Collection<ApplicationCoordinates> allServices = applicationManager.getAllCoordinates();
        for (final ApplicationCoordinates service : allServices) {
            final ApplicationSpecification spec = AppMgrUtils.getApplicationSpecification(applicationManager, service);
            if (Objects.equals(this.region, spec.getServiceDefaultRegion())) {
                servicesDefaultingToThisRegion.add(service);

                // need to have at least 1 instance running
                if (!planHasContainerForService(newServicePlan, service)) {
                    final boolean result = allocateRequiredContainer(newServicePlan, nodesWithAvailableCapacity,
                            loadPercentages, service, containersPerService, reports);
                    if (!result) {
                        LOGGER.error(
                                "{} defaults to this region and has no containers in the plan and there are no available nodes to start the service",
                                service);
                    }
                } // no plan to use this service
            } // services defaults to this region
        } // foreach service

        return servicesDefaultingToThisRegion;
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

    private static Set<ServiceIdentifier<?>> getDownscaleableServices(
            final Map<ServiceIdentifier<?>, Integer> serviceInstances) {
        final Set<ServiceIdentifier<?>> downscaleableServices = serviceInstances.entrySet().stream()
                .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());

        LOGGER.debug("getDownscaleableServices(): {}", downscaleableServices);
        return downscaleableServices;
    }

    private static Map<ServiceIdentifier<?>, Integer> computeContainersPerService(
            final Map<NodeIdentifier, ResourceReport> reports) {
        final Map<ServiceIdentifier<?>, Integer> serviceInstances = new HashMap<>();

        reports.forEach((node, report) -> {
            report.getContainerReports().forEach((containerId, containerReport) -> {
                serviceInstances.merge(containerReport.getService(), 1, Integer::sum);
            });
        });
        LOGGER.debug("containers per service: {}", serviceInstances);

        return serviceInstances;
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
        } else {
            return dcopPlan.getPlan();
        }
    }

    private static boolean infoSpecifiesRunningService(final ServiceIdentifier<?> service,
            final Collection<LoadBalancerPlan.ContainerInfo> infos) {
        return infos.stream()
                .filter(info -> !info.isStop() && !info.isStopTrafficTo() && service.equals(info.getService()))
                .findAny().isPresent();
    }

    /**
     * Start any services that DCOP plans to send to and stop any containers
     * that DCOP doesn't plan to send traffic to if this is not the default
     * region for the service.
     * 
     */
    private void startAndStopServicesForDcop(final RegionPlan dcopPlan,
            final LoadBalancerPlanBuilder newServicePlan,
            final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            final LoadPercentages loadPercentages,
            final Map<ServiceIdentifier<?>, Integer> containersPerService,
            final Map<NodeIdentifier, ResourceReport> reports,
            final Set<ServiceIdentifier<?>> servicesDefaultingToThisRegion) {
        if (AgentConfiguration.getInstance().isRlgNullOverflowPlan()) {
            // ignore the overflow plan from DCOP
            return;
        }

        final int containersToStart = AgentConfiguration.getInstance().getRlgAllocationsForDcop();
        if (containersToStart < 1) {
            LOGGER.warn("rlg allocations for DCOP is less than 1, not starting any containers for DCOP");
            return;
        }

        // start services that DCOP plans to have running in this region
        final Set<ServiceIdentifier<?>> servicesDcopPlansForThisRegion = new HashSet<>();
        dcopPlan.getPlan().forEach((service, servicePlan) -> {
            final double thisRegionWeight = servicePlan.getOrDefault(this.region, 0D);
            if (thisRegionWeight > 0) {
                servicesDcopPlansForThisRegion.add(service);

                final Optional<?> runningService = newServicePlan.getPlan().entrySet().stream()
                        .filter(e -> infoSpecifiesRunningService(service, e.getValue())).findAny();

                if (!runningService.isPresent()) {
                    // need to start 1 instance of service
                    final Optional<Map.Entry<NodeIdentifier, Integer>> newNodeAndCap = nodesWithAvailableCapacity
                            .entrySet().stream().findAny();
                    if (newNodeAndCap.isPresent()) {
                        final NodeIdentifier newNode = newNodeAndCap.get().getKey();
                        newServicePlan.addService(newNode, service, containersToStart);
                        // subtract 1 from available container capacity on
                        // newNode
                        nodesWithAvailableCapacity.merge(newNode, -1, Integer::sum);
                        if (nodesWithAvailableCapacity.get(newNode) <= 0) {
                            nodesWithAvailableCapacity.remove(newNode);
                        }
                    } else {
                        final boolean result = allocateRequiredContainer(newServicePlan, nodesWithAvailableCapacity,
                                loadPercentages, service, containersPerService, reports);
                        if (!result) {
                            LOGGER.warn("Unable to find a node to start service {} for DCOP", service);
                        }
                    }
                }
            } // if dcop is using this region for the service
        }); // foreach dcop service

        if (AgentConfiguration.getInstance().getRlgStopForDcop() && !dcopPlan.getPlan().isEmpty()) {
            // If there is a DCOP plan stop services not in the DCOP plan. If
            // the plan is empty, assume that DCOP hasn't published a plan yet
            // and don't stop any containers.
            final List<Runnable> removeContainers = new LinkedList<>();
            newServicePlan.getPlan().forEach((ncp, containerInfos) -> {
                containerInfos.forEach(containerInfo -> {
                    final ServiceIdentifier<?> service = containerInfo.getService();
                    if (!containerInfo.isStop() && null != containerInfo.getId()) {
                        final NodeIdentifier container = containerInfo.getId();
                        if (!servicesDefaultingToThisRegion.contains(service)
                                && !servicesDcopPlansForThisRegion.contains(service)) {
                            // stop this container

                            removeContainers.add(() -> {
                                // cannot execute inside the loop otherwise
                                // we'll get a
                                // concurrent modification exception, so build a
                                // Runnable and execute outside the loop
                                newServicePlan.stopTrafficToContainer(ncp, container);
                                newServicePlan.stopContainer(ncp, container);
                                servicePriorityManager.notifyDeallocation(service, 1.0);
                            });

                        } // container should stop
                    } // running container
                });
            });

            // Do the removals
            removeContainers.forEach(Runnable::run);
        } // plan is not empty and containers should be stopped
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
        final ImmutableSet<ResourceReport> resourceReports = rlgInfoProvider.getRlgResourceReports();
        final LoadBalancerPlan prevPlan = rlgInfoProvider.getRlgPlan();

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

        final Map<ServiceIdentifier<?>, Integer> containersPerService = computeContainersPerService(reports);

        final RlgUtils.LoadPercentages loadPercentages = RlgUtils.computeServiceLoadPercentages(reports);

        final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity = findNodesWithAvailableContainerCapacity(
                reports, newServicePlan);

        final Set<ServiceIdentifier<?>> servicesDefaultingToThisRegion = ensureServicesInDefaultRegion(newServicePlan,
                nodesWithAvailableCapacity, loadPercentages, containersPerService, reports);

        // make sure there is at least one instance of each service that DCOP
        // wants running in this region
        if (!resourceReports.isEmpty()) {
            startAndStopServicesForDcop(dcopPlan, newServicePlan, nodesWithAvailableCapacity, loadPercentages,
                    containersPerService, reports, servicesDefaultingToThisRegion);
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
            // LOGGER.debug("New containers: {}", newContainers);
            // int contToAdd = newContainers - curContainers;
            // LOGGER.debug("contToAdd: {}", contToAdd);
            // serviceContToAdd.put(serviceName, contToAdd);
            // }
        }

        prevLoads = serviceLoads;
        prevContainers = serviceContainers;

        LOGGER.debug("New heuristic debug, serviceLoads: {}", serviceLoads);
        LOGGER.debug("New heuristic debug, serviceContainers: {}", serviceContainers);
        LOGGER.debug("New heuristic debug, serviceContToAdd: {}", serviceContToAdd);

        // add new servers at this point
        for (Server server : serverCollection) {
            // LOGGER.debug("Checking server {}",
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
                // LOGGER.debug("RLG: New service added.");
            }
            // else {
            // LOGGER.debug("RLG: Old service, not added.");
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
        final Set<ServiceIdentifier<?>> downscaleableServices = getDownscaleableServices(containersPerService);

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

            for (Map<NodeAttribute, Double> load : totalContainerLoadByService
                    .getOrDefault(serviceId, Collections.emptyMap()).values()) {
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

                        LOGGER.debug("Service to shut down: {}", serviceId);
                        LOGGER.debug("Container to shut down: {}", containerId);
                        LOGGER.debug("NCP for shut down: {}", nodeId);

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

        validatePlan(reports, plan);

        return plan;
    }

}
