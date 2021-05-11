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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionServiceState;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.bbn.protelis.networkresourcemanagement.ServiceStatus;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java8.util.Sets;

/**
 * Provides useful functions for testing RLG implementations.
 */
public final class RlgTestUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(RlgTestUtils.class);

    private RlgTestUtils() {
    }

    /**
     * Counts the number of instances for each service on each node in the given
     * set of {@link ResourceReport}s.
     * 
     * @param reports
     *            the {@link ResourceReport}s to count instances from
     * @return the number of instances for each discovered service on each
     *         discovered node
     */
    public static Map<ServiceIdentifier<?>, Map<NodeIdentifier, Integer>> countServiceNodeInstances(
            Set<ResourceReport> reports) {
        Map<ServiceIdentifier<?>, Map<NodeIdentifier, Integer>> instances = new HashMap<>();

        for (ResourceReport report : reports) {
            for (ImmutableMap.Entry<NodeIdentifier, ContainerResourceReport> entry : report.getContainerReports()
                    .entrySet()) {
                NodeIdentifier nodeId = entry.getKey();
                ContainerResourceReport containerReport = entry.getValue();
                ServiceIdentifier<?> serviceId = containerReport.getService();

                instances.computeIfAbsent(serviceId, k -> new HashMap<>()).merge(nodeId, 1, Integer::sum);
            }
        }

        return instances;
    }

    /**
     * Counts the number of instances for each service in the given set of
     * {@link ResourceReport}s.
     * 
     * @param reports
     *            the {@link ResourceReport}s to count instances from
     * @return the number of instances for each discovered service
     */
    public static Map<ServiceIdentifier<?>, Integer> countServiceInstances(Set<ResourceReport> reports) {
        Map<ServiceIdentifier<?>, Integer> instances = new HashMap<>();

        Map<ServiceIdentifier<?>, Map<NodeIdentifier, Integer>> serviceNodeInstances = countServiceNodeInstances(
                reports);
        serviceNodeInstances.forEach((service, nodeInstances) -> {
            nodeInstances.forEach((node, instanceCount) -> {
                instances.merge(service, instanceCount, Integer::sum);
            });
        });

        return instances;
    }

    /**
     * Counts the number of instances for each service on each node in the given
     * {@link LoadBalancerPlan}.
     * 
     * @param rlgPlan
     *            the {@link LoadBalancerPlan} to count instances from
     * @return the number of instances for each discovered service on each
     *         discovered node
     */
    public static Map<ServiceIdentifier<?>, Map<NodeIdentifier, Integer>> countServiceNodeInstances(
            LoadBalancerPlan rlgPlan) {
        Map<ServiceIdentifier<?>, Map<NodeIdentifier, Integer>> instances = new HashMap<>();

        rlgPlan.getServicePlan().forEach((node, containerInfos) -> {
            containerInfos.forEach((containerInfo) -> {
                instances.computeIfAbsent(containerInfo.getService(), k -> new HashMap<>()).merge(node, 1,
                        Integer::sum);
            });
        });

        return instances;
    }

    /**
     * Counts the number of instances for each service in the given
     * {@link LoadBalancerPlan}.
     * 
     * @param rlgPlan
     *            the {@link LoadBalancerPlan} to count instances from
     * @return the number of instances for each discovered service
     */
    public static Map<ServiceIdentifier<?>, Integer> countServiceInstances(LoadBalancerPlan rlgPlan) {
        Map<ServiceIdentifier<?>, Integer> instances = new HashMap<>();

        Map<ServiceIdentifier<?>, Map<NodeIdentifier, Integer>> serviceNodeInstances = countServiceNodeInstances(
                rlgPlan);
        serviceNodeInstances.forEach((service, nodeInstances) -> {
            nodeInstances.forEach((node, instanceCount) -> {
                instances.merge(service, instanceCount, Integer::sum);
            });
        });

        return instances;
    }

    /**
     * Creates a {@link ContainerResourceReport} with the given values.
     * 
     * @param time
     *            the timestamp of the report
     * @param containerName
     *            the container identifier for the container of this report
     * @param service
     *            the service for the container
     * @param load
     *            the load on the container
     * @param averageProcessingTime
     *            the average server processing time of requests for the
     *            container
     * @return a new container resource report with the given criteria
     */
    public static ContainerResourceReport createContainerReport(long time,
            NodeIdentifier containerName,
            ServiceIdentifier<?> service,
            Map<NodeAttribute, Double> load,
            double averageProcessingTime) {
        NodeIdentifier client1 = new DnsNameIdentifier("client1");

        ContainerResourceReport containerResourceReport = new ContainerResourceReport(containerName, time, service,
                ServiceStatus.RUNNING, EstimationWindow.SHORT, ImmutableMap.of(NodeAttribute.TASK_CONTAINERS, 1.0),
                ImmutableMap.of(client1, ImmutableMap.copyOf(load)),
                ImmutableMap.of(client1, ImmutableMap.copyOf(load)), averageProcessingTime);

        return containerResourceReport;
    }

    /**
     * Creates a {@link ResourceReport} with the given values.
     * 
     * @param time
     *            the timestamp of the report
     * @param node
     *            the node identifier for the node of this report
     * @param maxContainers
     *            the capacity of the node in number of containers
     * @param capacity
     *            the capacity of the node
     * @param containerReports
     *            the {@link ContainerResourceReport}s to include in this report
     * @return a new report with the given criteria
     */
    public static ResourceReport createResourceReport(long time,
            NodeIdentifier node,
            int maxContainers,
            Map<NodeAttribute, Double> capacity,
            Set<ContainerResourceReport> containerReports) {
        Map<NodeIdentifier, ContainerResourceReport> containerReportsMap = new HashMap<>();
        containerReports.forEach((report) -> {
            containerReportsMap.put(report.getContainerName(), report);
        });

        ResourceReport resourceReport = new ResourceReport(node, time, EstimationWindow.SHORT,
                ImmutableMap.copyOf(capacity), ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(),
                ImmutableMap.copyOf(containerReportsMap), maxContainers, containerReports.size());

        return resourceReport;
    }

    // /**
    // * Creates a new set of {@link ResourceReport}s for a test.
    // *
    // * @param serviceLoads
    // * the amount of load to report for each service
    // * @return a set of new reports
    // */
    // public static Set<ResourceReport> initializeResourceReports(
    // Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> serviceLoads)
    // {
    // // TODO
    // return null;
    // }

    /**
     * Creates an initial {@link RegionData} object for a region with the given
     * number of nodes.
     * 
     * @param region
     *            the region identifier
     * @param nodes
     *            the number of nodes in the region
     * @param capacityPerNode
     *            the capacity of each node in the region
     * @param maxContainersPerNode
     *            the maximum number of containers for each node in the region
     * @return the new {@link RegionData} object for the region
     */
    public static RegionData createInitialRegionData(RegionIdentifier region,
            int nodes,
            Map<NodeAttribute, Double> capacityPerNode,
            int maxContainersPerNode) {
        Set<NodeIdentifier> activeNodes = new HashSet<>();
        Map<NodeIdentifier, Map<NodeAttribute, Double>> nodeCapacities = new HashMap<>();
        Map<NodeIdentifier, Integer> nodeMaxContainers = new HashMap<>();

        for (int n = 0; n < nodes; n++) {
            NodeIdentifier node = new DnsNameIdentifier("node" + region.getName() + n);
            activeNodes.add(node);
            nodeCapacities.put(node, capacityPerNode);
            nodeMaxContainers.put(node, maxContainersPerNode);
        }

        RegionData regionData = new RegionData(region, activeNodes, nodeCapacities, nodeMaxContainers);
        regionData = updateRegionData(0, regionData, new LoadBalancerPlan(region, ImmutableMap.of(), ImmutableMap.of()),
                new HashMap<>());

        return regionData;
    }

    /**
     * Stores current data for {@link ResourceReports}s, the
     * {@link RegionServiceState}, and node capacities.
     * 
     * @author awald
     *
     */
    static class RegionData {
        private RegionIdentifier region;

        private Set<NodeIdentifier> activeNodes = new HashSet<>();

        private Map<NodeIdentifier, Map<NodeAttribute, Double>> nodeCapacities;
        private Map<NodeIdentifier, Integer> nodeMaxContainers;

        private Set<ResourceReport> resourceReports;
        private RegionServiceState regionServiceState;

        RegionData(RegionIdentifier region,
                Set<NodeIdentifier> activeNodes,
                Map<NodeIdentifier, Map<NodeAttribute, Double>> nodeCapacities,
                Map<NodeIdentifier, Integer> nodeMaxContainers) {
            this.region = region;

            this.activeNodes = activeNodes;
            this.nodeCapacities = nodeCapacities;
            this.nodeMaxContainers = nodeMaxContainers;
            this.resourceReports = new HashSet<>();
            this.regionServiceState = new RegionServiceState(region, ImmutableSet.of());
        }

        RegionData(RegionData oldRegionData) {
            this.region = oldRegionData.region;

            this.activeNodes = oldRegionData.activeNodes;
            this.nodeCapacities = oldRegionData.nodeCapacities;
            this.nodeMaxContainers = oldRegionData.nodeMaxContainers;

            this.resourceReports = oldRegionData.resourceReports;
            this.regionServiceState = oldRegionData.regionServiceState;
        }

        Set<ResourceReport> getResourceReports() {
            return resourceReports;
        }

        Set<NodeIdentifier> getActiveNodes() {
            return activeNodes;
        }

        void setActiveNodes(Set<NodeIdentifier> activeNodes) {
            this.activeNodes = activeNodes;
        }

        Map<NodeIdentifier, Map<NodeAttribute, Double>> getNodeCapacities() {
            return nodeCapacities;
        }

        Map<NodeAttribute, Double> getRegionCapacities() {
            Map<NodeAttribute, Double> regionCapacities = new HashMap<>();

            getNodeCapacities().forEach((node, capacityValues) -> {
                capacityValues.forEach((attr, value) -> {
                    regionCapacities.merge(attr, value, Double::sum);
                });
            });

            return regionCapacities;
        }

        void setNodeCapacities(Map<NodeIdentifier, Map<NodeAttribute, Double>> nodeCapacities) {
            this.nodeCapacities = nodeCapacities;
        }

        Map<NodeIdentifier, Integer> getNodeMaxContainers() {
            return nodeMaxContainers;
        }

        void setNodeMaxContainers(Map<NodeIdentifier, Integer> nodeMaxContainers) {
            this.nodeMaxContainers = nodeMaxContainers;
        }

        void setResourceReports(Set<ResourceReport> resourceReports) {
            this.resourceReports = resourceReports;
        }

        void setRegionServiceState(RegionServiceState regionServiceState) {
            this.regionServiceState = regionServiceState;
        }

        RegionServiceState getRegionServiceState() {
            return regionServiceState;
        }

        int getMaxContainersForNode(NodeIdentifier node) {
            return nodeMaxContainers.get(node);
        }

        Map<NodeAttribute, Double> getCapacitiesForNode(NodeIdentifier node) {
            return nodeCapacities.get(node);
        }

        RegionIdentifier getRegion() {
            return region;
        }
    }

    /**
     * Updates a {@link RegionServiceState} and set of {@link ResourceReport}s
     * within a {@link RegionData} object according to a
     * {@link LoadBalancerPlan} and new service loads. For testing purposes,
     * this method simulates actual resource reports and service states updating
     * as a result of RLG plans being realized in a region.
     * 
     * @param time
     *            the time stamp to use in the updated reports
     * @param regionData
     *            the region current data to be updated
     * @param rlgPlan
     *            the RLG plan to use to update container reports
     * @param serviceLoads
     *            the new service loads to report
     * @return an object containing a region service state and a new set of
     *         reports that are updated versions of the given set of reports
     */
    public static RegionData updateRegionData(long time,
            RegionData regionData,
            LoadBalancerPlan rlgPlan,
            Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> serviceLoads) {
        Map<NodeIdentifier, Map<ServiceIdentifier<?>, Integer>> plannedNodeInstances = new HashMap<>();
        Map<ServiceIdentifier<?>, Integer> plannedTotalInstances = new HashMap<>();
        Set<NodeIdentifier> stoppedContainers = new HashSet<>();
        processLoadBalancerPlan(rlgPlan, plannedNodeInstances, plannedTotalInstances, stoppedContainers);

        Map<NodeIdentifier, Map<ServiceIdentifier<?>, Set<NodeIdentifier>>> updatedContainers = new HashMap<>();
        Map<NodeIdentifier, Map<ServiceIdentifier<?>, Set<NodeIdentifier>>> newContainers = new HashMap<>();
        Map<ServiceIdentifier<?>, Set<NodeIdentifier>> serviceContainers = new HashMap<>();
        Map<NodeIdentifier, Integer> maxContainers = new HashMap<>();
        Set<NodeIdentifier> activeNodes = Sets.copyOf(regionData.getActiveNodes());

        regionData.getActiveNodes().forEach((activeNode) -> {
            updatedContainers.computeIfAbsent(activeNode, k -> new HashMap<>());
            newContainers.computeIfAbsent(activeNode, k -> new HashMap<>());
        });

        Set<ResourceReport> reports = regionData.getResourceReports();
        RegionServiceState state = regionData.getRegionServiceState();

        reports.forEach((report) -> {
            NodeIdentifier node = report.getNodeName();

            if (!maxContainers.containsKey(node)) {
                maxContainers.put(node, report.getMaximumServiceContainers());
            } else {
                throw new RuntimeException("Found multiple resource reports for node '" + node + "': " + reports);
            }

            report.getContainerReports().forEach((container, containerReport) -> {
                if (updatedContainers.containsKey(node) && !stoppedContainers.contains(container)) {
                    updatedContainers.get(node).computeIfAbsent(containerReport.getService(), k -> new HashSet<>())
                            .add(container);

                    serviceContainers.computeIfAbsent(containerReport.getService(), k -> new HashSet<>())
                            .add(container);
                }
            });
        });

        state.getServiceReports().forEach((serviceReport) -> {
            NodeIdentifier node = serviceReport.getNodeName();

            serviceReport.getServiceState().forEach((container, serviceState) -> {
                if (updatedContainers.containsKey(node) && !stoppedContainers.contains(container)) {
                    updatedContainers.get(node).computeIfAbsent(serviceState.getService(), k -> new HashSet<>())
                            .add(container);

                    serviceContainers.computeIfAbsent(serviceState.getService(), k -> new HashSet<>()).add(container);
                }
            });
        });

        Map<NodeIdentifier, Integer> newNodeInstances = new HashMap<>();

        plannedNodeInstances.forEach((node, serviceInstances) -> {
            serviceInstances.forEach((service, instances) -> {
                while (updatedContainers.get(node).computeIfAbsent(service, k -> new HashSet<>()).size()
                        + newContainers.get(node).computeIfAbsent(service, k -> new HashSet<>()).size() < instances) {
                    // create a new container identifier as if an actual node
                    // were picking a new container id
                    // for a null id in a ContainerInfo object
                    final String newContainerName = node + "_c" + time + "-" + newNodeInstances.getOrDefault(node, 0);
                    final NodeIdentifier newContainerId = new DnsNameIdentifier(newContainerName);
                    newContainers.computeIfAbsent(node, k -> new HashMap<>())
                            .computeIfAbsent(service, k -> new HashSet<>()).add(newContainerId);

                    newNodeInstances.merge(node, 1, Integer::sum);

                    serviceContainers.computeIfAbsent(service, k -> new HashSet<>()).add(newContainerId);
                }

                if (instances > maxContainers.get(node)) {
                    throw new RuntimeException("The planned number of instances on node '" + node + "' (" + instances
                            + ") exceeded the node's capacity of " + maxContainers.get(node) + " containers.");
                }
            });
        });

        LOGGER.debug("updateRegionData: updatedContainers: {}, newContainers: {}", updatedContainers, newContainers);

        // divide load up equally among containers for each service
        Map<ServiceIdentifier<?>, Map<NodeAttribute, Double>> loadPerNode = new HashMap<>();
        serviceLoads.forEach((service, loadAttrValues) -> {
            Map<NodeAttribute, Double> serviceLoad = serviceLoads.get(service);

            serviceLoad.forEach((attr, value) -> {
                loadPerNode.computeIfAbsent(service, k -> new HashMap<>()).put(attr,
                        (value / serviceContainers.get(service).size()));
            });
        });

        Set<ServiceReport> newServiceReports = new HashSet<>();
        final Set<NodeIdentifier> remainingNodes = new HashSet<>();
        remainingNodes.addAll(activeNodes);

        state.getServiceReports().forEach((serviceReport) -> {
            NodeIdentifier node = serviceReport.getNodeName();

            if (remainingNodes.remove(node)) {
                Map<NodeIdentifier, ServiceState> newContainerServiceStates = new HashMap<>();

                serviceReport.getServiceState().forEach((container, serviceState) -> {
                    final ServiceStatus newStatus;

                    // update the container status according to
                    // stoppedContainers and the old status
                    if (stoppedContainers.contains(container)) {
                        // if the container is planned to stop, immediately
                        // switch the state to STOPPED
                        switch (serviceState.getStatus()) {
                        case STARTING:
                        case RUNNING:
                        case STOPPING:
                        case UNKNOWN:
                        default:
                            newStatus = ServiceStatus.STOPPED;
                            break;

                        case STOPPED:
                            newStatus = null;
                        }
                    } else {
                        // if the container is not planned to stop, immediately
                        // switch the state to RUNNING
                        switch (serviceState.getStatus()) {
                        case STARTING:
                        case RUNNING:
                        case STOPPING:
                        case STOPPED:
                        case UNKNOWN:
                        default:
                            newStatus = ServiceStatus.RUNNING;
                            break;
                        }
                    }

                    if (newStatus != null) {
                        ServiceState newServiceState = new ServiceState(serviceState.getService(), newStatus);
                        newContainerServiceStates.put(container, newServiceState);
                    }
                });

                // add new container states according to the plan
                newContainers.get(node).forEach((service, containers) -> {
                    containers.forEach((container) -> {
                        // state is immediately set to RUNNING
                        ServiceState newServiceState = new ServiceState(service, ServiceStatus.RUNNING);
                        newContainerServiceStates.put(container, newServiceState);
                    });
                });

                ServiceReport newServiceReport = new ServiceReport(node, 0,
                        ImmutableMap.copyOf(newContainerServiceStates));
                newServiceReports.add(newServiceReport);
            }
        });

        // add reports for additional nodes as needed
        remainingNodes.forEach((node) -> {
            newServiceReports.add(new ServiceReport(node, 0, ImmutableMap.of()));
        });

        RegionServiceState newRegionServiceState = new RegionServiceState(state.getRegion(),
                ImmutableSet.copyOf(newServiceReports));

        Set<ResourceReport> newResourceReports = new HashSet<>();
        remainingNodes.clear();
        remainingNodes.addAll(activeNodes);

        reports.forEach((report) -> {
            NodeIdentifier node = report.getNodeName();

            if (remainingNodes.remove(node)) {
                Set<ContainerResourceReport> newContainerReports = new HashSet<>();

                // Copy old container reports for containers that are not
                // stopped
                report.getContainerReports().forEach((containerName, containerReport) -> {
                    if (!stoppedContainers.contains(containerName)) {
                        ServiceIdentifier<?> service = containerReport.getService();

                        // create a new container report that is similar to the
                        // old report but has updated load
                        newContainerReports.add(
                                createContainerReport(time, containerName, service, loadPerNode.get(service), 0.0));
                    }
                });

                // add new resource reports according to the plan
                newContainers.get(node).forEach((service, containers) -> {
                    containers.forEach((container) -> {
                        Map<NodeAttribute, Double> containerLoad = loadPerNode.get(service);

                        if (containerLoad == null) {
                            throw new RuntimeException("Load for service '" + service + "' is undefined.");
                        }

                        // create a new container report
                        newContainerReports.add(createContainerReport(time, container, service, containerLoad, 0.0));
                    });
                });

                newResourceReports.add(createResourceReport(time, node, report.getMaximumServiceContainers(),
                        report.getNodeComputeCapacity(), newContainerReports));
            }
        });

        // add reports for additional nodes as needed
        remainingNodes.forEach((node) -> {
            newResourceReports.add(createResourceReport(time, node, regionData.getMaxContainersForNode(node),
                    regionData.getCapacitiesForNode(node), ImmutableSet.of()));
        });

        RegionData newRegionData = new RegionData(regionData);
        newRegionData.setRegionServiceState(newRegionServiceState);
        newRegionData.setResourceReports(newResourceReports);

        return newRegionData;
    }

    private static void processLoadBalancerPlan(LoadBalancerPlan rlgPlan,
            Map<NodeIdentifier, Map<ServiceIdentifier<?>, Integer>> plannedNodeInstances,
            Map<ServiceIdentifier<?>, Integer> plannedTotalInstances,
            Set<NodeIdentifier> stoppedContainers) {
        rlgPlan.getServicePlan().forEach((node, containerInfos) -> {
            containerInfos.forEach((containerInfo) -> {
                ServiceIdentifier<?> service = containerInfo.getService();
                NodeIdentifier container = containerInfo.getId();

                if (containerInfo.isStop()) {
                    stoppedContainers.add(container);
                } else {
                    plannedNodeInstances.computeIfAbsent(node, k -> new HashMap<>()).merge(service, 1, Integer::sum);
                    plannedTotalInstances.merge(service, 1, Integer::sum);
                }
            });
        });
    }
}
