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

import java.io.IOException;
import java.io.Reader;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.ServiceConfiguration;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.protelis.networkresourcemanagement.NetworkClient;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.VirtualClock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.UnmodifiableIterator;

/**
 * Simulate a client by producing demand.
 * 
 */
public class ClientSim {

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
    private final Thread thread;

    private int numRequestsAttempted = 0;

    /**
     * The number of client requests attempted is the sum of the
     * {@link ClientRequest#getNumClients()} property of the
     * {@link ClientRequest} objects that have been processed.
     * 
     * @return the number of client requests that were attempted
     */
    public int getNumRequestsAttempted() {
        return numRequestsAttempted;
    }

    private int numRequestsSucceeded = 0;

    /**
     * The number of client requests succeeded.
     * 
     * @return the number of client requests that succeeded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSucceeded() {
        return numRequestsSucceeded;
    }

    private int numRequestsFailedForServerLoad = 0;

    private int numRequestsFailedForNetworkLoad = 0;

    /**
     * @return the number of client requests that failed because the network
     *         path was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsFailedForNetworkLoad() {
        return numRequestsFailedForNetworkLoad;
    }

    /**
     * @return the number of client requests that failed because the server was
     *         overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsFailedForServerLoad() {
        return numRequestsFailedForServerLoad;
    }

    private int numRequestsSlowForNetworkLoad = 0;

    /**
     * @return the number of client requests that were slow because the network
     *         path was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForNetworkLoad() {
        return numRequestsSlowForNetworkLoad;
    }

    private int numRequestsSlowForNetworkAndServerLoad = 0;

    /**
     * @return the number of client requests that were slow because both the
     *         network path and the server were overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForNetworkAndServerLoad() {
        return numRequestsSlowForNetworkAndServerLoad;
    }

    private int numRequestsSlowForServerLoad = 0;

    /**
     * @return the number of client requests that are slow because the server
     *         was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForServerLoad() {
        return numRequestsSlowForServerLoad;
    }

    private final Map<RegionIdentifier, Integer> numRequestsServicedByRegion = new HashMap<>();

    /**
     * The number of requests from this client that were serviced by each
     * region.
     * 
     * @return key is region, value is count. Unmodifiable map.
     */
    public Map<RegionIdentifier, Integer> getNumRequestsServicedByRegion() {
        return Collections.unmodifiableMap(numRequestsServicedByRegion);
    }

    private final Simulation simulation;
    private final ImmutableList<ClientRequest> clientRequests;

    /**
     * 
     * @return the demand that this client will put on the network.
     */
    @JsonIgnore
    public ImmutableList<ClientRequest> getClientRequests() {
        return clientRequests;
    }

    private boolean running = false;
    private final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations;

    /**
     * @param simulation
     *            the simulation
     * @param client
     *            the client that we're simulating demand for
     * @param demandPath
     *            the base path to demand, may be null
     * @param serviceConfigurations
     *            information about where to find each service
     */
    public ClientSim(@Nonnull final Simulation simulation,
            @Nonnull final NetworkClient client,
            final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations,
            final Path demandPath) {
        thread = new Thread(() -> {
            runSim();
        }, client.getNodeIdentifier().getName() + "-simulator");
        this.simulation = simulation;
        this.client = client;
        this.serviceConfigurations = serviceConfigurations;

        if (null == demandPath) {
            this.clientRequests = ImmutableList.of();
        } else {
            final String demandFilename = String.format("%s.json", client.getNodeIdentifier().getName());
            final Path clientDemandPath = demandPath.resolve(demandFilename);

            this.clientRequests = parseClientDemand(clientDemandPath);
        }
    }

