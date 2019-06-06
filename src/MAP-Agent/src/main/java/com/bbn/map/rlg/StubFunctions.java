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

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.map.rlg.RlgUtils.LoadPercentages;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlanBuilder;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.google.common.collect.ImmutableSet;

/**
 * Functions used by the stub RLG.
 * 
 * @author jschewe
 *
 */
/* package */ final class StubFunctions {

    private static final Logger LOGGER = LoggerFactory.getLogger(StubFunctions.class);

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
            @Nonnull final Map<NodeIdentifier, Integer> nodesWithAvailableCapacity) {
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
    public static NodeIdentifier chooseRandomNode(final Map<NodeIdentifier, Integer> nodesWithAvailableCapacity) {
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
            @Nonnull final Map<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final LoadBalancerPlanBuilder newServicePlan) {
        final Set<NodeIdentifier> serviceNodes = getNodesRunningService(service, newServicePlan);

        final Optional<NodeIdentifier> node = nodesWithAvailableCapacity.entrySet().stream().map(Map.Entry::getKey)
                .filter(n -> serviceNodes.contains(n)).findAny();
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
                .findAny().isPresent();
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
            @Nonnull final Map<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            @Nonnull final ServiceIdentifier<?> service,
            @Nonnull final LoadBalancerPlanBuilder newServicePlan) {

        final Set<NodeIdentifier> serviceNodes = getNodesRunningService(service, newServicePlan);

        final Optional<NodeIdentifier> node = nodesWithAvailableCapacity.entrySet().stream().map(Map.Entry::getKey)
                .filter(n -> !serviceNodes.contains(n)).findAny();
        if (node.isPresent()) {
            return node.get();
        } else {
            return chooseRandomNode(nodesWithAvailableCapacity);
        }
    }

    private static final class CompareByTaskContainers
            implements Comparator<Map.Entry<?, Map<NodeAttribute<?>, Double>>> {
        public static final CompareByTaskContainers INSTANCE = new CompareByTaskContainers();

        public int compare(final Map.Entry<?, Map<NodeAttribute<?>, Double>> o1,
                final Map.Entry<?, Map<NodeAttribute<?>, Double>> o2) {
            final Double v1 = o1.getValue().getOrDefault(NodeMetricName.TASK_CONTAINERS, 0D);
            final Double v2 = o2.getValue().getOrDefault(NodeMetricName.TASK_CONTAINERS, 0D);
            return Double.compare(v1, v2);
        }
    }

    public static NodeIdentifier chooseNodeWithLowestOverallLoad(
            @Nonnull final Map<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            @Nonnull final LoadPercentages loadPercentages) {

        if (nodesWithAvailableCapacity.isEmpty()) {
            return null;
        }

        final NodeIdentifier node = loadPercentages.allocatedLoadPercentagePerNode.entrySet().stream()
                .filter(e -> nodesWithAvailableCapacity.containsKey(e.getKey())).min(CompareByTaskContainers.INSTANCE)
                .get().getKey();

        return node;
    }

    public static void allocateContainers(@Nonnull final ImmutableSet<ServiceReport> serviceStates,
            @Nonnull final LoadBalancerPlanBuilder newServicePlan,
            @Nonnull final Map<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            @Nonnull final List<ServiceIdentifier<?>> overloadedServices,
            @Nonnull final LoadPercentages loadPercentages) {

        final Map<ServiceIdentifier<?>, Integer> runningContainers = new HashMap<>();

        serviceStates.forEach(sreport -> {
            final Stream<ServiceState> reportRunningContainers = sreport.getServiceState().entrySet().stream()
                    .map(Map.Entry::getValue).filter(s -> ServiceState.Status.RUNNING.equals(s.getStatus())
                            || ServiceState.Status.STARTING.equals(s.getStatus()));
            reportRunningContainers.forEach(sState -> {
                final ServiceIdentifier<?> service = sState.getService();
                runningContainers.merge(service, 1, Integer::sum);
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

        final AgentConfiguration.RlgStubChooseNcp chooseAlgorithm = AgentConfiguration.getInstance()
                .getRlgStubChooseNcp();
        if (!overloadedServices.isEmpty() && !nodesWithAvailableCapacity.isEmpty()) {
            // Add a new node for each node that is too busy

            overloadedServices.forEach(service -> {
                final int servicePlannedContainers = plannedContainers.getOrDefault(service, 0);
                final int serviceRunningContainers = runningContainers.getOrDefault(service, 0);

                if (nodesWithAvailableCapacity.isEmpty()) {
                    LOGGER.warn(
                            "Service {} is overloaded, but there are no nodes with available capacity. No more containers can be added to the plan",
                            service);
                } else if (servicePlannedContainers <= serviceRunningContainers) {
                    final NodeIdentifier newNode;
                    switch (chooseAlgorithm) {
                    case MOST_AVAILABLE_CONTAINERS:
                        newNode = StubFunctions.chooseNodeWithGreatestContainerCapacity(nodesWithAvailableCapacity);
                        break;
                    case RANDOM:
                        newNode = StubFunctions.chooseRandomNode(nodesWithAvailableCapacity);
                        break;
                    case CURRENTLY_NOT_RUNNING_SERIVCE:
                        newNode = StubFunctions.chooseNodeNotRunningService(nodesWithAvailableCapacity, service,
                                newServicePlan);
                        break;
                    case CURRENTLY_RUNNING_SERVICE:
                        newNode = StubFunctions.chooseNodeRunningService(nodesWithAvailableCapacity, service,
                                newServicePlan);
                        break;
                    case LOWEST_LOAD_PERCENTAGE:
                        newNode = chooseNodeWithLowestOverallLoad(nodesWithAvailableCapacity, loadPercentages);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown stub NCP choose algorithm: " + chooseAlgorithm);
                    }

                    LOGGER.debug("allocateContainers: newNode = {}, nodesWithAvailableCapacity = {}", newNode,
                            nodesWithAvailableCapacity);
                    LOGGER.debug("allocateContainers: nodesWithAvailableCapacity.get(newNode) = {}",
                            nodesWithAvailableCapacity.get(newNode));

                    if (null != newNode) {
                        final int availCap = nodesWithAvailableCapacity.get(newNode);

                        final int usedCap = Math.min(availCap, 10);
                        for (int i = 0; i < usedCap; ++i) {
                            newServicePlan.addService(newNode, service, 1);
                        }

                        final int newCapacity = availCap - usedCap;
                        if (newCapacity <= 0) {
                            nodesWithAvailableCapacity.remove(newNode);
                        } else {
                            nodesWithAvailableCapacity.put(newNode, newCapacity);
                        }

                        LOGGER.trace("newNode: {} availCap: {} usedCap: {} newCapacity: {} nodesAvailable: {}", newNode,
                                availCap, usedCap, newCapacity, nodesWithAvailableCapacity);
                        LOGGER.trace("New plan after allocation: {}", newServicePlan);
                    }

                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Overloaded service: " + service + " new node: " + newNode);
                    }
                } else {
                    LOGGER.warn(
                            "Service {} is overloaded. However the plan already wants to allocate more containers ({}) than are currently running ({}). No more containers will be added to the plan at this time. It is assumed that the node will start up more containers soon.",
                            service, servicePlannedContainers, serviceRunningContainers);
                }
            }); // foreach overloaded service

        } // something to do
    }
}
