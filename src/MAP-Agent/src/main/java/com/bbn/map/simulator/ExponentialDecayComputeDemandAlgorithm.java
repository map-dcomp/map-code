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

import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.utils.ImmutableUtils;
import com.google.common.collect.ImmutableMap;

/**
 * Track compute demand for a particular estimation window using exponential
 * decay.
 * 
 * @author jschewe
 *
 */
/* package */ class ExponentialDecayComputeDemandAlgorithm implements ComputeDemandAlgorithm {

    private final double alpha;

    private final Map<NodeIdentifier, Map<NodeAttribute, Double>> demand = new HashMap<>();

    /**
     * 
     * @param alpha
     *            the decay value
     */
    ExponentialDecayComputeDemandAlgorithm(final double alpha) {
        this.alpha = alpha;
    }

    @Override
    public void updateDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> computeLoad) {

        computeLoad.forEach((sourceNode, nodeData) -> {
            final Map<NodeAttribute, Double> sourceNodeDemand = demand.computeIfAbsent(sourceNode,
                    k -> new HashMap<>());
            nodeData.forEach((attr, value) -> {
                final double newValue;
                if (sourceNodeDemand.containsKey(attr)) {
                    final double oldValue = sourceNodeDemand.get(attr);
                    newValue = exponentialDecay(alpha, oldValue, value);
                } else {
                    newValue = value;
                }
                sourceNodeDemand.put(attr, newValue);
            });

            // decay attributes that aren't in computeLoad
            for (Map.Entry<NodeAttribute, Double> entry : sourceNodeDemand.entrySet()) {
                if (!nodeData.containsKey(entry.getKey())) {
                    final double newValue = exponentialDecay(alpha, entry.getValue(), 0);
                    entry.setValue(newValue);
                }
            }
        });

        // decay sourceNodes that aren't in computeLoad
        demand.forEach((sourceNode, sourceNodeDemand) -> {
            if (!computeLoad.containsKey(sourceNode)) {
                sourceNodeDemand.replaceAll((attr, v) -> exponentialDecay(alpha, v, 0));
            }
        });
    }

    /**
     * Base exponential decay computation.
     * 
     * @param alpha
     *            the decay variable
     * @param oldValue
     *            the previous value for the demand
     * @param value
     *            the new sample
     * @return the new demand value
     */
    public static double exponentialDecay(final double alpha, final double oldValue, final double value) {
        final double newValue = oldValue + alpha * (value - oldValue);
        return newValue;
    }

    @Override
    public ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> computeComputeDemand() {
        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> reportDemand = ImmutableUtils
                .makeImmutableMap2(this.demand);
        return reportDemand;
    }

}
