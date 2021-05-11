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

import com.bbn.map.AgentConfiguration;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.google.common.collect.ImmutableMap;

/**
 * Common code for tracking of compute demand.
 * 
 * @author jschewe
 *
 */
public class ComputeDemandTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeDemandTracker.class);

    private final ComputeDemandAlgorithm helperShort;
    private final ComputeDemandAlgorithm helperLong;
    private final Object helperLock = new Object();

    /**
     * Default constructor.
     */
    public ComputeDemandTracker() {
        if (AgentConfiguration.DemandComputationAlgorithm.MOVING_AVERAGE
                .equals(AgentConfiguration.getInstance().getDemandComputationAlgorithm())) {

            this.helperLong = new MovingAverageComputeDemandAlgorithm(
                    AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis());
            this.helperShort = new MovingAverageComputeDemandAlgorithm(
                    AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis());
        } else if (AgentConfiguration.DemandComputationAlgorithm.EXPONENTIAL_DECAY
                .equals(AgentConfiguration.getInstance().getDemandComputationAlgorithm())) {
            this.helperLong = new ExponentialDecayComputeDemandAlgorithm(
                    AgentConfiguration.getInstance().getDcopExponentialDemandDecay());
            this.helperShort = new ExponentialDecayComputeDemandAlgorithm(
                    AgentConfiguration.getInstance().getRlgExponentialDemandDecay());
        } else {
            throw new IllegalArgumentException("Unknown demand computation algorithm: "
                    + AgentConfiguration.getInstance().getDemandComputationAlgorithm());
        }
    }

    /**
     * Update demand values.
     * 
     * @param timestamp
     *            timestamp for {@code computeLoad}
     * @param computeLoad
     *            the current load
     */
    public void updateComputeDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> computeLoad) {

        LOGGER.debug("updateComputeDemandValues: timestamp: {}, computeLoad: {}", timestamp, computeLoad);

        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> newComputeLoad;
        synchronized (failedRequests) {
            // modify the compute load based on the failed requests
            if (!failedRequests.isEmpty()) {
                // TODO: ticket:86 assumes only the UNKNOWN node is used for the
                // source
                // of compute demand
                final Map<NodeAttribute, Double> newLoad = new HashMap<>(computeLoad.get(NodeIdentifier.UNKNOWN));

                final Iterator<Map.Entry<Long, Map<NodeAttribute, Double>>> iter = failedRequests.entrySet().iterator();
                while (iter.hasNext()) {
                    final Map.Entry<Long, Map<NodeAttribute, Double>> entry = iter.next();
                    final long endTime = entry.getKey();
                    final Map<NodeAttribute, Double> load = entry.getValue();
                    if (endTime > timestamp) {
                        load.forEach((attr, value) -> {
                            newLoad.merge(attr, value, Double::sum);
                        });
                    } else {
                        iter.remove();
                    }
                } // foreach failed request

                newComputeLoad = ImmutableMap.of(NodeIdentifier.UNKNOWN, ImmutableMap.copyOf(newLoad));
            } // if there are failed requests
            else {
                newComputeLoad = computeLoad;
            }
        } // critical section

        LOGGER.debug("updateComputeDemandValues: computeLoad: {}, newComputeLoad: {}, failedRequests: {}", computeLoad,
                newComputeLoad, failedRequests);

        synchronized (helperLock) {
            helperLong.updateDemandValues(timestamp, newComputeLoad);
            helperShort.updateDemandValues(timestamp, newComputeLoad);
        }
    }

    private final Map<Long, Map<NodeAttribute, Double>> failedRequests = new HashMap<>();

    /**
     * Add to the failed client request information.
     * 
     * @param serverEndTime
     *            expected end time of the server load
     * @param serverLoad
     *            server load of the request
     */
    public void addFailedRequest(final long serverEndTime, final Map<NodeAttribute, Double> serverLoad) {
        LOGGER.debug("addFailedRequest: serverEndTime: {}, serverLoad: {}, failedRequests.size() = {}", serverEndTime,
                serverLoad, failedRequests.size());

        final Map<NodeAttribute, Double> serverLoad2 = new HashMap<>(serverLoad);
        // ensure that TASK_CONTAINERS and CPU are the same
        if (serverLoad.containsKey(NodeAttribute.CPU) && !serverLoad.containsKey(NodeAttribute.TASK_CONTAINERS)) {
            serverLoad2.put(NodeAttribute.TASK_CONTAINERS, serverLoad.get(NodeAttribute.CPU));
        } else if (!serverLoad.containsKey(NodeAttribute.CPU)
                && serverLoad.containsKey(NodeAttribute.TASK_CONTAINERS)) {
            serverLoad2.put(NodeAttribute.CPU, serverLoad.get(NodeAttribute.TASK_CONTAINERS));
        }

        synchronized (failedRequests) {
            failedRequests.merge(serverEndTime, serverLoad2, (oldLoad, newLoad) -> {
                newLoad.forEach((attr, value) -> {
                    oldLoad.merge(attr, value, Double::sum);
                });
                return oldLoad;
            });

            LOGGER.debug("addFailedRequest: failedRequests.size() = {}, failedRequests: {}", failedRequests.size(),
                    failedRequests);
        }

    }

    /**
     * 
     * @param estimationWindow
     *            the window to compute the the demand over
     * @return the estimated demand as of the last call to
     *         {@link #updateComputeDemandValues(long, ImmutableMap)}
     */
    public ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> computeComputeDemand(
            @Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        synchronized (helperLock) {
            switch (estimationWindow) {
            case LONG:
                return helperLong.computeComputeDemand();
            case SHORT:
                return helperShort.computeComputeDemand();
            default:
                throw new IllegalArgumentException("Unknown estimation window: " + estimationWindow);
            }
        }
    }

    /* package */ static <K1, K2> ImmutableMap<K1, ImmutableMap<K2, Double>> historyMapAverage(
            final Map<K1, Map<K2, Double>> sums,
            final Map<K1, Map<K2, Integer>> counts) {
        final ImmutableMap.Builder<K1, ImmutableMap<K2, Double>> result = ImmutableMap.builder();

        sums.forEach((k1, sum1) -> {
            final Map<K2, Integer> count1 = counts.get(k1);
            final ImmutableMap.Builder<K2, Double> average1 = ImmutableMap.builder();

            sum1.forEach((k2, value) -> {
                final int count = count1.get(k2);
                final double average = (count > 0 ? value / count : 0.0);
                average1.put(k2, average);
            }); // sum1.forEach

            result.put(k1, average1.build());
        }); // sums.forEach

        return result.build();
    }

}
