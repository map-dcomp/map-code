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
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.Dependency;
import com.bbn.map.common.value.DependencyDemandFunction;
import com.bbn.map.common.value.LinkMetricName;
import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkClient;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.VirtualClock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java8.util.Objects;

/**
 * Simulate a client by producing demand.
 * 
 */
public class ClientSim extends Thread {

    /**
     * If a request is processed this many time units late a warning is output.
     */
    private static final long TIME_PROCESSING_THRESOLD = 10;

    /**
     * The status of applying a request on the network.
     * 
     * @author jschewe
     *
     */
    public enum RequestResult {
        /**
         * The client request succeeded.
         */
        SUCCESS,
        /**
         * The client request succeeded, but was slowed down.
         */
        SLOW,
        /**
         * The client request failed.
         */
        FAIL;

        /**
         * Provide a way to choose the worst result with {@link #FAIL} being
         * worse than {@link #SLOW}, which is worse than {@link #SUCCESS}.
         * 
         * @param one
         *            the first result to compare
         * @param two
         *            the second result to compare
         * @return the worst result
         */
        public static RequestResult chooseWorstResult(final RequestResult one, final RequestResult two) {
            if (one == FAIL || two == FAIL) {
                return FAIL;
            } else if (one == SLOW || two == SLOW) {
                return SLOW;
            } else {
                return SUCCESS;
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSim.class);
    private final NetworkClient client;

    private ObjectWriter mapper = Controller.createDumpWriter();

    /**
     * The number of client requests attempted is the sum of the
     * {@link ClientLoad#getNumClients()} property of the {@link ClientLoad}
     * objects that have been processed.
     * 
     * @return the number of client requests that were attempted
     */
    public int getNumRequestsAttempted() {
        return state.getNumRequestsAttempted();
    }

    /**
     * The number of client requests succeeded.
     * 
     * @return the number of client requests that succeeded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSucceeded() {
        return state.getNumRequestsSucceeded();
    }

    /**
     * @return the number of client requests that failed because the network
     *         path was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsFailedForNetworkLoad() {
        return state.getNumRequestsFailedForNetworkLoad();
    }

    /**
     * @return the number of client requests that failed because the server was
     *         overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsFailedForServerLoad() {
        return state.getNumRequestsFailedForServerLoad();
    }

    /**
     * @return the number of client requests that were slow because the network
     *         path was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForNetworkLoad() {
        return state.getNumRequestsSlowForNetworkLoad();
    }

    /**
     * @return the number of client requests that were slow because both the
     *         network path and the server were overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForNetworkAndServerLoad() {
        return state.getNumRequestsSlowForNetworkAndServerLoad();
    }

    /**
     * @return the number of client requests that are slow because the server
     *         was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForServerLoad() {
        return state.getNumRequestsSlowForServerLoad();
    }

    /**
     * The number of requests from this client that were serviced by each
     * region.
     * 
     * @return key is region, value is count. Unmodifiable map.
     */
    public Map<RegionIdentifier, Integer> getNumRequestsServicedByRegion() {
        return Collections.unmodifiableMap(state.getNumRequestsServicedByRegion());
    }

    private final Simulation simulation;
    private final ImmutableList<ClientLoad> clientRequests;

    /**
     * 
     * @return the demand that this client will put on the network.
     */
    @JsonIgnore
    public ImmutableList<ClientLoad> getClientRequests() {
        return clientRequests;
    }

    private AtomicBoolean running = new AtomicBoolean(false);

    /**
     * @param simulation
     *            the simulation
     * @param client
     *            the client that we're simulating demand for
     * @param demandPath
     *            the base path to demand, may be null
     */
    public ClientSim(@Nonnull final Simulation simulation, @Nonnull final NetworkClient client, final Path demandPath) {
        super(client.getNodeIdentifier().getName() + "-simulator");

        this.simulation = simulation;
        this.client = client;

        if (null == demandPath) {
            this.clientRequests = ImmutableList.of();
        } else {
            final String demandFilename = String.format("%s.json", client.getNodeIdentifier().getName());
            final Path clientDemandPath = demandPath.resolve(demandFilename);

            if (!Files.exists(clientDemandPath)) {
                LOGGER.warn("Cannot find {}", clientDemandPath);
            }
            this.clientRequests = ClientLoad.parseClientDemand(clientDemandPath);
        }

        state = new ClientState(this);
    }

    /**
     * Shutdown the client simulator.
     */
    public void shutdownSimulator() {
        running.set(false);
        this.interrupt();
    }

    /**
     * Start the simulator.
     */
    public void startSimulator() {
        start();
    }

    /**
     * Find the
     * 
     * @throws IllegalArgumentException
     *             if a network node with the specified id cannot be found
     */
    private NetworkNode lookupNode(final NodeIdentifier id) {
        final NetworkNode server = simulation.getControllerById(id);
        if (null == server) {
            final NetworkNode client = simulation.getClientById(id);
            if (null == client) {
                final ContainerSim container = simulation.getContainerById((NodeIdentifier) id);
                if (null == container) {
                    throw new IllegalArgumentException(
                            id + " cannot be found in the simulation as a client, server or container");
                } else {
                    return container.getParentNode();
                }
            } else {
                return client;
            }
        } else {
            return server;
        }
    }

    /**
     * Run the simulator. The thread will execute until either
     * {@link #shutdownSimulator()} is called or all client requests have been
     * dispatched and their durations completed.
     */
    @Override
    public void run() {
        running.set(true);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Starting client sim thread");
        }
        final VirtualClock clock = simulation.getClock();

        final PriorityQueue<QueueEntry> runQueue = new PriorityQueue<>(QueueComparator.INSTANCE);

        // create the initial queue of requests
        this.clientRequests.forEach(req -> {
            final QueueEntry entry = new QueueEntry(this.client.getNodeIdentifier(), req);
            runQueue.add(entry);
        });

        final ApplicationManagerApi applicationManager = AppMgrUtils.getApplicationManager();

        long latestEndOfRequest = 0;

        long numRequests = 0;
        long totalRequestStartDelay = 0;

        while (running.get() && !runQueue.isEmpty()) {
            final QueueEntry entry = runQueue.poll();
            if (null == entry) {
                LOGGER.error(
                        "Got null entry from queue that should not be empty. Going to continue and see what happens.");
                continue;
            }

            final NodeIdentifier clientId = entry.getClient();
            final ClientLoad req = entry.getClientLoad();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Waiting for request start time: " + req.getStartTime());
            }

            clock.waitUntilTime(req.getStartTime());
            LOGGER.info("Applying client request: " + req);

            final long requestStartDelay = clock.getCurrentTime() - req.getStartTime();
            if (requestStartDelay > TIME_PROCESSING_THRESOLD) {
                LOGGER.warn("Request is {} late", requestStartDelay);
            }
            totalRequestStartDelay += requestStartDelay;
            ++numRequests;

            final ApplicationCoordinates service = req.getService();
            final ApplicationSpecification appSpec = applicationManager.getApplicationSpecification(service);
            if (null == appSpec) {
                throw new RuntimeException("Unable to find application specification configuration for " + service);
            }

            final NetworkNode localClient = lookupNode(clientId);
            final RegionIdentifier clientRegion = localClient.getRegionIdentifier();

            // simulate multiple clients
            final long startOfProcessing = System.currentTimeMillis();
            for (int clientIndex = 0; clientIndex < req.getNumClients(); ++clientIndex) {
                final long now = clock.getCurrentTime();

                try (CloseableThreadContext.Instance ctc = CloseableThreadContext
                        .push(String.format("%d of %d", clientIndex + 1, req.getNumClients()))) {

                    RequestResult serverResult = RequestResult.FAIL;

                    // do a DNS lookup each time in case there are
                    // multiple servers for the requested service

                    try {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("getting container for hostname {}", appSpec.getServiceHostname());
                        }
                        final ContainerSim destContainer = simulation.getContainerForService(clientId, clientRegion,
                                service);
                        LOGGER.info("  request for {} goes to {}", appSpec.getServiceHostname(),
                                destContainer.getIdentifier());

                        final NetworkServer destNode = destContainer.getParentNode();
                        if (!destNode.isExecuting()) {
                            LOGGER.warn("Server {} is not running, cannot send request", destNode);
                            state.incrementRequestsFailedForDownNode();

                            dumpClientRequestRecord(new ClientRequestRecord(destNode.getNodeIdentifier(),
                                    destContainer.getIdentifier(), now, req, 0, null, null, serverResult, true),
                                    mapper);

                            continue;
                        }

                        final List<NetworkLink> networkPath = simulation.getPath(localClient, destNode);
                        if (networkPath.isEmpty()) {
                            LOGGER.warn("No path to {} from {}", localClient, destNode);
                            state.incrementRequestsFailedForDownNode();

                            dumpClientRequestRecord(new ClientRequestRecord(destNode.getNodeIdentifier(),
                                    destContainer.getIdentifier(), now, req, 0, null, null, serverResult, true),
                                    mapper);

                            continue;
                        }

                        final ImmutableTriple<RequestResult, List<LinkLoadEntry>, List<ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>>> networkResult = applyNetworkDemand(
                                clientId, localClient.getNodeIdentifier(), now, req, destContainer, networkPath);

                        if (!RequestResult.FAIL.equals(networkResult.getLeft())) {
                            serverResult = destContainer.addNodeLoad(clientId, now, clientRegion, req);

                            if (RequestResult.FAIL.equals(serverResult)) {
                                unapplyNetworkDemand(networkResult.getMiddle());
                            }
                        }

                        // record the results of the request
                        List<ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> linkLoads = new ArrayList<>();
                        for (ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> linkResult : networkResult
                                .getRight()) {
                            ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> linkLoad = linkResult;
                            linkLoads.add(linkLoad);
                        }

                        dumpClientRequestRecord(new ClientRequestRecord(destNode.getNodeIdentifier(),
                                destContainer.getIdentifier(), now, req, networkResult.getRight().size(), linkLoads,
                                networkResult.getLeft(), serverResult, false), mapper);

                        if (RequestResult.FAIL.equals(networkResult.getLeft())) {
                            state.incrementRequestsFailedForNetworkLoad();
                            LOGGER.info("Request failed for network load");
                        } else if (RequestResult.FAIL.equals(serverResult)) {
                            state.incrementRequestsFailedForServerLoad();
                            LOGGER.info("Request failed for server load");
                        } else {
                            if (RequestResult.SLOW.equals(networkResult.getLeft())) {
                                state.incrementRequestsSlowForNetworkLoad();
                            }
                            if (RequestResult.SLOW.equals(serverResult)) {
                                state.incrementRequestsSlowForServerLoad();
                            }
                            if (RequestResult.SLOW.equals(networkResult.getLeft())
                                    && RequestResult.SLOW.equals(serverResult)) {
                                state.incrementRequestsSlowForNetworkAndServerLoad();
                            }

                            final RegionIdentifier destinationRegion = destNode.getRegionIdentifier();
                            state.incrementRequestsServicedByRegion(destinationRegion);

                            final long endOfThisRequest = req.getStartTime()
                                    + Math.max(req.getNetworkDuration(), req.getServerDuration());
                            latestEndOfRequest = Math.max(latestEndOfRequest, endOfThisRequest);

                            // Create dependent load for each successful client
                            // request. One might be able to combine multiple
                            // requests from common destContainer if this
                            // creates too many requests.
                            for (final Dependency dependency : appSpec.getDependencies()) {
                                final ClientLoad dependentRequest = createDependentRequest(req, dependency, 1);

                                LOGGER.info("Created dependent demand {}", dependentRequest);
                                final QueueEntry dependentEntry = new QueueEntry(destContainer.getIdentifier(),
                                        dependentRequest);
                                runQueue.add(dependentEntry);
                            }
                        }

                    } catch (final UnknownHostException uhe) {
                        LOGGER.warn("Error finding container for service: {}. Client request failed.", service, uhe);

                        dumpClientRequestRecord(
                                new ClientRequestRecord(null, null, now, req, 0, null, null, serverResult, true),
                                mapper);

                        state.incrementRequestsFailedForDownNode();
                    }

                    // increment counters
                    state.incrementRequestsAttempted();

                } // logging context
                catch (final DNSSim.DNSLoopException e) {
                    LOGGER.error("Found DNS loop, client request failed", e);

                    dumpClientRequestRecord(new ClientRequestRecord(null, null, now, req, 0, null, null, null, true),
                            mapper);

                    state.incrementRequestsAttempted();
                } // allocate logger context and catch dns loops

            } // foreach client request

