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

import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Track compute demand for a particular estimation window using a moving
 * average.
 * 
 * @author jschewe
 *
 */
/* package */ class MovingAverageComputeDemandAlgorithm implements ComputeDemandAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(MovingAverageComputeDemandAlgorithm.class);

    private final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>> computeLoadHistory = new HashMap<>();

    private final long duration;

    private final Map<NodeIdentifier, Map<NodeAttribute, Double>> sums = new HashMap<>();
    private final Map<NodeIdentifier, Map<NodeAttribute, Integer>> counts = new HashMap<>();

    /**
     * 
     * @param duration
     *            how long to average data over
     */
    MovingAverageComputeDemandAlgorithm(final long duration) {
        this.duration = duration;
    }

    @Override
    public void updateDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> computeLoad) {

        // add new entry
        computeLoadHistory.put(timestamp, computeLoad);
        addToCountAndSum(computeLoad);

        // clean out expired entries
        final long historyCutoff = timestamp - duration;

        final Iterator<Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>>> networkIter = computeLoadHistory
                .entrySet().iterator();
        while (networkIter.hasNext()) {
            final Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>> entry = networkIter
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

    private void removeFromCountAndSum(final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> value) {
        updateHistoryMapCountSum(-1, value, sums, counts);
    }

    private void addToCountAndSum(final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> value) {
        updateHistoryMapCountSum(1, value, sums, counts);
    }

    private static <K1, K2> void updateHistoryMapCountSum(final int multiplier,
            final ImmutableMap<K1, ImmutableMap<K2, Double>> historyValue,
            final Map<K1, Map<K2, Double>> sums,
            final Map<K1, Map<K2, Integer>> counts) {
        historyValue.forEach((k1, v1) -> {
            final Map<K2, Double> sum1 = sums.computeIfAbsent(k1, k -> new HashMap<>());
            final Map<K2, Integer> count1 = counts.computeIfAbsent(k1, k -> new HashMap<>());

            v1.forEach((k2, value) -> {
                final double newSum = sum1.getOrDefault(k2, 0D) + multiplier * value;
                final int newCount = count1.getOrDefault(k2, 1) + multiplier * 1;

                sum1.put(k2, newSum);
                count1.put(k2, newCount);
            }); // v1.forEach

        }); // historyValue.forEach
    }

    @Override
    public ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> computeComputeDemand() {
        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> reportDemand = ComputeDemandTracker
                .historyMapAverage(sums, counts);
        return reportDemand;
    }

}
