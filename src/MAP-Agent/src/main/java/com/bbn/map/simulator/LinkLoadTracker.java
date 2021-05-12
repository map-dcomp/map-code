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

import java.util.HashMap;
import java.util.Map;

import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.RegionNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.ImmutableUtils;
import com.google.common.collect.ImmutableMap;

/**
 * Track link load for a {@link LinkResourceManager}.
 * 
 * @author jschewe
 *
 */
/* package */ final class LinkLoadTracker extends LoadTracker<LinkLoadEntry> {

    private final Map<LinkAttribute, Double> currentTotalLoad = new HashMap<>();
    private transient ImmutableMap<LinkAttribute, Double> currentTotalLoadImmutable = null;

    /**
     * @return the current load
     */
    public ImmutableMap<LinkAttribute, Double> getCurrentTotalLoad() {
        if (null == currentTotalLoadImmutable) {
            currentTotalLoadImmutable = ImmutableMap.copyOf(currentTotalLoad);
        }
        return currentTotalLoadImmutable;
    }

    private final Map<RegionNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> currentLoad = new HashMap<>();
    private transient ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> currentLoadImmutable = null;

    private final Map<RegionNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> currentLoadFlipped = new HashMap<>();
    private transient ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> currentLoadFlippedImmutable = null;

    /**
     * @return current load for use in {@link ContainerResourceReport}
     */
    public ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> getCurrentLoad() {
        if (null == currentLoadImmutable) {
            currentLoadImmutable = ImmutableUtils.makeImmutableMap3(currentLoad);
        }
        return currentLoadImmutable;
    }

    /**
     * Has TX/RX flipped.
     * 
     * @return current load for use in {@link ContainerResourceReport}
     */
    public ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>> getCurrentLoadFlipped() {
        if (null == currentLoadFlippedImmutable) {
            currentLoadFlippedImmutable = ImmutableUtils.makeImmutableMap3(currentLoadFlipped);
        }
        return currentLoadFlippedImmutable;
    }

    @Override
    protected void postAddLoad(final LinkLoadEntry entry) {
        commonPost(entry, 1);
    }

    @Override
    protected void postRemoveLoad(final LinkLoadEntry entry) {
        commonPost(entry, -1);
    }

    private void commonPost(final LinkLoadEntry entry, final double multiplier) {
        entry.getNetworkLoad().forEach((attr, value) -> {
            currentTotalLoad.merge(attr, multiplier * value, Double::sum);

            currentLoad.computeIfAbsent(entry.getFlow(), k -> new HashMap<>())
                    .computeIfAbsent(entry.getService(), k -> new HashMap<>())
                    .merge(attr, multiplier * value, Double::sum);

            final RegionNetworkFlow flowFlipped = new RegionNetworkFlow(entry.getFlow().getDestination(),
                    entry.getFlow().getSource(), entry.getFlow().getServer());
            final LinkAttribute flippedAttr;
            if (LinkAttribute.DATARATE_RX.equals(attr)) {
                flippedAttr = LinkAttribute.DATARATE_TX;
            } else if (LinkAttribute.DATARATE_TX.equals(attr)) {
                flippedAttr = LinkAttribute.DATARATE_RX;
            } else {
                flippedAttr = attr;
            }

            currentLoadFlipped.computeIfAbsent(flowFlipped, k -> new HashMap<>())
                    .computeIfAbsent(entry.getService(), k -> new HashMap<>())
                    .merge(flippedAttr, multiplier * value, Double::sum);

        });

        currentTotalLoadImmutable = null;
        currentLoadImmutable = null;
        currentLoadFlippedImmutable = null;
    }
}
