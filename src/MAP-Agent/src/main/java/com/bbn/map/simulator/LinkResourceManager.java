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
import java.util.Objects;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;

import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.utils.ImmutableUtils;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableMap;

/**
 * Track link information. This object is shared amoung the resource managers
 * for the nodes at both ends. This ensures that we don't need to compute load
 * twice for each end of the link and possibly have them inconsistent.
 * 
 * @author jschewe
 *
 */
class LinkResourceManager {

    private final Object lock = new Object();

    private final int hashCode;

    private final VirtualClock clock;
    private final double bandwidth;

    private final NodeIdentifier left;

    /**
     * 
     * @return the lesser identifier
     */
    @Nonnull
    public NodeIdentifier getLeft() {
        return left;
    }

    private final NodeIdentifier right;

    /**
     * 
     * @return the greater identifier
     */
    @Nonnull
    public NodeIdentifier getRight() {
        return right;
    }

    /**
     * Calls
     * {@link #LinkResourceManager(VirtualClock, NodeIdentifier, NodeIdentifier, double)
     * with the values from link.
     * 
     */
    /* package */ LinkResourceManager(@Nonnull final VirtualClock clock, @Nonnull final NetworkLink link) {
        this(clock, link.getLeft().getNodeIdentifier(), link.getRight().getNodeIdentifier(), link.getBandwidth());
    }

    /**
     * The left node is determined by comparing the names of the identifiers of
     * the two nodes. The one that compares less than is the left node. This is
     * done to aide in lookup.
     * 
     * @param clock
     *            the clock used when computing the current link load
     * @param one
     *            one of the nodes on the link
     * @param two
     *            the other node on the link
     * @param bandwidth
     *            the bandwidth of this link in bytes per second
     * @throws IllegalArgumentException
     *             if the two identifiers are equal
     */
    /* package */ LinkResourceManager(@Nonnull final VirtualClock clock,
            @Nonnull final NodeIdentifier one,
            @Nonnull final NodeIdentifier two,
            final double bandwidth) throws IllegalArgumentException {
        this.clock = clock;
        this.bandwidth = bandwidth;

        // this comparison for left and right needs to match
        // Simulation.getLinkResourceManager()
        final int compareResult = one.getName().compareTo(two.getName());
        if (compareResult < 0) {
            left = one;
            right = two;
        } else if (compareResult > 0) {
            left = two;
            right = one;
        } else {
            throw new IllegalArgumentException(
                    String.format("Links cannot go from a node to itself %s == %s", one, two));
        }
        hashCode = Objects.hash(left, right);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (null == o) {
            return false;
        } else if (this == o) {
            return true;
        } else if (getClass().equals(o.getClass())) {
            final LinkResourceManager other = (LinkResourceManager) o;
            return getLeft().equals(other.getLeft()) && getRight().equals(other.getRight());
        } else {
            return false;
        }
    }

    /**
     * The value specifies which linked node the load goes on.
     */
    private List<LinkLoadEntry> linkLoad = new LinkedList<>();

    /**
     * Specify some load on the link.
     * 
     * @param req
     *            the client request, only networkLoad is used
     * @param client
     *            the client causing the load
     * @return status of the request, if the status is
     *         {@link ClientSim.RequestResult#FAIL}, then the link load is not
     *         modified. The {@link LinkLoadEntry} can be used to remove the
     *         link load with {@link #removeLinkLoad(LinkLoadEntry)}
     * @see #removeLinkLoad(ClientRequest, NodeIdentifier)
     */
    public Pair<ClientSim.RequestResult, LinkLoadEntry> addLinkLoad(final NodeIdentifier client,
            final ClientRequest req) {
        synchronized (lock) {
            // need to add, then check the thresholds and then possibly
            // remove
            // TODO: ticket:29 will make the duration variable based on network
            // path state
            final LinkLoadEntry entry = new LinkLoadEntry(req, req.getNetworkDuration(), client);
            linkLoad.add(entry);

            final Pair<ImmutableMap<LinkAttribute<?>, Double>, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>>> linkLoadResult = computeCurrentLinkLoad();
            final ImmutableMap<LinkAttribute<?>, Double> load = linkLoadResult.getLeft();

            final ClientSim.RequestResult result = determineClientRequestStatus(load);

            if (result == ClientSim.RequestResult.FAIL) {
                linkLoad.remove(entry);
            }

            return Pair.of(result, entry);
        }
    }

