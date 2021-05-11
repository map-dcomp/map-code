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
package com.bbn.map.simulator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.protelis.networkresourcemanagement.BasicResourceManager;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkClient;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.bbn.protelis.networkresourcemanagement.ServiceStatus;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Resource manager for use with {@link Simulation}. This class is thread-safe.
 */
public class SimResourceManager implements ResourceManager<Controller> {

    // not final because it needs to change in init()
    private Logger logger;

    @Override
    public VirtualClock getClock() {
        return simulation.getClock();
    }

    private Controller node;

    /**
     * @return the node that the manager is for
     */
    @Nonnull
    public Controller getNode() {
        return node;
    }

    private final Simulation simulation;

    /* package */ Simulation getSimulation() {
        return simulation;
    }

    private final Object lock = new Object();
    private ResourceReport shortResourceReport;
    private ResourceReport longResourceReport;
    private final long pollingInterval;
    private final NetworkDemandTracker networkDemandTracker = new NetworkDemandTracker();
    private final boolean useFailedRequestsInDemand;

    /* package */ long getPollingInterval() {
        return pollingInterval;
    }

    private ImmutableList<NodeIdentifier> containerIds = null;

    private ImmutableList<NodeIdentifier> getContainerIds() {
        if (null == containerIds) {
            // compute the container IDs the first time someone asks for them
            // Can't do this in the constructor as the node hardware information
            // isn't available at that time.
            final ImmutableList.Builder<NodeIdentifier> idBuilder = ImmutableList.builder();
            final int numContainers = getContainerCapacity();
            for (int i = 0; i < numContainers; ++i) {
                final NodeIdentifier id = new DnsNameIdentifier(String.format("%s_c%02d", node.getName(), i));
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
     * @param pollingInterval
     *            the number of milliseconds between creating
     *            {@link ResourceReport} objects
     */
    public SimResourceManager(@Nonnull final Simulation simulation, final long pollingInterval) {
        logger = LoggerFactory.getLogger(SimResourceManager.class.getName() + ".unknown");
        // cache value so that it isn't checked every time
        this.useFailedRequestsInDemand = AgentConfiguration.getInstance().getUseFailedRequestsInDemand();

        this.simulation = simulation;
        this.pollingInterval = pollingInterval;
    }

    @Override
    public void init(@Nonnull Controller node, @Nonnull Map<String, Object> ignored) {
        logger = LoggerFactory.getLogger(SimResourceManager.class.getName() + "." + node.getName());

        this.node = node;
        this.shortResourceReport = ResourceReport.getNullReport(node.getNodeIdentifier(),
                ResourceReport.EstimationWindow.SHORT);
        this.longResourceReport = ResourceReport.getNullReport(node.getNodeIdentifier(),
                ResourceReport.EstimationWindow.LONG);
        simulation.addResourceManagerMapping(node, this);
    }

    private HardwareConfiguration getHardwareConfiguration() {
        final String serverHardware = node.getHardware();
        final HardwareConfiguration hardwareConfig = simulation.getHardwareConfiguration(serverHardware);
        return hardwareConfig;
    }

    @Override
    @Nonnull
    public ImmutableMap<NodeAttribute, Double> getComputeCapacity() {
        final HardwareConfiguration hardwareConfig = getHardwareConfiguration();
        if (null == hardwareConfig) {
            return ImmutableMap.of();
        } else {

            final ImmutableMap.Builder<NodeAttribute, Double> builder = ImmutableMap.builder();
            hardwareConfig.getCapacity().forEach((k, v) -> builder.put(k, v));

            // make lo-fi behave like hi-fi where CPU is measured and copied to
            // TASK_CONTAINERS. If CPU is missing from the request then TASK_CONTAINERS is copied to CPU.
            if (!hardwareConfig.getCapacity().containsKey(NodeAttribute.TASK_CONTAINERS)
                    && hardwareConfig.getCapacity().containsKey(NodeAttribute.CPU)) {
                builder.put(NodeAttribute.TASK_CONTAINERS, hardwareConfig.getCapacity().get(NodeAttribute.CPU));
            } else if (hardwareConfig.getCapacity().containsKey(NodeAttribute.TASK_CONTAINERS)
                    && !hardwareConfig.getCapacity().containsKey(NodeAttribute.CPU)) {
                builder.put(NodeAttribute.CPU, hardwareConfig.getCapacity().get(NodeAttribute.TASK_CONTAINERS));
            }
            return builder.build();
        }
    }

    /**
     * Get the number of containers that can run in this node.
     * 
     * @return computed based on {@link #getComputeCapacity()}
     */
    private int getContainerCapacity() {
        final HardwareConfiguration hardwareConfig = getHardwareConfiguration();
        if (null == hardwareConfig) {
            logger.warn("No hardware configuration, assuming this means no service containers");
            return 0;
        } else {
            return hardwareConfig.getMaximumServiceContainers();
        }
    }

    @Override
    public ResourceReport getCurrentResourceReport(@Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        synchronized (lock) {
            switch (estimationWindow) {
            case LONG:
                return longResourceReport;
            case SHORT:
                if (logger.isTraceEnabled()) {
                    logger.trace("Returning short resource report with compute load: {} on {}",
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

                if (logger.isTraceEnabled()) {
                    logger.trace("Updating resource reports time: {}", now);
                }

                final ImmutableMap.Builder<NodeIdentifier, ContainerResourceReport> longContainerReports = ImmutableMap
                        .builder();
                final ImmutableMap.Builder<NodeIdentifier, ContainerResourceReport> shortContainerReports = ImmutableMap
                        .builder();
                runningContainers.forEach((id, sim) -> {
                    sim.updateResourceReports();

                    if (ServiceStatus.RUNNING.equals(sim.getServiceStatus())) {
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
                        if (logger.isTraceEnabled()) {
                            logger.trace("Container is not running: {}", id);
                        }
                    }
                });

                final ImmutableMap<NodeAttribute, Double> reportComputeCapacity = ImmutableMap
                        .copyOf(getComputeCapacity());

                // compute network information
                final ImmutableMap.Builder<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> networkCapacity = ImmutableMap
                        .builder();

                // interface -> flow -> service -> values
                final ImmutableMap.Builder<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> networkLoad = ImmutableMap
                        .builder();
                node.getNeighbors().forEach(neighborId -> {
                    final LinkResourceManager lmgr = getLinkResourceManager(neighborId);

                    final ImmutableMap<LinkAttribute, Double> neighborCapacity = lmgr.getCapacity();
                    networkCapacity.put(BasicResourceManager.createInterfaceIdentifierForNeighbor(neighborId),
                            neighborCapacity);

                    // the neighbor is the "receiving" side to get the network
                    // direction to match the hi-fi environment
                    final ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> neighborLoad = lmgr
                            .computeCurrentLinkLoad(now, neighborId);
                    networkLoad.put(BasicResourceManager.createInterfaceIdentifierForNeighbor(neighborId),
                            neighborLoad);
                });

                // create immutable data structures to put into the report
                final ImmutableMap<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> reportNetworkCapacity = networkCapacity
                        .build();
                final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportNetworkLoad = networkLoad
                        .build();
                logger.trace("Computed network load to be {}", reportNetworkLoad);

                networkDemandTracker.updateDemandValues(now, reportNetworkLoad);

                final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportShortNetworkDemand = networkDemandTracker
                        .computeNetworkDemand(ResourceReport.EstimationWindow.SHORT);

                final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportLongNetworkDemand = networkDemandTracker
                        .computeNetworkDemand(ResourceReport.EstimationWindow.LONG);

                final boolean skipNetworkData = AgentConfiguration.getInstance().getSkipNetworkData();

                longResourceReport = new ResourceReport(node.getNodeIdentifier(), now,
                        ResourceReport.EstimationWindow.LONG, reportComputeCapacity, //
                        skipNetworkData ? ImmutableMap.of() : reportNetworkCapacity,
                        skipNetworkData ? ImmutableMap.of() : reportNetworkLoad,
                        skipNetworkData ? ImmutableMap.of() : reportLongNetworkDemand, //
                        longContainerReports.build(), getContainerCapacity(), runningContainers.size());

                shortResourceReport = new ResourceReport(node.getNodeIdentifier(), now,
                        ResourceReport.EstimationWindow.SHORT, reportComputeCapacity, //
                        skipNetworkData ? ImmutableMap.of() : reportNetworkCapacity,
                        skipNetworkData ? ImmutableMap.of() : reportNetworkLoad,
                        skipNetworkData ? ImmutableMap.of() : reportShortNetworkDemand, //
                        shortContainerReports.build(), getContainerCapacity(), runningContainers.size());

                if (logger.isTraceEnabled()) {
                    logger.trace("Short report computed load: {}", shortResourceReport.getComputeLoad());
                }
            } // end lock
        } // logging thread context
    }

    private ScheduledThreadPoolExecutor resourceReportTimer = null;

    /**
     * Start generation of {@link ResourceReport} objects.
     * 
     * @throws IllegalStateException
     *             if the simulation is already started
     */
    public void startSimulation() {
        synchronized (lock) {
            if (null != resourceReportTimer) {
                throw new IllegalStateException("Cannot start the simulation when it is already running");
            }
            resourceReportTimer = new ScheduledThreadPoolExecutor(1);
            resourceReportTimer.scheduleAtFixedRate(() -> updateResourceReports(), 0, pollingInterval,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop generation of new {@link ResourceReport} objects and shutdown all
     * containers.
     */
    public void stopSimulation() {
        synchronized (lock) {
            logger.debug("Stopping resource manager {} timer null? {}", node.getName(), null == resourceReportTimer);
            if (null != resourceReportTimer) {

                resourceReportTimer.shutdown();
                resourceReportTimer = null;

                runningContainers.forEach((id, sim) -> {
                    simulation.unregisterContainer(id);
                    logger.trace("Stopped simulation for container {}", id);
                });
                runningContainers.clear();

            }
            logger.debug("Finished stopping resource manager {}", node.getName());
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

    private final Map<NodeIdentifier, ContainerSim> runningContainers = new HashMap<>();

    @Override
    public NodeIdentifier startService(@Nonnull final ServiceIdentifier<?> service,
            @Nonnull final ContainerParameters parameters) {
        logger.trace("startService {}", service);

        synchronized (lock) {
            final NodeIdentifier nextAvailable = getContainerIds().stream()
                    .filter(id -> !runningContainers.containsKey(id)).findFirst().orElse(null);

            if (null == nextAvailable) {
                logger.warn("No containers available on node {}. Limit is {}. Running: {}", node.getName(),
                        getContainerIds().size(), runningContainers);
                return null;
            } else {
                final ImmutableMap<LinkAttribute, Double> genericNetworkCapacity = parameters.getNetworkCapacity();

                final ContainerSim container = new ContainerSim(this, service, nextAvailable,
                        parameters.getComputeCapacity(), genericNetworkCapacity);
                runningContainers.put(nextAvailable, container);

                simulation.registerContainer(nextAvailable, container);
                logger.trace("Started service {} on container {}", service, nextAvailable);
                return nextAvailable;
            }
        }
    }

    @Override
    public boolean stopService(@Nonnull final NodeIdentifier containerName) {
        logger.trace("Called stop service with {}", containerName);

        synchronized (lock) {
            final ContainerSim container = runningContainers.get(containerName);
            if (null != container) {
                final boolean result = container.stopService();
                if (result) {
                    cleanupContainer(containerName);
                    logger.trace("Stopped service on container {}", containerName);
                } else {
                    logger.warn("Unable to stop container {}", containerName);
                }
                return result;
            } else {
                logger.warn("Trying to stop service on non-existant container {} running containers: {}", containerName,
                        runningContainers);
                return false;
            }
        }
    }

    private ImmutableMap<NodeIdentifier, ServiceState> computeServiceState() {
        final ImmutableMap.Builder<NodeIdentifier, ServiceState> builder = ImmutableMap.builder();
        runningContainers.forEach((id, container) -> {
            final ServiceIdentifier<?> service = container.getService();
            if (null != service) {
                final ServiceState s = new ServiceState(service, container.getServiceStatus());
                builder.put(id, s);
            }
        });
        final ImmutableMap<NodeIdentifier, ServiceState> serviceState = builder.build();
        return serviceState;
    }

    /**
     * Cleanup the specified container. This ensures that the simulation is
     * properly notified. This method MUST be called with the lock held.
     */
    private void cleanupContainer(final NodeIdentifier id) {
        runningContainers.remove(id);
        simulation.unregisterContainer(id);
    }

    @Override
    @Nonnull
    public ServiceReport getServiceReport() {
        final long time = getClock().getCurrentTime();
        final ServiceReport report = new ServiceReport(node.getNodeIdentifier(), time, computeServiceState());

        logger.trace("Service report for node {} is {}", getNode().getNodeIdentifier(), report);

        return report;
    }

    private final Map<NodeIdentifier, LinkResourceManager> linkMgrCache = new HashMap<>();

    private LinkResourceManager getLinkResourceManager(@Nonnull final NodeIdentifier neighborId) {
        // TODO: will need to clean out this cache when neighbors can change

        final LinkResourceManager lmgr = linkMgrCache.computeIfAbsent(neighborId,
                k -> simulation.getLinkResourceManager(node.getNodeIdentifier(), neighborId));
        return lmgr;
    }

    /**
     * Do nothing in the simulation.
     */
    @Override
    public void fetchImage(ServiceIdentifier<?> service) {
        // do nothing
    }

    /**
     * @return true
     */
    @Override
    public boolean waitForImage(ServiceIdentifier<?> service) {
        return true;
    }

    @Override
    public void addFailedRequest(final NodeIdentifier clientId,
            final NodeIdentifier containerId,
            final long serverEndTime,
            final Map<NodeAttribute, Double> serverLoad,
            final long networkEndTime,
            final Map<LinkAttribute, Double> networkLoad) {
        if (useFailedRequestsInDemand) {
            final ContainerSim container = runningContainers.get(containerId);
            if (null == container) {
                logger.warn("Looking for container {} to notify of failed request, but it's not running", containerId);
                return;
            }

            final NetworkClient client = getSimulation().getClientById(clientId);
            if (null == client) {
                logger.warn("Cannot find client {} to record failed request", clientId);
                return;
            }

            // find path back to the client
            final List<NetworkLink> networkPath = getSimulation().getPath(getNode(), client);
            if (networkPath.isEmpty()) {
                logger.warn("Cannot find path from {} to {} to record failed request", getNode().getNodeIdentifier(),
                        clientId);
                return;
            }

            // the first link defines the interface
            final NetworkLink firstLink = networkPath.get(0);

            final NodeIdentifier neighbor;
            if (firstLink.getLeft().getNodeIdentifier().equals(getNode().getNodeIdentifier())) {
                neighbor = firstLink.getRight().getNodeIdentifier();
            } else if (firstLink.getRight().getNodeIdentifier().equals(getNode().getNodeIdentifier())) {
                neighbor = firstLink.getLeft().getNodeIdentifier();
            } else {
                throw new RuntimeException("Invalid path, cannot find one side of the link that has "
                        + getNode().getNodeIdentifier().getName());
            }

            final InterfaceIdentifier ifce = BasicResourceManager.createInterfaceIdentifierForNeighbor(neighbor);

            container.addFailedRequest(clientId, serverEndTime, serverLoad, networkEndTime, networkLoad);

            networkDemandTracker.addFailedRequest(ifce, clientId, containerId, container.getService(), networkEndTime,
                    networkLoad);
        }
    }
}
