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

import com.bbn.map.AgentConfiguration;
import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.ImmutableUtils;
import com.google.common.collect.ImmutableMap;

/**
 * Keep track of network demand.
 * 
 * @author jschewe
 *
 */
public class NetworkDemandTracker {

    private final NetworkDemandAlgorithm helperShort;
    private final NetworkDemandAlgorithm helperLong;
    private final Object helperLock = new Object();

    /**
     * Default constructor.
     */
    public NetworkDemandTracker() {
        if (AgentConfiguration.DemandComputationAlgorithm.MOVING_AVERAGE
                .equals(AgentConfiguration.getInstance().getDemandComputationAlgorithm())) {
            this.helperLong = new MovingAverageNetworkDemandAlgorithm(
                    AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis());
            this.helperShort = new MovingAverageNetworkDemandAlgorithm(
                    AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis());
        } else if (AgentConfiguration.DemandComputationAlgorithm.EXPONENTIAL_DECAY
                .equals(AgentConfiguration.getInstance().getDemandComputationAlgorithm())) {
            this.helperLong = new ExponentialDecayNetworkDemandAlgorithm(
                    AgentConfiguration.getInstance().getDcopExponentialDemandDecay());
            this.helperShort = new ExponentialDecayNetworkDemandAlgorithm(
                    AgentConfiguration.getInstance().getRlgExponentialDemandDecay());
        } else {
            throw new IllegalArgumentException("Unknown demand computation algorithm: "
                    + AgentConfiguration.getInstance().getDemandComputationAlgorithm());
        }
    }

    private static Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> mergeIfceData(
            final Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> oldIfceData,
            final Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> newIfceData) {

        // merge newIfceData into oldIfceData
        newIfceData.forEach((flow, flowData) -> {
            oldIfceData.merge(flow, flowData, (oldFlowData, newFlowData) -> {
                newFlowData.forEach((service, serviceData) -> {
                    oldFlowData.merge(service, serviceData, (oldServiceData, newServiceData) -> {
                        newServiceData.forEach((attr, value) -> {
                            oldServiceData.merge(attr, value, Double::sum);
                        });
                        return oldServiceData;
                    }); // merge attributes
                });
                return oldFlowData;
            }); // merge flowData
        });

        return oldIfceData;
    }

    /**
     * Update the current demand state. Used for
     * {@link #computeNetworkDemand(com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow)}.
     * 
     * @param timestamp
     *            the time that the value was measured at
     * @param networkLoad
     *            the value measured
     */
    public void updateDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> networkLoad) {

        final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> newNetworkLoad;
        synchronized (failedRequests) {
            // modify the compute load based on the failed requests
            if (!failedRequests.isEmpty()) {

                final Map<InterfaceIdentifier, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> newLoad = new HashMap<>();
                // put all of networkLoad into newLoad as mutable Maps so that
                // the merge below works
                networkLoad.forEach((ifce, ifceData) -> {
                    final Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> newIfceData = new HashMap<>();

                    ifceData.forEach((flow, flowData) -> {
                        final Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>> newFlowData = new HashMap<>();
                        flowData.forEach((service, serviceData) -> {
                            final Map<LinkAttribute, Double> newServiceData = new HashMap<>(serviceData);
                            newFlowData.put(service, newServiceData);
                        });
                        newIfceData.put(flow, newFlowData);
                    });
                    newLoad.put(ifce, newIfceData);
                });

                final Iterator<Map.Entry<Long, Map<InterfaceIdentifier, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>>>> iter = failedRequests
                        .entrySet().iterator();
                while (iter.hasNext()) {
                    final Map.Entry<Long, Map<InterfaceIdentifier, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>>> entry = iter
                            .next();
                    final long endTime = entry.getKey();
                    if (endTime > timestamp) {
                        // make atTime a deep copy of entry.getValue() from failedRequests to prevent any objects in failedRequests from getting into newLoad and being affected by the merge
                        final Map<InterfaceIdentifier, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> atTime = new HashMap<>();
                        entry.getValue().forEach((ifce, ifceData) -> {
                            ifceData.forEach((flow, flowData) -> {
                                flowData.forEach((service, serviceData) -> {
                                    serviceData.forEach((attr, value) -> {
                                        atTime.computeIfAbsent(ifce, k -> new HashMap<>())
                                            .computeIfAbsent(flow, k -> new HashMap<>())
                                            .computeIfAbsent(service, k -> new HashMap<>())
                                            .put(attr, value);
                                    });
                                });
                            });
                        });

                        // merge networkLoad and atTime into newLoad
                        atTime.forEach((ifce, ifceData) -> {
                            newLoad.merge(ifce, ifceData, NetworkDemandTracker::mergeIfceData);
                        });
                    } else {
                        iter.remove();
                    }
                } // foreach failed request

                newNetworkLoad = ImmutableUtils.makeImmutableMap4(newLoad);
            } // if there are failed requests
            else {
                newNetworkLoad = networkLoad;
            }
        } // critical section

        synchronized (helperLock) {
            helperLong.updateDemandValues(timestamp, newNetworkLoad);
            helperShort.updateDemandValues(timestamp, newNetworkLoad);
        }
    }

    /**
     * Compute the current network demand per interface.
     * 
     * @param estimationWindow
     *            the window over which to compute the demand
     * 
     * @return the demand value as of the last call to
     *         {@link #updateDemandValues(long, ImmutableMap)}
     */
    @Nonnull
    public ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> computeNetworkDemand(
            @Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        synchronized (helperLock) {
            switch (estimationWindow) {
            case LONG:
                return helperLong.computeNetworkDemand();
            case SHORT:
                return helperShort.computeNetworkDemand();
            default:
                throw new IllegalArgumentException("Unknown estimation window: " + estimationWindow);
            }
        }
    }

    /* package */ static <K1, K2, K3, K4> ImmutableMap<K1, ImmutableMap<K2, ImmutableMap<K3, ImmutableMap<K4, Double>>>> historyMapAverage(
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
                        final double average = (count > 0 ? value / count : 0.0);
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

    private final Map<Long, Map<InterfaceIdentifier, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>>> failedRequests = new HashMap<>();

    /**
     * Note that a request has failed.
     * 
     * @param networkEndTime
     *            the expected end time of the load had the request succeeded
     * @param rawLoad
     *            the expected load had the request succeeded
     * @param ifce
     *            the interface that the request was received on
     * @param client
     *            the client making the request
     * @param server
     *            the server that failed the request
     * @param service
     *            the requested service
     */
    public void addFailedRequest(final InterfaceIdentifier ifce,
            final NodeIdentifier client,
            final NodeIdentifier server,
            final ServiceIdentifier<?> service,
            final long networkEndTime,
            final Map<LinkAttribute, Double> rawLoad) {

        final Map<LinkAttribute, Double> rawLoad2 = new HashMap<>(rawLoad);
        final Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>> serviceLoad = new HashMap<>();
        serviceLoad.put(service, rawLoad2);

        final NodeNetworkFlow flow = new NodeNetworkFlow(client, server, server);
        final Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> flowLoad = new HashMap<>();
        flowLoad.put(flow, serviceLoad);

        final Map<InterfaceIdentifier, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> networkLoad = new HashMap<>();
        networkLoad.put(ifce, flowLoad);

        synchronized (failedRequests) {
            failedRequests.merge(networkEndTime, networkLoad, (oldLoad, newLoad) -> {

                // merge newLoad into oldLoad
                newLoad.forEach((i, ifceData) -> {
                    oldLoad.merge(i, ifceData, NetworkDemandTracker::mergeIfceData);
                });

                return oldLoad;
            });
        }
    }

}
