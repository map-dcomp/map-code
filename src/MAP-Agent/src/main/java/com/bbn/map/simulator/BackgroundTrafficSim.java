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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.Controller;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NetworkNode;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.VirtualClock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Simulate bacckground network traffic.
 * 
 * @author jschewe
 *
 */
public class BackgroundTrafficSim extends AbstractClientSimulator {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundTrafficSim.class);

    private final BaseClientState state;

    private final ImmutableList<BackgroundNetworkLoad> requests;

    /**
     * 
     * @return the demand that will be put on the network.
     */
    @JsonIgnore
    public ImmutableList<BackgroundNetworkLoad> getRequests() {
        return requests;
    }

    /**
     * 
     * @param simulation
     *            see {@link #getSimulation()}
     * @param backgroundTrafficFile
     *            the file to read the background traffic from, may be null
     * @see BackgroundNetworkLoad#parseBackgroundTraffic(Path)
     */
    public BackgroundTrafficSim(@Nonnull final Simulation simulation, final Path backgroundTrafficFile) {
        super(simulation);

        if (null == backgroundTrafficFile) {
            this.requests = ImmutableList.of();
        } else {
            if (!Files.exists(backgroundTrafficFile)) {
                LOGGER.debug("Cannot find {}, skipping", backgroundTrafficFile);
                this.requests = ImmutableList.of();
            } else {
                this.requests = BackgroundNetworkLoad.parseBackgroundTraffic(backgroundTrafficFile);
            }
        }

        state = new BaseClientState();
    }

    @Override
    public BaseClientState getSimulationState() {
        return state;
    }

    @Override
    public String getSimName() {
        return "background_traffic";
    }

    /**
     * Run the simulator. The thread will execute until either
     * {@link #shutdownSimulator()} is called or all requests have been
     * dispatched and their durations completed.
     */
    @Override
    public void run() {
        setRunnning();

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Starting background traffic thread");
        }
        final VirtualClock clock = getSimulation().getClock();

        final ObjectWriter mapper = Controller.createDumpWriter();

        long latestEndOfRequest = 0;

        long numRequests = 0;
        long totalRequestStartDelay = 0;

        for (final BackgroundNetworkLoad request : this.requests) {
            if (!isRunning()) {
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Waiting for request start time: " + request.getStartTime());
            }

            clock.waitUntilTime(request.getStartTime());
            LOGGER.info("Applying background request: {}", request);

            final long requestStartDelay = clock.getCurrentTime() - request.getStartTime();
            if (requestStartDelay > TIME_PROCESSING_THRESOLD) {
                LOGGER.warn("Request is {} late", requestStartDelay);
            }
            totalRequestStartDelay += requestStartDelay;
            ++numRequests;

            final NetworkNode clientNode = lookupNode(new DnsNameIdentifier(request.getClient()));
            final NetworkNode serverNode = lookupNode(new DnsNameIdentifier(request.getServer()));

            // simulate multiple clients
            final long startOfProcessing = System.currentTimeMillis();
            final long now = clock.getCurrentTime();

            try {
                final Pair<Boolean, Long> requestResult = executeRequest(clientNode, serverNode, mapper, now, request,
                        latestEndOfRequest);
                if (requestResult.getLeft()) {
                    latestEndOfRequest = requestResult.getRight();
                }

                // increment counters
                getSimulationState().incrementRequestsAttempted();

            } catch (final DNSSim.DNSLoopException e) {
                LOGGER.error("Found DNS loop, client request failed", e);

                dumpClientRequestRecord(new ClientRequestRecord(null, null, now, request, 0, null, null, null, true),
                        mapper);

                getSimulationState().incrementRequestsAttempted();
            } // allocate logger context and catch dns loops

            final long endOfProcess = System.currentTimeMillis();
            LOGGER.info("Took {} ms to process request", (endOfProcess - startOfProcessing));

        } // while running and demand left

        LOGGER.info("Waiting until {} when the latest request will finish", latestEndOfRequest);
        clock.waitUntilTime(latestEndOfRequest);

        LOGGER.info("Average delay in processing all {} requests is {}", numRequests,
                ((double) totalRequestStartDelay / numRequests));

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Background traffic sim thread finished.");
        }
    }

    /**
     * <code>destNode</code> and <code>destinationIdentifier</code> will be
     * different when <code>destContainer</code> is specified.
     * 
     * @param clientNode
     *            the node making the request
     * @param serverNode
     *            the node receiving the request, must be a node in the graph.
     *            Note that containers are not in the graph.
     * @param mapper
     *            used to write out the state of the request
     * @param now
     *            what time it is now
     * @param request
     *            the request
     * @param latestEndOfRequest
     *            the current end of the last request
     * @return boolean if the request succeeded, the new value of end of last
     *         request
     */
    private Pair<Boolean, Long> executeRequest(final NetworkNode clientNode,
            final NetworkNode serverNode,
            final ObjectWriter mapper,
            final long now,
            final BackgroundNetworkLoad request,
            final long latestEndOfRequest) {

        final NodeNetworkFlow flow = createNetworkFlow(clientNode.getNodeIdentifier(), serverNode.getNodeIdentifier());
        final List<NetworkLink> networkPath = getSimulation().getPath(clientNode, serverNode);
        if (networkPath.isEmpty()) {
            LOGGER.warn("No path to {} from {}", clientNode, serverNode);
            getSimulationState().incrementRequestsFailedForDownNode();

            dumpClientRequestRecord(new ClientRequestRecord(serverNode.getNodeIdentifier(),
                    serverNode.getNodeIdentifier(), now, request, 0, null, null, RequestResult.FAIL, true), mapper);

            return Pair.of(false, latestEndOfRequest);
        }

        final NetworkDemandApplicationResult networkResult = applyNetworkDemand(getSimulation(),
                clientNode.getNodeIdentifier(), clientNode.getNodeIdentifier(), now, request, null, flow, networkPath);

        // record the results of the request
        final List<ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> linkLoads = new ArrayList<>();
        for (ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> linkResult : networkResult.linkLoads) {
            ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> linkLoad = linkResult;
            linkLoads.add(linkLoad);
        }

        dumpClientRequestRecord(new ClientRequestRecord(serverNode.getNodeIdentifier(), serverNode.getNodeIdentifier(),
                now, request, linkLoads.size(), linkLoads, networkResult.result, null, false), mapper);

        if (RequestResult.FAIL.equals(networkResult.result)) {
            getSimulationState().incrementRequestsFailedForNetworkLoad();
            LOGGER.info("Request failed for network load");
            return Pair.of(false, latestEndOfRequest);
        } else {
            if (RequestResult.SLOW.equals(networkResult.result)) {
                getSimulationState().incrementRequestsSlowForNetworkLoad();
            }

            final RegionIdentifier destinationRegion = serverNode.getRegionIdentifier();
            getSimulationState().incrementRequestsServicedByRegion(destinationRegion);

            final long endOfThisRequest = request.getStartTime() + request.getNetworkDuration();
            final long newLatestEndOfRequest = Math.max(latestEndOfRequest, endOfThisRequest);

            return Pair.of(true, newLatestEndOfRequest);
        }
    }
}