    /**
     * @param clientDemandPath
     *            where to read from
     * @return client demand sorted by start time
     */
    @Nonnull
    private static ImmutableList<ClientRequest> parseClientDemand(final Path clientDemandPath) {
        if (!Files.exists(clientDemandPath)) {
            return ImmutableList.of();
        }

        try {
            try (Reader reader = Files.newBufferedReader(clientDemandPath, StandardCharsets.UTF_8)) {

                ObjectMapper mapper = new ObjectMapper().registerModule(new GuavaModule());

                final List<ClientRequest> demand = mapper.readValue(reader,
                        new TypeReference<LinkedList<ClientRequest>>() {
                        });
                demand.sort((one, two) -> Long.compare(one.getStartTime(), two.getStartTime()));

                return ImmutableList.copyOf(demand);
            }
        } catch (final IOException e) {
            LOGGER.error(
                    "Error reading client demand from " + clientDemandPath.toString() + ", ignoring client demand file",
                    e);
            return ImmutableList.of();
        }
    }

    /**
     * Shutdown the client simulator.
     */
    public void shutdownSimulator() {
        running = false;
        thread.interrupt();
    }

    /**
     * Start the simulator.
     */
    public void startSimulator() {
        running = true;
        thread.start();
    }

    private void runSim() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Starting client sim thread");
        }
        final VirtualClock clock = simulation.getClock();

        final UnmodifiableIterator<ClientRequest> iter = clientRequests.iterator();
        while (running && iter.hasNext()) {
            final ClientRequest req = iter.next();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Waiting for request start time: " + req.getStartTime());
            }

            clock.waitUntilTime(req.getStartTime());

            LOGGER.info("Applying client request: " + req);

            final ServiceIdentifier<?> service = req.getService();
            final ServiceConfiguration serviceConfig = serviceConfigurations.get(service);
            if (null == serviceConfig) {
                throw new RuntimeException("Unable to find service configuration for " + service);
            }

            try {

                // simulate multiple clients
                for (int clientIndex = 0; clientIndex < req.getNumClients(); ++clientIndex) {
                    try (CloseableThreadContext.Instance ctc = CloseableThreadContext
                            .push(String.format("%d of %d", clientIndex + 1, req.getNumClients()))) {

                        RequestResult serverResult = RequestResult.FAIL;

                        // do a DNS lookup each time in case there are
                        // multiple
                        // servers for the requested service
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("getting container for hostname {}", serviceConfig.getHostname());
                        }

                        final ContainerSim destContainer = simulation.getContainerForService(client, service);
                        LOGGER.info("  request for {} goes to {}", serviceConfig.getHostname(),
                                destContainer.getIdentifier());

                        final NetworkServer destNode = destContainer.getParentNode();

                        final List<NetworkLink> networkPath = simulation.getPath(client, destNode);

                        final Pair<RequestResult, List<Pair<LinkResourceManager, LinkResourceManager.LinkLoadEntry>>> networkResult = applyNetworkDemand(
                                req, destContainer, networkPath);

                        if (RequestResult.FAIL != networkResult.getLeft()) {
                            serverResult = destContainer.addNodeLoad(this.client, req);

                            if (RequestResult.FAIL == serverResult) {
                                unapplyNetworkDemand(networkResult.getRight());
                            }
                        }

                        // increment counters
                        ++numRequestsAttempted;
                        if (RequestResult.FAIL == networkResult.getLeft()) {
                            ++numRequestsFailedForNetworkLoad;
                            LOGGER.info("Request failed for network load");
                        } else if (RequestResult.FAIL == serverResult) {
                            ++numRequestsFailedForServerLoad;
                            LOGGER.info("Request failed for server load");
                        } else {
                            if (RequestResult.SLOW == networkResult.getLeft()) {
                                ++numRequestsSlowForNetworkLoad;
                            }
                            if (RequestResult.SLOW == serverResult) {
                                ++numRequestsSlowForServerLoad;
                            }
                            if (RequestResult.SLOW == networkResult.getLeft() && RequestResult.SLOW == serverResult) {
                                ++numRequestsSlowForNetworkAndServerLoad;
                            }

                            final RegionIdentifier destinationRegion = destNode.getRegionIdentifier();
                            final int prevRegionCount = numRequestsServicedByRegion.getOrDefault(destinationRegion, 0);
                            numRequestsServicedByRegion.put(destinationRegion, prevRegionCount + 1);

                            ++numRequestsSucceeded;
                        }

                    } // logging context
                    catch (final DNSSim.DNSLoopException e) {
                        LOGGER.error("Found DNS loop, client request failed", e);
                        ++numRequestsAttempted;
                    }

                } // foreach client request

            } catch (final UnknownHostException e) {
                throw new RuntimeException("Invalid client demand configuration", e);
            }

        } // while running and demand left

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Client sim thread finished.");
        }
    }

    /**
     * Apply the network demand to all nodes on the path.
     * 
     * @return the request status and the applied loads for later unapplying if
     *         needed
     */
    private Pair<RequestResult, List<Pair<LinkResourceManager, LinkResourceManager.LinkLoadEntry>>> applyNetworkDemand(
            final ClientRequest req, final ContainerSim serviceContainer, final List<NetworkLink> path) {

        // need to break once there is a failure
        // need to keep track of the applied link loads

        RequestResult result = RequestResult.SUCCESS;

        final List<Pair<LinkResourceManager, LinkResourceManager.LinkLoadEntry>> appliedLoads = new LinkedList<>();

        NetworkNode localSource = client;
        for (final NetworkLink link : path) {
            final LinkResourceManager lmgr = simulation.getLinkResourceManager(link);

            final Pair<RequestResult, LinkResourceManager.LinkLoadEntry> linkResult = lmgr
                    .addLinkLoad(client.getNodeIdentifier(), req);

            result = RequestResult.chooseWorstResult(result, linkResult.getLeft());
            if (linkResult.getLeft() == RequestResult.FAIL) {
                // no sense going farther
                break;
            } else {
                appliedLoads.add(Pair.of(lmgr, linkResult.getRight()));
            }

            final NetworkNode localDest;
            if (link.getLeft().equals(localSource)) {
                localDest = link.getRight();
            } else if (link.getRight().equals(localSource)) {
                localDest = link.getLeft();
            } else {
                throw new RuntimeException("Invalid path, cannot find one side of the link that has "
                        + localSource.getNodeIdentifier().getName());
            }

            localSource = localDest;
        } // foreach link in the path

        if (RequestResult.FAIL != result) {
            // apply to the container
            final NetworkLink lastLink = path.get(path.size() - 1);
            final NetworkServer containerParent = serviceContainer.getParentNode();

            // determine which neighbor node the traffic is from
            final NetworkNode otherNode;
            if (lastLink.getLeft().equals(containerParent)) {
                otherNode = lastLink.getRight();
            } else if (lastLink.getRight().equals(containerParent)) {
                otherNode = lastLink.getLeft();
            } else {
                throw new RuntimeException("Invalid path, cannot find one side of the link that has "
                        + containerParent.getNodeIdentifier().getName());
            }

            final Pair<RequestResult, NetworkLoadTracker.LinkLoadEntry> containerResult = serviceContainer
                    .addLinkLoad(req, client.getNodeIdentifier(), otherNode.getNodeIdentifier());

            result = RequestResult.chooseWorstResult(result, containerResult.getLeft());
        }

        if (RequestResult.FAIL == result) {
            // unapply on failure
            unapplyNetworkDemand(appliedLoads);
        }

        return Pair.of(result, appliedLoads);
    }

    /**
     * Unapply the specified link loads from the specified resource managers
     */
    private void unapplyNetworkDemand(
            final List<Pair<LinkResourceManager, LinkResourceManager.LinkLoadEntry>> appliedLoads) {
        appliedLoads.forEach(p -> {
            final LinkResourceManager resManager = p.getLeft();
            final LinkResourceManager.LinkLoadEntry entry = p.getRight();

            final boolean removed = resManager.removeLinkLoad(entry);
            if (!removed) {
                LOGGER.error("Could not remove link load {}. This is likely a programming error.", entry.getRequest());
            }
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

}
