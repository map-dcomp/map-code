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
package com.bbn.map.ap;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.value.NodeMetricName;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Some utilties for working with {@link ResourceReport} objects.
 * 
 * @author jschewe
 *
 */
public final class ReportUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportUtils.class);

    private ReportUtils() { // no instances
    }

    /**
     * Compute percentage of capacity for either load or demand.
     * 
     * @param serverCapacity
     *            the server capacity from
     *            {@link ResourceReport#getComputeCapacity()}
     * @param serverLoad
     *            the load from {@link ResourceReport#getServerLoad()} or demand
     *            from {@link ResourceReport#getServerDemand()}
     * @return the load percentage for {@link NodeMetricName#TASK_CONTAINERS}.
     *         The value is between 0 and 1.
     */
    public static double computeServerContainersPercentage(final ImmutableMap<NodeAttribute<?>, Double> serverCapacity,
            final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> serverLoad) {
        final NodeAttribute<?> containersAttribute = NodeMetricName.TASK_CONTAINERS;

        if (serverCapacity.containsKey(containersAttribute)) {
            final double capacity = serverCapacity.get(containersAttribute);

            double load = 0;
            for (final Map.Entry<?, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> serviceEntry : serverLoad
                    .entrySet()) {
                for (final Map.Entry<?, ImmutableMap<NodeAttribute<?>, Double>> regionEntry : serviceEntry.getValue()
                        .entrySet()) {
                    final double value = regionEntry.getValue().getOrDefault(containersAttribute, 0D);

                    LOGGER.trace("Adding {} to load from service {} and node {}", value, serviceEntry.getKey(), regionEntry.getKey());

                    load += value;
                }
            }

            LOGGER.trace("capacity: {} load: {}", capacity, load);

            return load / capacity;
        } else {
            LOGGER.debug("No task containers in server capacity {}, using 0 for computed load percentage",
                    serverCapacity);

            return 0;
        }
    }

    /**
     * @param report
     *            the report to work with
     * @return the load percentage for {@link NodeMetricName#TASK_CONTAINERS}.
     *         The value is between 0 and 1.
     * @see #computeServerContainersPercentage(ImmutableMap, ImmutableMap)
     * @see ResourceReport#getServerLoad()
     */
    public static double computeServerLoadPercentage(final ResourceReport report) {
        final ImmutableMap<NodeAttribute<?>, Double> serverCapacity = report.getAllocatedComputeCapacity();
        final ImmutableMap<ServiceIdentifier<?>, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute<?>, Double>>> serverLoad = report
                .getComputeLoad();

        return computeServerContainersPercentage(serverCapacity, serverLoad);
    }

}
