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
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Track network demand for a particular estimation window using a moving
 * average.
 * 
 * @author jschewe
 *
 */
/* package */ class MovingAverageNetworkDemandAlgorithm implements NetworkDemandAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(MovingAverageNetworkDemandAlgorithm.class);

    private final Map<Long, ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>>> networkLoadHistory = new HashMap<>();

    private final long duration;

    private final Map<InterfaceIdentifier, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> sums = new HashMap<>();
    private final Map<InterfaceIdentifier, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Integer>>>> counts = new HashMap<>();

    /**
     * 
     * @param duration
     *            how long to average data over
     */
    MovingAverageNetworkDemandAlgorithm(final long duration) {
        this.duration = duration;
    }

    @Override
    public void updateDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> networkLoad) {

        // add new entry
        networkLoadHistory.put(timestamp, networkLoad);
        addToCountAndSum(networkLoad);

        // clean out expired entries
        final long historyCutoff = timestamp - duration;

        final Iterator<Map.Entry<Long, ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>>>> networkIter = networkLoadHistory
                .entrySet().iterator();
        while (networkIter.hasNext()) {
            final Map.Entry<Long, ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>>> entry = networkIter
                    .next();
            if (entry.getKey() < historyCutoff) {

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Removing network demand value {} because it's time {} is before {}", entry.getValue(),
                            entry.getKey(), historyCutoff);
                }

                removeFromCountAndSum(entry.getValue());
                networkIter.remove();
            }
        }
    }

    private void removeFromCountAndSum(
            final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> value) {
        updateHistoryMapCountSum(-1, value, sums, counts);
    }

    private void addToCountAndSum(
            final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> value) {
        updateHistoryMapCountSum(1, value, sums, counts);
    }

    private static <K1, K2, K3, K4> void updateHistoryMapCountSum(final int multiplier,
            final ImmutableMap<K1, ImmutableMap<K2, ImmutableMap<K3, ImmutableMap<K4, Double>>>> historyValue,
            final Map<K1, Map<K2, Map<K3, Map<K4, Double>>>> sums,
            final Map<K1, Map<K2, Map<K3, Map<K4, Integer>>>> counts) {
        historyValue.forEach((k1, v1) -> {
            final Map<K2, Map<K3, Map<K4, Double>>> sum1 = sums.computeIfAbsent(k1, k -> new HashMap<>());
            final Map<K2, Map<K3, Map<K4, Integer>>> count1 = counts.computeIfAbsent(k1, k -> new HashMap<>());

            v1.forEach((k2, v2) -> {
                final Map<K3, Map<K4, Double>> sum2 = sum1.computeIfAbsent(k2, k -> new HashMap<>());
                final Map<K3, Map<K4, Integer>> count2 = count1.computeIfAbsent(k2, k -> new HashMap<>());

                v2.forEach((k3, v3) -> {
                    final Map<K4, Double> sum3 = sum2.computeIfAbsent(k3, k -> new HashMap<>());
                    final Map<K4, Integer> count3 = count2.computeIfAbsent(k3, k -> new HashMap<>());

                    v3.forEach((k4, value) -> {
                        final double newSum = sum3.getOrDefault(k4, 0D) + multiplier * value;
                        final int newCount = count3.getOrDefault(k4, 0) + multiplier * 1;

                        sum3.put(k4, newSum);
                        count3.put(k4, newCount);
                    }); // v3.forEach

                }); // v2.forEach
            }); // v1.forEach
        }); // historyValue.forEach

    }

    @Override
    public ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> computeNetworkDemand() {
        final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportDemand = NetworkDemandTracker
                .historyMapAverage(sums, counts);
        return reportDemand;
    }

}
