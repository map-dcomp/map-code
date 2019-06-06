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
package com.bbn.map.rlg;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AbstractPeriodicService;
import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlanBuilder;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNodeState;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.RegionServiceState;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
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
    private static final NodeAttribute<?> COMPUTE_LOAD_ATTRIBUTE = NodeMetricName.TASK_CONTAINERS;

    /**
     * If load drop below this value for a service, the underload procedure to shutdown a container is initiated.
     */
    private static final double SERIVCE_UNDERLOAD_THESHOLD = 0.25;
    
    /**
     * If load exceeds this value, the underload procedure is cancelled.
     */
    private static final double SERVICE_UNDERLOAD_ENDED_THRESHOLD = 0.35;
    
    /**
     * 10 second delay between stopping traffic and shutting down a container
     */
    private static final Duration SERVICE_UNDERLOADED_TO_CONTAINER_SHUTDOWN_DELAY = Duration.ofSeconds(3);
    
    /**
     * The maximum number of containers to schedule for shutdown at one time.
     */
    private static final int MAXIMUM_SIMULTANEOUS_SHUTDOWNS = 3;
    
    /**
     * The maximum load percentage that should be reached after scheduled shutdowns occur.
     * This is intended to ensure that shutdowns do not cause the load percentage to exceed the overload threshold,
     * triggering an immediate reallocation.
     */
    private static final double TARGET_MAXIMUM_SHUTDOWN_LOAD_PERCENTAGE = 0.65;
    
    
    
    
    /**
     * The scheduled container shutdowns.
     */
    private Map<ServiceIdentifier<?>, Map<NodeIdentifier, LocalTime>> scheduledContainerShutdowns = new HashMap<>();

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
    }

    private final RegionIdentifier region;

    private final RlgInfoProvider rlgInfoProvider;

    /**
     * Where to get application specifications and profiles from.
     */
    private final ApplicationManagerApi applicationManager;

    private final BinPacking rlgBins;

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
        LOGGER.info("Using {} RLG algorithm", AgentConfiguration.getInstance().getRlgAlgorithm());

        if (AgentConfiguration.RlgAlgorithm.STUB.equals(AgentConfiguration.getInstance().getRlgAlgorithm())) {
            return stubComputePlan();
        } else if (AgentConfiguration.RlgAlgorithm.BIN_PACKING
                .equals(AgentConfiguration.getInstance().getRlgAlgorithm())) {
            return realComputePlan();
        } else {
            throw new IllegalArgumentException(
                    "Unknown RLG algorithm: " + AgentConfiguration.getInstance().getRlgAlgorithm());
        }
    }

    /**
     * Compute a new plan.
     * 
     * @return the plan or null if no new plan should be sent out
     */
    private LoadBalancerPlan stubComputePlan() {
        LOGGER.debug("---- stubComputePlan ----");

        final RegionPlan dcopPlan = rlgInfoProvider.getDcopPlan();
        final RegionNodeState regionState = rlgInfoProvider.getRegionNodeState();
        // assumption is that all resourceReports are for the SHORT estimation
        // window
        final ImmutableSet<ResourceReport> resourceReports = regionState.getNodeResourceReports();
        final RegionIdentifier thisRegion = region;

        // get and set the shared RLG information through rlgInfoProvider
        final ImmutableMap<RegionIdentifier, RlgSharedInformation> infoFromAllRlgs = rlgInfoProvider
                .getAllRlgSharedInformation();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("shared rlg information: " + infoFromAllRlgs);
        }

        LOGGER.trace("Resource reports: {}", resourceReports);
        LOGGER.trace("DCOP plan: {}", dcopPlan);
        LOGGER.trace("ApplicationManager: {}", applicationManager);

        final RegionServiceState regionServiceState = rlgInfoProvider.getRegionServiceState();
        final ImmutableSet<ServiceReport> serviceStates = regionServiceState.getServiceReports();
        LOGGER.trace("serviceStates: {}", serviceStates);

        // track which services are running and on which node
        final RlgSharedInformation newRlgShared = new RlgSharedInformation();
        final Map<NodeIdentifier, ServiceReport> serviceReportMap = new HashMap<>();
        serviceStates.forEach(sr -> {
            serviceReportMap.put(sr.getNodeName(), sr);
        });
        LOGGER.trace("serviceReportMap: {}", serviceReportMap);

        final LoadBalancerPlanBuilder newServicePlan = createBasePlan(resourceReports, serviceReportMap);

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
                serviceReportMap, newServicePlan);
        LOGGER.trace("Nodes with available capacity: {}", nodesWithAvailableCapacity);

        // make sure there is at least one instance of each service that DCOP
        // wants running in this region
        if (!resourceReports.isEmpty()) {
            startServicesForDcop(dcopPlan, nodesWithAvailableCapacity, thisRegion, newServicePlan);
        } else {
            LOGGER.warn("No resource reports, RLG cannot do much of anything");
        }

        LOGGER.trace("Service plan after creating instances for DCOP: " + newServicePlan);

        // find services that are overloaded
        final NodeAttribute<?> containersAttribute = NodeMetricName.TASK_CONTAINERS;

        final RlgUtils.LoadPercentages loadPercentages = RlgUtils.computeServiceLoadPercentages(reports);

        final List<ServiceIdentifier<?>> overloadedServices = new LinkedList<>();
        final List<ServiceIdentifier<?>> underloadedServices = new LinkedList<>();
        final List<ServiceIdentifier<?>> underloadedEndedServices = new LinkedList<>();

        final double serviceOverloadThreshold = AgentConfiguration.getInstance().getRlgLoadThreshold();

        for (final Map.Entry<ServiceIdentifier<?>, Map<NodeAttribute<?>, Double>> entry : loadPercentages.allocatedLoadPercentagePerService
                .entrySet()) {
            final ServiceIdentifier<?> service = entry.getKey();
            final double containerLoad = entry.getValue().getOrDefault(containersAttribute, 0D);

            if (containerLoad >= serviceOverloadThreshold) {
                final ApplicationSpecification spec = AppMgrUtils.getApplicationSpecification(applicationManager,
                        service);

                if (spec.isReplicable()) {
                    overloadedServices.add(service);
                } else {
                    LOGGER.debug("Overloaded service {} is not replicable", service);
                }
            }

            if (containerLoad >= SERVICE_UNDERLOAD_ENDED_THRESHOLD) {
                LOGGER.trace("Service {} is underload ended and has load {} which is greater than or equal to {}",
                        entry.getKey(), containerLoad, SERVICE_UNDERLOAD_ENDED_THRESHOLD);
                underloadedEndedServices.add(entry.getKey());
            } else if (containerLoad < SERIVCE_UNDERLOAD_THESHOLD) {
                LOGGER.trace("Service {} is underloaded and has load {} which less than {}", entry.getKey(),
                        containerLoad, SERIVCE_UNDERLOAD_THESHOLD);
                underloadedServices.add(entry.getKey());
            }
        }

        LOGGER.trace("Services above threshold: {}", overloadedServices);
        LOGGER.trace("Plan before handling overloads: {}", newServicePlan);

        StubFunctions.allocateContainers(serviceStates, newServicePlan, nodesWithAvailableCapacity, overloadedServices,
                loadPercentages);

        // Beginning of container down scaling part of RLG stub
        LOGGER.debug("---- start container shutdown iteration ----");
        LOGGER.debug(" *** newServicePlan: {}", newServicePlan);

        // initial computations
        final LocalTime currentTime = LocalTime.now();
        final Set<ServiceIdentifier<?>> downscaleableServices = getDownscaleableServices();

        if (!downscaleableServices.isEmpty())
            LOGGER.debug("Found downscaleable services: {} ", downscaleableServices);

        final Map<NodeIdentifier, NodeIdentifier> containerToNodeMap = createContainerToNodeMap();
        final Map<NodeIdentifier, ServiceState> containerServiceStates = getContainerServiceStates();
        
        Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute<?>, Double>>> totalContainerLoadByService =
                getTotalContainerLoadByService(containerServiceStates, COMPUTE_LOAD_ATTRIBUTE);
        
        final Map<ServiceIdentifier<?>, List<NodeIdentifier>> smallestLoadServiceContainers = sortServiceContainersByAscendingLoadLoad(totalContainerLoadByService, COMPUTE_LOAD_ATTRIBUTE);

        // cancel any shutdowns for services that are underload ended
        underloadedEndedServices.forEach((serviceId) -> {
            Map<NodeIdentifier, LocalTime> containers = scheduledContainerShutdowns.get(serviceId);

            if (containers != null) {
                LOGGER.debug(" *** Canceling scheduled shutdown of containers for service {}: {}", serviceId,
                        containers);
                
                for (NodeIdentifier containerId : containers.keySet())
                {
                    NodeIdentifier nodeId = containerToNodeMap.get(containerId);
                    
                    if (nodeId != null)
                    {
                        LOGGER.trace("Calling allowTrafficToContainer({}, {})", nodeId, containerId);
                        newServicePlan.allowTrafficToContainer(nodeId, containerId);
                    }
                    else
                    {
                        LOGGER.error("Unable to allowTrafficToContainer for container '{}' because the container could not be mapped to a node.", containerId);
                    }
                }
                
                // cancel scheduled shutdown
                scheduledContainerShutdowns.remove(serviceId);
            }
        });

        for (ServiceIdentifier<?> serviceId : downscaleableServices) {
            
            double totalServiceLoad = 0.0;
            
            for (Map<NodeAttribute<?>, Double> load : totalContainerLoadByService.get(serviceId).values())
            {
                totalServiceLoad += load.getOrDefault(COMPUTE_LOAD_ATTRIBUTE, 0.0);
            }
            

            // check if a container shutdown should be scheduled
            if (!scheduledContainerShutdowns.containsKey(serviceId)) {
                if (underloadedServices.contains(serviceId)) {
                    
                    LOGGER.debug("Load per allocated capacity for service '{}' is below {}.", serviceId,
                            SERIVCE_UNDERLOAD_THESHOLD);
                    final Map<NodeIdentifier, LocalTime> serviceStopContainers = scheduledContainerShutdowns
                            .computeIfAbsent(serviceId, (cs) -> new HashMap<>());

                    final List<NodeIdentifier> containerIds = smallestLoadServiceContainers.get(serviceId);       
                    
                    if (containerIds != null) {
                        
                        // remove containers that are already scheduled for shutdown from the list
                        containerIds.removeAll(serviceStopContainers.keySet());
                        
                        // determine an upper limit on the number of containers that can be shutdown without triggering an immediate reallocation
                        // TODO: Currently this assumes a capacity of 1 TASK_CONTAINER per container. Add code to sum actual capacityies of each container
                        int availableContainersForShutdown = containerIds.size();
                        int maxStableShutdowns = (int)Math.floor(availableContainersForShutdown - totalServiceLoad / TARGET_MAXIMUM_SHUTDOWN_LOAD_PERCENTAGE);                        
                        
                        // remove a container from containerIds so that the last container cannot be stopped
                        if (!containerIds.isEmpty())
                        {
                            containerIds.remove(containerIds.size() - 1);
                        }
                        
                        // limit the number of containers that can be scheduled for shutdown in this round
                        while (containerIds.size() > MAXIMUM_SIMULTANEOUS_SHUTDOWNS)
                        {
                            containerIds.remove(containerIds.size() - 1);
                        }
                        
                        if (containerIds.size() > maxStableShutdowns)
                        {
                            LOGGER.debug(" *** For service '{}', limiting number of containers being scheduled for shutdown"
                                    + "from {} to {} to prevent an immediate reallocation. containerIds before limit: {},"
                                    + "availableContainersForShutdown: {}, totalServiceLoad: {}", serviceId, containerIds.size(),
                                    maxStableShutdowns, containerIds, availableContainersForShutdown, totalServiceLoad);
                            
                            while (containerIds.size() > maxStableShutdowns)
                            {
                                containerIds.remove(containerIds.size() - 1);
                            }
                        }
                        
                        LocalTime shutdownTime = currentTime.plus(SERVICE_UNDERLOADED_TO_CONTAINER_SHUTDOWN_DELAY);
                        
                        for (NodeIdentifier container : containerIds)
                        {
                            serviceStopContainers.put(container, shutdownTime);
                        }
                        
//                        int plannedNumberOfShutdowns = Math.min(serviceStopContainers.size() + MAXIMUM_SIMULTANEOUS_SHUTDOWNS, containerIds.size());
//                        
//                        for (int n = 0; serviceStopContainers.size() < plannedNumberOfShutdowns && n < containerIds.size(); n++)
//                        {
//                            serviceStopContainers.computeIfAbsent(containerIds.get(n), k -> shutdownTime);
//                        }
                        
                        LOGGER.debug(" *** Scheduling shutdown of containers '{}' for service '{}' "
                                + "(scheduled shutdown time: {}) at time {}", containerIds, serviceId, shutdownTime, currentTime);
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
            final Map<NodeIdentifier, LocalTime> potentialShutdownContainers = new HashMap<>(
                    scheduledContainerShutdowns.get(serviceId));

            for (final Map.Entry<NodeIdentifier, LocalTime> entry : potentialShutdownContainers.entrySet()) {
                final NodeIdentifier containerId = entry.getKey();
                final LocalTime shutdownTime = entry.getValue();
                final NodeIdentifier nodeId = containerToNodeMap.get(containerId);

                // remove a container from scheduledContainerShutdowns if the
                // container is no longer supplying a service status or if its
                // status is STOPPED
                if (!containerServiceStates.containsKey(containerId)
                        || containerServiceStates.get(containerId).getStatus().equals(ServiceState.Status.STOPPED)) {
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

        // build up the final plan
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan;
        if (AgentConfiguration.getInstance().isRlgNullOverflowPlan()) {
            overflowPlan = ImmutableMap.of();
        } else {
            overflowPlan = computeOverflowPlan(dcopPlan);
        }

        final LoadBalancerPlan plan = newServicePlan.toLoadBalancerPlan(regionServiceState, overflowPlan);

        // tell other RLGs about the services running in this region
        LOGGER.trace("Sharing information about services: {}", newRlgShared);
        rlgInfoProvider.setLocalRlgSharedInformation(newRlgShared);

        return plan;
    }

    private Set<ServiceIdentifier<?>> getDownscaleableServices() {
        Set<ServiceIdentifier<?>> downscaleableServices = new HashSet<>();

        RegionServiceState regionServiceState = rlgInfoProvider.getRegionServiceState();
        Map<ServiceIdentifier<?>, Integer> serviceInstances = new HashMap<>();

        regionServiceState.getServiceReports().forEach((report) -> {
            report.getServiceState().forEach((containerId, serviceState) -> {
                serviceInstances.merge(serviceState.getService(), 1, Integer::sum);
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

    private Map<NodeIdentifier, NodeIdentifier> createContainerToNodeMap() {
        Map<NodeIdentifier, NodeIdentifier> containerToNodeMap = new HashMap<>();

        Set<ResourceReport> resourceReports = rlgInfoProvider.getRegionNodeState().getNodeResourceReports();

        for (ResourceReport resourceReport : resourceReports) {
            NodeIdentifier nodeId = resourceReport.getNodeName();

            resourceReport.getContainerReports().forEach((containerId, containerReport) -> {
                containerToNodeMap.put(containerId, nodeId);
            });
        }

        LOGGER.debug("containerToNodeMap: {}", containerToNodeMap);

        return containerToNodeMap;
    }

    
    
    private Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute<?>, Double>>> getTotalContainerLoadByService(
            Map<NodeIdentifier, ServiceState> containerServiceStates, NodeAttribute<?> loadAttribute)
    {
        // determine the total amount of load on each container
        final Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute<?>, Double>>> totalContainerLoadByService = new HashMap<>();

        LOGGER.debug("findServiceContainerWithSmallestLoad: containerServiceStates: {}", containerServiceStates);

        Set<ResourceReport> resourceReports = rlgInfoProvider.getRegionNodeState().getNodeResourceReports();

        for (ResourceReport resourceReport : resourceReports) {
            
            resourceReport.getContainerReports().forEach((containerId, containerReport) -> {
                ServiceIdentifier<?> serviceId = containerReport.getService();
                ServiceState serviceState = containerServiceStates.get(containerId);

                if (serviceId != null && serviceState != null) {
                    if (ServiceState.Status.RUNNING.equals(serviceState.getStatus())) {
                        Map<NodeIdentifier, Map<NodeAttribute<?>, Double>> totalLoadByContainer = totalContainerLoadByService
                                .computeIfAbsent(containerReport.getService(), (service) -> new HashMap<>());

                        Map<NodeAttribute<?>, Double> totalLoad = totalLoadByContainer.computeIfAbsent(containerId,
                                (container) -> new HashMap<>());

                        containerReport.getComputeLoad().forEach((clientNodeId, loadFromNode) -> {
                            loadFromNode.forEach((attr, value) -> {
                                totalLoad.merge(attr, value, Double::sum);
                            });
                        });
                    } else {
                        LOGGER.debug(
                                "findServiceContainerWithSmallestLoad: Container '{}' was not considered for smallest load because it has service state '{}'.",
                                containerId, serviceState);
                    }
                } else {
                    if (serviceId == null) {
                        LOGGER.debug(
                                "findServiceContainerWithSmallestLoad: Container '{}' has report with null service.",
                                containerId);
                    }

                    if (serviceState == null) {
                        LOGGER.debug(
                                "findServiceContainerWithSmallestLoad: Container '{}' has no available service state.",
                                containerId);
                    }
                }

            });
        }

        LOGGER.debug("totalContainerLoadByService: {}", totalContainerLoadByService);
        
        return totalContainerLoadByService;
    }
    
    
    
    private Map<ServiceIdentifier<?>, List<NodeIdentifier>> sortServiceContainersByAscendingLoadLoad(
            Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute<?>, Double>>> totalContainerLoadByService,
            //Map<NodeIdentifier, ServiceState> containerServiceStates,
            NodeAttribute<?> loadAttribute) {
        
        final Map<ServiceIdentifier<?>, List<NodeIdentifier>> ascendingLoadContainersByService = new HashMap<>();
        
        
//        // determine the total amount of load on each container
//        final Map<ServiceIdentifier<?>, Map<NodeIdentifier, Map<NodeAttribute<?>, Double>>> totalContainerLoadByService = new HashMap<>();
//
//        LOGGER.debug("findServiceContainerWithSmallestLoad: containerServiceStates: {}", containerServiceStates);
//
//        Set<ResourceReport> resourceReports = rlgInfoProvider.getRegionNodeState().getNodeResourceReports();
//
//        for (ResourceReport resourceReport : resourceReports) {
//            
//            resourceReport.getContainerReports().forEach((containerId, containerReport) -> {
//                ServiceIdentifier<?> serviceId = containerReport.getService();
//                ServiceState serviceState = containerServiceStates.get(containerId);
//
//                if (serviceId != null && serviceState != null) {
//                    if (serviceState.getStatus().equals(ServiceState.Status.RUNNING)) {
//                        Map<NodeIdentifier, Map<NodeAttribute<?>, Double>> totalLoadByContainer = totalContainerLoadByService
//                                .computeIfAbsent(containerReport.getService(), (service) -> new HashMap<>());
//
//                        Map<NodeAttribute<?>, Double> totalLoad = totalLoadByContainer.computeIfAbsent(containerId,
//                                (container) -> new HashMap<>());
//
//                        containerReport.getComputeLoad().forEach((clientNodeId, loadFromNode) -> {
//                            loadFromNode.forEach((attr, value) -> {
//                                totalLoad.merge(attr, value, Double::sum);
//                            });
//                        });
//                    } else {
//                        LOGGER.debug(
//                                "findServiceContainerWithSmallestLoad: Container '{}' was not considered for smallest load because it has service state '{}'.",
//                                containerId, serviceState);
//                    }
//                } else {
//                    if (serviceId == null) {
//                        LOGGER.debug(
//                                "findServiceContainerWithSmallestLoad: Container '{}' has report with null service.",
//                                containerId);
//                    }
//
//                    if (serviceState == null) {
//                        LOGGER.debug(
//                                "findServiceContainerWithSmallestLoad: Container '{}' has no available service state.",
//                                containerId);
//                    }
//                }
//
//            });
//        }
//
//        LOGGER.debug("totalContainerLoadByService: {}", totalContainerLoadByService);        
        
        // create a list of containers ascending by load for each service
        totalContainerLoadByService.forEach((service, totalContainerLoads) ->
        {
            List<NodeIdentifier> ascendingLoadContainers = new LinkedList<>();
            ascendingLoadContainers.addAll(totalContainerLoads.keySet());
            
            // sort containers in ascending order by load for loadAttribute
            Collections.sort(ascendingLoadContainers, new Comparator<NodeIdentifier>()
            {
                @Override
                public int compare(NodeIdentifier a, NodeIdentifier b)
                {
                    return (int)Math.signum(totalContainerLoads.get(a).getOrDefault(loadAttribute, 0.0) -
                            totalContainerLoads.get(b).getOrDefault(loadAttribute, 0.0));
                }
            });
            
              ascendingLoadContainersByService.put(service, ascendingLoadContainers);
        });
        
        
        LOGGER.debug("ascendingLoadContainers: {}", ascendingLoadContainersByService);
        
        return ascendingLoadContainersByService;
    }

    private Map<NodeIdentifier, ServiceState> getContainerServiceStates() {
        Set<ServiceReport> serviceReports = rlgInfoProvider.getRegionServiceState().getServiceReports();
        Map<NodeIdentifier, ServiceState> states = new HashMap<>();

        serviceReports.forEach((serviceReport) -> {
            serviceReport.getServiceState().forEach((containerId, state) -> {
                states.put(containerId, state);
            });
        });

        return states;
    }

    /**
     * Take the DCOP plan and filter out overflow to regions that don't already
     * have the specified service running. Also filter out this region.
     */
    private ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> computeOverflowPlan(
            final RegionPlan dcopPlan) {

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
            final Map<NodeIdentifier, ServiceReport> serviceReportMap) {

        final LoadBalancerPlanBuilder newServicePlan = new LoadBalancerPlanBuilder(rlgInfoProvider.getRlgPlan(),
                rlgInfoProvider.getRegionServiceState());

        LOGGER.debug("Base plan {} from {}", newServicePlan, rlgInfoProvider.getRegionServiceState());

        return newServicePlan;
    }

    /**
     * 
     * @return nodeid -> available containers
     */
    private Map<NodeIdentifier, Integer> findNodesWithAvailableContainerCapacity(
            final Map<NodeIdentifier, ResourceReport> reports,
            final Map<NodeIdentifier, ServiceReport> serviceReportMap,
            final LoadBalancerPlanBuilder newServicePlan) {

        final Map<NodeIdentifier, Integer> servicePlanUsedContainers = new HashMap<>();
        newServicePlan.getPlan().forEach((node, containers) -> {
            final int runningContainers = containers.stream().mapToInt(info -> !info.isStop() ? 1 : 0).sum();
            servicePlanUsedContainers.put(node, runningContainers);
        });
        LOGGER.trace("servicePlanUsedContainers: {} from: {}", servicePlanUsedContainers, newServicePlan);

        final Map<NodeIdentifier, Integer> availableNodeCapacity = new HashMap<>();

        reports.forEach((nodeid, report) -> {
            final ImmutableMap<NodeAttribute<?>, Double> nodeComputeCapacity = report.getNodeComputeCapacity();
            final int containerCapacity = nodeComputeCapacity.getOrDefault(COMPUTE_LOAD_ATTRIBUTE, 0D).intValue();
            LOGGER.trace("{} container capacity: {}", nodeid, containerCapacity);

            final ServiceReport serviceReport = serviceReportMap.get(nodeid);
            final int serviceReportContainersInUse;
            if (null == serviceReport) {
                serviceReportContainersInUse = 0;
            } else {
                serviceReportContainersInUse = (int) serviceReport.getServiceState().entrySet().stream()
                        .filter(e -> !e.getValue().getStatus().equals(ServiceState.Status.STOPPED)).count();
            }

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
        final RegionNodeState regionState = rlgInfoProvider.getRegionNodeState();
        // assumption is that all resourceReports are for the SHORT estimation
        // window
        final ImmutableSet<ResourceReport> resourceReports = regionState.getNodeResourceReports();
        final RegionIdentifier thisRegion = regionState.getRegion();

        // get and set the shared RLG information through rlgInfoProvider
        final ImmutableMap<RegionIdentifier, RlgSharedInformation> infoFromAllRlgs = rlgInfoProvider
                .getAllRlgSharedInformation();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("shared rlg information: " + infoFromAllRlgs);
        }

        LOGGER.trace("Resource reports: {}", resourceReports);
        LOGGER.trace("DCOP plan: {}", dcopPlan);
        LOGGER.trace("ApplicationManager: {}", applicationManager);

        final RegionServiceState regionServiceState = rlgInfoProvider.getRegionServiceState();
        final ImmutableSet<ServiceReport> serviceStates = regionServiceState.getServiceReports();
        LOGGER.trace("serviceStates: {}", serviceStates);

        // track which services are running and on which node
        final Map<NodeIdentifier, ServiceReport> serviceReportMap = new HashMap<>();
        serviceStates.forEach(sr -> {
            serviceReportMap.put(sr.getNodeName(), sr);
        });
        LOGGER.trace("serviceReportMap: {}", serviceReportMap);

        final LoadBalancerPlanBuilder newServicePlan = createBasePlan(resourceReports, serviceReportMap);
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
                serviceReportMap, newServicePlan);

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

        resourceReports.forEach(report -> {
            NodeIdentifier serverName = report.getNodeName();
            Double serverCapacity = report.getNodeComputeCapacity().get(NodeMetricName.TASK_CONTAINERS);
            if (null == serverCapacity) {
                LOGGER.error("Server capacity for node {} is null, using 0", report.getNodeName());
                serverCapacity = 0D;
            }
            Server server = new Server(serverName, serverCapacity);
            serverCollection.add(server);

            // this is to get list of all services
            for (Map.Entry<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> entry : report
                    .getComputeDemand().entrySet()) {
                LOGGER.info("Service name actual: " + Objects.toString(entry.getKey()));
                ServiceIdentifier<?> serviceName = entry.getKey();

                Double serviceLoad = 0.0;
                for (Map.Entry<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> entry2 : entry.getValue()
                        .entrySet()) {
                    serviceLoad += entry2.getValue().getOrDefault(NodeMetricName.TASK_CONTAINERS, 0D);
                    // for (Map.Entry<NodeAttribute<?>, Double> entry3 :
                    // entry2.getValue().entrySet()) {
                    // serviceLoad = entry3.getValue();
                    // }
                }

                LOGGER.info("Service load actual: " + serviceLoad);

                Service dummyService = new Service(serviceName, serviceLoad, 1);
                serviceCollection.add(dummyService);

                LOGGER.info("Server load: " + Objects.toString(report.getComputeLoad()));
                LOGGER.info("Server demand: " + Objects.toString(report.getComputeDemand()));
            }
        });

        // add new servers at this point
        for (Server server : serverCollection) {
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

        for (Service service : serviceCollection) {
            // if service is not already present, then add it using best fit
            if (oldServices.contains(service.getName())) {
                rlgBins.addBestFit(service.getName(), service.getLoad());
                // System.out.println("RLG: New service added.");
            }
            // else {
            // System.out.println("RLG: Old service, not added.");
            // }
        }
        // bins.print();

        rlgBins.constructRLGPlan(newServicePlan);

        // create the final plan
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan;
        if (AgentConfiguration.getInstance().isRlgNullOverflowPlan()) {
            overflowPlan = ImmutableMap.of();
        } else {
            overflowPlan = computeOverflowPlan(dcopPlan);
        }

        final LoadBalancerPlan plan = newServicePlan.toLoadBalancerPlan(regionServiceState, overflowPlan);

        return plan;
    }

}
