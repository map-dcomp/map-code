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

import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAccumulator;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.Dependency;
import com.bbn.map.common.value.DependencyDemandFunction;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkClient;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.VirtualClock;
import com.diffplug.common.base.Errors;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Simulate a client by producing demand.
 * 
 */
public class ClientSim extends AbstractClientSimulator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSim.class);
    private final NetworkClient client;

    private final ImmutableList<ClientLoad> clientRequests;

    /**
     * 
     * @return the demand that this client will put on the network.
     */
    @JsonIgnore
    public ImmutableList<ClientLoad> getClientRequests() {
        return clientRequests;
    }

    /**
     * @param simulation
     *            the simulation
     * @param client
     *            the client that we're simulating demand for
     * @param demandPath
     *            the base path to demand, may be null
     */
    public ClientSim(@Nonnull final Simulation simulation, @Nonnull final NetworkClient client, final Path demandPath) {
        super(simulation);

        setName(client.getNodeIdentifier().getName() + "-simulator");

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

    private static final long IDLE_THREAD_TIMEOUT_MINUTES = 5;

    /**
     * Run the simulator. The thread will execute until either
     * {@link #shutdownSimulator()} is called or all client requests have been
     * dispatched and their durations completed.
     */
    @Override
    public void run() {
        setRunnning();

        final ThreadFactory threadPoolFactory = new ThreadFactoryBuilder().setNameFormat("clientSim" + "-%d").build();
        final ExecutorService threadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, IDLE_THREAD_TIMEOUT_MINUTES,
                TimeUnit.MINUTES, new SynchronousQueue<Runnable>(), threadPoolFactory);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Starting client sim thread");
        }
        final VirtualClock clock = getSimulation().getClock();

        final PriorityQueue<QueueEntry> runQueue = new PriorityQueue<>(QueueComparator.INSTANCE);

        // create the initial queue of requests
        this.clientRequests.forEach(req -> {
            final QueueEntry entry = new QueueEntry(this.client.getNodeIdentifier(), req);
            runQueue.add(entry);
        });

        final ApplicationManagerApi applicationManager = AppMgrUtils.getApplicationManager();

        final ThreadLocalObjectWriter mapper = new ThreadLocalObjectWriter();

        final LongAccumulator latestEndOfRequest = new LongAccumulator(Long::max, 0);

        long numRequests = 0;
        long totalRequestStartDelay = 0;

        List<Future<?>> requestFutures = new LinkedList<>();

        while (isRunning() && !(runQueue.isEmpty() && requestFutures.isEmpty())) {
            // prune finished futures
            final Iterator<Future<?>> futureIter = requestFutures.iterator();
            while (futureIter.hasNext()) {
                final Future<?> future = futureIter.next();
                if (future.isDone()) {
                    futureIter.remove();
                }
            }

            if (runQueue.isEmpty()) {
                // this happens if there are request futures and no more items
                // in the queue
                continue;
            }

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
            LOGGER.info("Applying client request: {} from {}", req, clientId);

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
                final int idx = clientIndex;
                final Future<?> future = threadPool.submit(() -> {
                    final long now = clock.getCurrentTime();

                    simulateClientRequest(runQueue, mapper, service, appSpec, clientId, req, localClient, clientRegion,
                            now, latestEndOfRequest, idx);
                });
                requestFutures.add(future);
            } // foreach client request

            final long endOfProcess = System.currentTimeMillis();
            LOGGER.info("Took {} ms to process request", (endOfProcess - startOfProcessing));
        } // while running and demand left

        // wait for all futures to finish to ensure that latestEndOfRequest is
        // accurate
        requestFutures.forEach(Errors.rethrow().wrap(f -> {
            f.get();
        }));

        LOGGER.info("Waiting until {} when the latest request will finish", latestEndOfRequest.get());
        clock.waitUntilTime(latestEndOfRequest.get());

        LOGGER.info("Average delay in processing all {} requests is {}", numRequests,
                ((double) totalRequestStartDelay / numRequests));

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Client sim thread finished.");
        }
    }

    private void simulateClientRequest(final PriorityQueue<QueueEntry> runQueue,
            final ThreadLocalObjectWriter mapper,
            final ApplicationCoordinates service,
            final ApplicationSpecification appSpec,
            final NodeIdentifier clientId,
            final ClientLoad req,
            final NetworkNode localClient,
            final RegionIdentifier clientRegion,
            final long now,
            final LongAccumulator latestEndOfRequest,
            final int clientIndex) {

        try (CloseableThreadContext.Instance ctc = CloseableThreadContext
                .push(String.format("%d - %d of %d", req.getStartTime(), clientIndex + 1, req.getNumClients()))) {

            // do a DNS lookup each time in case there are
            // multiple servers for the requested service

            try {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("getting container for hostname {}", appSpec.getServiceHostname());
                }
                final ContainerSim destContainer = getSimulation().getContainerForService(clientId, clientRegion,
                        service);
                // final destination for the network traffic
                final NodeIdentifier destinationIdentifier = destContainer.getIdentifier();
                LOGGER.info("  request for {} goes to {}", appSpec.getServiceHostname(), destinationIdentifier);

                final NetworkServer destNode = destContainer.getParentNode();
                final ResourceManager<?> destNodeResMgr = destNode.getResourceManager();
                if (!destNode.isExecuting()) {
                    LOGGER.warn("Server {} is not running, cannot send request", destNode);
                    getSimulationState().incrementRequestsFailedForDownNode();

                    dumpClientRequestRecord(new ClientRequestRecord(destNode.getNodeIdentifier(), destinationIdentifier,
                            now, req, 0, null, null, RequestResult.FAIL, true), mapper);

                    destNodeResMgr.addFailedRequest(clientId, destinationIdentifier, now + req.getServerDuration(),
                            req.getNodeLoad(), now + req.getNetworkDuration(), req.getNetworkLoadAsAttribute());
                    return;
                }

                final boolean requestResult = executeRequest(clientId, destinationIdentifier, localClient, destNode,
                        mapper, destContainer, clientRegion, now, req, latestEndOfRequest);
                if (requestResult) {
                    // Create dependent load for each successful
                    // client
                    // request. One might be able to combine
                    // multiple
                    // requests from common destContainer if this
                    // creates too many requests.
                    for (final Dependency dependency : appSpec.getDependencies()) {
                        final ClientLoad dependentRequest = createDependentRequest(req, dependency);

                        LOGGER.info("Created dependent demand {}", dependentRequest);
                        final QueueEntry dependentEntry = new QueueEntry(destinationIdentifier, dependentRequest);
                        runQueue.add(dependentEntry);
                    }
                } else {
                    destNodeResMgr.addFailedRequest(clientId, destinationIdentifier, now + req.getServerDuration(),
                            req.getNodeLoad(), now + req.getNetworkDuration(), req.getNetworkLoadAsAttribute());
                }

            } catch (final UnknownHostException uhe) {
                LOGGER.warn("Error finding container for service: {}. Client request failed.", service, uhe);

                dumpClientRequestRecord(
                        new ClientRequestRecord(null, null, now, req, 0, null, null, RequestResult.FAIL, true), mapper);

                getSimulationState().incrementRequestsFailedForDownNode();
                getSimulationState().incrementRequestsAttempted();
            }

        } // logging context
        catch (final DNSSim.DNSLoopException e) {
            LOGGER.error("Found DNS loop, client request failed", e);

            dumpClientRequestRecord(new ClientRequestRecord(null, null, now, req, 0, null, null, null, true), mapper);

            getSimulationState().incrementRequestsAttempted();
        } // allocate logger context and catch dns loops

    }

    /**
     * @param req
     *            the original request
     * @param dependency
     *            the specified dependency
     * @return a new request to be executed
     */
    public static ClientLoad createDependentRequest(final ClientLoad req, final Dependency dependency) {
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

        final ImmutableMap<NodeAttribute, Double> nodeLoad = ImmutableMap
                .copyOf(demandFunction.computeDependencyNodeLoad(req.getNodeLoad()));
        final ImmutableMap<LinkAttribute, Double> linkLoad = ImmutableMap
                .copyOf(demandFunction.computeDependencyLinkLoad(req.getNetworkLoad()));

        // assume that there are no arguments to pass to the client
        final ClientLoad dependentLoad = new ClientLoad(startTime, serverDuration, networkDuration, 1, dependentService,
                nodeLoad, linkLoad, ImmutableList.of());
        return dependentLoad;
    }

    private static final int MAX_ATTEMPTS = 2;

    /**
     * <code>destNode</code> and <code>destinationIdentifier</code> will be
     * different when <code>destContainer</code> is specified.
     * 
     * @param clientId
     *            the client making the request
     * @param destinationIdentifier
     *            the identifier of the destination node
     * @param localClient
     *            the node making the request
     * @param destNodegoes
     *            the node receiving the request, must be a node in the graph.
     *            Note that containers are not in the graph.
     * @param mapper
     *            used to write out the state of the request
     * @param destContainer
     *            the container receiving the traffic, may be null
     * @param clientRegion
     *            the region that the request is being made from
     * @param now
     *            what time it is now
     * @param req
     *            the request
     * @param latestEndOfRequest
     *            the current end of the last request
     * @return if the request succeeded
     */
    private boolean executeRequest(final NodeIdentifier clientId,
            final NodeIdentifier destinationIdentifier,
            final NetworkNode localClient,
            final NetworkNode destNode,
            final ThreadLocalObjectWriter mapper,
            final ContainerSim destContainer,
            final RegionIdentifier clientRegion,
            final long now,
            final ClientLoad req,
            final LongAccumulator latestEndOfRequest) {
        throw new RuntimeException("HACK for testing");
/*        
        final NodeNetworkFlow flow = createNetworkFlow(clientId, destinationIdentifier);

        final ImmutableMap<LinkAttribute, Double> networkLoadAsAttribute = req.getNetworkLoadAsAttribute();
        final ImmutableMap<LinkAttribute, Double> networkLoadAsAttributeFlipped = req
                .getNetworkLoadAsAttributeFlipped();
        final ApplicationCoordinates service = req.getService();
        long networkDuration = req.getNetworkDuration();
        long serverDuration = req.getServerDuration();
        final long start = System.currentTimeMillis();
        long reduceDuration = 0;

        NetworkDemandApplicationResult networkResult = null;
        RequestResult serverResult = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; ++attempt) {
            getSimulationState().incrementRequestsAttempted();

            if (attempt > 0) {
                // delay before a retry
                final long diff = System.currentTimeMillis() - start;
                final long delayStart = getRetryDelay(req);
                reduceDuration = reduceDuration + delayStart + diff;
                networkDuration = req.getNetworkDuration() - reduceDuration;
                serverDuration = req.getServerDuration() - reduceDuration;
                LOGGER.warn("Delaying start by {} ms, reducing duration by {} ms, attempt {}", delayStart,
                        reduceDuration, attempt);

                if (networkDuration < 1) {
                    LOGGER.error("Network duration is less than 1 for retry, failing request");
                    break;
                }

                if (serverDuration < 1) {
                    LOGGER.error("Server duration is less than 1 for retry, failing request");
                    break;
                }

                try {
                    Thread.sleep(delayStart);
                } catch (final InterruptedException sleepEx) {
                    LOGGER.warn("Delayed start interrupted", sleepEx);
                }
            }

            if (!localClient.getNodeIdentifier().equals(destNode.getNodeIdentifier())) {
                final List<NetworkLink> networkPath = getSimulation().getPath(localClient, destNode);
                if (networkPath.isEmpty()) {
                    LOGGER.warn("No path to {} from {}", destNode, localClient);
                    getSimulationState().incrementRequestsFailedForDownNode();

                    dumpClientRequestRecord(new ClientRequestRecord(destNode.getNodeIdentifier(), destinationIdentifier,
                            now, req, 0, null, null, RequestResult.FAIL, true), mapper);

                    return false;
                } else {
                    LOGGER.trace("Path from {} to {} is {}", localClient, destNode, networkPath);
                }

                networkResult = applyNetworkDemand(getSimulation(), clientId, localClient.getNodeIdentifier(), now,
                        networkLoadAsAttribute, networkLoadAsAttributeFlipped, service, networkDuration, destContainer,
                        flow, networkPath);
            } else {
                // traffic is between 2 containers on the same node, no network
                // traffic to apply, treat this as success with nothing to do
                networkResult = new NetworkDemandApplicationResult();
                networkResult.result = RequestResult.SUCCESS;
                LOGGER.trace("Network connection is between 2 containers on the same node: {} -- {}", localClient,
                        destNode);
            }

            if (!RequestResult.FAIL.equals(networkResult.result)) {
                if (null != destContainer) {
                    serverResult = destContainer.addNodeLoad(clientId, now + Math.round(networkResult.pathLinkDelay),
                            serverDuration, clientRegion, req);
                } else {
                    // no destination container means there is no
                    // way for the server to fail
                    serverResult = RequestResult.SUCCESS;
                }

                if (RequestResult.FAIL.equals(serverResult)) {
                    unapplyNetworkDemand(networkResult.appliedLoads);
                } else {
                    // success, no more attempts
                    break;
                }
            } else {
                serverResult = RequestResult.FAIL;
            }
        }

        if (null == networkResult || null == serverResult) {
            throw new RuntimeException(
                    "Internal error network result or server result is null, check logic in client attempts loop");
        }

        // record the results of the request
        final List<ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> linkLoads = new ArrayList<>();
        for (ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> linkResult : networkResult.linkLoads) {
            ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> linkLoad = linkResult;
            linkLoads.add(linkLoad);
        }

        dumpClientRequestRecord(new ClientRequestRecord(destNode.getNodeIdentifier(), destinationIdentifier, now, req,
                linkLoads.size(), linkLoads, networkResult.result, serverResult, false), mapper);

        LOGGER.info("network result: {} server result: {}", networkResult.result, serverResult);
        if (RequestResult.FAIL.equals(networkResult.result)) {
            getSimulationState().incrementRequestsFailedForNetworkLoad();
            LOGGER.info("Request for {} to {} failed for network load", req.getService().getArtifact(),
                    destContainer.getIdentifier());
            return false;
        } else if (RequestResult.FAIL.equals(serverResult)) {
            getSimulationState().incrementRequestsFailedForServerLoad();
            LOGGER.info("Request for {} to {} failed for server load", req.getService().getArtifact(),
                    destContainer.getIdentifier());
            return false;
        } else {
            if (RequestResult.SLOW.equals(networkResult.result)) {
                getSimulationState().incrementRequestsSlowForNetworkLoad();
            }
            if (RequestResult.SLOW.equals(serverResult)) {
                getSimulationState().incrementRequestsSlowForServerLoad();
            }
            if (RequestResult.SLOW.equals(networkResult.result) && RequestResult.SLOW.equals(serverResult)) {
                getSimulationState().incrementRequestsSlowForNetworkAndServerLoad();
            }
            if (!RequestResult.FAIL.equals(networkResult.result) && !RequestResult.FAIL.equals(serverResult)) {
                LOGGER.info("Request for {} to {} succeeded", req.getService().getArtifact(),
                        destContainer.getIdentifier());
            }

            final RegionIdentifier destinationRegion = destNode.getRegionIdentifier();
            getSimulationState().incrementRequestsServicedByRegion(destinationRegion);

            final long endOfThisRequest = now + Math.max(req.getNetworkDuration(), req.getServerDuration());
            latestEndOfRequest.accumulate(endOfThisRequest);

            return true;
        }
        */
    }

    /**
     * @return the region of the client
     */
    public RegionIdentifier getClientRegion() {
        return client.getRegionIdentifier();
    }

    @Override
    public String getSimName() {
        return client.getName();
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

    private ClientState state;

    @Override
    public ClientState getSimulationState() {
        return state;
    }

    private static final double MIN_DELAY_PERCENT = 1 / 60.0;
    private static final double MAX_DELAY_PERCENT = 3 / 60.0;

    private static long getRetryDelay(final ClientLoad req) {
        final long maxDuration = Math.max(req.getServerDuration(), req.getNetworkDuration());
        final long minDelay = (long) Math.floor(MIN_DELAY_PERCENT * maxDuration);
        final long maxDelay = (long) Math.ceil(MAX_DELAY_PERCENT * maxDuration);
        return getRetryDelay(minDelay, maxDelay);
    }

    private static long getRetryDelay(long minDelay, long maxDelay) {
        return Math.abs(ThreadLocalRandom.current().nextLong() / 2) % (maxDelay - minDelay) + minDelay;
    }
}