    /**
     * Undoes the changes done by {@Link #addLinkLoad(ClientRequest)}.
     *
     * @param entry
     *            the return value from {@link #addLinkLoad(ClientRequest)}
     * @return true if the load was removed
     * @see #addLinkLoad(ClientRequest)
     */
    public boolean removeLinkLoad(final LinkLoadEntry entry) {
        synchronized (lock) {
            return linkLoad.remove(entry);
        }
    }

    /**
     * Compute the current link load for the link. The lock must be held for
     * this method to be called.
     * 
     * @return (aggregate link load, link load by client)
     */
    /* package */ Pair<ImmutableMap<LinkAttribute<?>, Double>, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute<?>, Double>>> computeCurrentLinkLoad() {
        final long now = clock.getCurrentTime();

        final Map<LinkAttribute<?>, Double> load = new HashMap<>();
        final Map<NodeIdentifier, Map<LinkAttribute<?>, Double>> loadPerClient = new HashMap<>();

        final Iterator<LinkLoadEntry> linkDemandIter = linkLoad.iterator();
        while (linkDemandIter.hasNext()) {
            final LinkLoadEntry entry = linkDemandIter.next();
            final ClientRequest req = entry.getRequest();
            final long duration = entry.getDuration();
            final long end = req.getStartTime() + duration;
            final NodeIdentifier client = entry.getClient();

            if (req.getStartTime() <= now && now < end) {
                final ImmutableMap<LinkAttribute<?>, Double> clientNetworkLoad = req.getNetworkLoadAsAttribute();
                clientNetworkLoad.forEach((k, v) -> {
                    load.merge(k, v, Double::sum);

                    final Map<LinkAttribute<?>, Double> clientLoad = loadPerClient.computeIfAbsent(client,
                            k1 -> new HashMap<>());
                    clientLoad.merge(k, v, Double::sum);
                });

            } else if (end > now) {
                // already happened, drop it
                linkDemandIter.remove();
            }
        }

        return Pair.of(ImmutableMap.copyOf(load), ImmutableUtils.makeImmutableMap2(loadPerClient));
    }

    private ClientSim.RequestResult determineClientRequestStatus(
            final ImmutableMap<LinkAttribute<?>, Double> linkLoad) {
        for (final Map.Entry<LinkAttribute<?>, Double> entry : linkLoad.entrySet()) {
            final LinkAttribute<?> attribute = entry.getKey();
            if (Simulation.LINK_BANDWIDTH_ATTRIBUTE.equals(attribute)) {

                final double attributeValue = entry.getValue();

                final double percentageOfCapacity = attributeValue / bandwidth;
                if (percentageOfCapacity > 1) {
                    return ClientSim.RequestResult.FAIL;
                } else if (percentageOfCapacity > SimulationConfiguration.getInstance().getSlowNetworkThreshold()) {
                    return ClientSim.RequestResult.SLOW;
                }

            }
        }

        return ClientSim.RequestResult.SUCCESS;
    }

    /**
     * Class for storing information about the link load.
     * 
     * @author jschewe
     *
     */
    public static final class LinkLoadEntry {
        /**
         * 
         * @param request
         *            see {@link #getRequest()}
         * @param duration
         *            see {@link #getDuration()}
         * @param client
         *            see {@Link #getClient()}
         */
        /* package */ LinkLoadEntry(@Nonnull final ClientRequest request,
                final long duration,
                @Nonnull final NodeIdentifier client) {
            this.request = request;
            this.duration = duration;
            this.client = client;
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
         *         of the network at the time that the client requested the
         *         service
         */
        public long getDuration() {
            return duration;
        }

        private final NodeIdentifier client;

        /**
         * 
         * @return the client generating the load
         */
        public NodeIdentifier getClient() {
            return client;
        }
    }

}
