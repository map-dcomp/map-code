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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.protelis.networkresourcemanagement.ContainerIdentifier;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkClient;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.bbn.protelis.utils.ImmutableUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;

/**
 * Class to simulate a container within a {@link NetworkServer} in the MAP
 * network.
 * 
 * @author jschewe
 *
 */
public class ContainerSim {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerSim.class);

    private final Object lock = new Object();
    private ContainerResourceReport shortResourceReport;
    private ContainerResourceReport longResourceReport;
    private final NetworkLoadTracker networkLoadTracker;

    /**
     * 
     * @param id
     *            the identifier to use for this container
     * @param parent
     *            the resource manager for the parent node
     * @param computeCapacity
     *            see {@link #getComputeCapacity()}
     * @param networkCapacity
     *            see {@link #getNeighborNetworkCapacity()}
     */
    public ContainerSim(@Nonnull final SimResourceManager parent,
            @Nonnull final ContainerIdentifier id,
            @Nonnull final ImmutableMap<NodeAttribute<?>, Double> computeCapacity,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> networkCapacity) {
        this.identifier = id;
        this.parent = parent;
        this.computeCapacity = computeCapacity;
        this.neighborNetworkCapacity = networkCapacity;
        this.shortResourceReport = ContainerResourceReport.getNullReport(getIdentifier(),
                ResourceReport.EstimationWindow.SHORT);
        this.longResourceReport = ContainerResourceReport.getNullReport(getIdentifier(),
                ResourceReport.EstimationWindow.LONG);
        this.networkLoadTracker = new NetworkLoadTracker();

        updateResourceReports();
    }

    private final ImmutableMap<NodeAttribute<?>, Double> computeCapacity;

    /**
     * 
     * @return the capacity of this container
     */
    @Nonnull
    public ImmutableMap<NodeAttribute<?>, Double> getComputeCapacity() {
        return computeCapacity;
    }

    private final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> neighborNetworkCapacity;

    /**
     * @return the capacity of this container to each neighboring node.
     */
    @Nonnull
    public ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> getNeighborNetworkCapacity() {
        return neighborNetworkCapacity;
    }

    private final SimResourceManager parent;

    /**
     * 
     * @return the node that this container lives in
     */
    @Nonnull
    public NetworkServer getParentNode() {
        return parent.getNode();
    }

    private final ContainerIdentifier identifier;

    /**
     * 
     * @return the name of the container
     */
    @Nonnull
    public ContainerIdentifier getIdentifier() {
        return identifier;
    }

    private ServiceIdentifier<?> service;

    /**
     * 
     * @return the current running service, will be null if no service is
     *         running
     */
    public ServiceIdentifier<?> getService() {
        synchronized (lock) {
            return service;
        }
    }

    private final Map<RegionIdentifier, Integer> numRequestsPerRegion = new HashMap<>();

    /**
     * The number of requests handled by this node for each source region. Key
     * is the source region, value is the number of requests.
     * 
     * @return unmodifiable copy of the internal state
     */
    public Map<RegionIdentifier, Integer> getNumRequestsPerRegion() {
        synchronized (lock) {
            return new HashMap<>(numRequestsPerRegion);
        }
    }

    /**
     * 
     * @param service
     *            the service to start
     * @return if the service was started, will fail if there is already a
     *         service running
     */
    public boolean startService(@Nonnull final ServiceIdentifier<?> service) {
        synchronized (lock) {
            if (null == this.service) {
                this.service = service;

                // TODO: eventually make this be starting and then delay for a
                // bit
                serviceStatus = ServiceState.Status.RUNNING;
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Stop the service in this container. Calling this method when the service
     * is stopping or already stopped is a nop.
     * 
     * @return if the service was stopped, will fail if the service is not
     *         running in this container
     */
    public boolean stopService() {
        synchronized (lock) {
            if (null == service) {
                return false;
            } else {
                // TODO: eventually make this go to stopping and delay for a bit
                serviceStatus = ServiceState.Status.STOPPED;

                this.service = null;
                return true;
            }
        }
    }

    private ServiceState.Status serviceStatus = ServiceState.Status.STOPPED;

    /**
     * 
     * @return the status of the service
     */
    public ServiceState.Status getServiceStatus() {
        synchronized (lock) {
            return serviceStatus;
        }
    }

    private List<NodeLoadEntry> nodeLoad = new LinkedList<>();

    /**
     * Specify that a client is causing load on this server.
     * 
     * @param req
     *            the client request, only the nodeLoad is used.
     * @param client
     *            the client creating the connection
     * @return status of the request, if the status is
     *         {@link ClientSim.RequestResult#FAIL}, then the node load is not
     *         modified.
     * 
     */
    public ClientSim.RequestResult addNodeLoad(final NetworkClient client, final ClientRequest req) {
        synchronized (lock) {
            final long newDuration = computeServerDurationForRequest(req);

            final ServiceIdentifier<?> service = req.getService();
            if (!getService().equals(service)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Attempting to use service {} on container {} when it's running service {}", service,
                            getIdentifier(), getService());
                }
                return ClientSim.RequestResult.FAIL;
            }

            // need to add, then check the thresholds and then possibly
            // remove
            final NodeLoadEntry entry = new NodeLoadEntry(client, req, newDuration);
            nodeLoad.add(entry);

            final ClientSim.RequestResult result = computeCurrentNodeLoad();

            if (result == ClientSim.RequestResult.FAIL) {
                // don't record processing time as the request was not completed
                nodeLoad.remove(entry);
            } else {
                final RegionIdentifier sourceRegion = client.getRegionIdentifier();
                numRequestsPerRegion.merge(sourceRegion, 1, Integer::sum);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Adding node load to {} with start: {} duration: {}", identifier,
                            entry.getRequest().getStartTime(), entry.getDuration());
                }
            }

            return result;
        }
    }

    private static final double NODE_LOAD_SLOW_MULTIPLIER = 1.5;
    private static final double NODE_LOAD_FAIL_MULTIPLIER = 2.0;

    /**
     * Compute the duration that the request will take on the server. The
     * duration of the request is the minimum duration. The duration may be
     * increased based on the current load.
     * 
     * @param request
     *            the request being made
     * @return the duration that will actually be used
     */
    private long computeServerDurationForRequest(final ClientRequest request) {
        // Simple implementation that just checks if the load is currently SLOW
        // or FAIL and then multiplies based on that.
        // This should ask the ApplicationManager for how the service responds
        // to load.
        final ClientSim.RequestResult result = computeCurrentNodeLoad();
        final double multiplier;
        switch (result) {
        case SLOW:
            multiplier = NODE_LOAD_SLOW_MULTIPLIER;
            break;
        case FAIL:
            multiplier = NODE_LOAD_FAIL_MULTIPLIER;
            break;
        default:
            multiplier = 1.0;
            break;
        }

        final long minDuration = request.getServerDuration();
        final long newDuration = (long) Math.ceil(minDuration * multiplier);
        return newDuration;
    }

    /**
     * Compute the current load for the node. The lock must be held for this
     * method to be called.
     * 
     * @return the result at the current point in time
     */
    private ClientSim.RequestResult computeCurrentNodeLoad() {
        final long now = parent.getClock().getCurrentTime();

        final Map<NodeAttribute<?>, Double> load = new HashMap<>();

        final Iterator<NodeLoadEntry> nodeDemandIter = nodeLoad.iterator();
        while (nodeDemandIter.hasNext()) {
            final NodeLoadEntry entry = nodeDemandIter.next();
            final ClientRequest req = entry.getRequest();
            final long end = req.getStartTime() + entry.getDuration();
            if (req.getStartTime() <= now && now < end) {
                final ImmutableMap<NodeAttribute<?>, Double> nodeServiceLoad = req.getNodeLoadAsAttribute();
                nodeServiceLoad.forEach((k, v) -> load.merge(k, v, Double::sum));
            } else if (end > now) {
                // finished request, add to averages and remove from
                // nodeLoad
                recordRequestFinishedServer(entry.getDuration(), req.getNumberOfContainers());

                nodeDemandIter.remove();
            }
        }

        final ImmutableMap<NodeAttribute<?>, Double> serverCapacity = getComputeCapacity();

        for (final Map.Entry<NodeAttribute<?>, Double> entry : load.entrySet()) {
            final NodeAttribute<?> attribute = entry.getKey();
            final double attributeValue = entry.getValue();
            final double attributeCapacity = serverCapacity.getOrDefault(attribute, 0D);

            final double percentageOfCapacity = attributeValue / attributeCapacity;
            if (percentageOfCapacity > 1) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.debug("Client request is FAIL because attribute {} is at {} out of {}", attribute,
                            attributeValue, attributeCapacity);
                }
                return ClientSim.RequestResult.FAIL;
            } else if (percentageOfCapacity > SimulationConfiguration.getInstance().getSlowNetworkThreshold()) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.debug("Client request is SLOW because attribute {} is at {} out of {}", attribute,
                            attributeValue, attributeCapacity);
                }
                return ClientSim.RequestResult.SLOW;
            }
        }

        return ClientSim.RequestResult.SUCCESS;
    }

    private int requestsCompleted = 0;
    /**
     * The amount of time that it would take a standard sized container to
     * process the request if it were fully utilitized.
     */
    private double timeForStandardContainerToProcessRequests = 0;

    /**
     * 
     * @param timeToProcess
     *            the amount of time to process a request
     * @param numContainersUsed
     *            the number of standard sized containers used to service the
     *            request
     */
    private void recordRequestFinishedServer(final long timeToProcess, final double numContainersUsed) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Recording server request finished node: {} container: {} time: {}",
                    parent.getNode().getNodeIdentifier(), identifier, timeToProcess);
        }

        ++requestsCompleted;
        timeForStandardContainerToProcessRequests += (timeToProcess / numContainersUsed);
    }

    /**
     * Specify some load on a link on the container.
     * 
     * @param req
     *            the client request, only networkLoad is used
     * @param otherNode
     *            The node on the other end of the link from the node being
     *            managed by the resource manager
     * @param client
     *            the client causing the load
     * @return status of the request, if the status is
     *         {@link ClientSim.RequestResult#FAIL}, then the link load is not
     *         modified. The {@link NetworkLoadTracker.LinkLoadEntry} can be
     *         used to remove the link load with
     *         {@link #removeLinkLoad(NetworkLoadTracker.LinkLoadEntry)}
     * @see #removeLinkLoad(ClientRequest, NodeIdentifier)
     */
    public Pair<ClientSim.RequestResult, NetworkLoadTracker.LinkLoadEntry> addLinkLoad(@Nonnull final ClientRequest req,
            @Nonnull final NodeIdentifier client,
            @Nonnull final NodeIdentifier otherNode) {
        synchronized (lock) {
            return networkLoadTracker.addLinkLoad(parent.getClock().getCurrentTime(), req, client,
                    getNeighborNetworkCapacity(), otherNode);
        }
    }

    /**
     * Undoes the changes done by {@Link #addLinkLoad(ClientRequest,
     * NodeIdentifier)}.
     *
     * @param entry
     *            the return value from
     *            {@link #addLinkLoad(ClientRequest, NodeIdentifier)}
     * @return true if the load was removed
     * @see #addLinkLoad(ClientRequest, NodeIdentifier)
     */
    public boolean removeLinkLoad(final NetworkLoadTracker.LinkLoadEntry entry) {
        synchronized (lock) {
            return networkLoadTracker.removeLinkLoad(entry);
        }
    }

    /**
     * 
     * @return the current state of the container
     * @param demandEstimationWindow
     *            the window over which to compute the demand
     */
    public ContainerResourceReport getContainerResourceReport(
            final ResourceReport.EstimationWindow demandEstimationWindow) {
        synchronized (lock) {
            switch (demandEstimationWindow) {
            case LONG:
                return longResourceReport;
            case SHORT:
                return shortResourceReport;
            default:
                throw new IllegalArgumentException("Unknown estimation window type: " + demandEstimationWindow);
            }
        }
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
            resourceReportTimer.scheduleAtFixedRate(() -> updateResourceReports(), 0, parent.getPollingInterval(),
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop generation of new {@link ResourceReport} objects.
     */
    public void stopSimulation() {
        synchronized (lock) {
            if (null != resourceReportTimer) {
                resourceReportTimer.shutdown();
                resourceReportTimer = null;
            }
        }
    }

    /**
     * Update the current resource reports. Package visibility for testing.
     */
    /* package */ void updateResourceReports() {
        try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(getIdentifier().getName())) {
            synchronized (lock) {
                final long now = parent.getClock().getCurrentTime();

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Updating resource reports time: {} nodeLoad.size: {}", now, nodeLoad.size());
                }

                final Map<NodeIdentifier, Map<NodeAttribute<?>, Double>> computeLoad = new HashMap<>();

                final ImmutableMap<NodeAttribute<?>, Double> computeCapacity = getComputeCapacity();

                // add node load to report
                final Iterator<NodeLoadEntry> nodeLoadIter = nodeLoad.iterator();
                while (nodeLoadIter.hasNext()) {
                    final NodeLoadEntry entry = nodeLoadIter.next();
                    final NetworkClient client = entry.getClient();
                    final ClientRequest req = entry.getRequest();
                    final ServiceIdentifier<?> serviceId = req.getService();
                    final long requestStart = req.getStartTime();
                    final long requestEnd = requestStart + entry.getDuration();
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Checking request start: {} duration: {} end: {}", requestStart,
                                entry.getDuration(), requestEnd);
                    }

                    if (requestStart <= now && now < requestEnd) {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Request is active");
                        }
                        if (serviceId.equals(getService())) {

                            final Map<NodeAttribute<?>, Double> regionComputeLoad = computeLoad
                                    .computeIfAbsent(client.getNodeIdentifier(), k -> new HashMap<>());

                            final ImmutableMap<NodeAttribute<?>, Double> nodeComputeLoad = req.getNodeLoadAsAttribute();

                            nodeComputeLoad.forEach((attr, value) -> {
                                regionComputeLoad.merge(attr, value, Double::sum);
                            });
                        } // if correct service
                    } else if (requestEnd <= now) {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.debug("Request is expired");
                        }

                        // finished request, add to averages and remove from
                        // nodeLoad
                        recordRequestFinishedServer(entry.getDuration(), req.getNumberOfContainers());

                        nodeLoadIter.remove();
                    } else {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.debug("Request is in the future");
                        }
                    }
                } // foreach node demand

                // compute average processing time
                final double serverAverageProcessTime = timeForStandardContainerToProcessRequests / requestsCompleted;

                final ImmutableMap<NodeAttribute<?>, Double> reportServerCapacity = ImmutableMap
                        .copyOf(computeCapacity);
                final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> reportComputeLoad = ImmutableUtils
                        .makeImmutableMap2(computeLoad);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportNeighborNetworkCapacity = ImmutableMap
                        .copyOf(neighborNetworkCapacity);

                final NetworkLoadTracker.NetworkLoadResult loadResult = networkLoadTracker
                        .computeCurrentNetworkLoad(now);
                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportNeighborNetworkLoad = ImmutableUtils
                        .makeImmutableMap2(loadResult.loadPerNeighbor);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportClientNetworkLoad = ImmutableUtils
                        .makeImmutableMap2(loadResult.loadPerClient);

                networkLoadTracker.updateClientDemandValues(now, reportClientNetworkLoad);
                networkLoadTracker.updateNeighborDemandValues(now, reportNeighborNetworkLoad);

                updateComputeDemandValues(now, reportComputeLoad);

                // compute network capacity per client
                final Map<NodeIdentifier, Map<LinkAttribute<?>, Double>> clientNetworkCapacity = new HashMap<>();
                loadResult.clientToNeighbor.forEach((client, neighbors) -> {
                    neighbors.forEach(n -> {
                        final Map<LinkAttribute<?>, Double> neighborCapacity = reportNeighborNetworkCapacity
                                .getOrDefault(n, ImmutableMap.of());
                        final Map<LinkAttribute<?>, Double> clientCapacity = clientNetworkCapacity
                                .computeIfAbsent(client, c -> new HashMap<>());
                        neighborCapacity.forEach((attr, value) -> clientCapacity.merge(attr, value, Double::sum));
                    });

                });
                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportClientNetworkCapacity = ImmutableUtils
                        .makeImmutableMap2(clientNetworkCapacity);

                final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> reportLongServerDemand = computeComputeDemand(
                        now, ResourceReport.EstimationWindow.LONG);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportLongNeighborNetworkDemand = networkLoadTracker
                        .computeNeighborNetworkDemand(now, ResourceReport.EstimationWindow.LONG);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportLongClientNetworkDemand = networkLoadTracker
                        .computeClientNetworkDemand(now, ResourceReport.EstimationWindow.LONG);

                longResourceReport = new ContainerResourceReport(getIdentifier(), now, getService(),
                        ResourceReport.EstimationWindow.LONG, reportServerCapacity, reportComputeLoad,
                        reportLongServerDemand, serverAverageProcessTime, reportClientNetworkCapacity,
                        reportClientNetworkLoad, reportLongClientNetworkDemand, reportNeighborNetworkCapacity,
                        reportNeighborNetworkLoad, reportLongNeighborNetworkDemand);

                final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> reportShortServerDemand = computeComputeDemand(
                        now, ResourceReport.EstimationWindow.SHORT);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportShortNeighborNetworkDemand = networkLoadTracker
                        .computeNeighborNetworkDemand(now, ResourceReport.EstimationWindow.SHORT);

                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportShortClientNetworkDemand = networkLoadTracker
                        .computeClientNetworkDemand(now, ResourceReport.EstimationWindow.SHORT);

                shortResourceReport = new ContainerResourceReport(getIdentifier(), now, getService(),
                        ResourceReport.EstimationWindow.SHORT, reportServerCapacity, reportComputeLoad,
                        reportShortServerDemand, serverAverageProcessTime, reportClientNetworkCapacity,
                        reportClientNetworkLoad, reportShortClientNetworkDemand, reportNeighborNetworkCapacity,
                        reportNeighborNetworkLoad, reportShortNeighborNetworkDemand);
            } // end lock
        } // logging thread context
    }

    private ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> computeComputeDemand(final long now,
            @Nonnull final ResourceReport.EstimationWindow estimationWindow) {
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
        final Map<NodeIdentifier, Map<NodeAttribute<?>, Double>> sums = new HashMap<>();
        final Map<NodeIdentifier, Map<NodeAttribute<?>, Integer>> counts = new HashMap<>();
        NetworkLoadTracker.twoLevelHistoryMapCountSum(computeLoadHistory, cutoff, sums, counts);

        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> reportDemand = NetworkLoadTracker
                .twoLevelMapAverage(sums, counts);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("compute demand over window {} is {}", estimationWindow, reportDemand);
        }

        return reportDemand;
    }

    private final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> computeLoadHistory = new HashMap<>();

    private void updateComputeDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> computeLoad) {
        computeLoadHistory.put(timestamp, computeLoad);

        // clean out old entries from server load and network load
        final long historyCutoff = timestamp
                - Math.max(AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis(),
                        AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis());
        final Iterator<Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>>> serverIter = computeLoadHistory
                .entrySet().iterator();
        while (serverIter.hasNext()) {
            final Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> entry = serverIter
                    .next();
            if (entry.getKey() < historyCutoff) {

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Removing compute demand value {} because it's time {} is before {}", entry.getValue(),
                            entry.getKey(), historyCutoff);
                }

                serverIter.remove();
            }
        }
    }

    @Override
    public String toString() {
        return "[ " + getClass().getSimpleName() + " service: " + getService() + " ]";
    }

    /**
     * Class for storing information in the node load.
     * 
     * @author jschewe
     *
     */
    private static final class NodeLoadEntry {
        /**
         * 
         * @param client
         *            see {@link #getClient()}
         * @param request
         *            see {@link #getRequest()}
         */
        /* package */ NodeLoadEntry(@Nonnull final NetworkClient client,
                @Nonnull final ClientRequest request,
                final long duration) {
            this.client = client;
            this.request = request;
            this.duration = duration;
        }

        private final NetworkClient client;

        /**
         * @return the client that is causing the load.
         */
        @Nonnull
        public NetworkClient getClient() {
            return client;
        }

        private final ClientRequest request;

        /**
         * 
         * @return the request that is creating the load
         */
        @Nonnull
        public ClientRequest getRequest() {
            return request;
        }

        private final long duration;

        /**
         * @return The duration that the load takes, this is based on the load
         *         of the system at the time that the client requested the
         *         service
         */
        public long getDuration() {
            return duration;
        }

        /**
         * @return the number of standard sized containers that the request is
         *         using
         */
        @JsonIgnore
        public double getNumberOfContainers() {
            return getRequest().getNumberOfContainers();
        }

    }

}
