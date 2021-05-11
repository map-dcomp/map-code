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

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.rlg.RlgUtils.LoadPercentages;
import com.bbn.map.utils.MAPServices;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlanBuilder;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceStatus;
import com.google.common.collect.ImmutableSet;

/**
 * Functions used by the stub RLG.
 * 
 * @author jschewe
 *
 */
/* package */ final class StubFunctions {

    private static final Logger LOGGER = LoggerFactory.getLogger(StubFunctions.class);

    /**
     * How many containers to allocate at a time.
     */
    private static final int NUM_CONTAINERS_TO_ALLOCATE = AgentConfiguration.getInstance().getRlgMaxAllocationsPerRoundPerService();

    private StubFunctions() {
    }

    /**
     * Find the node with the most available capacity.
     * 
     * @param nodesWithAvailableCapacity
     *            node to number of container available
     * @return the node with the most container capacity
     */
    public static NodeIdentifier chooseNodeWithGreatestContainerCapacity(
            @Nonnull final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity) {
        if (nodesWithAvailableCapacity.isEmpty()) {
            return null;
        }

        final NodeIdentifier node = nodesWithAvailableCapacity.entrySet().stream().max(Map.Entry.comparingByValue())
                .get().getKey();
        return node;
    }

    private static final Random RANDOM = new Random();

    /**
     * 
     * @param nodesWithAvailableCapacity
     *            nodes to choose from
     * @return a random node
     */
    public static NodeIdentifier chooseRandomNode(final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity) {
        if (nodesWithAvailableCapacity.isEmpty()) {
            return null;
        }

        final int index = RANDOM.nextInt(nodesWithAvailableCapacity.size());
        return nodesWithAvailableCapacity.entrySet().stream().skip(index).findFirst().get().getKey();
    }

    /**
     * Choose an NCP that is currently running the service.
     * 
     * @param nodesWithAvailableCapacity
     *            the nodes to choose from
     * @param newServicePlan
     *            used to determine which nodes are running the service
     * @param service
     *            the service to find a node for
     * @return the node, falls back to random if no node is available that is
     *         running the service
     */
    public static NodeIdentifier chooseNodeRunningService(
            @Nonnull final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final LoadBalancerPlanBuilder newServicePlan) {
        final Set<NodeIdentifier> serviceNodes = getNodesRunningService(service, newServicePlan);

        final Optional<NodeIdentifier> node = nodesWithAvailableCapacity.entrySet().stream().map(Map.Entry::getKey)
                .filter(n -> serviceNodes.contains(n)).findFirst();
        if (node.isPresent()) {
            return node.get();
        } else {
            return chooseRandomNode(nodesWithAvailableCapacity);
        }
    }

    private static boolean isNodeRunningService(final ServiceIdentifier<?> service,
            final Collection<LoadBalancerPlan.ContainerInfo> containerInfos) {
        return containerInfos.stream()
                .filter(info -> !info.isStop() && !info.isStopTrafficTo() && service.equals(info.getService()))
                .findFirst().isPresent();
    }

    private static Set<NodeIdentifier> getNodesRunningService(final ServiceIdentifier<?> service,
            final LoadBalancerPlanBuilder newServicePlan) {
        final Set<NodeIdentifier> serviceNodes = newServicePlan.getPlan().entrySet().stream()
                .filter(e -> isNodeRunningService(service, e.getValue())).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return serviceNodes;
    }

    /**
     * Choose an NCP that is currently not running the service.
     * 
     * @param nodesWithAvailableCapacity
     *            the nodes to choose from
     * @param newServicePlan
     *            used to determine which nodes are running the service
     * @param service
     *            the service to find a node for
     * @return the node, falls back to random if no node is available that is
     *         running the service
     */
    public static NodeIdentifier chooseNodeNotRunningService(
            @Nonnull final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final LoadBalancerPlanBuilder newServicePlan) {

        final Set<NodeIdentifier> serviceNodes = getNodesRunningService(service, newServicePlan);

        final Optional<NodeIdentifier> node = nodesWithAvailableCapacity.entrySet().stream().map(Map.Entry::getKey)
                .filter(n -> !serviceNodes.contains(n)).findFirst();
        if (node.isPresent()) {
            return node.get();
        } else {
            return chooseRandomNode(nodesWithAvailableCapacity);
        }
    }

    private static final class CompareByTaskContainers implements Comparator<Map.Entry<?, Map<NodeAttribute, Double>>> {
        public static final CompareByTaskContainers INSTANCE = new CompareByTaskContainers();

        public int compare(final Map.Entry<?, Map<NodeAttribute, Double>> o1,
                final Map.Entry<?, Map<NodeAttribute, Double>> o2) {
            final Double v1 = o1.getValue().getOrDefault(NodeAttribute.TASK_CONTAINERS, 0D);
            final Double v2 = o2.getValue().getOrDefault(NodeAttribute.TASK_CONTAINERS, 0D);
            return Double.compare(v1, v2);
        }
    }

    public static NodeIdentifier chooseNodeWithLowestOverallLoad(
            @Nonnull final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            @Nonnull final LoadPercentages loadPercentages) {

        if (nodesWithAvailableCapacity.isEmpty()) {
            return null;
        }

        final NodeIdentifier node = loadPercentages.allocatedLoadPercentagePerNode.entrySet().stream()
                .filter(e -> nodesWithAvailableCapacity.containsKey(e.getKey())).min(CompareByTaskContainers.INSTANCE)
                .get().getKey();

        return node;
    }

    public static void allocateContainersForOverloadedServices(@Nonnull ServicePriorityManager servicePriorityManager,
            @Nonnull final ImmutableSet<ResourceReport> resourceReports,
            @Nonnull final LoadBalancerPlanBuilder newServicePlan,
            @Nonnull final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            @Nonnull final List<ServiceIdentifier<?>> overloadedServices,
            @Nonnull final LoadPercentages loadPercentages) {

        final Map<ServiceIdentifier<?>, Integer> runningContainers = new HashMap<>();

        resourceReports.forEach(resourceReport -> {
            resourceReport.getContainerReports().forEach((containerId, containerReport) -> {
                final ServiceStatus serviceStatus = containerReport.getServiceStatus();
                if (ServiceStatus.RUNNING.equals(serviceStatus) || ServiceStatus.STARTING.equals(serviceStatus)) {
                    final ServiceIdentifier<?> service = containerReport.getService();
                    runningContainers.merge(service, 1, Integer::sum);
                }
            });
        });

        // count how many containers are planned for each service
        final Map<ServiceIdentifier<?>, Integer> plannedContainers = new HashMap<>();
        newServicePlan.getPlan().forEach((node, containerInfos) -> {
            containerInfos.forEach(info -> {
                if (!info.isStop() && !info.isStopTrafficTo()) {
                    plannedContainers.merge(info.getService(), 1, Integer::sum);
                }
            });
        });

        if (!overloadedServices.isEmpty()) {
            // Add a new node for each node that is too busy

            List<ServiceIdentifier<?>> allocationServices = servicePriorityManager.getPriorityServiceAllocationList()
                    .stream().filter(s -> overloadedServices.contains(s)).collect(Collectors.toList());

            for (ServiceIdentifier<?> service : allocationServices) {
                if (MAPServices.UNPLANNED_SERVICES.contains(service)) {
                    LOGGER.warn("Service {} is overloaded, but MAP does not manage the service.", service);
                } else {

                    final int servicePlannedContainers = plannedContainers.getOrDefault(service, 0);
                    final int serviceRunningContainers = runningContainers.getOrDefault(service, 0);

                    if (servicePlannedContainers <= serviceRunningContainers) {
                        // If the number of planned containers is less than the
                        // number of currently running containers and the
                        // service is
                        // overloaded, then we can add more nodes.
                        //
                        // If the number of planned containers is more than the
                        // number of running containers, then we are in a
                        // situation
                        // where the plan has not been realized yet, so we don't
                        // want to add more containers as this can cause
                        // over-allocation.

                        if (!nodesWithAvailableCapacity.isEmpty()) {
                            final NodeIdentifier newNode = chooseNode(service, newServicePlan,
                                    nodesWithAvailableCapacity, loadPercentages);

                            LOGGER.debug("allocateContainers: newNode = {}, nodesWithAvailableCapacity = {}", newNode,
                                    nodesWithAvailableCapacity);

                            if (null != newNode) {
                                LOGGER.debug("allocateContainers: nodesWithAvailableCapacity.get(newNode) = {}",
                                        nodesWithAvailableCapacity.get(newNode));

                                allocateContainers(servicePriorityManager, service, newNode, NUM_CONTAINERS_TO_ALLOCATE,
                                        newServicePlan, nodesWithAvailableCapacity);
                            } else {                                
                                servicePriorityManager.requestAllocation(service, NUM_CONTAINERS_TO_ALLOCATE);
                            }

                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("Overloaded service: " + service + " new node: " + newNode);
                            }

                        } else {
                            LOGGER.warn("Service {} is overloaded, but there are no nodes with available capacity. "
                                    + "No more containers can be immediately added to the plan, "
                                    + "but more containers are being requested.", service);

                            servicePriorityManager.requestAllocation(service, NUM_CONTAINERS_TO_ALLOCATE);
                        }
                    } else {
                        LOGGER.warn(
                                "Service {} is overloaded. However the plan already wants to allocate more containers ({}) "
                                + "than are currently running ({}). No more containers will be added to the plan at this time. "
                                + "It is assumed that the node will start up more containers soon.",
                                service, servicePlannedContainers, serviceRunningContainers);
                    }
                } // MAP managed service
            } // for each overloaded service

        } // something to do
    }

    /**
     * Allocate containers of service on the specified node. Up to maxContainers
     * will be allocated based on available capacity.
     * 
     * @param service
     *            the service being allocated
     * @param newNode
     *            the node to allocate the service on
     * @param maxContainers
     *            the maximum number of containers to allocate
     * @param newServicePlan
     *            the plan
     * @param nodesWithAvailableCapacity
     *            information about which nodes have available capacity, updated
     *            by this method
     */
    public static void allocateContainers(@Nonnull final ServicePriorityManager servicePriorityManager,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final NodeIdentifier newNode,
            final int maxContainers,
            @Nonnull final LoadBalancerPlanBuilder newServicePlan,
            @Nonnull final Map<NodeIdentifier, Integer> nodesWithAvailableCapacity) {
        if (!nodesWithAvailableCapacity.containsKey(newNode) || null == nodesWithAvailableCapacity.get(newNode)) {
            LOGGER.error("Unable to allocate {} containers on {}. availableCapacity: {}", maxContainers,
                    newNode.getName(), nodesWithAvailableCapacity);
            return;
        }
        final int availCap = nodesWithAvailableCapacity.get(newNode);
        if (availCap < 1) {
            LOGGER.error("Unable to allocate {} containers on {}. availableCapacity: {}", maxContainers,
                    newNode.getName(), nodesWithAvailableCapacity);
            return;
        }

        final int maxUsedCap = Math.min(availCap, maxContainers);
        final int usedCap = (int) Math.floor(servicePriorityManager.requestAllocation(service, maxUsedCap));
        for (int i = 0; i < usedCap; ++i) {
            newServicePlan.addService(newNode, service, 1);
        }

        final int newCapacity = availCap - usedCap;
        if (newCapacity <= 0) {
            nodesWithAvailableCapacity.remove(newNode);
        } else {
            nodesWithAvailableCapacity.put(newNode, newCapacity);
        }

        LOGGER.trace("newNode: {} availCap: {} usedCap: {} newCapacity: {} nodesAvailable: {}", newNode, availCap,
                usedCap, newCapacity, nodesWithAvailableCapacity);
        LOGGER.trace("New plan after allocation: {}", newServicePlan);
    }

    /**
     * Choose a node to run the specified service on.
     * 
     * @param service
     *            the service to find a node for
     * @param newServicePlan
     *            the plan that is being built
     * @param nodesWithAvailableCapacity
     *            information about what nodes have capacity
     * @param loadPercentages
     *            the load information
     * @return the node to use or null if there are no nodes with available
     *         capacity
     */
    public static NodeIdentifier chooseNode(@Nonnull final ServiceIdentifier<?> service,
            @Nonnull final LoadBalancerPlanBuilder newServicePlan,
            @Nonnull final SortedMap<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            @Nonnull final LoadPercentages loadPercentages) {

        if (nodesWithAvailableCapacity.isEmpty()) {
            LOGGER.error("Asking to choose a node for service {} and there are no nodes with available capacity",
                    service);
            return null;
        }

        final AgentConfiguration.RlgStubChooseNcp chooseAlgorithm = AgentConfiguration.getInstance()
                .getRlgStubChooseNcp();

        switch (chooseAlgorithm) {
        case MOST_AVAILABLE_CONTAINERS:
            return StubFunctions.chooseNodeWithGreatestContainerCapacity(nodesWithAvailableCapacity);
        case RANDOM:
            return StubFunctions.chooseRandomNode(nodesWithAvailableCapacity);
        case CURRENTLY_NOT_RUNNING_SERIVCE:
            return StubFunctions.chooseNodeNotRunningService(nodesWithAvailableCapacity, service, newServicePlan);
        case CURRENTLY_RUNNING_SERVICE:
            return StubFunctions.chooseNodeRunningService(nodesWithAvailableCapacity, service, newServicePlan);
        case LOWEST_LOAD_PERCENTAGE:
            return chooseNodeWithLowestOverallLoad(nodesWithAvailableCapacity, loadPercentages);
        default:
            throw new IllegalArgumentException("Unknown stub NCP choose algorithm: " + chooseAlgorithm);
        }
    }
}
