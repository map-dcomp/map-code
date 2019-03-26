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
package com.bbn.map.rlg;

import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AbstractService;
import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.map.ap.ReportUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.ContainerIdentifier;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.NetworkStateProvider;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNodeState;
import com.bbn.protelis.networkresourcemanagement.RegionNodeStateProvider;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.RegionServiceState;
import com.bbn.protelis.networkresourcemanagement.RegionServiceStateProvider;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * The main entry point for RLG. The {@link Controller} will use this class to
 * interact with RLG. The {@link Controller} will start this service as
 * appropriate for the node.
 */
public class RLGService extends AbstractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RLGService.class);

    /**
     * Construct an RLG service.
     * 
     * @param resourceProvider
     *            where resource summary information is to be retrieved from.
     * 
     * @param networkStateProvider
     *            where resource summary information is to be retrieved from.
     * 
     * @param serviceStateProvider
     *            where to get information about what services are running in
     *            the region
     * 
     * @param applicationManager
     *            source of information about applications, including
     *            specifications and profiles
     * @param nodeName
     *            the name of the node that this service is running on (for
     *            logging)
     * @param rlgInfoProvider
     *            how to access {@link RlgSharedInformation}
     */
    public RLGService(@Nonnull final String nodeName,
            @Nonnull final NetworkStateProvider networkStateProvider,
            @Nonnull final RlgInfoProvider rlgInfoProvider,
            @Nonnull final ApplicationManagerApi applicationManager,
            @Nonnull final RegionNodeStateProvider resourceProvider,
            @Nonnull final RegionServiceStateProvider serviceStateProvider) {
        super("RLG-" + nodeName);
        this.resourceProvider = resourceProvider;
        this.networkStateProvider = networkStateProvider;
        this.applicationManager = applicationManager;
        this.rlgInfoProvider = rlgInfoProvider;
        this.serviceStateProvider = serviceStateProvider;
    }

    private final RlgInfoProvider rlgInfoProvider;

    /**
     * Where to get resource information from.
     */
    private final RegionNodeStateProvider resourceProvider;

    /**
     * Where to get resource summary information from.
     */
    private final NetworkStateProvider networkStateProvider;

    private final RegionServiceStateProvider serviceStateProvider;

    /**
     * Where to get application specifications and profiles from.
     */
    private final ApplicationManagerApi applicationManager;

    @Override
    protected void executeService() {

        while (Status.RUNNING == getStatus()) {
            final LocalTime beforePlan = LocalTime.now();

            final LoadBalancerPlan newPlan = computePlan();
            if (null != newPlan) {
                LOGGER.info("Publishing RLG plan: {}", newPlan);

                networkStateProvider.getNetworkState().setLoadBalancerPlan(newPlan);
            }

            // check before sleep in case computePlan is slow
            if (Status.RUNNING == getStatus()) {
                try {
                    // sleep if the computation didn't take too long
                    final Duration timeSinceStart = Duration.between(beforePlan, LocalTime.now());
                    final Duration sleepDuration = AgentConfiguration.getInstance().getRlgRoundDuration()
                            .minus(timeSinceStart);
                    if (!sleepDuration.isZero() && !sleepDuration.isNegative()) {
                        Thread.sleep(sleepDuration.toMillis());
                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Sleep duration was not positive, no sleep: {}", sleepDuration);
                        }
                    }
                } catch (final InterruptedException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Got interrupted, likely time to shutdown, top of while loop will confirm.");
                    }
                }
            }
        }
    }

    private LoadBalancerPlan computePlan() {
        return stubComputePlan();
    }

    /**
     * Compute a new plan. NOTE: that this implementation will add capacity, but
     * will never reduce it.
     * 
     * @return the plan or null if no new plan should be sent out
     */
    private LoadBalancerPlan stubComputePlan() {
        final RegionPlan dcopPlan = networkStateProvider.getNetworkState().getRegionPlan();
        final RegionNodeState regionState = resourceProvider.getRegionNodeState();
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

        // do something with the data, for now just log
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resource reports: " + Objects.toString(resourceReports));
            LOGGER.debug("DCOP plan: " + Objects.toString(dcopPlan));
            LOGGER.debug("ApplicationManager: " + applicationManager);

            // this is used to get the information about what services are
            // running in which containers
            LOGGER.debug("service state: " + serviceStateProvider.getRegionServiceState());
        }

        final RegionServiceState regionServiceState = serviceStateProvider.getRegionServiceState();
        final ImmutableSet<ServiceReport> serviceStates = regionServiceState.getServiceReports();

        // track which services are running and on which node
        final RlgSharedInformation newRlgShared = new RlgSharedInformation();
        final Map<NodeIdentifier, ServiceReport> serviceReportMap = new HashMap<>();
        serviceStates.forEach(sr -> {
            serviceReportMap.put(sr.getNodeName(), sr);

            sr.getServiceState().forEach((k, state) -> {
                newRlgShared.addRunningService(state.getService());
            });
        });

        final Map<ServiceIdentifier<?>, Map<NodeIdentifier, Integer>> newServicePlan = new HashMap<>();

        createBasePlan(resourceReports, serviceReportMap, newServicePlan);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Plan after doing base create: {}", newServicePlan);
        }

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

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Filtered reports: " + reports);
        }

        final Map<NodeIdentifier, Integer> nodesWithAvailableCapacity = findNodesWithAvailableContainerCapacity(reports,
                serviceReportMap);

        // make sure there is at least one instance of each service that DCOP
        // wants running in this region
        if (!resourceReports.isEmpty()) {
            startServicesForDcop(dcopPlan, nodesWithAvailableCapacity, thisRegion, newServicePlan);
        } else {
            LOGGER.warn("No resource reports, RLG cannot do much of anything");
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("All reports: " + resourceReports);

            LOGGER.trace("Service plan after creating instances for DCOP: " + newServicePlan);
        }

        // partition the nodes to those needing help and those that are ok
        final double thresholdPercentage = 0.75;
        final Map<NodeIdentifier, ResourceReport> aboveThreshold = new HashMap<>();
        reports.forEach((node, report) -> {
            final double load = ReportUtils.computeServerLoadPercentage(report);

            LOGGER.trace("Computed load for node {} capacity: {} load: {} percentage: {}", report.getNodeName(),
                    report.getAllocatedComputeCapacity(), report.getComputeLoad(), load);

            if (load >= thresholdPercentage) {
                aboveThreshold.put(node, report);
            }
        });

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Above: " + aboveThreshold);
            LOGGER.trace("available capacity: " + nodesWithAvailableCapacity);
            LOGGER.trace("Plan before handling overloads: " + newServicePlan);
        }

        if (!aboveThreshold.isEmpty() && !nodesWithAvailableCapacity.isEmpty()) {
            // Add a new node for each node that is too busy

            aboveThreshold.forEach((node, report) -> {
                final ServiceIdentifier<?> service = findHighestServiceLoad(report);

                // Make sure the service is still running on the old node
                // this shouldn't be needed, but in the tests that are currently
                // being run we depend on the default node. - 8/4/2017 Jon
                // Schewe
                newServicePlan.computeIfAbsent(service, v -> new HashMap<>()).merge(node, 1, Integer::max);

                NodeIdentifier newNode;
                if (nodesWithAvailableCapacity.containsKey(node)) {
                    // this node has available capacity, add another container
                    newNode = node;

                } else {
                    newNode = nodesWithAvailableCapacity.entrySet().stream().map(Map.Entry::getKey).findAny()
                            .orElse(null);
                }

                if (null != newNode) {
                    final int availCap = nodesWithAvailableCapacity.get(newNode);

                    final int usedCap = Math.min(availCap, 10);
                    newServicePlan.computeIfAbsent(service, v -> new HashMap<>()).merge(newNode, usedCap, Integer::sum);

                    final int newCapacity = availCap - usedCap;
                    if (newCapacity <= 0) {
                        nodesWithAvailableCapacity.remove(newNode);
                    } else {
                        nodesWithAvailableCapacity.put(newNode, newCapacity);
                    }
                }

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Overloaded node: " + node + " top service: " + service + " new node: " + newNode);
                }

            });

        }

        // make an immutable version of the plan to share
        final ImmutableMap.Builder<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, Integer>> servicePlanBuilder = ImmutableMap
                .builder();
        for (final Map.Entry<ServiceIdentifier<?>, Map<NodeIdentifier, Integer>> planEntry : newServicePlan
                .entrySet()) {
            final ImmutableMap<NodeIdentifier, Integer> servicePlan = ImmutableMap.copyOf(planEntry.getValue());
            servicePlanBuilder.put(planEntry.getKey(), servicePlan);
        }

        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = computeOverflowPlan(
                infoFromAllRlgs, dcopPlan);

        // stop traffic to all loaded containers
        final ImmutableMap.Builder<NodeIdentifier, ImmutableSet<ContainerIdentifier>> stopTrafficTo = ImmutableMap
                .builder();
        reports.forEach((nodeId, report) -> {

            final ImmutableSet.Builder<ContainerIdentifier> nodeStop = ImmutableSet.builder();

            report.getContainerReports().forEach((containerId, creport) -> {
                final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> cload = creport
                        .getComputeLoad();
                final double containerLoad = cload.entrySet().stream().map(Map.Entry::getValue)
                        .map(m -> m.getOrDefault(NodeMetricName.TASK_CONTAINERS, 0D)).mapToDouble(Double::doubleValue)
                        .sum();
                final double containerCapacity = creport.getComputeCapacity()
                        .getOrDefault(NodeMetricName.TASK_CONTAINERS, 0D);
                if (containerLoad >= containerCapacity && containerLoad > 0) {
                    nodeStop.add(containerId);
                }

            });

            final ImmutableSet<ContainerIdentifier> nodeStopIm = nodeStop.build();
            if (!nodeStopIm.isEmpty()) {
                stopTrafficTo.put(nodeId, nodeStopIm);
            }
        });

        final ImmutableMap<NodeIdentifier, ImmutableSet<ContainerIdentifier>> stopContainers = ImmutableMap.of();

        final LoadBalancerPlan plan = new LoadBalancerPlan(regionState.getRegion(), servicePlanBuilder.build(),
                overflowPlan, stopTrafficTo.build(), stopContainers);

        // tell other RLGs about the services running in this region
        rlgInfoProvider.setLocalRlgSharedInformation(newRlgShared);

        return plan;
    }

    private ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> computeOverflowPlan(
            final ImmutableMap<RegionIdentifier, RlgSharedInformation> infoFromAllRlgs, final RegionPlan dcopPlan) {
        // Take the DCOP plan and filter out overflow to regions that don't
        // already have the specified service running.

        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> dcopOverflowPlan = dcopPlan
                .getPlan();

        final ImmutableMap.Builder<ServiceIdentifier<?>, ImmutableMap<RegionIdentifier, Double>> overflowPlan = ImmutableMap
                .builder();
        dcopOverflowPlan.forEach((service, dcopServiceOverflowPlan) -> {
            final ImmutableMap.Builder<RegionIdentifier, Double> serviceOverflowPlan = ImmutableMap.builder();

            dcopServiceOverflowPlan.forEach((region, weight) -> {
                // check if service is running in region
                final RlgSharedInformation destInfo = infoFromAllRlgs.get(region);
                if (null != destInfo && destInfo.getRunningServices().contains(service)) {
                    serviceOverflowPlan.put(region, weight);
                } else {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("No instance of {} in region {}, skipping delegation", service, region);
                    }
                }
            });

            overflowPlan.put(service, serviceOverflowPlan.build());
        });

        return overflowPlan.build();
    }

    /**
     * Start any services that DCOP plans to send to.
     */
    private void startServicesForDcop(final RegionPlan dcopPlan,
            Map<NodeIdentifier, Integer> nodesWithAvailableCapacity,
            final RegionIdentifier thisRegion,
            final Map<ServiceIdentifier<?>, Map<NodeIdentifier, Integer>> newServicePlan) {
        dcopPlan.getPlan().forEach((service, servicePlan) -> {
            if (servicePlan.containsKey(thisRegion)) {
                // need an entry for the service
                final Map<NodeIdentifier, Integer> serviceNodes = newServicePlan.computeIfAbsent(service,
                        v -> new HashMap<>());

                final int desiredCapacity = 10;
                boolean done = false;
                while (!done) {
                    final int currentCapacity = serviceNodes.entrySet().stream().mapToInt(Map.Entry::getValue).sum();
                    if (currentCapacity >= desiredCapacity) {
                        done = true;
                    } else {
                        final int neededCapacity = desiredCapacity - currentCapacity;

                        final Map.Entry<NodeIdentifier, Integer> newNodeAndCap = nodesWithAvailableCapacity.entrySet()
                                .stream().findAny().orElse(null);

                        if (null != newNodeAndCap) {
                            final NodeIdentifier newNode = newNodeAndCap.getKey();
                            final int nodeAvailableCap = newNodeAndCap.getValue();

                            if (nodeAvailableCap > neededCapacity) {
                                final int newCap = nodeAvailableCap - neededCapacity;
                                nodesWithAvailableCapacity.put(newNode, newCap);

                                newServicePlan.computeIfAbsent(service, v -> new HashMap<>()).merge(newNode,
                                        neededCapacity, Integer::sum);
                            } else {
                                // take what is available on newNode
                                nodesWithAvailableCapacity.remove(newNode);
                                newServicePlan.computeIfAbsent(service, v -> new HashMap<>()).merge(newNode,
                                        nodeAvailableCap, Integer::sum);
                            }

                        } else {
                            // didn't find enough space, just go with what we
                            // have
                            done = true;
                        }

                    } // need to add capacity
                }

            } // if dcop is using this region
        });
    }

    /**
     * Copy the previous plan and assume that all currently running services
     * should continue to run.
     * 
     */
    private void createBasePlan(final ImmutableSet<ResourceReport> resourceReports,
            final Map<NodeIdentifier, ServiceReport> serviceReportMap,
            final Map<ServiceIdentifier<?>, Map<NodeIdentifier, Integer>> newServicePlan) {
        // copy current plan to new plan (assuming that the network is executing
        // the current plan)
        final LoadBalancerPlan previousServicePlan = networkStateProvider.getNetworkState().getLoadBalancerPlan();
        previousServicePlan.getServicePlan().forEach((service, nodes) -> {
            newServicePlan.merge(service, new HashMap<>(nodes), (v1, v2) -> {
                v1.putAll(v2);
                return v1;
            });
        });

        // Add in all nodes that have load. The assumption is that any node that
        // is already running a service should keep running it.
        resourceReports.forEach(report -> {
            report.getComputeLoad().forEach((service, load) -> {
                final Map<NodeIdentifier, Integer> serviceNodes = newServicePlan.computeIfAbsent(service,
                        v -> new HashMap<>());

                final NodeIdentifier nodeId = report.getNodeName();
                final ServiceReport serviceReport = serviceReportMap.get(nodeId);
                if (null != serviceReport) {
                    final int currentInstancesOnNode = (int) serviceReport.getServiceState().entrySet().stream()
                            .filter(e -> !e.getValue().getStatus().equals(ServiceState.Status.STOPPED)
                                    && service.equals(e.getValue().getService()))
                            .count();

                    if (!serviceNodes.containsKey(nodeId)) {
                        serviceNodes.put(nodeId, currentInstancesOnNode);
                    }
                }
            });
        });
    }

    /**
     * 
     * @return nodeid -> available containers
     */
    private Map<NodeIdentifier, Integer> findNodesWithAvailableContainerCapacity(
            final Map<NodeIdentifier, ResourceReport> reports,
            final Map<NodeIdentifier, ServiceReport> serviceReportMap) {
        final Map<NodeIdentifier, Integer> availableNodeCapacity = new HashMap<>();

        reports.forEach((nodeid, report) -> {
            final ImmutableMap<NodeAttribute<?>, Double> nodeComputeCapacity = report.getNodeComputeCapacity();
            final int containerCapacity = nodeComputeCapacity.getOrDefault(NodeMetricName.TASK_CONTAINERS, 0D)
                    .intValue();
            final ServiceReport serviceReport = serviceReportMap.get(nodeid);
            if (null != serviceReport) {
                final int containersInUse = (int) serviceReport.getServiceState().entrySet().stream()
                        .filter(e -> !e.getValue().getStatus().equals(ServiceState.Status.STOPPED)).count();

                final int availableContainers = containerCapacity - containersInUse;
                if (availableContainers > 0) {
                    availableNodeCapacity.put(nodeid, availableContainers);
                }
            }
        });

        return availableNodeCapacity;
    }

    /**
     * Determine which service in the report is causing the most task container
     * load.
     */
    private static ServiceIdentifier<?> findHighestServiceLoad(final ResourceReport report) {
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> allLoad = report
                .getComputeLoad();
        final NodeAttribute<?> containersAttribute = NodeMetricName.TASK_CONTAINERS;

        ServiceIdentifier<?> maxService = null;
        double maxServiceLoad = Double.NaN;
        for (final Map.Entry<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> loadEntry : allLoad
                .entrySet()) {
            final ServiceIdentifier<?> service = loadEntry.getKey();
            final ImmutableMap<?, ImmutableMap<NodeAttribute<?>, Double>> serviceLoad = loadEntry.getValue();

            double load = 0;
            for (final Map.Entry<?, ImmutableMap<NodeAttribute<?>, Double>> serviceEntry : serviceLoad.entrySet()) {
                final ImmutableMap<NodeAttribute<?>, Double> nodeLoad = serviceEntry.getValue();
                final double value = nodeLoad.getOrDefault(containersAttribute, 0D);
                load += value;
            }

            if (null == maxService || load > maxServiceLoad) {
                maxService = service;
                maxServiceLoad = load;
            }
        }

        return maxService;
    }

}
