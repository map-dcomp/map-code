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
package com.bbn.map.simulator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;

/**
 * Class to simulate a container within a {@link NetworkServer} in the MAP
 * network.
 * 
 * @author jschewe
 *
 */
public class ContainerSim {

    private final Logger logger;

    private final Object lock = new Object();
    private ContainerResourceReport shortResourceReport;
    private ContainerResourceReport longResourceReport;
    private final LinkResourceManager networkLoadTracker;
    private final NetworkDemandTracker networkDemandTracker;
    private final NodeLoadTracker loadTracker = new NodeLoadTracker();

    private ObjectWriter mapper = Controller.createDumpWriter();

    /**
     * 
     * @param id
     *            the identifier to use for this container
     * @param parent
     *            the resource manager for the parent node
     * @param computeCapacity
     *            see {@link #getComputeCapacity()}
     * @param networkCapacity
     *            see {@link #getNetworkCapacity()}
     * @param serviceId
     *            see {@link #getService()}
     */
    public ContainerSim(@Nonnull final SimResourceManager parent,
            @Nonnull final ServiceIdentifier<?> serviceId,
            @Nonnull final NodeIdentifier id,
            @Nonnull final ImmutableMap<NodeAttribute<?>, Double> computeCapacity,
            @Nonnull final ImmutableMap<LinkAttribute<?>, Double> networkCapacity) {
        logger = LoggerFactory.getLogger(ContainerSim.class.getName() + "." + id);

        this.identifier = id;
        this.service = serviceId;
        this.parent = parent;
        this.computeCapacity = computeCapacity;
        this.shortResourceReport = ContainerResourceReport.getNullReport(getIdentifier(),
                ResourceReport.EstimationWindow.SHORT);
        this.longResourceReport = ContainerResourceReport.getNullReport(getIdentifier(),
                ResourceReport.EstimationWindow.LONG);
        this.networkLoadTracker = new LinkResourceManager(parent.getNode().getNodeIdentifier(), id, networkCapacity);
        this.networkDemandTracker = new NetworkDemandTracker();

        // TODO: eventually make this be starting and then delay for a
        // bit
        this.serviceStatus = ServiceState.Status.RUNNING;

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

    private final SimResourceManager parent;

    /**
     * 
     * @return the node that this container lives in
     */
    @Nonnull
    public NetworkServer getParentNode() {
        return parent.getNode();
    }

    private final NodeIdentifier identifier;

    /**
     * 
     * @return the name of the container
     */
    @Nonnull
    public NodeIdentifier getIdentifier() {
        return identifier;
    }

    private final ServiceIdentifier<?> service;

    /**
     * 
     * @return the current running service, will be null if no service is
     *         running
     */
    @Nonnull
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
     * Stop the service in this container. Calling this method when the service
     * is stopping or already stopped is a nop.
     * 
     * @return if the service was stopped
     */
    public boolean stopService() {
        synchronized (lock) {
            serviceStatus = ServiceState.Status.STOPPED;
            return true;
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

    /**
     * Specify that a client is causing load on this server.
     * 
     * @param req
     *            the client request, only the nodeLoad is used.
     * @param clientId
     *            the client creating the connection
     * @param regionId
     *            the region that the client is in
     * @return status of the request, if the status is
     *         {@link ClientSim.RequestResult#FAIL}, then the node load is not
     *         modified.
     * @param now
     *            whe the load started
     * 
     */
    public ClientSim.RequestResult addNodeLoad(@Nonnull final NodeIdentifier clientId,
            final long now,
            @Nonnull final RegionIdentifier regionId,
            @Nonnull final ClientLoad req) {
        long timeReceived = parent.getClock().getCurrentTime();

        synchronized (lock) {
            final ServiceIdentifier<?> service = req.getService();
            if (!getService().equals(service)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Attempting to use service {} on container {} when it's running service {}", service,
                            getIdentifier(), getService());
                }
                return ClientSim.RequestResult.FAIL;
            }

            // expire old entries first
            loadTracker.removeExpiredEntries(now, entry -> recordRequestFinishedServer(entry));

            // need to add, then check the thresholds and then possibly
            // remove
            final NodeLoadEntry entry = new NodeLoadEntry(now, clientId, req, req.getServerDuration());
            loadTracker.addLoad(entry);

            final Pair<ClientSim.RequestResult, Map<NodeAttribute<?>, Double>> result = computeCurrentNodeLoad();

            if (result.getLeft() == ClientSim.RequestResult.FAIL) {
                // don't record processing time as the request was not completed
                loadTracker.removeLoad(entry);
            } else {
                numRequestsPerRegion.merge(regionId, 1, Integer::sum);

                if (logger.isDebugEnabled()) {
                    logger.debug("Adding node load to {} with start: {} duration: {}", identifier,
                            entry.getRequest().getStartTime(), entry.getDuration());
                }
            }

            try {
                dumpClientRequestRecord(new ClientRequestReceivedRecord(clientId, req, timeReceived, result.getLeft(),
                        result.getRight(), computeCapacity), mapper);
            } catch (IOException e) {
                logger.error("Unable to dump client request record.", e);
            }

            return result.getLeft();
        }
    }

    /**
     * Records a client request and other relevant information to a JSON file.
     * 
     * @param record
     *            the object storing the request information
     * @param mapper
     *            the {@link ObjectWriter} for outputting the information to
     *            JSON
     * @throws IOException
     *             if the JSON file could not be written
     * 
     */
    private void dumpClientRequestRecord(@Nonnull final ClientRequestReceivedRecord record,
            @Nonnull final ObjectWriter mapper) throws IOException {
        final Path outputDirectory = this.parent.getNode().getNodeOutputDirectory();

        if (outputDirectory != null) {
            final Path clientRequestsLogFilename = outputDirectory
                    .resolve(String.format("client_requests_received-%s.json", getIdentifier().getName()));

            try (FileWriterWithEncoding writer = new FileWriterWithEncoding(clientRequestsLogFilename.toFile(),
                    Charset.defaultCharset(), true)) {
                mapper.writeValue(writer, record);
                logger.debug("Write to client request log file: {}", clientRequestsLogFilename);
            }
        }
    }

    /**
     * Compute the current load for the node. The lock must be held for this
     * method to be called.
     * 
     * @return the result and load at the current point in time
     */
    private Pair<ClientSim.RequestResult, Map<NodeAttribute<?>, Double>> computeCurrentNodeLoad() {
        final Map<NodeAttribute<?>, Double> load = loadTracker.getTotalCurrentLoad();

        final ImmutableMap<NodeAttribute<?>, Double> serverCapacity = getComputeCapacity();

        for (final Map.Entry<NodeAttribute<?>, Double> entry : load.entrySet()) {
            final NodeAttribute<?> attribute = entry.getKey();
            final double attributeValue = entry.getValue();
            final double attributeCapacity = serverCapacity.getOrDefault(attribute, 0D);

            final double percentageOfCapacity = attributeValue / attributeCapacity;
            
            logger.trace("Checking attribute: {} value: {} capacity: {} percentage: {}", attribute, attributeValue,
                    attributeCapacity, percentageOfCapacity);

            if (attributeValue > attributeCapacity || percentageOfCapacity > 1) {
                logger.trace("Client request is FAIL because attribute {} is at {} out of {}", attribute,
                        attributeValue, attributeCapacity);
                return Pair.of(ClientSim.RequestResult.FAIL, load);
            } else if (percentageOfCapacity > SimulationConfiguration.getInstance().getSlowNetworkThreshold()) {
                logger.trace("Client request is SLOW because attribute {} is at {} out of {}", attribute,
                        attributeValue, attributeCapacity);
                return Pair.of(ClientSim.RequestResult.SLOW, load);
            }
        }

        return Pair.of(ClientSim.RequestResult.SUCCESS, load);
    }

    private int requestsCompleted = 0;
    /**
     * The amount of time that it would take a standard sized container to
     * process the request if it were fully utilized.
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
    private void recordRequestFinishedServer(final NodeLoadEntry entry) {
        final long timeToProcess = entry.getDuration();
        final double numContainersUsed = entry.getRequest().getNumberOfContainers();

        logger.debug("Recording server request finished node: {} container: {} time: {}",
                parent.getNode().getNodeIdentifier(), identifier, timeToProcess);

        ++requestsCompleted;
        timeForStandardContainerToProcessRequests += (timeToProcess / numContainersUsed);
    }

    /**
     * Specify some load on a link on the container.
     * 
     * @param req
     *            the client request, only networkLoad is used
     * @param client
     *            the client causing the load
     * @return status of the request, if the status is
     *         {@link ClientSim.RequestResult#FAIL}, then the link load is not
     *         modified. The {@link LinkLoadEntry} can be used to remove the
     *         link load with
     *         {@link #removeLinkLoad(LinkResourceManager.LinkLoadEntry)}
     * @see #removeLinkLoad(ClientLoad, NodeIdentifier)
     */
    public Pair<ClientSim.RequestResult, LinkLoadEntry> addLinkLoad(@Nonnull final ClientLoad req,
            @Nonnull final NodeIdentifier client) {
        synchronized (lock) {
            // all traffic to containers only sees the host as the neighbor
            final NodeIdentifier host = parent.getNode().getNodeIdentifier();
            final ImmutableTriple<ClientSim.RequestResult, LinkLoadEntry, ?> linkManagerResult = networkLoadTracker
                    .addLinkLoad(parent.getClock().getCurrentTime(), req, client, host);
            return Pair.of(linkManagerResult.getLeft(), linkManagerResult.getMiddle());
        }
    }

    /**
     * Undoes the changes done by {@Link #addLinkLoad(ClientRequest,
     * NodeIdentifier)}.
     *
     * @param entry
     *            the return value from
     *            {@link #addLinkLoad(ClientLoad, NodeIdentifier)}
     * @see #addLinkLoad(ClientLoad, NodeIdentifier)
     */
    public void removeLinkLoad(final LinkLoadEntry entry) {
        synchronized (lock) {
            networkLoadTracker.removeLinkLoad(entry);
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

    /**
     * Update the current resource reports. Package visibility for testing.
     */
    /* package */ void updateResourceReports() {
        synchronized (lock) {
            final long now = parent.getClock().getCurrentTime();
            try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(getIdentifier().getName())
                    .push("now: " + now)) {

                logger.trace("Updating resource reports time: {}", now);

                // expire old entries first
                loadTracker.removeExpiredEntries(now, entry -> recordRequestFinishedServer(entry));

                final ImmutableMap<NodeAttribute<?>, Double> reportComputeCapacity = getComputeCapacity();
                final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>> reportNetworkCapacity = ImmutableMap
                        .of(parent.getNode().getNodeIdentifier(), networkLoadTracker.getCapacity());

                // add node load to report
                final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> reportComputeLoad = loadTracker
                        .getCurrentLoadPerClient();

                // compute average processing time
                final double serverAverageProcessTime = timeForStandardContainerToProcessRequests / requestsCompleted;

                final ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> linkNetworkLoad = networkLoadTracker
                        .computeCurrentLinkLoad(now, identifier);
                final ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> reportNetworkLoad = ImmutableMap
                        .of(parent.getNode().getNodeIdentifier(), linkNetworkLoad);
                logger.trace("network load: {}", reportNetworkLoad);

                networkDemandTracker.updateDemandValues(now, reportNetworkLoad);

                updateComputeDemandValues(now, reportComputeLoad);

                final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> reportLongServerDemand = computeComputeDemand(
                        now, ResourceReport.EstimationWindow.LONG);

                final ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> reportLongNetworkDemand = networkDemandTracker
                        .computeNetworkDemand(now, ResourceReport.EstimationWindow.LONG);

                longResourceReport = new ContainerResourceReport(getIdentifier(), now, getService(),
                        ResourceReport.EstimationWindow.LONG, reportComputeCapacity, reportComputeLoad,
                        reportLongServerDemand, serverAverageProcessTime, reportNetworkCapacity, reportNetworkLoad,
                        reportLongNetworkDemand);

                final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> reportShortServerDemand = computeComputeDemand(
                        now, ResourceReport.EstimationWindow.SHORT);

                final ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> reportShortNetworkDemand = networkDemandTracker
                        .computeNetworkDemand(now, ResourceReport.EstimationWindow.SHORT);

                shortResourceReport = new ContainerResourceReport(getIdentifier(), now, getService(),
                        ResourceReport.EstimationWindow.SHORT, reportComputeCapacity, reportComputeLoad,
                        reportShortServerDemand, serverAverageProcessTime, reportNetworkCapacity, reportNetworkLoad,
                        reportShortNetworkDemand);
            } // logging thread context
        } // end lock
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
        historyMapCountSum(computeLoadHistory, cutoff, sums, counts);

        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> reportDemand = historyMapAverage(
                sums, counts);

        if (logger.isTraceEnabled()) {
            logger.trace("compute demand over window {} is {}", estimationWindow, reportDemand);
        }

        return reportDemand;
    }

    private static <K1, K2> void historyMapCountSum(final Map<Long, ImmutableMap<K1, ImmutableMap<K2, Double>>> history,
            final long historyCutoff,
            final Map<K1, Map<K2, Double>> sums,
            final Map<K1, Map<K2, Integer>> counts) {
        history.forEach((timestamp, historyValue) -> {
            if (timestamp >= historyCutoff) {

                historyValue.forEach((k1, v1) -> {
                    final Map<K2, Double> sum1 = sums.computeIfAbsent(k1, k -> new HashMap<>());
                    final Map<K2, Integer> count1 = counts.computeIfAbsent(k1, k -> new HashMap<>());

                    v1.forEach((k2, value) -> {
                        final double newSum = sum1.getOrDefault(k2, 0D) + value;
                        final int newCount = count1.getOrDefault(k2, 1) + 1;

                        sum1.put(k2, newSum);
                        count1.put(k2, newCount);
                    }); // v1.forEach

                }); // historyValue.forEach

            } // inside window
        }); // history.forEach

    }

    private static <K1, K2> ImmutableMap<K1, ImmutableMap<K2, Double>> historyMapAverage(
            final Map<K1, Map<K2, Double>> sums,
            final Map<K1, Map<K2, Integer>> counts) {
        final ImmutableMap.Builder<K1, ImmutableMap<K2, Double>> result = ImmutableMap.builder();

        sums.forEach((k1, sum1) -> {
            final Map<K2, Integer> count1 = counts.get(k1);
            final ImmutableMap.Builder<K2, Double> average1 = ImmutableMap.builder();

            sum1.forEach((k2, value) -> {
                final int count = count1.get(k2);
                final double average = value / count;
                average1.put(k2, average);
            }); // sum1.forEach

            result.put(k1, average1.build());
        }); // sums.forEach

        return result.build();
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

                if (logger.isTraceEnabled()) {
                    logger.trace("Removing compute demand value {} because it's time {} is before {}", entry.getValue(),
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
     * Class for recording client requests and other relevant information.
     * 
     * @author awald
     *
     */
    public static final class ClientRequestReceivedRecord {
        private NodeIdentifier client;
        private ClientLoad request;
        private long timeReceived;
        private ClientSim.RequestResult status;
        private Map<NodeAttribute<?>, Double> load;
        private ImmutableMap<NodeAttribute<?>, Double> serverCapacity;

        /**
         * 
         * @param client
         *            see {@link #getClient()}
         * @param request
         *            see {@link #getRequest()}
         * @param timeReceived
         *            see {@link #getTimeReceived()}
         * @param status
         *            see {@link #getStatus()}
         * @param load
         *            see {@link #getLoad()}
         * @param serverCapacity
         *            see {@link #getServerCapacity()}
         */
        public ClientRequestReceivedRecord(@JsonProperty("client") final NodeIdentifier client,
                @JsonProperty("request") final ClientLoad request,
                @JsonProperty("timeReceived") final long timeReceived,
                @JsonProperty("status") final ClientSim.RequestResult status,
                @JsonProperty("load") Map<NodeAttribute<?>, Double> load,
                @JsonProperty("serverCapacity") ImmutableMap<NodeAttribute<?>, Double> serverCapacity) {
            this.client = client;
            this.request = request;
            this.timeReceived = timeReceived;
            this.status = status;
            this.load = load;
            this.serverCapacity = serverCapacity;
        }

        /**
         * @return the client that is causing the load.
         */
        public NodeIdentifier getClient() {
            return client;
        }

        /**
         * @return the request that is creating the load
         */
        public ClientLoad getRequest() {
            return request;
        }

        /**
         * @return the time that the request was received
         */
        public long getTimeReceived() {
            return timeReceived;
        }

        /**
         * @return the result of processing the request
         */
        public ClientSim.RequestResult getStatus() {
            return status;
        }

        /**
         * @return the new load after the request was received
         */
        public Map<NodeAttribute<?>, Double> getLoad() {
            return load;
        }

        /**
         * @return the compute capacity of the server
         */
        public ImmutableMap<NodeAttribute<?>, Double> getServerCapacity() {
            return serverCapacity;
        }
    }
}