            final long endOfProcess = System.currentTimeMillis();
            LOGGER.info("Took {} ms to process request", (endOfProcess - startOfProcessing));

        } // while running and demand left

        LOGGER.info("Waiting until {} when the latest request will finish", latestEndOfRequest);
        clock.waitUntilTime(latestEndOfRequest);

        LOGGER.info("Average delay in processing all {} requests is {}", numRequests,
                ((double) totalRequestStartDelay / numRequests));

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Client sim thread finished.");
        }
    }

    private ClientLoad createDependentRequest(final ClientLoad req, final Dependency dependency, final int numClients) {
        final DependencyDemandFunction demandFunction = dependency.getDemandFunction();
        Objects.requireNonNull(demandFunction, "The demand function for a dependency cannot be null: " + dependency);

        final ApplicationSpecification dependentApplication = dependency.getDependentApplication();
        Objects.requireNonNull(dependentApplication,
                "The dependent service for a dependency cannot be null: " + dependency);

        final ApplicationCoordinates dependentService = dependentApplication.getCoordinates();
        Objects.requireNonNull(dependentApplication,
                "The coordinates for an application cannot be null: " + dependentApplication);

        final long startTime = demandFunction.computeStartTime(req.getStartTime(), req.getServerDuration(),
                req.getNetworkDuration());
        final long serverDuration = demandFunction.computeServerDuration(req.getServerDuration(),
                req.getNetworkDuration());
        final long networkDuration = demandFunction.computeNetworkDuration(req.getServerDuration(),
                req.getNetworkDuration());

        final ImmutableMap<NodeMetricName, Double> nodeLoad = ImmutableMap
                .copyOf(demandFunction.computeDependencyNodeLoad(req.getNodeLoad()));
        final ImmutableMap<LinkMetricName, Double> linkLoad = ImmutableMap
                .copyOf(demandFunction.computeDependencyLinkLoad(req.getNetworkLoad()));

        final ClientLoad dependentLoad = new ClientLoad(startTime, serverDuration, networkDuration, numClients,
                dependentService, nodeLoad, linkLoad);
        return dependentLoad;
    }

    // lock for the dump properties
    private final Object dumpPropertyLock = new Object();

    private Path baseOutputDirectory = null;

    /**
     * 
     * @return the base directory to write to, the node will create a directory
     *         within this directory to put it's state. May be null, in which
     *         case data will not be dumped, but an error will be logged.
     */
    public Path getBaseOutputDirectory() {
        synchronized (dumpPropertyLock) {
            return baseOutputDirectory;
        }
    }

    /**
     * 
     * @param v
     *            see {@link #getBaseOutputDirectory()}
     */
    public void setBaseOutputDirectory(final Path v) {
        synchronized (dumpPropertyLock) {
            baseOutputDirectory = v;
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
     * 
     */
    private void dumpClientRequestRecord(@Nonnull final ClientRequestRecord record,
            @Nonnull final ObjectWriter mapper) {
        if (baseOutputDirectory != null) {
            try {
                Path outputDirectory = baseOutputDirectory.resolve(getClientName());

                if (outputDirectory.toFile().exists() || outputDirectory.toFile().mkdirs()) {
                    Path clientRequestsLogFilename = outputDirectory
                            .resolve(String.format("client_requests_sent-%s.json", getClient().getName()));

                    try (FileWriterWithEncoding writer = new FileWriterWithEncoding(clientRequestsLogFilename.toFile(),
                            Charset.defaultCharset(), true)) {
                        mapper.writeValue(writer, record);
                        LOGGER.debug("Write to client request log file: {}", clientRequestsLogFilename);
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Unable to dump client request record.", e);
            }
        }
    }

    /**
     * Apply the network demand to all nodes on the path.
     *
     * @param firstNodeId
     *            the first node in the path, this is the same as the clientId
     *            if the client is a node on the network graph and not a
     *            container
     * @param clientId
     *            the client that is creating the network load
     * @return the request status, the applied loads for later unapplying if
     *         needed, and the current loads
     */
    private ImmutableTriple<RequestResult, List<LinkLoadEntry>, List<ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>>> applyNetworkDemand(
            final NodeIdentifier clientId,
            final NodeIdentifier firstNodeId,
            final long now,
            final ClientLoad req,
            final ContainerSim serviceContainer,
            final List<NetworkLink> path) {

        List<ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> linkLoads = new ArrayList<>();

        // need to break once there is a failure
        // need to keep track of the applied link loads

        RequestResult result = RequestResult.SUCCESS;

        final List<LinkLoadEntry> appliedLoads = new LinkedList<>();

        NodeIdentifier localSource = firstNodeId;
        for (final NetworkLink link : path) {
            final LinkResourceManager lmgr = simulation.getLinkResourceManager(link);

            final ImmutableTriple<ClientSim.RequestResult, LinkLoadEntry, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> linkResult = lmgr
                    .addLinkLoad(now, req, clientId, localSource);

            final ClientSim.RequestResult requestResult = linkResult.getLeft();
            final LinkLoadEntry appliedLinkLoad = linkResult.getMiddle();
            final ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> linkLoad = linkResult
                    .getRight();
            linkLoads.add(linkLoad);

            result = RequestResult.chooseWorstResult(result, requestResult);
            if (linkResult.getLeft() == RequestResult.FAIL) {
                LOGGER.trace("Failed for network load at link {}", link);

                // no sense going farther
                break;
            } else {
                appliedLoads.add(appliedLinkLoad);
            }

            final NodeIdentifier localDest;
            if (link.getLeft().getNodeIdentifier().equals(localSource)) {
                localDest = link.getRight().getNodeIdentifier();
            } else if (link.getRight().getNodeIdentifier().equals(localSource)) {
                localDest = link.getLeft().getNodeIdentifier();
            } else {
                throw new RuntimeException(
                        "Invalid path, cannot find one side of the link that has " + localSource.getName());
            }

            localSource = localDest;
        } // foreach link in the path

        if (RequestResult.FAIL != result) {
            // apply to the container
            final Pair<RequestResult, LinkLoadEntry> containerResult = serviceContainer.addLinkLoad(req, clientId);

            result = RequestResult.chooseWorstResult(result, containerResult.getLeft());
        }

        if (RequestResult.FAIL == result) {
            // unapply on failure
            unapplyNetworkDemand(appliedLoads);
        }

        return ImmutableTriple.of(result, appliedLoads, linkLoads);
    }

    /**
     * Unapply the specified link loads from the specified resource managers
     */
    private static void unapplyNetworkDemand(final List<LinkLoadEntry> appliedLoads) {
        appliedLoads.forEach(entry -> {
            final LinkResourceManager resManager = entry.getParent();

            resManager.removeLinkLoad(entry);
        });

    }

    /**
     * @return the region of the client
     */
    public RegionIdentifier getClientRegion() {
        return client.getRegionIdentifier();
    }

    /**
     * @return the name of the client
     */
    public String getClientName() {
        return client.getName();
    }

    private ClientState state;

    /**
     * @return the state information of the simulation
     */
    public ClientState getSimulationState() {
        return state;
    }

    /**
     * @return the {@link NetworkClient} object that this simulator is using
     */
    @Nonnull
    public NetworkClient getClient() {
        return client;
    }

    /**
     * Comparison function for {@link QueueEntry} objects. The
     * {@link QueueEntry} class is not marked comparable since there may be two
     * objects that have equivalent priority, but aren't consider equal.
     * 
     * @author jschewe
     *
     */
    private static final class QueueComparator implements Comparator<QueueEntry> {
        public static final QueueComparator INSTANCE = new QueueComparator();

        @Override
        public int compare(final QueueEntry o1, final QueueEntry o2) {
            // sort by time
            return Long.compare(o1.getClientLoad().getStartTime(), o2.getClientLoad().getStartTime());
        }

    }

    /**
     * Entry in the priority queue of tasks client load requests to execute.
     * 
     * @author jschewe
     *
     */
    private static final class QueueEntry {
        private QueueEntry(@Nonnull final NodeIdentifier client, @Nonnull final ClientLoad clientLoad) {
            this.client = client;
            this.clientLoad = clientLoad;
        }

        private final NodeIdentifier client;

        @Nonnull
        public NodeIdentifier getClient() {
            return client;
        }

        private final ClientLoad clientLoad;

        @Nonnull
        public ClientLoad getClientLoad() {
            return clientLoad;
        }
    }

}
