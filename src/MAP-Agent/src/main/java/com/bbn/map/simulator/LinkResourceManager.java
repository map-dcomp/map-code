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

import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkLink;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNetworkFlow;
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

    private final ImmutableMap<LinkAttribute, Double> capacity;

    private final LinkLoadTracker loadTracker;

    private final double delay;

    /**
     * 
     * @return {@link NetworkLink#getDelay()}
     */
    public double getDelay() {
        return delay;
    }

    /**
     * 
     * @return the capacity of this link
     */
    public ImmutableMap<LinkAttribute, Double> getCapacity() {
        return capacity;
    }

    private final NodeIdentifier receiver;

    /**
     * @return The default node for receiving traffic.
     * @see #addLinkLoad(long, ImmutableMap, ImmutableMap,
     *      ApplicationCoordinates, long, NodeIdentifier, NodeIdentifier)
     */
    @Nonnull
    public NodeIdentifier getReceiver() {
        return receiver;
    }

    private final NodeIdentifier transmitter;

    /**
     * 
     * @return The default node for transmitting traffic.
     * @see #addLinkLoad(long, ImmutableMap, ImmutableMap,
     *      ApplicationCoordinates, long, NodeIdentifier, NodeIdentifier)
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
     * @see LinkAttribute#DATARATE_RX
     * @see LinkAttribute#DATARATE_TX
     */
    /* package */ LinkResourceManager(@Nonnull final NetworkLink link) {
        this(link.getLeft().getNodeIdentifier(), link.getRight().getNodeIdentifier(),
                ImmutableMap.of(LinkAttribute.DATARATE_RX, link.getBandwidth(), LinkAttribute.DATARATE_TX,
                        link.getBandwidth(), LinkAttribute.DELAY, link.getDelay()),
                link.getDelay());
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
     * @param delay
     *            {@link NetworkLink#getDelay()}
     * @throws IllegalArgumentException
     *             if the two identifiers are equal
     * @throws IllegalArgumentException
     *             if the datarate rx and tx are not equal, async links are not
     *             supported
     */
    /* package */ LinkResourceManager(@Nonnull final NodeIdentifier one,
            @Nonnull final NodeIdentifier two,
            @Nonnull final ImmutableMap<LinkAttribute, Double> capacity,
            final double delay) throws IllegalArgumentException {
        this.capacity = capacity;
        this.delay = delay;
        this.loadTracker = new LinkLoadTracker();

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

        final double capacityRx = this.capacity.getOrDefault(LinkAttribute.DATARATE_RX, 0D);
        final double capacityTx = this.capacity.getOrDefault(LinkAttribute.DATARATE_TX, 0D);
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
     * @param startTime
     *            The current time and the start of the network request.
     * @param networkLoadAsAttribute
     *            {@link BaseNetworkLoad#getNetworkLoadAsAttribute()}
     * @param networkLoadAsAttributeFlipped
     *            {@link BaseNetworkLoad#getNetworkLoadAsAttributeFlipped()}
     * @param service
     *            {@link BaseNetworkLoad#getService()}
     * @param duration
     *            {@link BaseNetworkLoad#getNetworkDuration()}
     * @param clientReqFlow
     *            the network flow information.
     * @param transmittingNode
     *            the end point that is doing the transmitting, must be one of
     *            {@link #getReceiver()} or {@link #getTransmitter(). This is
     *            used to determine how to store
     *            {@link LinkAttribute#DATARATE_RX} and
     *            {@link LinkAttribute#DATARATE_TX} so that this information can
     *            be properly reported in resource reports.
     * @return status of the request, if the status is
     *         {@link RequestResult#FAIL}, then the link load is not modified.
     *         The {@link LinkLoadEntry} can be used to remove the link load
     *         with {@link #removeLinkLoad(LinkLoadEntry)}. The resulting load
     *         is also returned.
     * @see #removeLinkLoad(BaseNetworkLoad, NodeIdentifier)
     * @see LoadTracker#addLoad(LoadEntry)
     */
    public ImmutableTriple<RequestResult, LinkLoadEntry, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> addLinkLoad(
            final long startTime,
            @Nonnull final ImmutableMap<LinkAttribute, Double> networkLoadAsAttribute,
            @Nonnull final ImmutableMap<LinkAttribute, Double> networkLoadAsAttributeFlipped,
            @Nonnull final ApplicationCoordinates service,
            final long duration,
            @Nonnull final RegionNetworkFlow clientReqFlow,
            @Nonnull final NodeIdentifier transmittingNode) {
        synchronized (lock) {

            // FIXME need to know if to include the load in the final result. The load needs to be added to the link for bandwidth checks, but not added to the resource report. 
            
            final boolean flipDatarateDirection;
            if (getTransmitter().equals(transmittingNode)) {
                flipDatarateDirection = false;
            } else if (getReceiver().equals(transmittingNode)) {
                flipDatarateDirection = true;
            } else {
                throw new IllegalArgumentException(String.format("The transmitting node (%s) must be %s or %s",
                        transmittingNode, getReceiver(), getTransmitter()));
            }

            // when flipping the network direction, the flow needs to be flipped
            // as well
            final ImmutableMap<LinkAttribute, Double> networkLoad;
            final RegionNetworkFlow flow;
            if (flipDatarateDirection) {
                networkLoad = networkLoadAsAttributeFlipped;
                flow = new RegionNetworkFlow(clientReqFlow.getDestination(), clientReqFlow.getSource(),
                        clientReqFlow.getServer());
            } else {
                networkLoad = networkLoadAsAttribute;
                flow = clientReqFlow;
            }

            // first remove expired entries
            loadTracker.removeExpiredEntries(startTime, ignore -> {
            });

            // need to add, then check the thresholds and then possibly
            // remove
            final LinkLoadEntry entry = new LinkLoadEntry(this, flow, startTime, duration, service, networkLoad);
            loadTracker.addLoad(entry);

            final ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> load = loadTracker
                    .getCurrentLoad();
            if (LOGGER.isTraceEnabled()) {
                // if statement to ensure that getCurrentLoad doesn't do work
                // unless it needs to
                LOGGER.trace("Link state for {} --> {}: load/capacity: {} / {}, ", getTransmitter(), getReceiver(),
                        loadTracker.getCurrentLoad(), getCapacity());
            }

            final RequestResult result = determineClientRequestStatus();

            if (RequestResult.FAIL.equals(result)) {
                loadTracker.removeLoad(entry);
            }

            return ImmutableTriple.of(result, entry, load);
        }
    }

    /**
     * Undoes the changes done by {@Link #addLinkLoad(ClientRequest)}.
     *
     * @param entry
     *            the return value from {@link #addLinkLoad(BaseNetworkLoad)}
     * @return true if the load was removed
     * @see #addLinkLoad(BaseNetworkLoad)
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
     * @return flow -> service -> attribute -> value
     */
    public ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> computeCurrentLinkLoad(
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

    private RequestResult determineClientRequestStatus() {
        final Map<LinkAttribute, Double> aggregateLinkLoad = loadTracker.getCurrentTotalLoad();

        for (final Map.Entry<LinkAttribute, Double> entry : aggregateLinkLoad.entrySet()) {
            final LinkAttribute attribute = entry.getKey();
            final double attributeValue = entry.getValue();

            if (capacity.containsKey(attribute)) {
                // don't check attributes that don't have capacity
                final double attributeCapacity = capacity.getOrDefault(attribute, 0D);

                final double percentageOfCapacity = attributeValue / attributeCapacity;
                if (percentageOfCapacity > 1) {
                    LOGGER.trace("adding link load failed: link: {} --> {}, attribute: {}, capacity: {}, value: {}",
                            getTransmitter(), getReceiver(), attribute, capacity, attributeValue);

                    return RequestResult.FAIL;
                } else if (percentageOfCapacity > SimulationConfiguration.getInstance().getSlowNetworkThreshold()) {
                    return RequestResult.SLOW;
                }
            }
        }

        return RequestResult.SUCCESS;
    }

}
