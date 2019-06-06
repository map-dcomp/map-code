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

import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.value.LinkMetricName;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableMap;

/**
 * Track link information. This object is shared among the resource managers for
 * the nodes at both ends. This ensures that we don't need to compute load twice
 * for each end of the link and possibly have them inconsistent.
 * 
 * The receiving node is defined to be the one with the lesser hashcode. This is
 * done to make equality and hashcode operations easy.
 * 
 * @author jschewe
 *
 */
/* package */ final class LinkResourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkResourceManager.class);

    private final Object lock = new Object();

    private final int hashCode;

    private final ImmutableMap<LinkAttribute<?>, Double> capacity;

    private final LinkLoadTracker loadTracker = new LinkLoadTracker();

    /**
     * 
     * @return the capacity of this link
     */
    public ImmutableMap<LinkAttribute<?>, Double> getCapacity() {
        return capacity;
    }

    private final NodeIdentifier receiver;

    /**
     * @return The default node for receiving traffic.
     * @see #addLinkLoad(long, ClientLoad, NodeIdentifier, NodeIdentifier)
     */
    @Nonnull
    public NodeIdentifier getReceiver() {
        return receiver;
    }

    private final NodeIdentifier transmitter;

    /**
     * 
     * @return The default node for transmitting traffic.
     * @see #addLinkLoad(long, ClientLoad, NodeIdentifier, NodeIdentifier)
     */
    @Nonnull
    public NodeIdentifier getTransmitter() {
        return transmitter;
    }

    /**
     * Calls
     * {@link #LinkResourceManager(VirtualClock, NodeIdentifier, NodeIdentifier, ImmutableMap)
     * with the values from link.
     * 
     * @see LinkMetricName#DATARATE_RX
     * @see LinkMetricName#DATARATE_TX
     */
    /* package */ LinkResourceManager(@Nonnull final NetworkLink link) {
        this(link.getLeft().getNodeIdentifier(), link.getRight().getNodeIdentifier(), ImmutableMap
                .of(LinkMetricName.DATARATE_RX, link.getBandwidth(), LinkMetricName.DATARATE_TX, link.getBandwidth()));
    }

    /**
     * The left node is determined by comparing the names of the identifiers of
     * the two nodes. The one that compares less than is the left node. This is
     * done to aide in lookup.
     * 
     * @param one
     *            one of the nodes on the link
     * @param two
     *            the other node on the link
     * @param capacity
     *            the capacity of this link in megabits per second
     * @throws IllegalArgumentException
     *             if the two identifiers are equal
     * @throws IllegalArgumentException
     *             if the datarate rx and tx are not equal, async links are not
     *             supported
     */
    /* package */ LinkResourceManager(@Nonnull final NodeIdentifier one,
            @Nonnull final NodeIdentifier two,
            @Nonnull final ImmutableMap<LinkAttribute<?>, Double> capacity) throws IllegalArgumentException {
        this.capacity = capacity;

        // this comparison for left and right needs to match
        // Simulation.getLinkResourceManager()
        final int compareResult = one.getName().compareTo(two.getName());
        if (compareResult < 0) {
            receiver = one;
            transmitter = two;
        } else if (compareResult > 0) {
            receiver = two;
            transmitter = one;
        } else {
            throw new IllegalArgumentException(
                    String.format("Links cannot go from a node to itself %s == %s", one, two));
        }
        hashCode = Objects.hash(receiver, transmitter);

        final double capacityRx = this.capacity.getOrDefault(LinkMetricName.DATARATE_RX, 0D);
        final double capacityTx = this.capacity.getOrDefault(LinkMetricName.DATARATE_TX, 0D);
        final double capacityTolerance = 1E-6;
        if (Math.abs(capacityRx - capacityTx) > capacityTolerance) {
            throw new IllegalArgumentException(
                    String.format("Rx (%f) and Tx (%f) capacities must be equal", capacityRx, capacityTx));
        }
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
            return getReceiver().equals(other.getReceiver()) && getTransmitter().equals(other.getTransmitter());
        } else {
            return false;
        }
    }

    /**
     * Specify some load on the link.
     * 
     * @param now
     *            the current time from the clock used
     * @param req
     *            the client request, only networkLoad is used
     * @param client
     *            the client causing the load. This is the originator of the
     *            traffic, not necessarily one of the end points of the link.
     * @param transmittingNode
     *            the end point that is doing the transmitting, must be one of
     *            {@link #getReceiver()} or {@link #getTransmitter(). This is
     *            used to determine how to store
     *            {@link LinkMetricName#DATARATE_RX} and
     *            {@link LinkMetricName#DATARATE_TX} so that this information
     *            can be properly reported in resource reports.
     * @return status of the request, if the status is
     *         {@link ClientSim.RequestResult#FAIL}, then the link load is not
     *         modified. The {@link LinkLoadEntry} can be used to remove the
     *         link load with {@link #removeLinkLoad(LinkLoadEntry)}. The
     *         resulting load is also returned.
     * @see #removeLinkLoad(ClientLoad, NodeIdentifier)
     */
    public ImmutableTriple<ClientSim.RequestResult, LinkLoadEntry, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> addLinkLoad(
            final long now,
            @Nonnull final ClientLoad req,
            @Nonnull final NodeIdentifier client,
            @Nonnull final NodeIdentifier transmittingNode) {
        synchronized (lock) {
            final boolean flipDatarateDirection;
            if (getTransmitter().equals(transmittingNode)) {
                flipDatarateDirection = false;
            } else if (getReceiver().equals(transmittingNode)) {
                flipDatarateDirection = true;
            } else {
                throw new IllegalArgumentException(String.format("The transmitting node (%s) must be %s or %s",
                        transmittingNode, getReceiver(), getTransmitter()));
            }

            final ImmutableMap<LinkAttribute<?>, Double> networkLoad;
            if (flipDatarateDirection) {
                networkLoad = req.getNetworkLoadAsAttributeFlipped();
            } else {
                networkLoad = req.getNetworkLoadAsAttribute();
            }

            // first remove expired entries
            loadTracker.removeExpiredEntries(now, ignore -> {
            });

            // need to add, then check the thresholds and then possibly
            // remove
            final LinkLoadEntry entry = new LinkLoadEntry(this, client, req.getStartTime(), req.getNetworkDuration(),
                    req.getService(), networkLoad);
            loadTracker.addLoad(entry);

            final ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> load = loadTracker
                    .getCurrentLoad();

            final ClientSim.RequestResult result = determineClientRequestStatus();

            if (ClientSim.RequestResult.FAIL.equals(result)) {
                loadTracker.removeLoad(entry);
            }

            return ImmutableTriple.of(result, entry, load);
        }
    }

    /**
     * Undoes the changes done by {@Link #addLinkLoad(ClientRequest)}.
     *
     * @param entry
     *            the return value from {@link #addLinkLoad(ClientLoad)}
     * @return true if the load was removed
     * @see #addLinkLoad(ClientLoad)
     */
    public void removeLinkLoad(final LinkLoadEntry entry) {
        synchronized (lock) {
            loadTracker.removeLoad(entry);
        }
    }

    /**
     * Compute the current link load for the link.
     * 
     * @param now
     *            the current time from the clock used
     * @param receivingNode
     *            the node that is receiving traffic. If this does not match
     *            {@link #getReceiver()}, then the network RX/TX values will be
     *            flipped.
     * @return source -> service -> attribute -> value
     */
    public ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>> computeCurrentLinkLoad(
            final long now,
            @Nonnull final NodeIdentifier receivingNode) {

        synchronized (lock) {
            // first remove expired entries
            loadTracker.removeExpiredEntries(now, ignore -> {
            });

            if (getTransmitter().equals(receivingNode)) {
                return loadTracker.getCurrentLoadFlipped();
            } else if (getReceiver().equals(receivingNode)) {
                return loadTracker.getCurrentLoad();
            } else {
                throw new IllegalArgumentException(String.format("The receiving node (%s) must be %s or %s",
                        receivingNode, getReceiver(), getTransmitter()));
            }
        }
    }

    private ClientSim.RequestResult determineClientRequestStatus() {
        final Map<LinkAttribute<?>, Double> aggregateLinkLoad = loadTracker.getCurrentTotalLoad();

        for (final Map.Entry<LinkAttribute<?>, Double> entry : aggregateLinkLoad.entrySet()) {
            final LinkAttribute<?> attribute = entry.getKey();
            final double attributeValue = entry.getValue();
            final double attributeCapacity = capacity.getOrDefault(attribute, 0D);

            final double percentageOfCapacity = attributeValue / attributeCapacity;
            if (percentageOfCapacity > 1) {
                LOGGER.trace("adding link load failed attribute {} capacity: {} value: {}", attribute, capacity,
                        attributeValue);

                return ClientSim.RequestResult.FAIL;
            } else if (percentageOfCapacity > SimulationConfiguration.getInstance().getSlowNetworkThreshold()) {
                return ClientSim.RequestResult.SLOW;
            }
        }

        return ClientSim.RequestResult.SUCCESS;
    }

}
