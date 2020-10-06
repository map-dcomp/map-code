/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
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
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;

/**
 * Base class for client simulation and background traffic simulation.
 * 
 * @author jschewe
 *
 */
/* package */ abstract class AbstractClientSimulator extends Thread {

    /**
     * If a request is processed this many time units late a warning is output.
     */
    protected static final long TIME_PROCESSING_THRESOLD = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractClientSimulator.class);

    private final Simulation simulation;

    @JsonIgnore
    protected final Simulation getSimulation() {
        return simulation;
    }

    /* package */ AbstractClientSimulator(final Simulation simulation) {
        this.simulation = simulation;
    }

    /**
     * The number of client requests attempted is the sum of the
     * {@link ClientLoad#getNumClients()} property of the {@link ClientLoad}
     * objects that have been processed.
     * 
     * @return the number of client requests that were attempted
     */
    public int getNumRequestsAttempted() {
        return getSimulationState().getNumRequestsAttempted();
    }

    /**
     * The number of client requests succeeded.
     * 
     * @return the number of client requests that succeeded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSucceeded() {
        return getSimulationState().getNumRequestsSucceeded();
    }

    /**
     * @return the number of client requests that failed because the network
     *         path was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsFailedForNetworkLoad() {
        return getSimulationState().getNumRequestsFailedForNetworkLoad();
    }

    /**
     * @return the number of client requests that failed because the server was
     *         overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsFailedForServerLoad() {
        return getSimulationState().getNumRequestsFailedForServerLoad();
    }

    /**
     * @return the number of client requests that were slow because the network
     *         path was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForNetworkLoad() {
        return getSimulationState().getNumRequestsSlowForNetworkLoad();
    }

    /**
     * @return the number of client requests that were slow because both the
     *         network path and the server were overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForNetworkAndServerLoad() {
        return getSimulationState().getNumRequestsSlowForNetworkAndServerLoad();
    }

    /**
     * @return the number of client requests that are slow because the server
     *         was overloaded
     * @see #getNumRequestsAttempted()
     */
    public int getNumRequestsSlowForServerLoad() {
        return getSimulationState().getNumRequestsSlowForServerLoad();
    }

    /**
     * The number of requests from this client that were serviced by each
     * region.
     * 
     * @return key is region, value is count. Unmodifiable map.
     */
    public Map<RegionIdentifier, Integer> getNumRequestsServicedByRegion() {
        return Collections.unmodifiableMap(getSimulationState().getNumRequestsServicedByRegion());
    }

    /**
     * @return the state information of the simulation
     */
    public abstract BaseClientState getSimulationState();

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
    protected final void dumpClientRequestRecord(@Nonnull final ClientRequestRecord record,
            @Nonnull final ObjectWriter mapper) {
        if (baseOutputDirectory != null) {
            try {
                Path outputDirectory = baseOutputDirectory.resolve(getSimName());

                if (outputDirectory.toFile().exists() || outputDirectory.toFile().mkdirs()) {
                    Path clientRequestsLogFilename = outputDirectory
                            .resolve(String.format("client_requests_sent-%s.json", getSimName()));

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

    // CHECKSTYLE:OFF - data class
    protected static final class NetworkDemandApplicationResult {
        RequestResult result = RequestResult.FAIL;
        List<LinkLoadEntry> appliedLoads = Collections.emptyList();
        List<ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> linkLoads = Collections
                .emptyList();
        double pathLinkDelay = Double.NaN;
    }
    // CHECKSTYLE:ON

    /**
     * Apply the network demand to all nodes on the path.
     * 
     * Package visible for testing
     * 
     * @param simulation
     *            the simulation being run
     * @param clientId
     *            the client that is creating the network load
     * @param firstNodeId
     *            the first node in the path, this is the same as the clientId
     *            if the client is a node on the network graph and not a
     *            container
     * @param serviceContainer
     *            the container, will be null if the destination is not a
     *            container.
     *
     * @return the request status, the applied loads for later unapplying if
     *         needed, and the current loads
     */
    /* package */ static NetworkDemandApplicationResult applyNetworkDemand(final Simulation simulation,
            final NodeIdentifier clientId,
            final NodeIdentifier firstNodeId,
            final long now,
            final BaseNetworkLoad req,
            final ContainerSim serviceContainer,
            final NodeNetworkFlow flow,
            final List<NetworkLink> path) {

        final List<ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> linkLoads = new ArrayList<>();

        // need to break once there is a failure
        // need to keep track of the applied link loads

        RequestResult result = RequestResult.SUCCESS;

        final List<LinkLoadEntry> appliedLoads = new LinkedList<>();

        double pathLinkDelay = 0;

        NodeIdentifier localSource = firstNodeId;
        for (final NetworkLink link : path) {
            final LinkResourceManager lmgr = simulation.getLinkResourceManager(link);

            final NodeIdentifier localDest;
            if (link.getLeft().getNodeIdentifier().equals(localSource)) {
                localDest = link.getRight().getNodeIdentifier();
            } else if (link.getRight().getNodeIdentifier().equals(localSource)) {
                localDest = link.getLeft().getNodeIdentifier();
            } else {
                throw new RuntimeException(
                        "Invalid path, cannot find one side of the link that has " + localSource.getName());
            }

            // The transmitting node is the destination because the load in the
            // client request is from the server's perspective. So as we work
            // our way down the path from the client to the server, the
            // transmitting node needs to match the TX value in the load, which
            // will be the node closest to the server.
            final ImmutableTriple<RequestResult, LinkLoadEntry, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> linkResult = lmgr
                    .addLinkLoad(now + Math.round(pathLinkDelay), req, flow, localDest);

            final RequestResult requestResult = linkResult.getLeft();
            final LinkLoadEntry appliedLinkLoad = linkResult.getMiddle();
            final ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> linkLoad = linkResult
                    .getRight();
            linkLoads.add(linkLoad);

            pathLinkDelay += computeLinkDelay(link);

            result = RequestResult.chooseWorstResult(result, requestResult);
            if (linkResult.getLeft() == RequestResult.FAIL) {
                LOGGER.trace("Failed for network load at link {}", link);

                // no sense going farther
                break;
            } else {
                appliedLoads.add(appliedLinkLoad);
            }

            localSource = localDest;
        } // foreach link in the path

        if (RequestResult.FAIL != result && null != serviceContainer) {
            // apply to the container
            final Pair<RequestResult, LinkLoadEntry> containerResult = serviceContainer
                    .addLinkLoad(now + Math.round(pathLinkDelay), req, clientId);

            result = RequestResult.chooseWorstResult(result, containerResult.getLeft());
        }

        if (RequestResult.FAIL == result) {
            // unapply on failure
            unapplyNetworkDemand(appliedLoads);
        }

        final NetworkDemandApplicationResult networkResult = new NetworkDemandApplicationResult();
        networkResult.result = result;
        networkResult.appliedLoads = appliedLoads;
        networkResult.linkLoads = linkLoads;
        return networkResult;
    }

    /**
     * Unapply the specified link loads from the specified resource managers
     */
    protected static final void unapplyNetworkDemand(final List<LinkLoadEntry> appliedLoads) {
        appliedLoads.forEach(entry -> {
            final LinkResourceManager resManager = entry.getParent();

            resManager.removeLinkLoad(entry);
        });

    }

    private static final double LINK_FP_ERROR = 1E-6;
    private static final double AVERAGE_PACKET_SIZE_BITS = 12000;

    private static double computeLinkDelay(final NetworkLink link) {
        if (AgentConfiguration.getInstance().isSimulateLinkLatencies()) {
            final double linkDelay = link.getDelay();
            if (Math.abs(linkDelay) < LINK_FP_ERROR) {
                // zero, compute bandwidth delay

                // For simplicity, let us assume 1500 byte packets. This is
                // 12,000
                // bits. This should give us a worst case delay as the MTU on
                // most
                // links is 1500 bytes.

                // Bandwidth delay = 12000 / (bits/usec). For 100 Mbps bits/usec
                // is
                // 100. So for a 1500 byte packet on a 100Mbps link the
                // bandwidth
                // delay is 120 usec. This is 0.12 milliseconds.

                final double bandwidthDelay = AVERAGE_PACKET_SIZE_BITS / link.getBandwidth();
                return bandwidthDelay;
            } else {
                // use the specified delay
                return linkDelay;
            }
        } else {
            // not simulating link latencies
            return 0;
        }
    }

    /**
     * Package visible for testing.
     * 
     * @param clientId
     *            the client in the flow
     * @param destContainer
     *            the server container in the flow
     * @return the flow object to use
     */
    /* package */ static NodeNetworkFlow createNetworkFlow(final NodeIdentifier clientId,
            final NodeIdentifier destContainerId) {
        // the flow source is the server since the client load requests are
        // from the perspective of the server
        final NodeNetworkFlow flow = new NodeNetworkFlow(destContainerId, clientId, destContainerId);
        return flow;
    }

    /**
     * @return the name of the simulator, should not have spaces or special
     *         characters in it
     */
    public abstract String getSimName();

    private final AtomicBoolean running = new AtomicBoolean(false);

    protected final void setRunnning() {
        running.set(true);
    }

    protected final boolean isRunning() {
        return running.get();
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
     * @throws IllegalArgumentException
     *             if a network node with the specified id cannot be found
     * @param id
     *            the node to find
     * @return the node
     */
    protected final NetworkNode lookupNode(final NodeIdentifier id) {
        final NetworkNode server = getSimulation().getControllerById(id);
        if (null == server) {
            final NetworkNode client = getSimulation().getClientById(id);
            if (null == client) {
                final ContainerSim container = getSimulation().getContainerById((NodeIdentifier) id);
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

}
