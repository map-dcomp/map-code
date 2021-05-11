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

import javax.annotation.Nonnull;

import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.utils.ImmutableUtils;
import com.google.common.collect.ImmutableMap;

/**
 * Track network demand for a particular estimation window using exponential
 * decay.
 * 
 * @author jschewe
 *
 */
/* package */ class ExponentialDecayNetworkDemandAlgorithm implements NetworkDemandAlgorithm {

    private final double alpha;

    private final Map<InterfaceIdentifier, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> demand = new HashMap<>();

    /**
     * 
     * @param alpha
     *            the decay value
     */
    ExponentialDecayNetworkDemandAlgorithm(final double alpha) {
        this.alpha = alpha;
    }

    @Override
    public void updateDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> networkLoad) {

        networkLoad.forEach((ifce, ifceData) -> {
            final Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> ifceDemand = demand
                    .computeIfAbsent(ifce, k -> new HashMap<>());

            ifceData.forEach((flow, flowData) -> {
                final Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>> flowDemand = ifceDemand
                        .computeIfAbsent(flow, k -> new HashMap<>());

                flowData.forEach((service, serviceData) -> {
                    final Map<LinkAttribute, Double> serviceDemand = flowDemand.computeIfAbsent(service,
                            k -> new HashMap<>());

                    serviceData.forEach((attr, value) -> {
                        final double newValue;
                        if (serviceDemand.containsKey(attr)) {
                            final double oldValue = serviceDemand.get(attr);
                            newValue = ExponentialDecayComputeDemandAlgorithm.exponentialDecay(alpha, oldValue, value);
                        } else {
                            newValue = value;
                        }
                        serviceDemand.put(attr, newValue);
                    }); // foreach attribute in network load

                    // decay attributes not in networkLoad
                    for (Map.Entry<LinkAttribute, Double> entry : serviceDemand.entrySet()) {
                        if (!serviceData.containsKey(entry.getKey())) {
                            final double newValue = ExponentialDecayComputeDemandAlgorithm.exponentialDecay(alpha,
                                    entry.getValue(), 0);
                            entry.setValue(newValue);
                        }
                    }

                }); // foreach service in network load

                // decay services not in network load
                flowDemand.forEach((service, serviceDemand) -> {
                    if (!flowData.containsKey(service)) {
                        serviceDemand.replaceAll(
                                (attr, v) -> ExponentialDecayComputeDemandAlgorithm.exponentialDecay(alpha, v, 0));
                    }
                });

            }); // foreach flow in network load

            // decay flows not in network load
            ifceDemand.forEach((flow, flowDemand) -> {
                if (!ifceData.containsKey(flow)) {
                    flowDemand.forEach((service, serviceDemand) -> {
                        serviceDemand.replaceAll(
                                (attr, v) -> ExponentialDecayComputeDemandAlgorithm.exponentialDecay(alpha, v, 0));
                    });
                }
            });

        }); // foreach interface in network load

        // decay interfaces that aren't in networkLoad
        demand.forEach((ifce, ifceDemand) -> {
            if (!networkLoad.containsKey(ifce)) {
                ifceDemand.forEach((flow, flowDemand) -> {
                    flowDemand.forEach((service, serviceDemand) -> {
                        serviceDemand.replaceAll(
                                (attr, v) -> ExponentialDecayComputeDemandAlgorithm.exponentialDecay(alpha, v, 0));
                    });
                });
            }
        });
    }

    @Override
    public ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> computeNetworkDemand() {
        final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportDemand = ImmutableUtils
                .makeImmutableMap4(this.demand);
        return reportDemand;
    }

}
