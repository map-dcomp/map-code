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
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Keep track of network demand.
 * 
 * @author jschewe
 *
 */
public class NetworkDemandTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkDemandTracker.class);

    private final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>>> networkLoadHistory = new HashMap<>();

    /**
     * Update the current demand state. Used for
     * {@link #computeNetworkDemand(long, com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow)}.
     * 
     * @param timestamp the time that the value was measured at
     * @param networkLoad the value measured
     */
    public void updateDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> networkLoad) {
        updateDemandValues(timestamp, networkLoad, networkLoadHistory);
    }

    private static void updateDemandValues(final long timestamp,
            final ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> networkLoad,
            final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>>> networkLoadHistory) {
        // add new entry
        networkLoadHistory.put(timestamp, networkLoad);

        // clean out old entries
        final long historyCutoff = timestamp
                - Math.max(AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis(),
                        AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis());

        final Iterator<Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>>>> networkIter = networkLoadHistory
                .entrySet().iterator();
        while (networkIter.hasNext()) {
            final Map.Entry<Long, ?> entry = networkIter.next();
            if (entry.getKey() < historyCutoff) {

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Removing network demand value {} because it's time {} is before {}", entry.getValue(),
                            entry.getKey(), historyCutoff);
                }

                networkIter.remove();
            }
        }
    }

    /**
     * Compute the current network demand per client.
     * 
     * @param now
     *            the current time
     * @param estimationWindow
     *            the window over which to compute the demand
     * @return the demand value
     */
    @Nonnull
    public ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> computeNetworkDemand(
            final long now, @Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        return computeNetworkDemand(now, estimationWindow, networkLoadHistory);
    }

    private ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> computeNetworkDemand(
            final long now,
            @Nonnull final ResourceReport.EstimationWindow estimationWindow,
            @Nonnull final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>>> networkLoadHistory) {
        final long duration;
        switch (estimationWindow) {
        case LONG:
            duration = AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis();
            break;
        case SHORT:
            duration = AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis();
            break;
        default:
            throw new IllegalArgumentException("Unknown estimation window: " + estimationWindow);
        }

        final long cutoff = now - duration;
        final Map<NodeIdentifier, Map<NodeIdentifier, Map<ServiceIdentifier<?>, Map<LinkAttribute<?>, Double>>>> sums = new HashMap<>();
        final Map<NodeIdentifier, Map<NodeIdentifier, Map<ServiceIdentifier<?>, Map<LinkAttribute<?>, Integer>>>> counts = new HashMap<>();
        historyMapCountSum(networkLoadHistory, cutoff, sums, counts);

        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeIdentifier, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute<?>, Double>>>> reportDemand = historyMapAverage(
                sums, counts);

        return reportDemand;
    }

    private static <K1, K2, K3, K4> void historyMapCountSum(
            final Map<Long, ImmutableMap<K1, ImmutableMap<K2, ImmutableMap<K3, ImmutableMap<K4, Double>>>>> history,
            final long historyCutoff,
            final Map<K1, Map<K2, Map<K3, Map<K4, Double>>>> sums,
            final Map<K1, Map<K2, Map<K3, Map<K4, Integer>>>> counts) {
        history.forEach((timestamp, historyValue) -> {
            if (timestamp >= historyCutoff) {

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
                                final double newSum = sum3.getOrDefault(k4, 0D) + value;
                                final int newCount = count3.getOrDefault(k4, 1) + 1;

                                sum3.put(k4, newSum);
                                count3.put(k4, newCount);
                            }); // v3.forEach

                        }); // v2.forEach
                    }); // v1.forEach
                }); // historyValue.forEach

            } // inside window
        }); // history.forEach

    }

    private static <K1, K2, K3, K4> ImmutableMap<K1, ImmutableMap<K2, ImmutableMap<K3, ImmutableMap<K4, Double>>>> historyMapAverage(
            final Map<K1, Map<K2, Map<K3, Map<K4, Double>>>> sums,
            final Map<K1, Map<K2, Map<K3, Map<K4, Integer>>>> counts) {
        final ImmutableMap.Builder<K1, ImmutableMap<K2, ImmutableMap<K3, ImmutableMap<K4, Double>>>> result = ImmutableMap
                .builder();

        sums.forEach((k1, sum1) -> {
            final Map<K2, Map<K3, Map<K4, Integer>>> count1 = counts.get(k1);
            final ImmutableMap.Builder<K2, ImmutableMap<K3, ImmutableMap<K4, Double>>> average1 = ImmutableMap
                    .builder();

            sum1.forEach((k2, sum2) -> {
                final Map<K3, Map<K4, Integer>> count2 = count1.get(k2);
                final ImmutableMap.Builder<K3, ImmutableMap<K4, Double>> average2 = ImmutableMap.builder();

                sum2.forEach((k3, sum3) -> {
                    final Map<K4, Integer> count3 = count2.get(k3);
                    final ImmutableMap.Builder<K4, Double> average3 = ImmutableMap.builder();

                    sum3.forEach((k4, value) -> {
                        final int count = count3.get(k4);
                        final double average = value / count;
                        average3.put(k4, average);
                    }); // sum3.forEach

                    average2.put(k3, average3.build());
                }); // sum2.forEach

                average1.put(k2, average2.build());

            }); // sum1.forEach

            result.put(k1, average1.build());
        }); // sums.forEach

        return result.build();
    }

}
