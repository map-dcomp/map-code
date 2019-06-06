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

import java.util.HashMap;
import java.util.Map;

import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.utils.ImmutableUtils;
import com.google.common.collect.ImmutableMap;

/**
 * Track node load for a {@link ContainerSim}.
 * 
 * @author jschewe
 *
 */
/* package */ final class NodeLoadTracker extends LoadTracker<NodeLoadEntry> {

    private final Map<NodeAttribute<?>, Double> currentTotalLoad = new HashMap<>();
    private final Map<NodeIdentifier, Map<NodeAttribute<?>, Double>> currentLoadPerClient = new HashMap<>();

    private transient ImmutableMap<NodeAttribute<?>, Double> currentLoadImmutable = null;

    /**
     * @return the current total load
     */
    public ImmutableMap<NodeAttribute<?>, Double> getTotalCurrentLoad() {
        if (null == currentLoadImmutable) {
            currentLoadImmutable = ImmutableMap.copyOf(currentTotalLoad);
        }
        return currentLoadImmutable;
    }

    private transient ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> currentLoadPerClientImmutable = null;

    /**
     * 
     * @return current load per client
     */
    public ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>> getCurrentLoadPerClient() {
        if (null == currentLoadPerClientImmutable) {
            currentLoadPerClientImmutable = ImmutableUtils.makeImmutableMap2(currentLoadPerClient);
        }
        return currentLoadPerClientImmutable;
    }

    @Override
    protected void postAddLoad(final NodeLoadEntry entry) {
        commonPost(entry, 1);
    }

    @Override
    protected void postRemoveLoad(final NodeLoadEntry entry) {
        commonPost(entry, -1);
    }

    private void commonPost(final NodeLoadEntry entry, final double multiplier) {
        entry.getRequest().getNodeLoad().forEach((attr, value) -> {
            currentTotalLoad.merge(attr, multiplier * value, Double::sum);
            currentLoadPerClient.computeIfAbsent(entry.getClient(), k -> new HashMap<>()).merge(attr,
                    multiplier * value, Double::sum);
        });
        currentLoadImmutable = null;
        currentLoadPerClientImmutable = null;
    }
}
