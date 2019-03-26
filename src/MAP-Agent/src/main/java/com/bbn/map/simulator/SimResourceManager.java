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
package com.bbn.map.simulator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.ContainerIdentifier;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.bbn.protelis.utils.ImmutableUtils;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Resource manager for use with {@link Simulation}. This class is thread-safe.
 */
public class SimResourceManager implements ResourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimResourceManager.class);

    @Override
    public VirtualClock getClock() {
        return simulation.getClock();
    }

    private final NetworkServer node;

    /**
     * @return the node that the manager is for
     */
    @Nonnull
    public NetworkServer getNode() {
        return node;
    }

    private final Simulation simulation;
    private final Object lock = new Object();
    private ResourceReport shortResourceReport;
    private ResourceReport longResourceReport;
    private final long pollingInterval;

    /* package */ long getPollingInterval() {
        return pollingInterval;
    }

    private ImmutableList<ContainerIdentifier> containerIds = null;

    private ImmutableList<ContainerIdentifier> getContainerIds() {
        if (null == containerIds) {
            // compute the container IDs the first time someone asks for them
            // Can't do this in the constructor as the node hardware information
            // isn't available at that time.
            final ImmutableList.Builder<ContainerIdentifier> idBuilder = ImmutableList.builder();
            final int numContainers = getContainerCapacity();
            for (int i = 0; i < numContainers; ++i) {
                final ContainerIdentifier id = new DnsNameIdentifier(String.format("%s_c%02d", node.getName(), i));
                idBuilder.add(id);
            }
            containerIds = idBuilder.build();
        }

        return containerIds;
    }

    /**
     * Create a simulated resource manager for the specified node.
     * 
     * @param simulation
     *            get all of the simulation data from here
     * @param node
     *            the node that this manager is for
     * @param pollingInterval
     *            the number of milliseconds between creating
     *            {@link ResourceReport} objects
     */
    public SimResourceManager(@Nonnull final Simulation simulation,
            @Nonnull final NetworkServer node,
            final long pollingInterval) {
        this.simulation = simulation;
        this.node = node;
        this.shortResourceReport = ResourceReport.getNullReport(node.getNodeIdentifier(),
                ResourceReport.EstimationWindow.SHORT);
        this.longResourceReport = ResourceReport.getNullReport(node.getNodeIdentifier(),
                ResourceReport.EstimationWindow.LONG);
        this.pollingInterval = pollingInterval;
    }

    @Override
    @Nonnull
    public ImmutableMap<NodeAttribute<?>, Double> getComputeCapacity() {
        final String serverHardware = node.getHardware();
        final HardwareConfiguration hardwareConfig = simulation.getHardwareConfiguration(serverHardware);
        if (null == hardwareConfig) {
            return ImmutableMap.of();
        } else {

            final ImmutableMap.Builder<NodeAttribute<?>, Double> builder = ImmutableMap.builder();
            hardwareConfig.getCapacity().forEach((k, v) -> builder.put(k, v));

            return builder.build();
        }
    }

    /**
     * Get the number of containers that can run in this node.
     * 
     * @return computed based on {@link #getComputeCapacity()}
     */
    private int getContainerCapacity() {
        final ImmutableMap<NodeAttribute<?>, Double> capacity = getComputeCapacity();
        final Double value = capacity.get(NodeMetricName.TASK_CONTAINERS);
        if (null != value) {
            return value.intValue();
        } else {
            return 0;
        }
    }

    @Override
    public ResourceReport getCurrentResourceReport(@Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        synchronized (lock) {
            switch (estimationWindow) {
            case LONG:
                return longResourceReport;
            case SHORT:
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Returning short resource report with compute load: {} on {}",
                            shortResourceReport.getComputeDemand(), getNode().getName());
                }
                return shortResourceReport;
            default:
                throw new IllegalArgumentException("Unknown estimation window type: " + estimationWindow);
            }
        }
    }

    /**
     * Package visibility for testing. This allows me to force the creation of
     * the latest ResourceReports.
     */
    /* package */ void updateResourceReports() {
        try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(node.getNodeIdentifier().getName())) {
            synchronized (lock) {
                final long now = getClock().getCurrentTime();

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Updating resource reports time: {}", now);
                }

                final ImmutableMap.Builder<ContainerIdentifier, ContainerResourceReport> longContainerReports = ImmutableMap
                        .builder();
                final ImmutableMap.Builder<ContainerIdentifier, ContainerResourceReport> shortContainerReports = ImmutableMap
                        .builder();
                runningContainers.forEach((id, sim) -> {
                    if (ServiceState.Status.RUNNING.equals(sim.getServiceStatus())) {
                        final ContainerResourceReport longReport = sim
                                .getContainerResourceReport(ResourceReport.EstimationWindow.LONG);
                        longContainerReports.put(id, longReport);

                        final ContainerResourceReport shortReport = sim
                                .getContainerResourceReport(ResourceReport.EstimationWindow.SHORT);
                        shortContainerReports.put(id, shortReport);
                        //
                        // if (LOGGER.isTraceEnabled()) {
                        // LOGGER.trace("Adding container resource report short
                        // server load: {}",
                        // shortReport.getComputeLoad());
                        // }
                    } else {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Container is not running: {}", id);
                        }
                    }
                });

                final ImmutableMap<NodeAttribute<?>, Double> reportComputeCapacity = ImmutableMap
                        .copyOf(getComputeCapacity());

                // compute network information
                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportNeighborNetworkCapacity = node
                        .getNeighborLinkCapacity(LinkMetricName.DATARATE);

                final Map<NodeIdentifier, Map<LinkAttribute<?>, Double>> clientNetworkCapacity = new HashMap<>();

                // client->load
                final Map<NodeIdentifier, Map<LinkAttribute<?>, Double>> clientNetworkLoad = new HashMap<>();

                // neighbor->load
                final ImmutableMap.Builder<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> neighborNetworkLoad = ImmutableMap
                        .builder();
                node.getNeighbors().forEach(neighborId -> {
                    final LinkResourceManager lmgr = getLinkResourceManager(neighborId);
                    final ImmutableMap<LinkAttribute<?>, Double> neighborCapacity = reportNeighborNetworkCapacity
                            .getOrDefault(neighborId, ImmutableMap.of());

                    final Pair<ImmutableMap<LinkAttribute<?>, Double>, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>>> linkLoadResult = lmgr
                            .computeCurrentLinkLoad();

                    final ImmutableMap<LinkAttribute<?>, Double> linkLoad = linkLoadResult.getLeft();
                    final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> loadPerClient = linkLoadResult
                            .getRight();

                    loadPerClient.forEach((client, load) -> {
                        final Map<LinkAttribute<?>, Double> clientLoad = clientNetworkLoad.computeIfAbsent(client,
                                c -> new HashMap<>());
                        load.forEach((attr, value) -> clientLoad.merge(attr, value, Double::sum));

                        // compute the capacity to the client, if a given client
                        // is accessible through multiple neighbors, sum the
                        // capacity
                        final Map<LinkAttribute<?>, Double> clientCapacity = clientNetworkCapacity
                                .computeIfAbsent(client, c -> new HashMap<>());
                        neighborCapacity.forEach((attr, value) -> clientCapacity.merge(attr, value, Double::sum));

                    });

                    neighborNetworkLoad.put(neighborId, linkLoad);

                });

                // add neighbor capacity to client capacity, but don't sum
                reportNeighborNetworkCapacity.forEach((neighbor, capacity) -> {
                    clientNetworkCapacity.computeIfAbsent(neighbor, n -> capacity);
                });
                
                
                // create immutable data structures to put into the report
                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportClientNetworkCapacity = ImmutableUtils
                        .makeImmutableMap2(clientNetworkCapacity);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportNeighborNetworkLoad = neighborNetworkLoad
                        .build();
                updateNeighborDemandValues(now, reportNeighborNetworkLoad);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportShortNeighborNetworkDemand = computeNeighborNetworkDemand(
                        now, ResourceReport.EstimationWindow.SHORT);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportLongNeighborNetworkDemand = computeNeighborNetworkDemand(
                        now, ResourceReport.EstimationWindow.LONG);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportClientNetworkLoad = ImmutableUtils
                        .makeImmutableMap2(clientNetworkLoad);
                updateClientDemandValues(now, reportClientNetworkLoad);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportShortClientNetworkDemand = computeClientNetworkDemand(
                        now, ResourceReport.EstimationWindow.SHORT);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportLongClientNetworkDemand = computeClientNetworkDemand(
                        now, ResourceReport.EstimationWindow.LONG);

                longResourceReport = new ResourceReport(node.getNodeIdentifier(), now,
                        ResourceReport.EstimationWindow.LONG, reportComputeCapacity, reportClientNetworkCapacity,
                        reportClientNetworkLoad, reportLongClientNetworkDemand, reportNeighborNetworkCapacity,
                        reportNeighborNetworkLoad, reportLongNeighborNetworkDemand, longContainerReports.build());

                shortResourceReport = new ResourceReport(node.getNodeIdentifier(), now,
                        ResourceReport.EstimationWindow.SHORT, reportComputeCapacity, reportClientNetworkCapacity,
                        reportClientNetworkLoad, reportShortClientNetworkDemand, reportNeighborNetworkCapacity,
                        reportNeighborNetworkLoad, reportShortNeighborNetworkDemand, shortContainerReports.build());

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Short report computed load: {}", shortResourceReport.getComputeLoad());
                }
            } // end lock
        } // logging thread context
    }

    private final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>>> clientNetworkLoadHistory = new HashMap<>();
    private final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>>> neighborNetworkLoadHistory = new HashMap<>();

    private void updateClientDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkLoad) {
        updateDemandValues(timestamp, networkLoad, clientNetworkLoadHistory);
    }

    private void updateNeighborDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkLoad) {
        updateDemandValues(timestamp, networkLoad, neighborNetworkLoadHistory);
    }

    private static void updateDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkLoad,
            final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>>> networkLoadHistory) {
        networkLoadHistory.put(timestamp, networkLoad);

        // clean out old entries from network load
        final long historyCutoff = timestamp
                - Math.max(AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis(),
                        AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis());
        final Iterator<Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>>>> networkIter = networkLoadHistory
                .entrySet().iterator();
        while (networkIter.hasNext()) {
            final Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>>> entry = networkIter
                    .next();
            if (entry.getKey() < historyCutoff) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Removing network demand value {} because it's time {} is before {}", entry.getValue(),
                            entry.getKey(), historyCutoff);
                }
                networkIter.remove();
            }
        }
    }

    private ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> computeClientNetworkDemand(
            final long now, @Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        return computeNetworkDemand(now, estimationWindow, clientNetworkLoadHistory);
    }

    private ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> computeNeighborNetworkDemand(
            final long now, @Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        return computeNetworkDemand(now, estimationWindow, neighborNetworkLoadHistory);
    }

    private static ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> computeNetworkDemand(
            final long now,
            @Nonnull final ResourceReport.EstimationWindow estimationWindow,
            final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>>> networkLoadHistory) {

        final long duration;
        switch (estimationWindow) {
        case LONG:
            duration = AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis();
            break;
        case SHORT:
            duration = AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis();
            break;
        default:
            throw new IllegalArgumentException("Unknown estimation window: " + estimationWindow);
        }

        final long cutoff = now - duration;
        final Map<NodeIdentifier, Map<LinkAttribute<?>, Double>> sums = new HashMap<>();
        final Map<NodeIdentifier, Map<LinkAttribute<?>, Integer>> counts = new HashMap<>();
        NetworkLoadTracker.twoLevelHistoryMapCountSum(networkLoadHistory, cutoff, sums, counts);

        final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportDemand = NetworkLoadTracker
                .twoLevelMapAverage(sums, counts);

        return reportDemand;
    }

    private ScheduledThreadPoolExecutor resourceReportTimer = null;

    /**
     * Start generation of {@link ResourceReport} objects.
     * 
     * @throws IllegalStateException
     *             if the simulation is already started
     */
    public void startSimulation() {
        if (null != resourceReportTimer) {
            throw new IllegalStateException("Cannot start the simulation when it is already running");
        }
        resourceReportTimer = new ScheduledThreadPoolExecutor(1);
        resourceReportTimer.scheduleAtFixedRate(() -> updateResourceReports(), 0, pollingInterval,
                TimeUnit.MILLISECONDS);

        runningContainers.forEach((id, sim) -> {
            sim.startSimulation();
        });
    }

    /**
     * Stop generation of new {@link ResourceReport} objects.
     */
    public void stopSimulation() {
        if (null != resourceReportTimer) {
            resourceReportTimer.shutdown();
            resourceReportTimer = null;

            runningContainers.forEach((id, sim) -> {
                sim.stopSimulation();
            });
        }
    }

    /**
     * The number of requests handled by this node for each source region. Key
     * is the source region, value is the number of requests.
     * 
     * @return unmodifiable copy of the internal state
     */
    public Map<RegionIdentifier, Integer> getNumRequestsPerRegion() {
        synchronized (lock) {
            final Map<RegionIdentifier, Integer> combined = new HashMap<>();
            runningContainers.forEach((id, sim) -> {
                final Map<RegionIdentifier, Integer> reqs = sim.getNumRequestsPerRegion();
                reqs.forEach((region, requests) -> {
                    combined.merge(region, requests, Integer::sum);
                });
            });
            return combined;
        }
    }

    private final Map<ContainerIdentifier, ContainerSim> runningContainers = new HashMap<>();

    /**
     * Find a container by id.
     * 
     * @param containerId
     *            the container to find
     * @return the container or null if not found
     */
    public ContainerSim getContainerById(@Nonnull final ContainerIdentifier containerId) {
        synchronized (lock) {
            return runningContainers.get(containerId);
        }
    }

    @Override
    public ContainerIdentifier startService(@Nonnull final ServiceIdentifier<?> service,
            @Nonnull final ContainerParameters parameters) {
        synchronized (lock) {
            // clean up stopped containers first, makes the search for a
            // container ID easier to implement
            reapContainers();

            final ContainerIdentifier nextAvailable = getContainerIds().stream()
                    .filter(id -> !runningContainers.containsKey(id)).findFirst().orElse(null);

            if (null == nextAvailable) {
                LOGGER.warn("No containers available on node {}. Limit is {}. Running: {}", node.getName(),
                        getContainerIds().size(), runningContainers);
                return null;
            } else {
                if (!runningContainers.containsKey(nextAvailable)) {
                    // container isn't running, add to running
                    final ImmutableMap<LinkAttribute<?>, Double> genericNetworkCapacity = parameters
                            .getNetworkCapacity();

                    final ImmutableMap.Builder<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> containerNetworkCapacity = ImmutableMap
                            .builder();
                    node.getNeighbors().forEach(neighbor -> {
                        containerNetworkCapacity.put(neighbor, genericNetworkCapacity);
                    });

                    final ContainerSim container = new ContainerSim(this, nextAvailable,
                            parameters.getComputeCapacity(), containerNetworkCapacity.build());
                    runningContainers.put(nextAvailable, container);

                    if (null != resourceReportTimer) {
                        container.startSimulation();
                    }
                } // else container is running, but doesn't have a service

                final ContainerSim container = runningContainers.get(nextAvailable);
                if (container.startService(service)) {
                    simulation.registerContainer(nextAvailable, container);

                    return nextAvailable;
                } else {
                    LOGGER.warn("Unable to start service {} on container {}", service, nextAvailable);
                    return null;
                }
            }
        }
    }

    @Override
    public boolean stopService(@Nonnull final ContainerIdentifier containerName) {
        synchronized (lock) {
            final ContainerSim container = runningContainers.get(containerName);
            if (null != container) {
                final boolean result = container.stopService();
                if (result) {
                    runningContainers.remove(containerName);
                    container.stopSimulation();
                }
                return result;
            } else {
                LOGGER.warn("Trying to stop service on non-existant container {}", containerName);
                return false;
            }
        }
    }

    private ImmutableMap<ContainerIdentifier, ServiceState> computeServiceState() {
        reapContainers();

        final ImmutableMap.Builder<ContainerIdentifier, ServiceState> builder = ImmutableMap.builder();
        runningContainers.forEach((id, container) -> {
            final ServiceIdentifier<?> service = container.getService();
            if (null != service) {
                final ServiceState s = new ServiceState(service, container.getServiceStatus());
                builder.put(id, s);
            }
        });
        final ImmutableMap<ContainerIdentifier, ServiceState> serviceState = builder.build();
        return serviceState;
    }

    /**
     * Any container that is in the STOPPED state can be removed. This method
     * MUST be called with the lock held.
     */
    private void reapContainers() {
        // create separate list so there isn't a concurrent modification
        // exception
        final List<ContainerIdentifier> toRemove = runningContainers.entrySet().stream()
                .filter(e -> e.getValue().getServiceStatus() == ServiceState.Status.STOPPED).map(Map.Entry::getKey)
                .collect(Collectors.toList());
        toRemove.forEach(id -> cleanupContainer(id));
    }

    /**
     * Cleanup the specified container. This ensures that the simulation is
     * properly notified. This method MUST be called with the lock held.
     */
    private void cleanupContainer(final ContainerIdentifier id) {
        runningContainers.remove(id);
        simulation.unregisterContainer(id);
    }

    @Override
    @Nonnull
    public ServiceReport getServiceReport() {
        final ServiceReport report = new ServiceReport(node.getNodeIdentifier(), computeServiceState());
        return report;
    }

    private final Map<NodeIdentifier, LinkResourceManager> linkMgrCache = new HashMap<>();

    private LinkResourceManager getLinkResourceManager(@Nonnull final NodeIdentifier neighborId) {
        // TODO: will need to clean out this cache when neighbors can change

        final LinkResourceManager lmgr = linkMgrCache.computeIfAbsent(neighborId,
                k -> simulation.getLinkResourceManager(node.getNodeIdentifier(), neighborId));
        return lmgr;
    }

}
